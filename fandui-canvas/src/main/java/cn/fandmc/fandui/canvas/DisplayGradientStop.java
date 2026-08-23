package cn.fandmc.fandui.canvas;

import java.util.Objects;

public record DisplayGradientStop(float offset, PremultipliedColor color) {
    public DisplayGradientStop {
        if (!Float.isFinite(offset) || offset < 0.0f || offset > 1.0f) {
            throw new IllegalArgumentException("offset must be between 0 and 1");
        }
        Objects.requireNonNull(color, "color");
    }
}
