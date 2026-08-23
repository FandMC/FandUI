package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.internal.validation.Preconditions;

/** Immutable unpremultiplied sRGB color; rendering converts it to premultiplied alpha. */
public record Color(float red, float green, float blue, float alpha) {
    public Color {
        Preconditions.unit(red, "red");
        Preconditions.unit(green, "green");
        Preconditions.unit(blue, "blue");
        Preconditions.unit(alpha, "alpha");
    }

    public static Color rgb(int rgb) {
        return new Color(channel(rgb, 16), channel(rgb, 8), channel(rgb, 0), 1.0f);
    }

    public static Color argb(int argb) {
        return new Color(channel(argb, 16), channel(argb, 8), channel(argb, 0), channel(argb, 24));
    }

    public Color withAlpha(float alpha) {
        return new Color(red, green, blue, alpha);
    }

    private static float channel(int packed, int shift) {
        return ((packed >>> shift) & 0xff) / 255.0f;
    }
}
