package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.layout.Rect;

import java.util.Objects;

final class NanoVgPixelAlignment {
    private static final float TRANSFORM_EPSILON = 1.0e-5f;
    private static final float OFFSET_EPSILON = 1.0e-6f;

    private NanoVgPixelAlignment() {
    }

    static boolean textOffset(
            float[] transform,
            Rect destination,
            float devicePixelRatio,
            float[] output) {
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(output, "output");
        if (transform.length < 6) {
            throw new IllegalArgumentException("transform must contain six values");
        }
        if (output.length < 2) {
            throw new IllegalArgumentException("output must contain two values");
        }
        if (!Float.isFinite(devicePixelRatio) || devicePixelRatio <= 0.0f) {
            throw new IllegalArgumentException("devicePixelRatio must be finite and positive");
        }

        output[0] = 0.0f;
        output[1] = 0.0f;
        if (!isTranslation(transform)) {
            return false;
        }

        output[0] = snapDelta(destination.x() + transform[4], devicePixelRatio);
        output[1] = snapDelta(destination.y() + transform[5], devicePixelRatio);
        return output[0] != 0.0f || output[1] != 0.0f;
    }

    private static boolean isTranslation(float[] transform) {
        return approximately(transform[0], 1.0f)
                && approximately(transform[1], 0.0f)
                && approximately(transform[2], 0.0f)
                && approximately(transform[3], 1.0f)
                && Float.isFinite(transform[4])
                && Float.isFinite(transform[5]);
    }

    private static boolean approximately(float value, float expected) {
        return Float.isFinite(value) && Math.abs(value - expected) <= TRANSFORM_EPSILON;
    }

    private static float snapDelta(float coordinate, float devicePixelRatio) {
        double physical = (double) coordinate * devicePixelRatio;
        float snapped = (float) (Math.rint(physical) / devicePixelRatio);
        float delta = snapped - coordinate;
        return Math.abs(delta) <= OFFSET_EPSILON ? 0.0f : delta;
    }
}
