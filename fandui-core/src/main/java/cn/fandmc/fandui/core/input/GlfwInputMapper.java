package cn.fandmc.fandui.core.input;

import cn.fandmc.fandui.api.event.KeyCode;
import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.PointerButton;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Maps stable GLFW numeric input values without exposing LWJGL in the core module. */
public final class GlfwInputMapper {
    private static final int MOD_SHIFT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_ALT = 0x0004;
    private static final int MOD_SUPER = 0x0008;
    private static final int MOD_CAPS_LOCK = 0x0010;
    private static final int MOD_NUM_LOCK = 0x0020;
    private static final int KNOWN_MODIFIERS = MOD_SHIFT | MOD_CONTROL | MOD_ALT
            | MOD_SUPER | MOD_CAPS_LOCK | MOD_NUM_LOCK;
    private static final KeyCode[] LETTER_KEYS = createLetterKeys();
    private static final KeyCode[] DIGIT_KEYS = createDigitKeys();
    private static final KeyCode[] FUNCTION_KEYS = createFunctionKeys();
    private static final KeyCode[] KEYPAD_DIGIT_KEYS = createKeypadDigitKeys();
    private static final List<Set<KeyModifier>> MODIFIER_SETS = createModifierSets();

    private GlfwInputMapper() {
    }

    public static KeyCode key(int key) {
        if (key >= 65 && key <= 90) {
            return LETTER_KEYS[key - 65];
        }
        if (key >= 48 && key <= 57) {
            return DIGIT_KEYS[key - 48];
        }
        if (key >= 290 && key <= 314) {
            return FUNCTION_KEYS[key - 290];
        }
        if (key >= 320 && key <= 329) {
            return KEYPAD_DIGIT_KEYS[key - 320];
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
        return MODIFIER_SETS.get(modifiers & KNOWN_MODIFIERS);
    }

    public static Set<KeyModifier> modifiers(boolean shift, boolean control, boolean alt) {
        int modifiers = (shift ? MOD_SHIFT : 0)
                | (control ? MOD_CONTROL : 0)
                | (alt ? MOD_ALT : 0);
        return modifiers(modifiers);
    }

    private static KeyCode[] createLetterKeys() {
        KeyCode[] result = new KeyCode[26];
        for (int index = 0; index < result.length; index++) {
            result[index] = Keys.letter((char) ('a' + index));
        }
        return result;
    }

    private static KeyCode[] createDigitKeys() {
        KeyCode[] result = new KeyCode[10];
        for (int index = 0; index < result.length; index++) {
            result[index] = Keys.digit(index);
        }
        return result;
    }

    private static KeyCode[] createFunctionKeys() {
        KeyCode[] result = new KeyCode[25];
        for (int index = 0; index < result.length; index++) {
            result[index] = Keys.function(index + 1);
        }
        return result;
    }

    private static KeyCode[] createKeypadDigitKeys() {
        KeyCode[] result = new KeyCode[10];
        for (int index = 0; index < result.length; index++) {
            result[index] = Keys.keypadDigit(index);
        }
        return result;
    }

    private static List<Set<KeyModifier>> createModifierSets() {
        List<Set<KeyModifier>> result = new ArrayList<>(KNOWN_MODIFIERS + 1);
        for (int modifiers = 0; modifiers <= KNOWN_MODIFIERS; modifiers++) {
            EnumSet<KeyModifier> values = EnumSet.noneOf(KeyModifier.class);
            add(values, modifiers, MOD_SHIFT, KeyModifier.SHIFT);
            add(values, modifiers, MOD_CONTROL, KeyModifier.CONTROL);
            add(values, modifiers, MOD_ALT, KeyModifier.ALT);
            add(values, modifiers, MOD_SUPER, KeyModifier.SUPER);
            add(values, modifiers, MOD_CAPS_LOCK, KeyModifier.CAPS_LOCK);
            add(values, modifiers, MOD_NUM_LOCK, KeyModifier.NUM_LOCK);
            result.add(Set.copyOf(values));
        }
        return List.copyOf(result);
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
