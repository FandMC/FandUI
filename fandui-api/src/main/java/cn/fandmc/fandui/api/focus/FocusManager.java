package cn.fandmc.fandui.api.focus;

import cn.fandmc.fandui.api.component.UiComponent;

import java.util.Optional;

/** UI-thread-confined focus owner and traversal service for one active session. */
public interface FocusManager {
    /** Returns the currently focused eligible component. */
    Optional<UiComponent> focused();

    /** Requests focus for an attached, visible, enabled, and focusable component. */
    boolean request(UiComponent component);

    /** Moves focus in logical tab order or transformed spatial geometry. */
    boolean move(FocusDirection direction);

    /** Clears current focus. */
    void clear();
}
