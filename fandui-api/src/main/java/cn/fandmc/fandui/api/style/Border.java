package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.Objects;

/** Immutable inward/outward centered border stroke description in logical pixels. */
public record Border(float width, Paint paint) {
    public Border {
        Preconditions.nonNegative(width, "width");
        Objects.requireNonNull(paint, "paint");
    }
}
