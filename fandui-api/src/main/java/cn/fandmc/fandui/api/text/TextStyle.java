package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable font fallback, metrics, color, spacing, and BCP-47 locale style. */
public final class TextStyle {
    private final List<FontFamily> families;
    private final float fontSize;
    private final FontWeight weight;
    private final FontSlant slant;
    private final Color color;
    private final float lineHeight;
    private final float letterSpacing;
    private final float wordSpacing;
    private final String locale;

    private TextStyle(Builder builder) {
        this.families = List.copyOf(builder.families);
        this.fontSize = builder.fontSize;
        this.weight = builder.weight;
        this.slant = builder.slant;
        this.color = builder.color;
        this.lineHeight = builder.lineHeight;
        this.letterSpacing = builder.letterSpacing;
        this.wordSpacing = builder.wordSpacing;
        this.locale = builder.locale;
    }

    public static Builder builder(float fontSize) {
        return new Builder(fontSize);
    }

    public static Builder builder(TextStyle base) {
        return new Builder(Objects.requireNonNull(base, "base"));
    }

    public static TextStyle of(float fontSize) {
        return builder(fontSize).build();
    }

    public List<FontFamily> families() {
        return families;
    }

    public float fontSize() {
        return fontSize;
    }

    public FontWeight weight() {
        return weight;
    }

    public FontSlant slant() {
        return slant;
    }

    public Color color() {
        return color;
    }

    public float lineHeight() {
        return lineHeight;
    }

    public float letterSpacing() {
        return letterSpacing;
    }

    public float wordSpacing() {
        return wordSpacing;
    }

    public String locale() {
        return locale;
    }

    public static final class Builder {
        private final float fontSize;
        private List<FontFamily> families = List.of(FontFamilies.DEFAULT);
        private FontWeight weight = FontWeight.NORMAL;
        private FontSlant slant = FontSlant.UPRIGHT;
        private Color color = Color.rgb(0xffffff);
        private float lineHeight;
        private float letterSpacing;
        private float wordSpacing;
        private String locale = "und";

        private Builder(float fontSize) {
            Preconditions.finite(fontSize, "fontSize");
            if (fontSize <= 0.0f) {
                throw new IllegalArgumentException("fontSize must be positive");
            }
            this.fontSize = fontSize;
        }

        private Builder(TextStyle base) {
            this.fontSize = base.fontSize;
            this.families = base.families;
            this.weight = base.weight;
            this.slant = base.slant;
            this.color = base.color;
            this.lineHeight = base.lineHeight;
            this.letterSpacing = base.letterSpacing;
            this.wordSpacing = base.wordSpacing;
            this.locale = base.locale;
        }

        public Builder families(List<FontFamily> families) {
            this.families = List.copyOf(Objects.requireNonNull(families, "families"));
            return this;
        }

        public Builder families(FontFamily... families) {
            Objects.requireNonNull(families, "families");
            return families(List.of(families));
        }

        public Builder weight(FontWeight weight) {
            this.weight = Objects.requireNonNull(weight, "weight");
            return this;
        }

        public Builder slant(FontSlant slant) {
            this.slant = Objects.requireNonNull(slant, "slant");
            return this;
        }

        public Builder color(Color color) {
            this.color = Objects.requireNonNull(color, "color");
            return this;
        }

        public Builder lineHeight(float lineHeight) {
            Preconditions.nonNegative(lineHeight, "lineHeight");
            this.lineHeight = lineHeight;
            return this;
        }

        public Builder letterSpacing(float letterSpacing) {
            this.letterSpacing = Preconditions.finite(letterSpacing, "letterSpacing");
            return this;
        }

        public Builder wordSpacing(float wordSpacing) {
            this.wordSpacing = Preconditions.finite(wordSpacing, "wordSpacing");
            return this;
        }

        public Builder locale(String locale) {
            Objects.requireNonNull(locale, "locale");
            try {
                this.locale = new Locale.Builder().setLanguageTag(locale).build().toLanguageTag();
            } catch (java.util.IllformedLocaleException exception) {
                throw new IllegalArgumentException("Invalid BCP-47 locale: " + locale, exception);
            }
            return this;
        }

        public TextStyle build() {
            return new TextStyle(this);
        }
    }
}
