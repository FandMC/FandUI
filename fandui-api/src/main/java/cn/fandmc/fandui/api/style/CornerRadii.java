package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.internal.validation.Preconditions;

/** Immutable per-corner radii in clockwise order from the top-left. */
public record CornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
    public static final CornerRadii ZERO = new CornerRadii(0.0f, 0.0f, 0.0f, 0.0f);

    public CornerRadii {
        Preconditions.nonNegative(topLeft, "topLeft");
        Preconditions.nonNegative(topRight, "topRight");
        Preconditions.nonNegative(bottomRight, "bottomRight");
        Preconditions.nonNegative(bottomLeft, "bottomLeft");
    }

    public static CornerRadii all(float value) {
        return new CornerRadii(value, value, value, value);
    }
}
