package cn.fandmc.fandui.api.layout;

import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.Objects;

/** Immutable inclusive minimum/maximum size constraints in logical pixels. */
public record Constraints(float minWidth, float maxWidth, float minHeight, float maxHeight) {
    public Constraints {
        Preconditions.nonNegative(minWidth, "minWidth");
        Preconditions.nonNegative(minHeight, "minHeight");
        validateMaximum(maxWidth, "maxWidth");
        validateMaximum(maxHeight, "maxHeight");
        if (minWidth > maxWidth) {
            throw new IllegalArgumentException("minWidth must not exceed maxWidth");
        }
        if (minHeight > maxHeight) {
            throw new IllegalArgumentException("minHeight must not exceed maxHeight");
        }
    }

    public static Constraints tight(Size size) {
        Objects.requireNonNull(size, "size");
        return new Constraints(size.width(), size.width(), size.height(), size.height());
    }

    public static Constraints tight(float width, float height) {
        return tight(new Size(width, height));
    }

    public static Constraints loose(Size maximum) {
        Objects.requireNonNull(maximum, "maximum");
        return new Constraints(0.0f, maximum.width(), 0.0f, maximum.height());
    }

    public static Constraints loose(float maximumWidth, float maximumHeight) {
        return loose(new Size(maximumWidth, maximumHeight));
    }

    public static Constraints unbounded() {
        return new Constraints(0.0f, Float.POSITIVE_INFINITY, 0.0f, Float.POSITIVE_INFINITY);
    }

    public Size constrain(Size size) {
        Objects.requireNonNull(size, "size");
        return new Size(
                clamp(size.width(), minWidth, maxWidth),
                clamp(size.height(), minHeight, maxHeight));
    }

    private static void validateMaximum(float value, String name) {
        if (Float.isNaN(value) || value == Float.NEGATIVE_INFINITY || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be non-negative or positive infinity");
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
