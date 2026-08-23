package cn.fandmc.fandui.core.input;

import cn.fandmc.fandui.api.event.KeyCode;
import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.PointerButton;

import java.util.EnumSet;
import java.util.Set;

/** Maps stable GLFW numeric input values without exposing LWJGL in the core module. */
public final class GlfwInputMapper {
    private static final int MOD_SHIFT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_ALT = 0x0004;
    private static final int MOD_SUPER = 0x0008;
    private static final int MOD_CAPS_LOCK = 0x0010;
    private static final int MOD_NUM_LOCK = 0x0020;

    private GlfwInputMapper() {
    }

    public static KeyCode key(int key) {
        if (key >= 65 && key <= 90) {
            return Keys.letter((char) ('a' + key - 65));
        }
        if (key >= 48 && key <= 57) {
            return Keys.digit(key - 48);
        }
        if (key >= 290 && key <= 314) {
            return Keys.function(key - 289);
        }
        if (key >= 320 && key <= 329) {
            return Keys.keypadDigit(key - 320);
        }
        return switch (key) {
            case 32 -> Keys.SPACE;
            case 256 -> Keys.ESCAPE;
            case 257, 335 -> Keys.ENTER;
            case 258 -> Keys.TAB;
            case 259 -> Keys.BACKSPACE;
            case 260 -> Keys.INSERT;
            case 261 -> Keys.DELETE;
            case 262 -> Keys.RIGHT;
            case 263 -> Keys.LEFT;
            case 264 -> Keys.DOWN;
            case 265 -> Keys.UP;
            case 266 -> Keys.PAGE_UP;
            case 267 -> Keys.PAGE_DOWN;
            case 268 -> Keys.HOME;
            case 269 -> Keys.END;
            default -> key < 0 ? Keys.UNKNOWN : KeyCode.of("key.glfw." + key);
        };
    }

    public static PointerButton button(int button) {
        return PointerButton.of(button);
    }

    public static Set<KeyModifier> modifiers(int modifiers) {
        EnumSet<KeyModifier> result = EnumSet.noneOf(KeyModifier.class);
        add(result, modifiers, MOD_SHIFT, KeyModifier.SHIFT);
        add(result, modifiers, MOD_CONTROL, KeyModifier.CONTROL);
        add(result, modifiers, MOD_ALT, KeyModifier.ALT);
        add(result, modifiers, MOD_SUPER, KeyModifier.SUPER);
        add(result, modifiers, MOD_CAPS_LOCK, KeyModifier.CAPS_LOCK);
        add(result, modifiers, MOD_NUM_LOCK, KeyModifier.NUM_LOCK);
        return Set.copyOf(result);
    }

    public static Set<KeyModifier> modifiers(boolean shift, boolean control, boolean alt) {
        EnumSet<KeyModifier> result = EnumSet.noneOf(KeyModifier.class);
        if (shift) {
            result.add(KeyModifier.SHIFT);
        }
        if (control) {
            result.add(KeyModifier.CONTROL);
        }
        if (alt) {
            result.add(KeyModifier.ALT);
        }
        return Set.copyOf(result);
    }

    private static void add(
            EnumSet<KeyModifier> result,
            int modifiers,
            int mask,
            KeyModifier value
    ) {
        if ((modifiers & mask) != 0) {
            result.add(value);
        }
    }
}
