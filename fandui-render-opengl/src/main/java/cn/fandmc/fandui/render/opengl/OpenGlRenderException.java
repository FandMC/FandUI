package cn.fandmc.fandui.render.opengl;

public final class OpenGlRenderException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OpenGlRenderException(String message) {
        super(message);
    }

    public OpenGlRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
