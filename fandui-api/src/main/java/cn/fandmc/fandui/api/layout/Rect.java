package cn.fandmc.fandui.api.layout;

import cn.fandmc.fandui.internal.validation.Preconditions;

/** Immutable finite rectangle with non-negative logical width and height. */
public record Rect(float x, float y, float width, float height) {
    public Rect {
        Preconditions.finite(x, "x");
        Preconditions.finite(y, "y");
        Preconditions.nonNegative(width, "width");
        Preconditions.nonNegative(height, "height");
    }
}
