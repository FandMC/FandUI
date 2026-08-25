package cn.fandmc.fandui.api.icon;

import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.SolidPaint;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable vector icon definition expressed in a logical view box. */
public final class IconDefinition {
    private static final Paint MONOCHROME = new SolidPaint(Color.rgb(0xffffff));
    private final Size viewBox;
    private final List<Layer> layers;

    private IconDefinition(Builder builder) {
        viewBox = new Size(builder.width, builder.height);
        layers = List.copyOf(builder.layers);
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("An icon definition requires at least one layer");
        }
    }

    /** Parses a bounded, platform-neutral SVG subset into a vector definition. */
    public static IconDefinition fromSvg(String svg) {
        return SvgParser.parse(svg);
    }

    public static Builder builder(float width, float height) {
        return new Builder(width, height);
    }

    /** Creates a monochrome filled icon from one path. */
    public static IconDefinition monochrome(float width, float height, Path path) {
        return builder(width, height).layer(Layer.fill(path, null)).build();
    }

    public Size viewBox() {
        return viewBox;
    }

    public List<Layer> layers() {
        return layers;
    }

    /** One immutable fill and/or stroke operation in view-box coordinates. */
    public record Layer(
            Path path,
            @Nullable Paint fill,
            @Nullable Paint stroke,
            @Nullable StrokeStyle strokeStyle) {
        public Layer {
            Objects.requireNonNull(path, "path");
            if (stroke != null && strokeStyle == null) {
                throw new IllegalArgumentException("A stroked icon layer needs strokeStyle");
            }
            if (stroke == null && strokeStyle != null) {
                throw new IllegalArgumentException("strokeStyle requires stroke");
            }
        }

        /** A fill layer. A null paint means the component tint. */
        public static Layer fill(Path path, @Nullable Paint paint) {
            return new Layer(path, paint == null ? MONOCHROME : paint, null, null);
        }

        /** A stroke layer. A null paint means the component tint. */
        public static Layer stroke(Path path, @Nullable Paint paint, StrokeStyle style) {
            return new Layer(path, null, paint == null ? MONOCHROME : paint,
                    Objects.requireNonNull(style, "style"));
        }

        /** A layer that uses the component tint for both fill and stroke. */
        public static Layer fillAndStroke(
                Path path,
                @Nullable Paint fill,
                @Nullable Paint stroke,
                StrokeStyle style) {
            return new Layer(path, fill, stroke, Objects.requireNonNull(style, "style"));
        }
    }

    /** Fluent immutable-definition builder. */
    public static final class Builder {
        private final float width;
        private final float height;
        private final List<Layer> layers = new ArrayList<>();

        private Builder(float width, float height) {
            if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0f || height <= 0.0f) {
                throw new IllegalArgumentException("Icon view-box dimensions must be finite and positive");
            }
            this.width = width;
            this.height = height;
        }

        public Builder layer(Layer layer) {
            layers.add(Objects.requireNonNull(layer, "layer"));
            return this;
        }

        public Builder layers(List<Layer> values) {
            Objects.requireNonNull(values, "values");
            for (Layer value : values) {
                layer(value);
            }
            return this;
        }

        public Builder fill(Path path, @Nullable Paint paint) {
            return layer(Layer.fill(path, paint));
        }

        public Builder stroke(Path path, @Nullable Paint paint, StrokeStyle style) {
            return layer(Layer.stroke(path, paint, style));
        }

        public IconDefinition build() {
            return new IconDefinition(this);
        }
    }
}
