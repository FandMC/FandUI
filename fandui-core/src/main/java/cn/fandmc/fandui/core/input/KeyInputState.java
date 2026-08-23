package cn.fandmc.fandui.core.input;

import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.KeyCode;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Tracks keyboard press state for one Minecraft Screen instance. */
public final class KeyInputState {
    private final Set<KeyCode> pressed = new HashSet<>();

    /**
     * Records a key press.
     *
     * @param key stable FandUI key identity
     * @return {@link KeyAction#PRESS} once and {@link KeyAction#REPEAT} thereafter
     */
    public KeyAction press(KeyCode key) {
        return pressed.add(Objects.requireNonNull(key, "key"))
                ? KeyAction.PRESS
                : KeyAction.REPEAT;
    }

    /**
     * Records a key release.
     *
     * @param key stable FandUI key identity
     * @return {@link KeyAction#RELEASE}
     */
    public KeyAction release(KeyCode key) {
        pressed.remove(Objects.requireNonNull(key, "key"));
        return KeyAction.RELEASE;
    }

    /** Clears all state when the host Screen is removed or reused. */
    public void clear() {
        pressed.clear();
    }

    /**
     * Checks whether the key is currently held by this Screen.
     *
     * @param key stable FandUI key identity
     * @return whether a press has not been paired with a release or clear
     */
    public boolean isPressed(KeyCode key) {
        return pressed.contains(Objects.requireNonNull(key, "key"));
    }
}
