package cn.fandmc.fandui.api.style;

/** Platform-neutral immutable paint accepted by Canvas2D and component styles. */
public sealed interface Paint permits SolidPaint, LinearGradient, RadialGradient {
    static SolidPaint solid(Color color) {
        return new SolidPaint(color);
    }

    static SolidPaint solidRgb(int rgb) {
        return new SolidPaint(Color.rgb(rgb));
    }
}
