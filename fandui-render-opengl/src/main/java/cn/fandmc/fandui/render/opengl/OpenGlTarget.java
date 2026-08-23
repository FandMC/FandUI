package cn.fandmc.fandui.render.opengl;

/** A borrowed view of the current Minecraft-owned color texture. */
public record OpenGlTarget(
        int colorTextureId,
        int mipLevel,
        int width,
        int height,
        long generationToken
) {
    public OpenGlTarget {
        if (colorTextureId <= 0) {
            throw new IllegalArgumentException("colorTextureId must be positive");
        }
        if (mipLevel < 0) {
            throw new IllegalArgumentException("mipLevel must not be negative");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("target dimensions must be positive");
        }
    }
}

