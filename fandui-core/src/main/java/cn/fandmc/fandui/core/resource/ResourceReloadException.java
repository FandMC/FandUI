package cn.fandmc.fandui.core.resource;

/** Reports a rejected candidate resource generation. */
public final class ResourceReloadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ResourceReloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
