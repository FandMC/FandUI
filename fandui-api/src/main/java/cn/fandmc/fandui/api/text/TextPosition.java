package cn.fandmc.fandui.api.text;

import java.util.Objects;

/** A UTF-16 caret position with boundary affinity. */
public record TextPosition(int offsetUtf16, TextAffinity affinity) {
    public TextPosition {
        if (offsetUtf16 < 0) {
            throw new IllegalArgumentException("offsetUtf16 must not be negative");
        }
        Objects.requireNonNull(affinity, "affinity");
    }

    public static TextPosition downstream(int offsetUtf16) {
        return new TextPosition(offsetUtf16, TextAffinity.DOWNSTREAM);
    }
}
