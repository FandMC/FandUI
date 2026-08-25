package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.ThemeToken;
import cn.fandmc.fandui.api.style.VisualState;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A low-allocation determinate progress bar. Values are clamped to {@code [0, 1]}. */
public final class ProgressIndicator extends UiComponent {
    public static final ThemeToken<Paint> TRACK = paintToken("progress/track", 0x39414a);
    public static final ThemeToken<Paint> VALUE = paintToken("progress/value", 0x36aee1);
    public static final ThemeToken<Insets> PADDING = ThemeToken.of(
            UiKey.of("fandui", "progress/padding"), Insets.class, new Insets(2.0f, 2.0f, 2.0f, 2.0f));
    public static final ThemeToken<Double> WIDTH = ThemeToken.of(
            UiKey.of("fandui", "progress/width"), Double.class, 180.0);
    public static final ThemeToken<Double> HEIGHT = ThemeToken.of(
            UiKey.of("fandui", "progress/height"), Double.class, 8.0);

    private double progress;

    private ProgressIndicator(Builder builder) {
        super(builder.key);
        progress = builder.progress;
        setStyle(builder.style == null ? this::defaultStyle : builder.style);
    }

    public static Builder builder() { return new Builder(); }
    public static ProgressIndicator of(double progress) { return builder().progress(progress).build(); }
    public double progress() { return progress; }

    public void setProgress(double value) {
        requireMutationThread();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("progress must be finite");
        double next = Math.max(0.0, Math.min(1.0, value));
        if (Double.compare(progress, next) != 0) {
            progress = next;
            invalidatePaint();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Insets padding = scope.style().padding();
        float width = positive(scope.theme().value(WIDTH), "width");
        float height = positive(scope.theme().value(HEIGHT), "height");
        Size size = constraints.constrain(new Size(
                width + padding.left() + padding.right(), height + padding.top() + padding.bottom()));
        return scope.layout(size.width(), size.height(), placements -> { });
    }

    @Override
    public void paint(PaintScope scope) {
        Insets padding = scope.style().padding();
        float width = Math.max(0.0f, scope.bounds().width() - padding.left() - padding.right());
        float height = Math.max(0.0f, scope.bounds().height() - padding.top() - padding.bottom());
        var rect = new cn.fandmc.fandui.api.layout.Rect(padding.left(), padding.top(), width, height);
        CornerRadii radii = CornerRadii.all(height * 0.5f);
        scope.canvas().fillRoundedRect(rect, radii, scope.theme().value(TRACK));
        scope.canvas().fillRoundedRect(
                new cn.fandmc.fandui.api.layout.Rect(rect.x(), rect.y(), rect.width() * (float) progress, rect.height()),
                radii,
                scope.theme().value(VALUE));
    }

    private Style defaultStyle(Theme theme, VisualState state) {
        return Style.builder().padding(theme.value(PADDING)).build();
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
        private double progress;
        private @Nullable StyleResolver style;
        private Builder() { }
        public Builder key(UiKey value) { key = Objects.requireNonNull(value, "value"); return this; }
        public Builder progress(double value) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("progress must be finite");
            progress = Math.max(0.0, Math.min(1.0, value));
            return this;
        }
        public Builder style(StyleResolver value) { style = Objects.requireNonNull(value, "value"); return this; }
        public ProgressIndicator build() { return new ProgressIndicator(this); }
    }
}
