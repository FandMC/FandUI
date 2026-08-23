package cn.fandmc.fandui.api.event;

/** Common stable key constants used by standard controls. */
public final class Keys {
    public static final KeyCode UNKNOWN = KeyCode.of("key.unknown");
    public static final KeyCode SPACE = KeyCode.of("key.space");
    public static final KeyCode ENTER = KeyCode.of("key.enter");
    public static final KeyCode TAB = KeyCode.of("key.tab");
    public static final KeyCode ESCAPE = KeyCode.of("key.escape");
    public static final KeyCode BACKSPACE = KeyCode.of("key.backspace");
    public static final KeyCode DELETE = KeyCode.of("key.delete");
    public static final KeyCode INSERT = KeyCode.of("key.insert");
    public static final KeyCode HOME = KeyCode.of("key.home");
    public static final KeyCode END = KeyCode.of("key.end");
    public static final KeyCode PAGE_UP = KeyCode.of("key.page.up");
    public static final KeyCode PAGE_DOWN = KeyCode.of("key.page.down");
    public static final KeyCode LEFT = KeyCode.of("key.arrow.left");
    public static final KeyCode RIGHT = KeyCode.of("key.arrow.right");
    public static final KeyCode UP = KeyCode.of("key.arrow.up");
    public static final KeyCode DOWN = KeyCode.of("key.arrow.down");

    private Keys() {
    }

    public static KeyCode letter(char asciiLetter) {
        char normalized = Character.toLowerCase(asciiLetter);
        if (normalized < 'a' || normalized > 'z') {
            throw new IllegalArgumentException("Expected an ASCII letter");
        }
        return KeyCode.of("key." + normalized);
    }

    public static KeyCode digit(int digit) {
        requireDigit(digit);
        return KeyCode.of("key." + digit);
    }

    public static KeyCode function(int number) {
        if (number < 1 || number > 25) {
            throw new IllegalArgumentException("Function key number must be between 1 and 25");
        }
        return KeyCode.of("key.f" + number);
    }

    public static KeyCode keypadDigit(int digit) {
        requireDigit(digit);
        return KeyCode.of("key.keypad." + digit);
    }

    private static void requireDigit(int digit) {
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException("Digit must be between 0 and 9");
        }
    }
}
