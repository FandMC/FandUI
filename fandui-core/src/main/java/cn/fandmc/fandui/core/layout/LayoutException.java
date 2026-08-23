package cn.fandmc.fandui.core.layout;

public final class LayoutException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public LayoutException(String message) {
        super(message);
    }

    public LayoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
