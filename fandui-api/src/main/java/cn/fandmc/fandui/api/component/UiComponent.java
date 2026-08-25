package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.event.EventHandler;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.UiEvent;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.input.CursorShape;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import cn.fandmc.fandui.internal.event.EventListeners;
import cn.fandmc.fandui.internal.event.ListenerBucket;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Base node of the mutable FandUI component tree.
 *
 * <p>A component may belong to one parent and one active session at a time. Mutations
 * made while attached require the FandUI UI thread and automatically invalidate layout
 * or paint. Core consumes margin, transform, opacity, clip, radii for rounded clipping,
 * and backdrop blur for every component. Padding, background, border, and decorative
 * radii are consumed only by components that explicitly document box styling, such as
 * {@link Box}, {@link Button}, {@link TextInput}, and {@link ScrollContainer}; custom
 * components consume those fields in their own measure and paint callbacks.</p>
 */
public abstract class UiComponent {
    private final @Nullable UiKey key;
    private final ListenerBucket listeners = new ListenerBucket();
    /** Published with the same structural lock used by {@link UiContainer}. */
    private volatile @Nullable UiContainer parent;
    private StyleResolver style = StyleResolver.fixed(Style.defaults());
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focusable;
    private int tabIndex;
    private HitTestBehavior hitTestBehavior = HitTestBehavior.OPAQUE;
    private CursorShape cursor = CursorShape.DEFAULT;

    protected UiComponent() {
        this(null);
    }

    protected UiComponent(@Nullable UiKey key) {
        this.key = key;
        EventListeners.bind(this, listeners);
    }

    public final Optional<UiKey> key() {
        return Optional.ofNullable(key);
    }

    public final Optional<UiContainer> parent() {
        return Optional.ofNullable(parent);
    }

    public final StyleResolver style() {
        return style;
    }

    public final void setStyle(StyleResolver style) {
        requireMutationThread();
        StyleResolver checked = Objects.requireNonNull(style, "style");
        if (this.style != checked) {
            this.style = checked;
            invalidateLayout();
        }
    }

    public final boolean visible() {
        return visible;
    }

    public final void setVisible(boolean visible) {
        requireMutationThread();
        if (this.visible != visible) {
            this.visible = visible;
            invalidateLayout();
            interactionChanged();
        }
    }

    public final boolean enabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        requireMutationThread();
        if (this.enabled != enabled) {
            this.enabled = enabled;
            invalidatePaint();
            interactionChanged();
        }
    }

    public final boolean focusable() {
        return focusable;
    }

    public final void setFocusable(boolean focusable) {
        requireMutationThread();
        if (this.focusable != focusable) {
            this.focusable = focusable;
            invalidatePaint();
            interactionChanged();
        }
    }

    public final int tabIndex() {
        return tabIndex;
    }

    public final void setTabIndex(int tabIndex) {
        requireMutationThread();
        if (this.tabIndex != tabIndex) {
            this.tabIndex = tabIndex;
            invalidatePaint();
        }
    }

    public final HitTestBehavior hitTestBehavior() {
        return hitTestBehavior;
    }

    public final void setHitTestBehavior(HitTestBehavior value) {
        requireMutationThread();
        HitTestBehavior checked = Objects.requireNonNull(value, "value");
        if (hitTestBehavior != checked) {
            hitTestBehavior = checked;
            invalidatePaint();
        }
    }

    public final CursorShape cursor() {
        return cursor;
    }

    public final void setCursor(CursorShape value) {
        requireMutationThread();
        CursorShape checked = Objects.requireNonNull(value, "value");
        if (cursor != checked) {
            cursor = checked;
            invalidatePaint();
            interactionChanged();
        }
    }

    public final <E extends UiEvent> EventRegistration on(
            Class<E> type,
            EventRoute route,
            EventHandler<E> handler) {
        requireMutationThread();
        return listeners.register(type, route, handler);
    }

    /**
     * Verifies that a custom component mutation may run on the current thread.
     * Call this before changing component-owned state in public mutation methods.
     */
    protected final void requireMutationThread() {
        ComponentBindings.assertMutationAllowed(this);
    }

    /** Measures this component for the supplied constraints and callback-scoped environment. */
    public abstract MeasureResult measure(MeasureScope scope, Constraints constraints);

    /** Records this component's own drawing before its children are painted. */
    public void paint(PaintScope scope) {
    }

    /** Called on the UI thread after the component enters an active session. */
    public void attached(ComponentContext context) {
    }

    /** Called on the UI thread before the component leaves its active session. */
    public void detached(ComponentContext context) {
    }

    protected final void invalidateLayout() {
        ComponentBindings.invalidateLayout(this);
    }

    protected final void invalidatePaint() {
        ComponentBindings.invalidatePaint(this);
    }

    private void interactionChanged() {
        ComponentBindings.interactionChanged(this);
    }

    final void assignParent(@Nullable UiContainer parent) {
        this.parent = parent;
    }
}
