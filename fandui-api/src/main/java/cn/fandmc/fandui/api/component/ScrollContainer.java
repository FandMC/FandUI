package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.component.control.ScrollController;
import cn.fandmc.fandui.api.event.EventContext;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.event.ScrollEvent;
import cn.fandmc.fandui.api.layout.Axis;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.api.style.ThemeToken;
import cn.fandmc.fandui.internal.control.ScrollControllerState;
import cn.fandmc.fandui.internal.control.ScrollControllers;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A single-child viewport with wheel, pointer-drag, and controller-driven scrolling. */
public final class ScrollContainer extends UiContainer implements ContentClipProvider {
    public static final ThemeToken<Double> LINE_EXTENT = ThemeToken.of(
            UiKey.of("fandui", "scroll/line_extent"),
            Double.class,
            24.0);

    private final ScrollController controller;
    private final ScrollControllerState controllerState;
    private Axis axis;
    private @Nullable ComponentContext context;
    private @Nullable EventRegistration controllerChanges;
    private boolean controllerBound;
    private boolean updatingExtent;
    private boolean dragging;
    private float dragStartCoordinate;
    private double dragStartOffset;

    private ScrollContainer(Builder builder) {
        super(builder.key, 1, 1);
        axis = builder.axis;
        controller = builder.controller;
        controllerState = ScrollControllers.state(controller);
        if (builder.style != null) {
            setStyle(builder.style);
        }
        add(builder.child);
        on(ScrollEvent.class, EventRoute.BUBBLE, this::handleScroll);
        on(PointerEvent.class, EventRoute.BUBBLE, this::handlePointer);
    }

    public static Builder builder(UiComponent child) {
        return new Builder(child);
    }

    public static ScrollContainer vertical(UiComponent child) {
        return builder(child).axis(Axis.VERTICAL).build();
    }

    public static ScrollContainer horizontal(UiComponent child) {
        return builder(child).axis(Axis.HORIZONTAL).build();
    }

    public UiComponent child() {
        return children().get(0);
    }

    public void setChild(UiComponent child) {
        replace(0, child);
    }

    public Axis axis() {
        return axis;
    }

    public void setAxis(Axis axis) {
        requireMutationThread();
        Axis checked = Objects.requireNonNull(axis, "axis");
        if (this.axis != checked) {
            this.axis = checked;
            invalidateLayout();
        }
    }

    public ScrollController controller() {
        return controller;
    }

    @Override
    public ClipMode contentClip() {
        return ClipMode.BOUNDS;
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Style style = scope.style();
        Insets padding = style.padding();
        float horizontalPadding = padding.left() + padding.right();
        float verticalPadding = padding.top() + padding.bottom();
        float maximumContentWidth = subtractMaximum(constraints.maxWidth(), horizontalPadding);
        float maximumContentHeight = subtractMaximum(constraints.maxHeight(), verticalPadding);
        Constraints childConstraints = axis == Axis.VERTICAL
                ? new Constraints(0.0f, maximumContentWidth, 0.0f, Float.POSITIVE_INFINITY)
                : new Constraints(0.0f, Float.POSITIVE_INFINITY, 0.0f, maximumContentHeight);
        Placeable child = scope.measure(child(), childConstraints);
        Size desired = new Size(
                child.size().width() + horizontalPadding,
                child.size().height() + verticalPadding);
        Size size = constraints.constrain(desired);
        float viewportExtent = axis == Axis.VERTICAL
                ? Math.max(0.0f, size.height() - verticalPadding)
                : Math.max(0.0f, size.width() - horizontalPadding);
        float childExtent = axis == Axis.VERTICAL ? child.size().height() : child.size().width();
        updateMaximum(Math.max(0.0, childExtent - viewportExtent));
        float offset = (float) controller.offset();
        return scope.layout(size.width(), size.height(), placements -> placements.place(
                child,
                padding.left() - (axis == Axis.HORIZONTAL ? offset : 0.0f),
                padding.top() - (axis == Axis.VERTICAL ? offset : 0.0f)));
    }

    @Override
    public void paint(PaintScope scope) {
        SingleChildSupport.paintBackground(scope);
    }

    @Override
    public void attached(ComponentContext context) {
        if (this.context != null) {
            throw new IllegalStateException("ScrollContainer is already attached");
        }
        this.context = Objects.requireNonNull(context, "context");
        controllerChanges = controller.onChange(() -> {
            if (!updatingExtent) {
                invalidateLayout();
            }
        });
    }

    @Override
    public void detached(ComponentContext context) {
        if (this.context != context) {
            throw new IllegalStateException("ScrollContainer detached from an unexpected context");
        }
        dragging = false;
        updatingExtent = true;
        try {
            if (controllerBound) {
                controllerState.unbind(this);
                controllerBound = false;
            }
        } finally {
            updatingExtent = false;
        }
        EventRegistration changes = controllerChanges;
        controllerChanges = null;
        if (changes != null) {
            changes.close();
        }
        this.context = null;
    }

    private void updateMaximum(double maximum) {
        if (context == null) {
            return;
        }
        updatingExtent = true;
        try {
            if (controllerBound) {
                controllerState.updateMaximum(this, maximum);
            } else {
                controllerState.bind(this, maximum);
                controllerBound = true;
            }
        } finally {
            updatingExtent = false;
        }
    }

    private void handleScroll(ScrollEvent event, EventContext eventContext) {
        if (!enabled()) {
            return;
        }
        double lines = axis == Axis.VERTICAL ? event.verticalLines() : event.horizontalLines();
        if (lines == 0.0) {
            return;
        }
        ComponentContext attached = context;
        double lineExtent = attached == null ? LINE_EXTENT.defaultValue() : attached.theme().value(LINE_EXTENT);
        if (!Double.isFinite(lineExtent) || lineExtent <= 0.0) {
            throw new IllegalStateException("Scroll line extent must be finite and positive");
        }
        double previous = controller.offset();
        controller.scrollBy(-lines * lineExtent);
        if (Double.compare(previous, controller.offset()) != 0) {
            eventContext.consume();
        }
    }

    private void handlePointer(PointerEvent event, EventContext eventContext) {
        if (event.action() == PointerAction.DOWN
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()) {
            if (!enabled() || controller.maximumOffset().orElse(0.0) <= 0.0) {
                return;
            }
            Point local = eventContext.sceneToLocal(event.scenePosition()).orElse(null);
            if (local == null) {
                return;
            }
            dragging = true;
            dragStartCoordinate = coordinate(local);
            dragStartOffset = controller.offset();
            eventContext.capturePointer();
            eventContext.consume();
            return;
        }
        if (event.action() == PointerAction.MOVE && dragging) {
            Point local = eventContext.sceneToLocal(event.scenePosition()).orElse(null);
            if (local != null) {
                controller.scrollTo(Math.max(0.0, dragStartOffset - (coordinate(local) - dragStartCoordinate)));
            }
            eventContext.consume();
            return;
        }
        if (event.action() == PointerAction.UP
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()
                && dragging) {
            dragging = false;
            eventContext.releasePointer();
            eventContext.consume();
            return;
        }
        if (event.action() == PointerAction.CANCEL) {
            dragging = false;
        }
    }

    private float coordinate(Point point) {
        return axis == Axis.VERTICAL ? point.y() : point.x();
    }

    private static float subtractMaximum(float maximum, float amount) {
        return Float.isInfinite(maximum) ? maximum : Math.max(0.0f, maximum - amount);
    }

    public static final class Builder {
        private final UiComponent child;
        private @Nullable UiKey key;
        private Axis axis = Axis.VERTICAL;
        private ScrollController controller = ScrollController.create();
        private @Nullable StyleResolver style;

        private Builder(UiComponent child) {
            this.child = Objects.requireNonNull(child, "child");
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder axis(Axis axis) {
            this.axis = Objects.requireNonNull(axis, "axis");
            return this;
        }

        public Builder controller(ScrollController controller) {
            this.controller = Objects.requireNonNull(controller, "controller");
            return this;
        }

        public Builder style(StyleResolver style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public ScrollContainer build() {
            return new ScrollContainer(this);
        }
    }
}
