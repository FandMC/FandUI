package cn.fandmc.fandui.api.layout;

import cn.fandmc.fandui.internal.validation.Preconditions;

/** Immutable non-negative logical width and height. */
public record Size(float width, float height) {
    public Size {
        Preconditions.nonNegative(width, "width");
        Preconditions.nonNegative(height, "height");
    }
}
