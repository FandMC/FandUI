package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.animation.AnimationSpec;
import cn.fandmc.fandui.api.animation.Easings;
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
import cn.fandmc.fandui.internal.control.ScalarTransition;
import cn.fandmc.fandui.internal.control.ValueChangeListeners;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/** A compact, keyboard-accessible on/off switch with a sliding thumb. */
public final class ToggleSwitch extends UiComponent {
    private static final AnimationSpec THUMB_TRANSITION = AnimationSpec.duration(Duration.ofMillis(140))
            .easing(Easings.EASE_OUT)
            .build();

    public static final ThemeToken<Paint> TRACK_OFF = paintToken("switch/track_off", 0x4b535d);
    public static final ThemeToken<Paint> TRACK_ON = paintToken("switch/track_on", 0x2fabe0);
    public static final ThemeToken<Paint> TRACK_HOVERED = paintToken("switch/track_hovered", 0x61c7e8);
    public static final ThemeToken<Paint> TRACK_DISABLED = paintToken("switch/track_disabled", 0x343941);
    public static final ThemeToken<Paint> THUMB = paintToken("switch/thumb", 0xf4f8fb);
    public static final ThemeToken<Paint> THUMB_DISABLED = paintToken("switch/thumb_disabled", 0x8a929b);
    public static final ThemeToken<Paint> FOCUS_RING = paintToken("switch/focus_ring", 0x9ce6ff);
    public static final ThemeToken<Insets> PADDING = ThemeToken.of(
            UiKey.of("fandui", "switch/padding"), Insets.class, Insets.all(3.0f));
    public static final ThemeToken<Double> WIDTH = ThemeToken.of(
            UiKey.of("fandui", "switch/width"), Double.class, 38.0);
    public static final ThemeToken<Double> HEIGHT = ThemeToken.of(
            UiKey.of("fandui", "switch/height"), Double.class, 22.0);
    public static final ThemeToken<Double> THUMB_SIZE = ThemeToken.of(
            UiKey.of("fandui", "switch/thumb_size"), Double.class, 16.0);
    public static final ThemeToken<Double> THUMB_INSET = ThemeToken.of(
            UiKey.of("fandui", "switch/thumb_inset"), Double.class, 3.0);

    private final ChangeListeners changes = new ChangeListeners();
    private final ValueChangeListeners<Boolean> valueChanges = new ValueChangeListeners<>();
    private final PressableSupport pressable = new PressableSupport();
    private final ScalarTransition thumbTransition;
    private boolean selected;
    private Size measuredSize = new Size(0.0f, 0.0f);

    private ToggleSwitch(Builder builder) {
        super(builder.key);
        selected = builder.selected;
        thumbTransition = new ScalarTransition(
                selected ? 1.0 : 0.0,
                THUMB_TRANSITION,
                ignored -> invalidatePaint());
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

    public static Builder builder() {
        return new Builder();
    }

    public static ToggleSwitch of(boolean selected) {
        return builder().selected(selected).build();
    }

    public boolean selected() {
        return selected;
    }

    /** Alias useful when a switch models a checked setting. */
    public boolean checked() {
        return selected;
    }

    public void setSelected(boolean selected) {
        requireMutationThread();
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
        thumbTransition.setTarget(selected ? 1.0 : 0.0);
        changes.notifyListeners();
        valueChanges.notifyListeners(selected);
    }

    public void setChecked(boolean checked) {
        setSelected(checked);
    }

    public void toggle() {
        setSelected(!selected);
    }

    public EventRegistration onChange(Runnable listener) {
        requireMutationThread();
        return changes.add(listener);
    }

    public EventRegistration onValueChange(Consumer<Boolean> listener) {
        requireMutationThread();
        return valueChanges.add(listener);
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Insets padding = scope.style().padding();
        float width = positive(scope.theme().value(WIDTH), "width");
        float height = positive(scope.theme().value(HEIGHT), "height");
        Size size = constraints.constrain(new Size(
                width + padding.left() + padding.right(),
                height + padding.top() + padding.bottom()));
        measuredSize = size;
        return scope.layout(size.width(), size.height(), placements -> { });
    }

    @Override
    public void paint(PaintScope scope) {
        Insets padding = scope.style().padding();
        float width = Math.max(0.0f, scope.bounds().width() - padding.left() - padding.right());
        float height = Math.max(0.0f, scope.bounds().height() - padding.top() - padding.bottom());
        if (width == 0.0f || height == 0.0f) {
            return;
        }
        var track = new cn.fandmc.fandui.api.layout.Rect(padding.left(), padding.top(), width, height);
        Paint trackPaint = scope.style().background();
        scope.canvas().fillRoundedRect(track, CornerRadii.all(height * 0.5f), trackPaint);

        float configuredInset = nonNegative(scope.theme().value(THUMB_INSET), "thumb inset");
        float verticalInset = Math.min(configuredInset, height * 0.5f);
        float thumbSize = Math.min(
                Math.max(0.0f, height - verticalInset * 2.0f),
                positive(scope.theme().value(THUMB_SIZE), "thumb size"));
        if (thumbSize == 0.0f) {
            return;
        }
        float horizontalInset = Math.min(configuredInset, Math.max(0.0f, width - thumbSize) * 0.5f);
        float travel = Math.max(0.0f, width - thumbSize - horizontalInset * 2.0f);
        float progress = (float) Math.max(0.0, Math.min(1.0, thumbTransition.value()));
        float left = track.x() + horizontalInset + travel * progress;
        var thumb = new cn.fandmc.fandui.api.layout.Rect(
                left, track.y() + (height - thumbSize) * 0.5f, thumbSize, thumbSize);
        scope.canvas().fillRoundedRect(
                thumb,
                CornerRadii.all(thumbSize * 0.5f),
                enabled() ? scope.theme().value(THUMB) : scope.theme().value(THUMB_DISABLED));
        Border border = scope.style().border();
        if (border.width() > 0.0f) {
            scope.canvas().stroke(
                    Path.builder().roundedRect(track, CornerRadii.all(height * 0.5f)).build(),
                    border.paint(),
                    StrokeStyle.width(border.width()).build());
        }
    }

    @Override
    public void attached(ComponentContext context) {
        thumbTransition.attach(context);
    }

    @Override
    public void detached(ComponentContext context) {
        thumbTransition.detach(context);
    }

    private void handlePointer(PointerEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        pressable.handlePointer(this, event, context, measuredSize, this::toggle);
    }

    private void handleKey(KeyEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        if (!enabled() || event.action() != cn.fandmc.fandui.api.event.KeyAction.PRESS) {
            return;
        }
        if (event.key().equals(cn.fandmc.fandui.api.event.Keys.ENTER)
                || event.key().equals(cn.fandmc.fandui.api.event.Keys.SPACE)
                || event.key().equals(cn.fandmc.fandui.api.event.Keys.LEFT)
                || event.key().equals(cn.fandmc.fandui.api.event.Keys.RIGHT)) {
            if (event.key().equals(cn.fandmc.fandui.api.event.Keys.LEFT)) {
                setSelected(false);
            } else if (event.key().equals(cn.fandmc.fandui.api.event.Keys.RIGHT)) {
                setSelected(true);
            } else {
                toggle();
            }
            context.consume();
        }
    }

    private Style defaultStyle(Theme theme, VisualState state) {
        Paint track = !enabled()
                ? theme.value(TRACK_DISABLED)
                : selected
                        ? theme.value(TRACK_ON)
                        : state.hovered() ? theme.value(TRACK_HOVERED) : theme.value(TRACK_OFF);
        return Style.builder()
                .padding(theme.value(PADDING))
                .background(track)
                .border(state.focused()
                        ? new Border(1.0f, theme.value(FOCUS_RING))
                        : new Border(0.0f, theme.value(TRACK_OFF)))
                .build();
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
        private @Nullable UiKey key;
        private boolean selected;
        private @Nullable Runnable onChange;
        private @Nullable Consumer<Boolean> onValueChange;
        private @Nullable StyleResolver style;

        private Builder() {
        }

        public Builder key(UiKey value) {
            key = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder selected(boolean value) {
            selected = value;
            return this;
        }

        public Builder checked(boolean value) {
            return selected(value);
        }

        public Builder onChange(Runnable value) {
            onChange = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder onValueChange(Consumer<Boolean> value) {
            onValueChange = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder style(StyleResolver value) {
            style = Objects.requireNonNull(value, "value");
            return this;
        }

        public ToggleSwitch build() {
            return new ToggleSwitch(this);
        }
    }
}
