package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.animation.AnimationManager;
import cn.fandmc.fandui.api.component.ComponentContext;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.UiContainer;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.UiEvent;
import cn.fandmc.fandui.api.focus.FocusManager;
import cn.fandmc.fandui.api.input.ClipboardService;
import cn.fandmc.fandui.api.input.CursorShape;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.session.SessionCloseListener;
import cn.fandmc.fandui.api.session.SessionCloseReason;
import cn.fandmc.fandui.api.session.UiSession;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.VisualState;
import cn.fandmc.fandui.api.text.TextService;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.core.layout.LayoutEngine;
import cn.fandmc.fandui.core.layout.LayoutSnapshot;
import cn.fandmc.fandui.core.scene.SceneCompiler;
import cn.fandmc.fandui.internal.component.ComponentBinding;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

abstract class AbstractCoreSession implements UiSession, ComponentBinding, ComponentContext {
    private static final UiViewport EMPTY_VIEWPORT = new UiViewport(0.0f, 0.0f, 0, 0, 1.0f);
    private static final EventType FRAME_EVENT_TYPE = EventType.getEventType(CoreFrameEvent.class);

    private final CoreUiRuntime runtime;
    private final UiComponent root;
    private final Theme theme;
    private final LayoutDirection layoutDirection;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();
    private final AtomicReference<SessionCloseReason> closeReason = new AtomicReference<>();
    private final Map<UiKey, UiComponent> keyedComponents = new LinkedHashMap<>();
    private final Set<UiComponent> components = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<CloseRegistration> closeListeners = new ArrayList<>();
    private final LayoutEngine layoutEngine = new LayoutEngine();
    private final SceneCompiler sceneCompiler = new SceneCompiler();
    private final CoreFocusManager focus;
    private final CoreAnimationManager animations;
    private final CoreEventDispatcher events;

    private volatile UiViewport viewport = EMPTY_VIEWPORT;
    private LayoutSnapshot layout;
    private DisplayList displayList;
    private boolean layoutDirty = true;
    private boolean paintDirty = true;
    private boolean detaching;
    private volatile int callbackDepth;
    private volatile PendingClose pendingClose;
    private long resourceGeneration = Long.MIN_VALUE;
    private UiComponent hovered;
    private UiComponent pressed;
    private UiComponent captured;

    AbstractCoreSession(
            CoreUiRuntime runtime,
            UiComponent root,
            Theme theme,
            LayoutDirection layoutDirection) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.root = Objects.requireNonNull(root, "root");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.layoutDirection = Objects.requireNonNull(layoutDirection, "layoutDirection");
        this.focus = new CoreFocusManager(this);
        this.animations = new CoreAnimationManager(this);
        this.events = new CoreEventDispatcher(this);
        runtime.assertUiThread();
        if (root.parent().isPresent()) {
            throw new IllegalStateException("Session root already belongs to a component tree");
        }
    }

    final void initialize() {
        runtime.assertUiThread();
        try {
            attachSubtree(root);
        } catch (RuntimeException | Error exception) {
            if (!active.get()) {
                pendingClose = null;
                cleanupStarted.set(true);
            }
            throw exception;
        }
    }

    @Override
    public final UiComponent root() {
        return root;
    }

    @Override
    public final boolean active() {
        return active.get();
    }

    @Override
    public final UiViewport viewport() {
        return viewport;
    }

    @Override
    public final FocusManager focus() {
        return focus;
    }

    @Override
    public final Theme theme() {
        return theme;
    }

    @Override
    public final AnimationManager animations() {
        return animations;
    }

    @Override
    public final Optional<UiComponent> find(UiKey key) {
        Objects.requireNonNull(key, "key");
        requireActiveUiThread();
        return Optional.ofNullable(keyedComponents.get(key));
    }

    @Override
    public final void invalidate() {
        requireActiveUiThread();
        layoutDirty = true;
        paintDirty = true;
    }

    @Override
    public final Optional<SessionCloseReason> closeReason() {
        return Optional.ofNullable(closeReason.get());
    }

    @Override
    public final EventRegistration onClose(SessionCloseListener listener) {
        Objects.requireNonNull(listener, "listener");
        requireActiveUiThread();
        CloseRegistration registration = new CloseRegistration(listener);
        synchronized (closeListeners) {
            closeListeners.add(registration);
        }
        return registration;
    }

    @Override
    public final void close() {
        requestClose(SessionCloseReason.API, notifyHostOnApiClose());
    }

    @Override
    public final UiSession session() {
        return this;
    }

    @Override
    public final TextService text() {
        return runtime.text();
    }

    @Override
    public final ResourceService resources() {
        return runtime.resources();
    }

    @Override
    public final ClipboardService clipboard() {
        return runtime.clipboard();
    }

    @Override
    public final CompletableFuture<Void> execute(Runnable action) {
        return runtime.defer(Objects.requireNonNull(action, "action"));
    }

    @Override
    public final void assertUiThread() {
        runtime.assertUiThread();
        if (!active.get() && !detaching) {
            throw new IllegalStateException("UI session is closed");
        }
    }

    @Override
    public final void invalidateLayout(UiComponent component) {
        requireMember(component);
        layoutDirty = true;
        paintDirty = true;
    }

    @Override
    public final void invalidatePaint(UiComponent component) {
        requireMember(component);
        paintDirty = true;
    }

    @Override
    public final void interactionChanged(UiComponent component) {
        requireMember(component);
        focus.reconcileEligibility();
        if (ancestorOrSelf(component, hovered)) {
            runtime.updateCursor(resolveCursor(hovered));
        }
    }

    @Override
    public final void childAdded(UiContainer parent, UiComponent child) {
        requireMember(parent);
        try {
            attachSubtree(child);
            layoutDirty = true;
            paintDirty = true;
        } catch (RuntimeException | Error exception) {
            requestCloseDeferred(SessionCloseReason.FAILED, true);
            throw exception;
        }
    }

    @Override
    public final void childRemoved(UiContainer parent, UiComponent child) {
        requireMember(parent);
        RuntimeException failure = detachSubtree(child);
        layoutDirty = true;
        paintDirty = true;
        if (failure != null) {
            requestClose(SessionCloseReason.FAILED, true);
        }
    }

    @Override
    public final void childReplaced(
            UiContainer parent,
            UiComponent previous,
            UiComponent replacement) {
        requireMember(parent);
        Map<UiKey, UiComponent> replacedKeys = subtreeKeys(previous);
        List<UiComponent> replacementNodes = collectSubtree(replacement);
        validateAttachable(replacementNodes, replacedKeys);
        try {
            attachNodes(replacementNodes, replacedKeys);
        } catch (RuntimeException | Error exception) {
            requestCloseDeferred(SessionCloseReason.FAILED, true);
            throw exception;
        }

        RuntimeException failure = detachSubtree(previous);
        layoutDirty = true;
        paintDirty = true;
        if (failure != null) {
            requestClose(SessionCloseReason.FAILED, true);
        }
    }

    public final boolean dispatch(UiEvent event) {
        Objects.requireNonNull(event, "event");
        requireActiveUiThread();
        if (!acceptsInput(event)) {
            return false;
        }
        enterCallback();
        try {
            return events.dispatch(event);
        } catch (RuntimeException | Error exception) {
            requestClose(SessionCloseReason.FAILED, true);
            throw exception;
        } finally {
            exitCallback();
        }
    }

    final UiSceneFrame prepareFrame(UiViewport nextViewport, long frameTimeNanos) {
        Objects.requireNonNull(nextViewport, "viewport");
        requireActiveUiThread();
        UiViewport previousViewport = viewport;
        long previousResourceGeneration = resourceGeneration;
        long nextResourceGeneration = runtime.resources().generation();
        if (resourceGeneration != nextResourceGeneration) {
            resourceGeneration = nextResourceGeneration;
            layoutDirty = true;
            paintDirty = true;
        }
        if (!nextViewport.equals(previousViewport)) {
            viewport = nextViewport;
            if (logicalSizeChanged(previousViewport, nextViewport)) {
                layoutDirty = true;
                paintDirty = true;
            }
        }

        int activeAnimations = animations.activeCount();
        boolean profileFrame = activeAnimations > 0 || layoutDirty || paintDirty;
        CoreFrameEvent performanceEvent = profileFrame && FRAME_EVENT_TYPE.isEnabled()
                ? new CoreFrameEvent()
                : null;
        if (performanceEvent != null) {
            performanceEvent.frameTimeNanos = frameTimeNanos;
            performanceEvent.activeAnimations = activeAnimations;
            performanceEvent.layoutRequested = layoutDirty;
            performanceEvent.paintRequested = paintDirty;
            performanceEvent.begin();
        }

        enterCallback();
        try {
            long animationStarted = performanceEvent == null ? 0L : System.nanoTime();
            animations.tick(frameTimeNanos);
            if (performanceEvent != null) {
                performanceEvent.animationNanos = System.nanoTime() - animationStarted;
            }
            if (!active.get()) {
                return null;
            }
            if (layoutDirty) {
                long layoutStarted = performanceEvent == null ? 0L : System.nanoTime();
                LayoutSnapshot candidateLayout = layoutEngine.layout(
                        root,
                        Constraints.tight(new Size(nextViewport.logicalWidth(), nextViewport.logicalHeight())),
                        layoutDirection,
                        theme,
                        this::visualState);
                if (performanceEvent != null) {
                    performanceEvent.layoutNanos = System.nanoTime() - layoutStarted;
                }
                long sceneStarted = performanceEvent == null ? 0L : System.nanoTime();
                DisplayList candidateDisplayList = sceneCompiler.compile(candidateLayout, frameTimeNanos);
                if (performanceEvent != null) {
                    performanceEvent.sceneNanos = System.nanoTime() - sceneStarted;
                }
                if (!active.get()) {
                    return null;
                }
                layout = candidateLayout;
                displayList = candidateDisplayList;
                layoutDirty = false;
                paintDirty = false;
            } else if (paintDirty) {
                long sceneStarted = performanceEvent == null ? 0L : System.nanoTime();
                DisplayList candidateDisplayList = sceneCompiler.compile(
                        Objects.requireNonNull(layout, "layout"), frameTimeNanos);
                if (performanceEvent != null) {
                    performanceEvent.sceneNanos = System.nanoTime() - sceneStarted;
                }
                if (!active.get()) {
                    return null;
                }
                displayList = candidateDisplayList;
                paintDirty = false;
            }
        } catch (RuntimeException | Error exception) {
            DisplayList previous = displayList;
            viewport = previousViewport;
            resourceGeneration = previousResourceGeneration;
            requestClose(SessionCloseReason.FAILED, true);
            boolean previousFrameIsCompatible = previous != null
                    && nextViewport.equals(previousViewport)
                    && nextResourceGeneration == previousResourceGeneration;
            return !previousFrameIsCompatible
                    ? null
                    : new UiSceneFrame(this, previous, previousViewport, frameTimeNanos);
        } finally {
            exitCallback();
            if (performanceEvent != null) {
                performanceEvent.end();
                performanceEvent.commit();
            }
        }

        return new UiSceneFrame(
                this,
                Objects.requireNonNull(displayList, "displayList"),
                viewport,
                frameTimeNanos);
    }

    private static boolean logicalSizeChanged(UiViewport previous, UiViewport next) {
        return Float.compare(previous.logicalWidth(), next.logicalWidth()) != 0
                || Float.compare(previous.logicalHeight(), next.logicalHeight()) != 0;
    }

    final void requestClose(SessionCloseReason reason, boolean notifyHost) {
        Objects.requireNonNull(reason, "reason");
        if (!beginClose(reason)) {
            return;
        }
        if (callbackDepth > 0) {
            pendingClose = new PendingClose(reason, notifyHost);
        } else {
            runtime.runCleanup(() -> finishClose(reason, notifyHost));
        }
    }

    private void requestCloseDeferred(SessionCloseReason reason, boolean notifyHost) {
        Objects.requireNonNull(reason, "reason");
        if (!beginClose(reason)) {
            return;
        }
        pendingClose = new PendingClose(reason, notifyHost);
        if (callbackDepth == 0) {
            schedulePendingClose();
        }
    }

    private boolean beginClose(SessionCloseReason reason) {
        if (!active.compareAndSet(true, false)) {
            return false;
        }
        closeReason.set(reason);
        return true;
    }

    final CoreUiRuntime runtime() {
        return runtime;
    }

    final void requireActiveOperation() {
        requireActiveUiThread();
    }

    final LayoutSnapshot layoutSnapshot() {
        return layout;
    }

    final CoreEventDispatcher events() {
        return events;
    }

    final CoreFocusManager focusManager() {
        return focus;
    }

    final boolean contains(UiComponent component) {
        return components.contains(component);
    }

    final void visualStateChanged() {
        layoutDirty = true;
        paintDirty = true;
    }

    final void animationFrameProduced() {
        paintDirty = true;
    }

    final void enterCallback() {
        runtime.assertUiThread();
        callbackDepth++;
    }

    final void exitCallback() {
        runtime.assertUiThread();
        if (callbackDepth <= 0) {
            throw new IllegalStateException("FandUI callback scope underflow");
        }
        callbackDepth--;
        if (callbackDepth == 0 && pendingClose != null) {
            schedulePendingClose();
        }
    }

    final UiComponent hovered() {
        return hovered;
    }

    final void hovered(UiComponent component) {
        if (hovered != component) {
            hovered = component;
            runtime.updateCursor(resolveCursor(component));
            visualStateChanged();
        }
    }

    final UiComponent pressed() {
        return pressed;
    }

    final void pressed(UiComponent component) {
        if (pressed != component) {
            pressed = component;
            visualStateChanged();
        }
    }

    final UiComponent captured() {
        return captured;
    }

    final void captured(UiComponent component) {
        if (component != null) {
            requireMember(component);
        }
        captured = component;
    }

    final Point sceneToLocal(UiComponent component, Point scenePoint) {
        requireMember(component);
        Objects.requireNonNull(scenePoint, "scenePoint");
        LayoutSnapshot currentLayout = layout;
        return currentLayout == null
                ? null
                : currentLayout.sceneToLocal(component, scenePoint).orElse(null);
    }

    final void componentDetached(UiComponent component) {
        if (focus.focused().orElse(null) == component) {
            focus.clearDetached();
        }
        if (hovered == component) {
            hovered(null);
        }
        if (pressed == component) {
            pressed = null;
        }
        if (captured == component) {
            events.cancelCapture();
        }
    }

    protected abstract boolean notifyHostOnApiClose();

    protected abstract boolean acceptsInput(UiEvent event);

    protected abstract void hostCloseRequested();

    protected abstract void detachedFromRuntime();

    private void finishClose(SessionCloseReason reason, boolean notifyHost) {
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        runtime.assertUiThread();
        detaching = true;
        RuntimeException failure = null;
        try {
            events.cancelCapture();
        } catch (RuntimeException exception) {
            failure = combine(failure, exception);
        }
        try {
            focus.clearForSessionClose();
        } catch (RuntimeException exception) {
            failure = combine(failure, exception);
        }
        try {
            animations.closeForSession();
        } catch (RuntimeException exception) {
            failure = combine(failure, exception);
        }
        failure = combine(failure, detachSubtree(root));
        detaching = false;

        try {
            detachedFromRuntime();
        } catch (RuntimeException exception) {
            failure = combine(failure, exception);
        }
        if (notifyHost) {
            try {
                hostCloseRequested();
            } catch (RuntimeException exception) {
                failure = combine(failure, exception);
            }
        }

        List<CloseRegistration> listeners;
        synchronized (closeListeners) {
            listeners = List.copyOf(closeListeners);
            closeListeners.clear();
        }
        for (CloseRegistration registration : listeners) {
            RuntimeException listenerFailure = registration.notifyClosed(reason);
            failure = combine(failure, listenerFailure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void attachSubtree(UiComponent subtree) {
        List<UiComponent> nodes = collectSubtree(subtree);
        validateAttachable(nodes, Map.of());
        attachNodes(nodes, Map.of());
    }

    private void validateAttachable(
            List<UiComponent> nodes,
            Map<UiKey, UiComponent> replacedKeys) {
        Map<UiKey, UiComponent> candidateKeys = new LinkedHashMap<>();
        for (UiComponent node : nodes) {
            if (ComponentBindings.bound(node)) {
                throw new IllegalStateException("Component is already attached: " + node);
            }
            node.key().ifPresent(key -> {
                UiComponent existing = keyedComponents.get(key);
                if (existing != null && replacedKeys.get(key) != existing) {
                    throw new IllegalStateException("Duplicate component key in session: " + key);
                }
                if (candidateKeys.putIfAbsent(key, node) != null) {
                    throw new IllegalStateException("Duplicate component key in session: " + key);
                }
            });
        }
    }

    private void attachNodes(
            List<UiComponent> nodes,
            Map<UiKey, UiComponent> replacedKeys) {
        List<UiComponent> bound = new ArrayList<>();
        List<UiComponent> callbacks = new ArrayList<>();
        try {
            for (UiComponent node : nodes) {
                ComponentBindings.bind(node, this);
                components.add(node);
                node.key().ifPresent(key -> keyedComponents.put(key, node));
                bound.add(node);
            }
            enterCallback();
            try {
                for (UiComponent node : nodes) {
                    node.attached(this);
                    callbacks.add(node);
                }
            } finally {
                exitCallback();
            }
        } catch (RuntimeException | Error exception) {
            for (int index = callbacks.size() - 1; index >= 0; index--) {
                try {
                    callbacks.get(index).detached(this);
                } catch (RuntimeException ignored) {
                    exception.addSuppressed(ignored);
                }
            }
            for (int index = bound.size() - 1; index >= 0; index--) {
                UiComponent node = bound.get(index);
                node.key().ifPresent(key -> keyedComponents.remove(key, node));
                components.remove(node);
                ComponentBindings.unbind(node, this);
            }
            replacedKeys.forEach(keyedComponents::putIfAbsent);
            throw exception;
        }
    }

    private Map<UiKey, UiComponent> subtreeKeys(UiComponent subtree) {
        Map<UiKey, UiComponent> result = new LinkedHashMap<>();
        for (UiComponent node : collectSubtree(subtree)) {
            node.key().ifPresent(key -> result.put(key, node));
        }
        return Map.copyOf(result);
    }

    private RuntimeException detachSubtree(UiComponent subtree) {
        List<UiComponent> nodes = collectSubtree(subtree);
        RuntimeException failure = null;
        for (UiComponent node : nodes) {
            componentDetached(node);
        }
        for (int index = nodes.size() - 1; index >= 0; index--) {
            UiComponent node = nodes.get(index);
            try {
                node.detached(this);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } finally {
                node.key().ifPresent(key -> keyedComponents.remove(key, node));
                components.remove(node);
                if (ComponentBindings.bound(node)) {
                    ComponentBindings.unbind(node, this);
                }
            }
        }
        return failure;
    }

    private List<UiComponent> collectSubtree(UiComponent subtree) {
        List<UiComponent> result = new ArrayList<>();
        collect(subtree, result);
        return result;
    }

    private static void collect(UiComponent component, List<UiComponent> result) {
        result.add(component);
        if (component instanceof UiContainer container) {
            for (UiComponent child : container.children()) {
                collect(child, result);
            }
        }
    }

    private static RuntimeException combine(RuntimeException current, RuntimeException next) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private VisualState visualState(UiComponent component) {
        return VisualState.of(
                ancestorOrSelf(component, hovered),
                ancestorOrSelf(component, pressed),
                focus.focused().orElse(null) == component,
                !component.enabled());
    }

    private static boolean ancestorOrSelf(UiComponent component, UiComponent descendant) {
        UiComponent current = descendant;
        while (current != null) {
            if (current == component) {
                return true;
            }
            current = current.parent().orElse(null);
        }
        return false;
    }

    private static CursorShape resolveCursor(UiComponent component) {
        UiComponent current = component;
        while (current != null) {
            if (current.cursor() != CursorShape.DEFAULT) {
                return current.cursor();
            }
            current = current.parent().orElse(null);
        }
        return CursorShape.DEFAULT;
    }

    private void schedulePendingClose() {
        PendingClose pending = pendingClose;
        pendingClose = null;
        if (pending != null) {
            runtime.deferCleanup(() -> finishClose(pending.reason(), pending.notifyHost()));
        }
    }

    private void requireActiveUiThread() {
        runtime.assertUiThread();
        if (!active.get()) {
            throw new IllegalStateException("UI session is closed");
        }
    }

    private void requireMember(UiComponent component) {
        Objects.requireNonNull(component, "component");
        if (!components.contains(component)) {
            throw new IllegalArgumentException("Component does not belong to this session");
        }
    }

    private final class CloseRegistration implements EventRegistration {
        private final SessionCloseListener listener;
        private final AtomicBoolean registrationActive = new AtomicBoolean(true);

        private CloseRegistration(SessionCloseListener listener) {
            this.listener = listener;
        }

        @Override
        public boolean active() {
            return registrationActive.get();
        }

        @Override
        public void close() {
            if (registrationActive.compareAndSet(true, false)) {
                synchronized (closeListeners) {
                    closeListeners.remove(this);
                }
            }
        }

        private RuntimeException notifyClosed(SessionCloseReason reason) {
            if (!registrationActive.compareAndSet(true, false)) {
                return null;
            }
            try {
                listener.closed(AbstractCoreSession.this, reason);
                return null;
            } catch (RuntimeException exception) {
                return exception;
            }
        }
    }

    private record PendingClose(SessionCloseReason reason, boolean notifyHost) {
    }

    @Name("cn.fandmc.fandui.CoreFrame")
    @Label("FandUI Core Animation Frame")
    @Category("FandUI")
    @StackTrace(false)
    static final class CoreFrameEvent extends Event {
        @Label("Frame Time")
        public long frameTimeNanos;

        @Label("Active Animations")
        public int activeAnimations;

        @Label("Layout Requested")
        public boolean layoutRequested;

        @Label("Paint Requested")
        public boolean paintRequested;

        @Label("Animation Tick")
        public long animationNanos;

        @Label("Layout")
        public long layoutNanos;

        @Label("Scene Compile")
        public long sceneNanos;
    }
}
