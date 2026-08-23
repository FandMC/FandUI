package cn.fandmc.fandui.render.opengl;

/** Logical frame geometry used by NanoVG and its Minecraft color target. */
public record OpenGlFrameInfo(float logicalWidth, float logicalHeight, float devicePixelRatio) {
    public OpenGlFrameInfo {
        requirePositiveFinite(logicalWidth, "logicalWidth");
        requirePositiveFinite(logicalHeight, "logicalHeight");
        requirePositiveFinite(devicePixelRatio, "devicePixelRatio");
    }

    void validateTarget(OpenGlTarget target) {
        float tolerance = Math.max(1.0f, devicePixelRatio) + 1.0e-3f;
        if (Math.abs(logicalWidth * devicePixelRatio - target.width()) > tolerance
                || Math.abs(logicalHeight * devicePixelRatio - target.height()) > tolerance) {
            throw new OpenGlRenderException(
                    "Logical viewport " + logicalWidth + "x" + logicalHeight
                            + " at DPR " + devicePixelRatio
                            + " does not match target " + target.width() + "x" + target.height());
        }
    }

    private static void requirePositiveFinite(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
