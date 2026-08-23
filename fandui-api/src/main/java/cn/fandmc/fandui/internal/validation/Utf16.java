package cn.fandmc.fandui.internal.validation;

import java.util.Objects;

public final class Utf16 {
    private Utf16() {
    }

    public static String wellFormed(String value, String name) {
        Objects.requireNonNull(value, name);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(name + " contains an unpaired high surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(name + " contains an unpaired low surrogate");
            }
        }
        return value;
    }

    public static void requireBoundary(String value, int offset, String name) {
        if (offset < 0 || offset > value.length()) {
            throw new IllegalArgumentException(name + " is outside the UTF-16 range");
        }
        if (offset > 0 && offset < value.length()
                && Character.isHighSurrogate(value.charAt(offset - 1))
                && Character.isLowSurrogate(value.charAt(offset))) {
            throw new IllegalArgumentException(name + " splits a surrogate pair");
        }
    }
}
