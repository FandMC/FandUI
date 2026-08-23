package cn.fandmc.fandui.api.style;

/** Immutable hovered, pressed, focused, and disabled state supplied to a style resolver. */
public final class VisualState {
    private static final VisualState DEFAULT = new VisualState(false, false, false, false);

    private final boolean hovered;
    private final boolean pressed;
    private final boolean focused;
    private final boolean disabled;

    private VisualState(boolean hovered, boolean pressed, boolean focused, boolean disabled) {
        this.hovered = hovered;
        this.pressed = pressed;
        this.focused = focused;
        this.disabled = disabled;
    }

    public static VisualState defaults() {
        return DEFAULT;
    }

    public static VisualState of(boolean hovered, boolean pressed, boolean focused, boolean disabled) {
        if (!hovered && !pressed && !focused && !disabled) {
            return DEFAULT;
        }
        return new VisualState(hovered, pressed, focused, disabled);
    }

    public boolean hovered() {
        return hovered;
    }

    public boolean pressed() {
        return pressed;
    }

    public boolean focused() {
        return focused;
    }

    public boolean disabled() {
        return disabled;
    }
}
