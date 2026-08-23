package cn.fandmc.fandui.render.opengl;

/** Result of one explicitly enabled OpenGL integration probe frame. */
public record OpenGlProbeReport(
        Status status,
        String hostName,
        int framebuffer,
        int internalFormat,
        int width,
        int height
) {
    public enum Status {
        DISABLED,
        NO_TARGET,
        RENDERED,
        TARGET_REBUILT
    }
}

