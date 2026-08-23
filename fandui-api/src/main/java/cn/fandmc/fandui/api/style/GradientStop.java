package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.Objects;

/** Immutable gradient color stop with a normalized offset. */
public record GradientStop(float offset, Color color) {
    public GradientStop {
        Preconditions.unit(offset, "offset");
        Objects.requireNonNull(color, "color");
    }

    public static GradientStop at(float offset, Color color) {
        return new GradientStop(offset, color);
    }
}
