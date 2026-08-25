package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.layout.Alignment;
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
import cn.fandmc.fandui.api.text.TextStyle;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A focusable single-child control activated by primary click, Enter, or Space. */
public final class Button extends UiContainer {
    public static final ThemeToken<Paint> BACKGROUND = paintToken("button/background", 0x34383f);
    public static final ThemeToken<Paint> HOVERED_BACKGROUND = paintToken("button/background_hovered", 0x454b54);
    public static final ThemeToken<Paint> PRESSED_BACKGROUND = paintToken("button/background_pressed", 0x25292f);
    public static final ThemeToken<Paint> DISABLED_BACKGROUND = paintToken("button/background_disabled", 0x25272b);
    public static final ThemeToken<Insets> PADDING = ThemeToken.of(
            UiKey.of("fandui", "button/padding"),
            Insets.class,
            new Insets(10.0f, 6.0f, 10.0f, 6.0f));
    public static final ThemeToken<Border> BORDER = ThemeToken.of(
            UiKey.of("fandui", "button/border"),
            Border.class,
            new Border(1.0f, new SolidPaint(Color.rgb(0x707780))));
    public static final ThemeToken<CornerRadii> CORNER_RADII = ThemeToken.of(
            UiKey.of("fandui", "button/corner_radii"),
            CornerRadii.class,
            new CornerRadii(4.0f, 4.0f, 4.0f, 4.0f));

    private Alignment alignment;
    private Runnable onClick;
    private Size measuredSize = new Size(0.0f, 0.0f);
    private final PressableSupport pressable = new PressableSupport();

    private Button(Builder builder) {
        super(builder.key, 1, 1);
        alignment = builder.alignment;
        onClick = builder.onClick;
        setFocusable(true);
        setStyle(builder.style == null ? Button::defaultStyle : builder.style);
        add(builder.child);
        on(PointerEvent.class, EventRoute.BUBBLE, this::handlePointer);
        on(KeyEvent.class, EventRoute.BUBBLE, this::handleKey);
    }

    public static Builder builder(UiComponent child) {
        return new Builder(child);
    }

    public static Button of(UiComponent child, Runnable onClick) {
        return builder(child).onClick(onClick).build();
    }

    public static Button text(String label, TextStyle textStyle, Runnable onClick) {
        return of(Text.of(label, textStyle), onClick);
    }

    public static Button text(String label, float fontSize, Runnable onClick) {
        return text(label, TextStyle.of(fontSize), onClick);
    }

    public UiComponent child() {
        return children().get(0);
    }

    public void setChild(UiComponent child) {
        replace(0, child);
    }

    public Alignment alignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        requireMutationThread();
        Alignment checked = Objects.requireNonNull(alignment, "alignment");
        if (this.alignment != checked) {
            this.alignment = checked;
            invalidateLayout();
        }
    }

    public void setOnClick(Runnable onClick) {
        requireMutationThread();
        this.onClick = Objects.requireNonNull(onClick, "onClick");
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        MeasureResult result = SingleChildSupport.measure(child(), alignment, scope, constraints);
        measuredSize = result.size();
        return result;
    }

    @Override
    public void paint(PaintScope scope) {
        SingleChildSupport.paintBackground(scope);
    }

    private void handlePointer(PointerEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        pressable.handlePointer(this, event, context, measuredSize, onClick);
    }

    private void handleKey(KeyEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        pressable.handleKey(this, event, context, onClick);
    }

    private static Style defaultStyle(Theme theme, VisualState state) {
        Paint background = state.disabled()
                ? theme.value(DISABLED_BACKGROUND)
                : state.pressed()
                        ? theme.value(PRESSED_BACKGROUND)
                        : state.hovered() ? theme.value(HOVERED_BACKGROUND) : theme.value(BACKGROUND);
        return Style.builder()
                .padding(theme.value(PADDING))
                .background(background)
                .border(theme.value(BORDER))
                .cornerRadii(theme.value(CORNER_RADII))
                .build();
    }

    private static ThemeToken<Paint> paintToken(String value, int rgb) {
        return ThemeToken.of(
                UiKey.of("fandui", value),
                Paint.class,
                new SolidPaint(Color.rgb(rgb)));
    }

    public static final class Builder {
        private final UiComponent child;
        private @Nullable UiKey key;
        private Alignment alignment = Alignment.CENTER;
        private Runnable onClick = () -> { };
        private @Nullable StyleResolver style;

        private Builder(UiComponent child) {
            this.child = Objects.requireNonNull(child, "child");
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder alignment(Alignment alignment) {
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public Builder onClick(Runnable onClick) {
            this.onClick = Objects.requireNonNull(onClick, "onClick");
            return this;
        }

        public Builder style(StyleResolver style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public Button build() {
            return new Button(this);
        }
    }
}
