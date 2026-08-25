package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.layout.Axis;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Border;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.ThemeToken;
import cn.fandmc.fandui.api.style.VisualState;
import cn.fandmc.fandui.internal.control.ChangeListeners;
import cn.fandmc.fandui.internal.control.RetargetableScalarTransition;
import cn.fandmc.fandui.internal.control.ValueChangeListeners;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/** A focusable continuous or stepped value control with horizontal or vertical orientation. */
public final class Slider extends UiComponent {
    private static final Duration VALUE_TRANSITION = Duration.ofMillis(96);

    public static final ThemeToken<Paint> TRACK = paintToken("slider/track", 0x39414a);
    public static final ThemeToken<Paint> FILL = paintToken("slider/fill", 0x36aee1);
    public static final ThemeToken<Paint> THUMB = paintToken("slider/thumb", 0xf0f7fb);
    public static final ThemeToken<Paint> DISABLED_TRACK = paintToken("slider/track_disabled", 0x2c3035);
    public static final ThemeToken<Paint> DISABLED_FILL = paintToken("slider/fill_disabled", 0x59616a);
    public static final ThemeToken<Insets> PADDING = ThemeToken.of(
            UiKey.of("fandui", "slider/padding"), Insets.class, new Insets(8.0f, 8.0f, 8.0f, 8.0f));
    public static final ThemeToken<Double> LENGTH = ThemeToken.of(
            UiKey.of("fandui", "slider/length"), Double.class, 160.0);
    public static final ThemeToken<Double> TRACK_THICKNESS = ThemeToken.of(
            UiKey.of("fandui", "slider/track_thickness"), Double.class, 4.0);
    public static final ThemeToken<Double> THUMB_SIZE = ThemeToken.of(
            UiKey.of("fandui", "slider/thumb_size"), Double.class, 16.0);
    public static final ThemeToken<Paint> FOCUS_RING = paintToken("slider/focus_ring", 0x8edcff);

    private final ChangeListeners changes = new ChangeListeners();
    private final ValueChangeListeners<Double> valueChanges = new ValueChangeListeners<>();
    private final Axis axis;
    private final RetargetableScalarTransition valueTransition;
    private double minimum;
    private double maximum;
    private double value;
    private double step;
    private boolean dragging;
    private float pointerStart;
    private float pointerLength = 1.0f;

    private Slider(Builder builder) {
        super(builder.key);
        axis = builder.axis;
        minimum = builder.minimum;
        maximum = builder.maximum;
        step = builder.step;
        value = snap(clamp(builder.value));
        valueTransition = new RetargetableScalarTransition(valueRatio(), VALUE_TRANSITION, ignored -> invalidatePaint());
        setFocusable(true);
        setStyle(builder.style == null ? this::defaultStyle : builder.style);
        on(PointerEvent.class, EventRoute.BUBBLE, this::handlePointer);
        on(KeyEvent.class, EventRoute.BUBBLE, this::handleKey);
        if (builder.onChange != null) {
            onChange(builder.onChange);
        }
        if (builder.onValueChange != null) {
            onValueChange(builder.onValueChange);
        }
    }

    public static Builder builder() { return new Builder(); }

    public Axis axis() { return axis; }
    public double minimum() { return minimum; }
    public double maximum() { return maximum; }
    public double value() { return value; }
    public double step() { return step; }

    public void setValue(double requested) {
        requireMutationThread();
        double next = snap(clamp(requireFinite(requested, "value")));
        if (Double.compare(value, next) == 0) {
            return;
        }
        value = next;
        valueTransition.setTarget(valueRatio());
        invalidatePaint();
        changes.notifyListeners();
        valueChanges.notifyListeners(next);
    }

    public void setRange(double nextMinimum, double nextMaximum) {
        requireMutationThread();
        validateRange(nextMinimum, nextMaximum);
        minimum = nextMinimum;
        maximum = nextMaximum;
        setValue(value);
        valueTransition.setTarget(valueRatio());
        invalidatePaint();
    }

    public void setStep(double nextStep) {
        requireMutationThread();
        validateStep(nextStep);
        step = nextStep;
        setValue(value);
    }

    public EventRegistration onChange(Runnable listener) {
        requireMutationThread();
        return changes.add(listener);
    }

    /** Registers a listener that receives the snapped value after each change. */
    public EventRegistration onValueChange(Consumer<Double> listener) {
        requireMutationThread();
        return valueChanges.add(listener);
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Insets padding = scope.style().padding();
        float length = positive(scope.theme().value(LENGTH), "length");
        float thickness = positive(scope.theme().value(THUMB_SIZE), "thumb size");
        Size desired = axis == Axis.HORIZONTAL
                ? new Size(length + padding.left() + padding.right(),
                        thickness + padding.top() + padding.bottom())
                : new Size(thickness + padding.left() + padding.right(),
                        length + padding.top() + padding.bottom());
        Size size = constraints.constrain(desired);
        float thumb = positive(scope.theme().value(THUMB_SIZE), "thumb size");
        if (axis == Axis.HORIZONTAL) {
            pointerStart = padding.left() + thumb * 0.5f;
            pointerLength = Math.max(1.0f, size.width() - padding.left() - padding.right() - thumb);
        } else {
            pointerStart = padding.top() + thumb * 0.5f;
            pointerLength = Math.max(1.0f, size.height() - padding.top() - padding.bottom() - thumb);
        }
        return scope.layout(size.width(), size.height(), placements -> { });
    }

    @Override
    public void paint(PaintScope scope) {
        Insets padding = scope.style().padding();
        float thumb = positive(scope.theme().value(THUMB_SIZE), "thumb size");
        float trackThickness = positive(scope.theme().value(TRACK_THICKNESS), "track thickness");
        float innerWidth = Math.max(0.0f, scope.bounds().width() - padding.left() - padding.right());
        float innerHeight = Math.max(0.0f, scope.bounds().height() - padding.top() - padding.bottom());
        double ratio = Math.max(0.0, Math.min(1.0, valueTransition.sample(scope.frameTimeNanos())));
        Paint trackPaint = scope.theme().value(enabled() ? TRACK : DISABLED_TRACK);
        Paint fillPaint = scope.theme().value(enabled() ? FILL : DISABLED_FILL);
        if (axis == Axis.HORIZONTAL) {
            float length = Math.max(0.0f, innerWidth - thumb);
            float start = padding.left() + thumb * 0.5f;
            float centerY = padding.top() + innerHeight * 0.5f;
            float thumbCenter = start + length * (float) ratio;
            scope.canvas().fillRoundedRect(
                    new cn.fandmc.fandui.api.layout.Rect(start, centerY - trackThickness * 0.5f,
                            length, trackThickness),
                    CornerRadii.all(trackThickness * 0.5f), trackPaint);
            scope.canvas().fillRoundedRect(
                    new cn.fandmc.fandui.api.layout.Rect(start, centerY - trackThickness * 0.5f,
                            Math.max(0.0f, thumbCenter - start), trackThickness),
                    CornerRadii.all(trackThickness * 0.5f), fillPaint);
            drawThumb(scope, thumbCenter, centerY, thumb);
        } else {
            float length = Math.max(0.0f, innerHeight - thumb);
            float start = padding.top() + thumb * 0.5f;
            float centerX = padding.left() + innerWidth * 0.5f;
            float thumbCenter = start + length * (float) (1.0 - ratio);
            scope.canvas().fillRoundedRect(
                    new cn.fandmc.fandui.api.layout.Rect(centerX - trackThickness * 0.5f, start,
                            trackThickness, length),
                    CornerRadii.all(trackThickness * 0.5f), trackPaint);
            scope.canvas().fillRoundedRect(
                    new cn.fandmc.fandui.api.layout.Rect(centerX - trackThickness * 0.5f, thumbCenter,
                            trackThickness, Math.max(0.0f, start + length - thumbCenter)),
                    CornerRadii.all(trackThickness * 0.5f), fillPaint);
            drawThumb(scope, centerX, thumbCenter, thumb);
        }
    }

    @Override
    public void attached(ComponentContext context) {
        valueTransition.attach(context);
    }

    @Override
    public void detached(ComponentContext context) {
        valueTransition.detach(context);
    }

    private void drawThumb(PaintScope scope, float centerX, float centerY, float size) {
        var rect = new cn.fandmc.fandui.api.layout.Rect(
                centerX - size * 0.5f, centerY - size * 0.5f, size, size);
        scope.canvas().fillRoundedRect(rect, CornerRadii.all(size * 0.5f), scope.theme().value(THUMB));
        Border border = scope.style().border();
        if (border.width() > 0.0f) {
            float inset = border.width() * 0.5f;
            var ring = new cn.fandmc.fandui.api.layout.Rect(
                    rect.x() + inset,
                    rect.y() + inset,
                    Math.max(0.0f, rect.width() - border.width()),
                    Math.max(0.0f, rect.height() - border.width()));
            scope.canvas().stroke(
                    Path.builder().roundedRect(ring, CornerRadii.all(size * 0.5f)).build(),
                    border.paint(),
                    cn.fandmc.fandui.api.canvas.StrokeStyle.width(border.width()).build());
        }
    }

    private void handlePointer(PointerEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        if (event.action() == PointerAction.DOWN
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()) {
            if (!enabled()) {
                return;
            }
            dragging = true;
            context.requestFocus();
            context.capturePointer();
            updateFromPointer(context.sceneToLocal(event.scenePosition()).orElse(null));
            context.consume();
        } else if (event.action() == PointerAction.MOVE && dragging) {
            updateFromPointer(context.sceneToLocal(event.scenePosition()).orElse(null));
            context.consume();
        } else if (event.action() == PointerAction.UP
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()) {
            if (dragging) {
                updateFromPointer(context.sceneToLocal(event.scenePosition()).orElse(null));
                dragging = false;
                context.releasePointer();
                context.consume();
            }
        } else if (event.action() == PointerAction.CANCEL) {
            dragging = false;
        }
    }

    private void handleKey(KeyEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        if (!enabled() || (event.action() != KeyAction.PRESS && event.action() != KeyAction.REPEAT)) {
            return;
        }
        double delta = step > 0.0 ? step : (maximum - minimum) / 100.0;
        boolean decrease = event.key().equals(axis == Axis.HORIZONTAL ? Keys.LEFT : Keys.DOWN);
        boolean increase = event.key().equals(axis == Axis.HORIZONTAL ? Keys.RIGHT : Keys.UP);
        if (decrease || increase) {
            setValue(value + (increase ? delta : -delta));
        } else if (event.key().equals(Keys.HOME)) {
            setValue(minimum);
        } else if (event.key().equals(Keys.END)) {
            setValue(maximum);
        } else if (event.key().equals(Keys.PAGE_UP)) {
            setValue(value + delta * 10.0);
        } else if (event.key().equals(Keys.PAGE_DOWN)) {
            setValue(value - delta * 10.0);
        } else {
            return;
        }
        context.consume();
    }

    private void updateFromPointer(@Nullable Point point) {
        if (point == null) {
            return;
        }
        // Pointer coordinates are interpreted against the component's measured local bounds.
        // The layout pass supplies a stable 0..length interval; clamping handles capture outside it.
        double ratio;
        if (axis == Axis.HORIZONTAL) {
            ratio = (point.x() - pointerStart) / pointerLength;
        } else {
            ratio = 1.0 - (point.y() - pointerStart) / pointerLength;
        }
        setValue(minimum + (maximum - minimum) * Math.max(0.0, Math.min(1.0, ratio)));
    }

    private Style defaultStyle(Theme theme, VisualState state) {
        return Style.builder()
                .padding(theme.value(PADDING))
                .border(state.focused()
                        ? new Border(1.0f, theme.value(FOCUS_RING))
                        : new Border(0.0f, theme.value(TRACK)))
                .build();
    }

    private double clamp(double requested) { return Math.max(minimum, Math.min(maximum, requested)); }
    private double valueRatio() { return (value - minimum) / (maximum - minimum); }
    private double snap(double requested) {
        if (step <= 0.0) return requested;
        return clamp(minimum + Math.round((requested - minimum) / step) * step);
    }
    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }
    private static void validateRange(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max) {
            throw new IllegalArgumentException("minimum must be finite and less than maximum");
        }
    }
    private static void validateStep(double value) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException("step must be finite and non-negative");
    }
    private static float positive(Double value, String name) {
        double checked = Objects.requireNonNull(value, name);
        if (!Double.isFinite(checked) || checked <= 0.0 || checked > Float.MAX_VALUE) throw new IllegalArgumentException(name + " must be finite and positive");
        return (float) checked;
    }
    private static ThemeToken<Paint> paintToken(String value, int rgb) {
        return ThemeToken.of(UiKey.of("fandui", value), Paint.class, new SolidPaint(Color.rgb(rgb)));
    }

    public static final class Builder {
        private @Nullable UiKey key;
        private Axis axis = Axis.HORIZONTAL;
        private double minimum = 0.0;
        private double maximum = 1.0;
        private double value;
        private double step;
        private @Nullable Runnable onChange;
        private @Nullable Consumer<Double> onValueChange;
        private @Nullable StyleResolver style;
        private Builder() { }
        public Builder key(UiKey value) { key = Objects.requireNonNull(value, "value"); return this; }
        public Builder axis(Axis value) { axis = Objects.requireNonNull(value, "value"); return this; }
        public Builder range(double min, double max) { validateRange(min, max); minimum = min; maximum = max; return this; }
        public Builder value(double value) { this.value = requireFinite(value, "value"); return this; }
        public Builder step(double value) { validateStep(value); step = value; return this; }
        public Builder onChange(Runnable value) { onChange = Objects.requireNonNull(value, "value"); return this; }
        public Builder onValueChange(Consumer<Double> value) { onValueChange = Objects.requireNonNull(value, "value"); return this; }
        public Builder style(StyleResolver value) { style = Objects.requireNonNull(value, "value"); return this; }
        public Slider build() { return new Slider(this); }
    }
}
