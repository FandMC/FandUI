package cn.fandmc.fandui.api.canvas;

import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.Objects;

/** Immutable stroke width, cap, join, miter, and dash configuration. */
public final class StrokeStyle {
    private final float width;
    private final LineCap cap;
    private final LineJoin join;
    private final float miterLimit;

    private StrokeStyle(Builder builder) {
        this.width = builder.width;
        this.cap = builder.cap;
        this.join = builder.join;
        this.miterLimit = builder.miterLimit;
    }

    public static Builder width(float width) {
        return new Builder(width);
    }

    public float width() {
        return width;
    }

    public LineCap cap() {
        return cap;
    }

    public LineJoin join() {
        return join;
    }

    public float miterLimit() {
        return miterLimit;
    }

    public static final class Builder {
        private final float width;
        private LineCap cap = LineCap.BUTT;
        private LineJoin join = LineJoin.MITER;
        private float miterLimit = 10.0f;

        private Builder(float width) {
            Preconditions.finite(width, "width");
            if (width <= 0.0f) {
                throw new IllegalArgumentException("width must be positive");
            }
            this.width = width;
        }

        public Builder cap(LineCap value) {
            this.cap = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder join(LineJoin value) {
            this.join = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder miterLimit(float value) {
            Preconditions.finite(value, "value");
            if (value <= 0.0f) {
                throw new IllegalArgumentException("miterLimit must be positive");
            }
            this.miterLimit = value;
            return this;
        }

        public StrokeStyle build() {
            return new StrokeStyle(this);
        }
    }
}
