package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.canvas.LineCap;
import cn.fandmc.fandui.api.canvas.LineJoin;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
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
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.internal.control.ChangeListeners;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A focusable boolean control with a single label child. */
public final class Checkbox extends UiContainer {
    public static final ThemeToken<Paint> BACKGROUND = paintToken("checkbox/background", 0x252a31);
    public static final ThemeToken<Paint> CHECKED_BACKGROUND = paintToken("checkbox/background_checked", 0x258ec3);
    public static final ThemeToken<Paint> HOVERED_BACKGROUND = paintToken("checkbox/background_hovered", 0x376878);
    public static final ThemeToken<Paint> DISABLED_BACKGROUND = paintToken("checkbox/background_disabled", 0x24272b);
    public static final ThemeToken<Paint> CHECK = paintToken("checkbox/check", 0xf5fbff);
    public static final ThemeToken<Border> BORDER = ThemeToken.of(
            UiKey.of("fandui", "checkbox/border"),
            Border.class,
            new Border(1.0f, new SolidPaint(Color.rgb(0x8d99a6))));
    public static final ThemeToken<Insets> PADDING = ThemeToken.of(
            UiKey.of("fandui", "checkbox/padding"),
            Insets.class,
            new Insets(4.0f, 4.0f, 4.0f, 4.0f));
    public static final ThemeToken<Double> INDICATOR_SIZE = ThemeToken.of(
            UiKey.of("fandui", "checkbox/indicator_size"), Double.class, 16.0);
    public static final ThemeToken<Double> GAP = ThemeToken.of(
            UiKey.of("fandui", "checkbox/gap"), Double.class, 6.0);
    public static final ThemeToken<CornerRadii> CORNER_RADII = ThemeToken.of(
            UiKey.of("fandui", "checkbox/corner_radii"),
            CornerRadii.class,
            CornerRadii.all(3.0f));

    private final ChangeListeners changes = new ChangeListeners();
    private final PressableSupport pressable = new PressableSupport();
    private boolean checked;
    private Size measuredSize = new Size(0.0f, 0.0f);

    private Checkbox(Builder builder) {
        super(builder.key, 1, 1);
        checked = builder.checked;
        setFocusable(true);
        setStyle(builder.style == null ? this::defaultStyle : builder.style);
        add(builder.label);
        on(PointerEvent.class, EventRoute.BUBBLE, this::handlePointer);
        on(KeyEvent.class, EventRoute.BUBBLE, this::handleKey);
        if (builder.onChange != null) {
            onChange(builder.onChange);
        }
    }

    public static Builder builder(UiComponent label) {
        return new Builder(label);
    }

    public static Checkbox text(String label, TextStyle style) {
        return builder(Text.of(label, style)).build();
    }

    public static Checkbox text(String label, float fontSize) {
        return text(label, TextStyle.of(fontSize));
    }

    public UiComponent child() {
        return children().get(0);
    }

    public void setChild(UiComponent child) {
        replace(0, child);
    }

    public boolean checked() {
        return checked;
    }

    public void setChecked(boolean value) {
        requireMutationThread();
        if (checked == value) {
            return;
        }
        checked = value;
        invalidatePaint();
        changes.notifyListeners();
    }

    public void toggle() {
        setChecked(!checked);
    }

    public EventRegistration onChange(Runnable listener) {
        requireMutationThread();
        return changes.add(listener);
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Insets padding = scope.style().padding();
        float indicator = positive(scope.theme().value(INDICATOR_SIZE), "indicator size");
        float gap = nonNegative(scope.theme().value(GAP), "gap");
        Placeable placeable = scope.measure(
                child(),
                subtractHorizontal(constraints, padding, indicator + gap));
        Size desired = new Size(
                padding.left() + indicator + gap + placeable.size().width() + padding.right(),
                padding.top() + Math.max(indicator, placeable.size().height()) + padding.bottom());
        Size size = constraints.constrain(desired);
        measuredSize = size;
        return scope.layout(size.width(), size.height(), placements -> placements.place(
                placeable,
                padding.left() + indicator + gap,
                padding.top() + Math.max(0.0f, (size.height() - padding.top() - padding.bottom()
                        - placeable.size().height()) * 0.5f)));
    }

    @Override
    public void paint(PaintScope scope) {
        Insets padding = scope.style().padding();
        float indicator = positive(scope.theme().value(INDICATOR_SIZE), "indicator size");
        float y = padding.top() + Math.max(0.0f, (scope.bounds().height()
                - padding.top() - padding.bottom() - indicator) * 0.5f);
        var rect = new cn.fandmc.fandui.api.layout.Rect(padding.left(), y, indicator, indicator);
        Paint background = scope.style().background();
        scope.canvas().fillRoundedRect(rect, scope.style().cornerRadii(), background);
        Border border = scope.style().border();
        if (border.width() > 0.0f) {
            scope.canvas().stroke(
                    Path.builder().roundedRect(rect, scope.style().cornerRadii()).build(),
                    border.paint(),
                    StrokeStyle.width(border.width()).build());
        }
        if (checked) {
            float left = rect.x() + indicator * 0.22f;
            float top = rect.y() + indicator * 0.52f;
            Path check = Path.builder()
                    .moveTo(left, top)
                    .lineTo(rect.x() + indicator * 0.44f, rect.y() + indicator * 0.74f)
                    .lineTo(rect.x() + indicator * 0.80f, rect.y() + indicator * 0.28f)
                    .build();
            scope.canvas().stroke(
                    check,
                    scope.theme().value(CHECK),
                    StrokeStyle.width(Math.max(1.5f, indicator * 0.12f))
                            .cap(LineCap.ROUND)
                            .join(LineJoin.ROUND)
                            .build());
        }
    }

    private void handlePointer(PointerEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        pressable.handlePointer(this, event, context, measuredSize, this::toggle);
    }

    private void handleKey(KeyEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        pressable.handleKey(this, event, context, this::toggle);
    }

    private Style defaultStyle(Theme theme, VisualState state) {
        Paint background = state.disabled()
                ? theme.value(DISABLED_BACKGROUND)
                : checked
                        ? theme.value(CHECKED_BACKGROUND)
                        : state.hovered() ? theme.value(HOVERED_BACKGROUND) : theme.value(BACKGROUND);
        return Style.builder()
                .padding(theme.value(PADDING))
                .background(background)
                .border(theme.value(BORDER))
                .cornerRadii(theme.value(CORNER_RADII))
                .build();
    }

    private static Constraints subtractHorizontal(Constraints constraints, Insets padding, float amount) {
        float horizontal = padding.left() + padding.right() + amount;
        return new Constraints(
                Math.max(0.0f, constraints.minWidth() - horizontal),
                Float.isInfinite(constraints.maxWidth())
                        ? Float.POSITIVE_INFINITY
                        : Math.max(0.0f, constraints.maxWidth() - horizontal),
                Math.max(0.0f, constraints.minHeight() - padding.top() - padding.bottom()),
                Float.isInfinite(constraints.maxHeight())
                        ? Float.POSITIVE_INFINITY
                        : Math.max(0.0f, constraints.maxHeight() - padding.top() - padding.bottom()));
    }

    private static float positive(Double value, String name) {
        double checked = Objects.requireNonNull(value, name);
        if (!Double.isFinite(checked) || checked <= 0.0 || checked > Float.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return (float) checked;
    }

    private static float nonNegative(Double value, String name) {
        double checked = Objects.requireNonNull(value, name);
        if (!Double.isFinite(checked) || checked < 0.0 || checked > Float.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return (float) checked;
    }

    private static ThemeToken<Paint> paintToken(String value, int rgb) {
        return ThemeToken.of(UiKey.of("fandui", value), Paint.class, new SolidPaint(Color.rgb(rgb)));
    }

    public static final class Builder {
        private final UiComponent label;
        private @Nullable UiKey key;
        private boolean checked;
        private @Nullable Runnable onChange;
        private @Nullable StyleResolver style;

        private Builder(UiComponent label) {
            this.label = Objects.requireNonNull(label, "label");
        }

        public Builder key(UiKey value) { key = Objects.requireNonNull(value, "value"); return this; }
        public Builder checked(boolean value) { checked = value; return this; }
        public Builder onChange(Runnable value) { onChange = Objects.requireNonNull(value, "value"); return this; }
        public Builder style(StyleResolver value) { style = Objects.requireNonNull(value, "value"); return this; }
        public Checkbox build() { return new Checkbox(this); }
    }
}
