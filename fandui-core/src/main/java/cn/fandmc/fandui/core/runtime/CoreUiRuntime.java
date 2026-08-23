package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.UiAvailability;
import cn.fandmc.fandui.api.UiCapabilities;
import cn.fandmc.fandui.api.UiRuntime;
import cn.fandmc.fandui.api.UiRuntimeState;
import cn.fandmc.fandui.api.UiUnavailableException;
import cn.fandmc.fandui.api.hud.HudService;
import cn.fandmc.fandui.api.input.ClipboardService;
import cn.fandmc.fandui.api.input.CursorShape;
import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.screen.ScreenService;
import cn.fandmc.fandui.api.session.SessionCloseReason;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.text.TextService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public final class CoreUiRuntime implements UiRuntime {
    private final UiThreadDispatcher dispatcher;
    private final ResourceService resources;
    private final TextService text;
    private final ClipboardService clipboard;
    private final CursorHost cursorHost;
    private final UiCapabilities capabilities;
    private final LongSupplier clock;
    private final AtomicReference<UiAvailability> availability = new AtomicReference<>(
            new UiAvailability(UiRuntimeState.STARTING, "bootstrap"));
    private final CoreScreenService screens;
    private final CoreHudService hud;
    private final AtomicBoolean cursorClosed = new AtomicBoolean();
    private CursorShape cursor = CursorShape.DEFAULT;

    public CoreUiRuntime(
            UiThreadDispatcher dispatcher,
            ScreenHost screenHost,
            ResourceService resources,
            TextService text,
            ClipboardService clipboard,
            CursorHost cursorHost,
            UiCapabilities capabilities,
            LongSupplier clock) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.text = Objects.requireNonNull(text, "text");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.cursorHost = Objects.requireNonNull(cursorHost, "cursorHost");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.screens = new CoreScreenService(this, Objects.requireNonNull(screenHost, "screenHost"));
        this.hud = new CoreHudService(this);
    }

    @Override
    public UiAvailability availability() {
        return availability.get();
    }

    @Override
    public UiCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public boolean isUiThread() {
        return dispatcher.isUiThread();
    }

    @Override
    public CompletableFuture<Void> execute(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (availability.get().state() == UiRuntimeState.STOPPED) {
            return CompletableFuture.failedFuture(new IllegalStateException("FandUI runtime is stopped"));
        }
        if (dispatcher.isUiThread()) {
            try {
                action.run();
                return CompletableFuture.completedFuture(null);
            } catch (RuntimeException | Error exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            dispatcher.execute(() -> {
                if (availability.get().state() == UiRuntimeState.STOPPED) {
                    completion.completeExceptionally(new IllegalStateException("FandUI runtime is stopped"));
                    return;
                }
                try {
                    action.run();
                    completion.complete(null);
                } catch (RuntimeException | Error exception) {
                    completion.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException | Error exception) {
            completion.completeExceptionally(exception);
        }
        return completion;
    }

    @Override
    public ScreenService screens() {
        return screens;
    }

    @Override
    public HudService hud() {
        return hud;
    }

    @Override
    public ResourceService resources() {
        return resources;
    }

    @Override
    public TextService text() {
        return text;
    }

    @Override
    public ClipboardService clipboard() {
        return clipboard;
    }

    public void markAvailable(String detail) {
        setOperationalState(UiRuntimeState.AVAILABLE, detail);
    }

    public void markRendererUnavailable(String detail) {
        UiRuntimeState previous = setOperationalState(UiRuntimeState.RENDERER_UNAVAILABLE, detail);
        if (previous == UiRuntimeState.AVAILABLE) {
            screens.closeCurrent(SessionCloseReason.FAILED, true);
            hud.closeAll(SessionCloseReason.FAILED);
        }
    }

    public void markFailed(String detail) {
        Objects.requireNonNull(detail, "detail");
        assertUiThread();
        availability.set(new UiAvailability(UiRuntimeState.FAILED, detail));
        screens.closeCurrent(SessionCloseReason.FAILED, true);
        hud.closeAll(SessionCloseReason.FAILED);
    }

    public List<UiSceneFrame> renderFrames(
            UiViewport viewport,
            long frameTimeNanos,
            boolean hudVisible) {
        List<UiSceneFrame> frames = new ArrayList<>();
        renderFramesInto(viewport, frameTimeNanos, hudVisible, frames);
        return List.copyOf(frames);
    }

    /** Fills a caller-owned frame list for allocation-sensitive render bridges. */
    public void renderFramesInto(
            UiViewport viewport,
            long frameTimeNanos,
            boolean hudVisible,
            List<UiSceneFrame> destination) {
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(destination, "destination");
        if (frameTimeNanos < 0L) {
            throw new IllegalArgumentException("frameTimeNanos must not be negative");
        }
        assertUiThread();
        destination.clear();
        if (availability.get().state() != UiRuntimeState.AVAILABLE) {
            return;
        }

        if (hudVisible) {
            for (CoreHudRegistration registration : hud.activeRegistrations()) {
                UiSceneFrame frame = registration.prepareFrame(viewport, frameTimeNanos);
                if (frame != null) {
                    destination.add(frame);
                }
            }
        }
        CoreScreenSession session = screens.activeSession();
        if (session != null) {
            UiSceneFrame frame = session.prepareFrame(viewport, frameTimeNanos);
            if (frame != null) {
                destination.add(frame);
            }
        }
    }

    public List<UiSceneFrame> renderFrames(UiViewport viewport, long frameTimeNanos) {
        return renderFrames(viewport, frameTimeNanos, true);
    }

    public void stop() {
        if (availability.getAndSet(new UiAvailability(UiRuntimeState.STOPPED, "shutdown")).state()
                == UiRuntimeState.STOPPED) {
            return;
        }
        runCleanup(() -> {
            try {
                screens.closeCurrent(SessionCloseReason.SHUTDOWN, false);
                hud.closeAll(SessionCloseReason.SHUTDOWN);
            } finally {
                closeCursorHost();
            }
        });
    }

    void updateCursor(CursorShape value) {
        Objects.requireNonNull(value, "value");
        assertUiThread();
        if (!cursorClosed.get() && cursor != value) {
            cursorHost.setCursor(value);
            cursor = value;
        }
    }

    private void closeCursorHost() {
        if (cursorClosed.compareAndSet(false, true)) {
            cursorHost.close();
            cursor = CursorShape.DEFAULT;
        }
    }

    void assertUiThread() {
        if (!dispatcher.isUiThread()) {
            throw new IllegalStateException("FandUI operation requires the UI thread");
        }
    }

    void requireAvailable() {
        UiAvailability current = availability.get();
        if (!current.available()) {
            throw new UiUnavailableException(current);
        }
    }

    long now() {
        long value = clock.getAsLong();
        if (value < 0L) {
            throw new IllegalStateException("Monotonic clock returned a negative value");
        }
        return value;
    }

    void runCleanup(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (dispatcher.isUiThread()) {
            action.run();
        } else {
            dispatcher.execute(action);
        }
    }

    void deferCleanup(Runnable action) {
        dispatcher.execute(Objects.requireNonNull(action, "action"));
    }

    CompletableFuture<Void> defer(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (availability.get().state() == UiRuntimeState.STOPPED) {
            return CompletableFuture.failedFuture(new IllegalStateException("FandUI runtime is stopped"));
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            dispatcher.execute(() -> {
                if (availability.get().state() == UiRuntimeState.STOPPED) {
                    completion.completeExceptionally(new IllegalStateException("FandUI runtime is stopped"));
                    return;
                }
                try {
                    action.run();
                    completion.complete(null);
                } catch (RuntimeException | Error exception) {
                    completion.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException | Error exception) {
            completion.completeExceptionally(exception);
        }
        return completion;
    }

    private UiRuntimeState setOperationalState(UiRuntimeState state, String detail) {
        Objects.requireNonNull(detail, "detail");
        assertUiThread();
        UiRuntimeState current = availability.get().state();
        if (current == UiRuntimeState.STOPPED) {
            throw new IllegalStateException("FandUI runtime is stopped");
        }
        availability.set(new UiAvailability(state, detail));
        return current;
    }
}
