package cn.fandmc.fandui.render.opengl;

/** Indicates a hard failure in the temporary OpenGL integration probe. */
public final class OpenGlProbeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OpenGlProbeException(String message) {
        super(message);
    }

    public OpenGlProbeException(String message, Throwable cause) {
        super(message, cause);
    }
}
