package cn.fandmc.fandui.api.layout;

import cn.fandmc.fandui.internal.validation.Preconditions;

/** Immutable non-negative logical-pixel insets ordered left, top, right, bottom. */
public record Insets(float left, float top, float right, float bottom) {
    public static final Insets ZERO = new Insets(0.0f, 0.0f, 0.0f, 0.0f);

    public Insets {
        Preconditions.nonNegative(left, "left");
        Preconditions.nonNegative(top, "top");
        Preconditions.nonNegative(right, "right");
        Preconditions.nonNegative(bottom, "bottom");
    }

    public static Insets all(float value) {
        return new Insets(value, value, value, value);
    }

    public static Insets symmetric(float horizontal, float vertical) {
        return new Insets(horizontal, vertical, horizontal, vertical);
    }
}
