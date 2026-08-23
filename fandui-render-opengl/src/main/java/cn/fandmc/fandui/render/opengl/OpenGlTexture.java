package cn.fandmc.fandui.render.opengl;

/** A borrowed FandUI-owned texture configured for the requested sampling mode. */
public record OpenGlTexture(int textureId) {
    public OpenGlTexture {
        if (textureId <= 0) {
            throw new IllegalArgumentException("textureId must be positive");
        }
    }
}
