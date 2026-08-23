package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.internal.validation.Preconditions;
import cn.fandmc.fandui.internal.validation.Utf16;

import java.util.Objects;

/** Immutable Unicode shaping, wrapping, alignment, and overflow request. */
public final class TextRequest {
    private final String text;
    private final TextStyle style;
    private final float maxWidth;
    private final int maxLines;
    private final TextWrap wrap;
    private final TextOverflow overflow;
    private final TextAlignment alignment;
    private final TextDirection direction;

    private TextRequest(Builder builder) {
        this.text = Utf16.wellFormed(builder.text, "text");
        this.style = builder.style;
        this.maxWidth = builder.maxWidth;
        this.maxLines = builder.maxLines;
        this.wrap = builder.wrap;
        this.overflow = builder.overflow;
        this.alignment = builder.alignment;
        this.direction = builder.direction;
    }

    public static Builder builder(String text, TextStyle style) {
        return new Builder(text, style);
    }

    public String text() {
        return text;
    }

    public TextStyle style() {
        return style;
    }

    public float maxWidth() {
        return maxWidth;
    }

    public int maxLines() {
        return maxLines;
    }

    public TextWrap wrap() {
        return wrap;
    }

    public TextOverflow overflow() {
        return overflow;
    }

    public TextAlignment alignment() {
        return alignment;
    }

    public TextDirection direction() {
        return direction;
    }

    public static final class Builder {
        private final String text;
        private final TextStyle style;
        private float maxWidth = Float.POSITIVE_INFINITY;
        private int maxLines = Integer.MAX_VALUE;
        private TextWrap wrap = TextWrap.WORD;
        private TextOverflow overflow = TextOverflow.CLIP;
        private TextAlignment alignment = TextAlignment.START;
        private TextDirection direction = TextDirection.AUTO;

        private Builder(String text, TextStyle style) {
            this.text = Objects.requireNonNull(text, "text");
            this.style = Objects.requireNonNull(style, "style");
        }

        public Builder maxWidth(float maxWidth) {
            if (Float.isNaN(maxWidth) || maxWidth == Float.NEGATIVE_INFINITY || maxWidth < 0.0f) {
                throw new IllegalArgumentException("maxWidth must be non-negative or positive infinity");
            }
            this.maxWidth = maxWidth;
            return this;
        }

        public Builder maxLines(int maxLines) {
            if (maxLines < 1) {
                throw new IllegalArgumentException("maxLines must be at least 1");
            }
            this.maxLines = maxLines;
            return this;
        }

        public Builder wrap(TextWrap wrap) {
            this.wrap = Objects.requireNonNull(wrap, "wrap");
            return this;
        }

        public Builder overflow(TextOverflow overflow) {
            this.overflow = Objects.requireNonNull(overflow, "overflow");
            return this;
        }

        public Builder alignment(TextAlignment alignment) {
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public Builder direction(TextDirection direction) {
            this.direction = Objects.requireNonNull(direction, "direction");
            return this;
        }

        public TextRequest build() {
            return new TextRequest(this);
        }
    }
}
