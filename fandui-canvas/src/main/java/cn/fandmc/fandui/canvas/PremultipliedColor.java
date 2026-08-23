package cn.fandmc.fandui.canvas;

import cn.fandmc.fandui.api.style.Color;

import java.util.Objects;

public record PremultipliedColor(float red, float green, float blue, float alpha) {
    public PremultipliedColor {
        requireUnit(red, "red");
        requireUnit(green, "green");
        requireUnit(blue, "blue");
        requireUnit(alpha, "alpha");
        if (red > alpha || green > alpha || blue > alpha) {
            throw new IllegalArgumentException("Premultiplied color channels must not exceed alpha");
        }
    }

    public static PremultipliedColor from(Color color) {
        Objects.requireNonNull(color, "color");
        return new PremultipliedColor(
                color.red() * color.alpha(),
                color.green() * color.alpha(),
                color.blue() * color.alpha(),
                color.alpha());
    }

    private static void requireUnit(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
