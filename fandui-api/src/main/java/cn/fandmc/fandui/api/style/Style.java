package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.Objects;

/**
 * Immutable component style resolved from a theme and visual state.
 *
 * <p>Core applies {@link #margin()}, {@link #opacity()}, {@link #transform()},
 * {@link #clip()}, {@link #backdropBlurRadius()}, and rounded clip radii to every
 * component. Box-oriented components explicitly consume {@link #padding()},
 * {@link #background()}, {@link #border()}, and decorative {@link #cornerRadii()}.
 * A custom component receives the resolved instance in measure and paint scopes and
 * decides how those box fields affect its own content. This avoids implicit double
 * padding or decoration around custom drawing.</p>
 */
public final class Style implements StyleResolver {
    private static final Paint TRANSPARENT = new SolidPaint(new Color(0.0f, 0.0f, 0.0f, 0.0f));
    private static final Insets ZERO_INSETS = new Insets(0.0f, 0.0f, 0.0f, 0.0f);
    private static final CornerRadii ZERO_RADII = new CornerRadii(0.0f, 0.0f, 0.0f, 0.0f);
    private static final Transform2D IDENTITY = new Transform2D(1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f);
    private static final Style DEFAULTS = new Builder().build();

    private final Insets margin;
    private final Insets padding;
    private final Paint background;
    private final Border border;
    private final CornerRadii cornerRadii;
    private final float backdropBlurRadius;
    private final float opacity;
    private final Transform2D transform;
    private final ClipMode clip;

    private Style(Builder builder) {
        this.margin = builder.margin;
        this.padding = builder.padding;
        this.background = builder.background;
        this.border = builder.border;
        this.cornerRadii = builder.cornerRadii;
        this.backdropBlurRadius = builder.backdropBlurRadius;
        this.opacity = builder.opacity;
        this.transform = builder.transform;
        this.clip = builder.clip;
    }

    public static Style defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Style base) {
        return new Builder(Objects.requireNonNull(base, "base"));
    }

    public Insets margin() {
        return margin;
    }

    public Insets padding() {
        return padding;
    }

    public Paint background() {
        return background;
    }

    public Border border() {
        return border;
    }

    public CornerRadii cornerRadii() {
        return cornerRadii;
    }

    public float backdropBlurRadius() {
        return backdropBlurRadius;
    }

    public float opacity() {
        return opacity;
    }

    public Transform2D transform() {
        return transform;
    }

    public ClipMode clip() {
        return clip;
    }

    /** Allows an immutable style to be passed directly wherever a fixed resolver is accepted. */
    @Override
    public Style resolve(Theme theme, VisualState state) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(state, "state");
        return this;
    }

    public static final class Builder {
        private Insets margin = ZERO_INSETS;
        private Insets padding = ZERO_INSETS;
        private Paint background = TRANSPARENT;
        private Border border = new Border(0.0f, TRANSPARENT);
        private CornerRadii cornerRadii = ZERO_RADII;
        private float backdropBlurRadius;
        private float opacity = 1.0f;
        private Transform2D transform = IDENTITY;
        private ClipMode clip = ClipMode.NONE;

        private Builder() {
        }

        private Builder(Style base) {
            this.margin = base.margin;
            this.padding = base.padding;
            this.background = base.background;
            this.border = base.border;
            this.cornerRadii = base.cornerRadii;
            this.backdropBlurRadius = base.backdropBlurRadius;
            this.opacity = base.opacity;
            this.transform = base.transform;
            this.clip = base.clip;
        }

        public Builder margin(Insets value) {
            this.margin = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder margin(float all) {
            return margin(Insets.all(all));
        }

        public Builder margin(float horizontal, float vertical) {
            return margin(Insets.symmetric(horizontal, vertical));
        }

        public Builder padding(Insets value) {
            this.padding = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder padding(float all) {
            return padding(Insets.all(all));
        }

        public Builder padding(float horizontal, float vertical) {
            return padding(Insets.symmetric(horizontal, vertical));
        }

        public Builder background(Paint value) {
            this.background = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder background(Color value) {
            return background(new SolidPaint(value));
        }

        public Builder border(Border value) {
            this.border = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder border(float width, Paint paint) {
            return border(new Border(width, paint));
        }

        public Builder border(float width, Color color) {
            return border(width, new SolidPaint(color));
        }

        public Builder cornerRadii(CornerRadii value) {
            this.cornerRadii = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder cornerRadius(float value) {
            return cornerRadii(CornerRadii.all(value));
        }

        public Builder backdropBlur(float radius) {
            this.backdropBlurRadius = Preconditions.nonNegative(radius, "radius");
            return this;
        }

        public Builder opacity(float value) {
            this.opacity = Preconditions.unit(value, "value");
            return this;
        }

        public Builder transform(Transform2D value) {
            this.transform = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder clip(ClipMode value) {
            this.clip = Objects.requireNonNull(value, "value");
            return this;
        }

        public Style build() {
            return new Style(this);
        }
    }
}
