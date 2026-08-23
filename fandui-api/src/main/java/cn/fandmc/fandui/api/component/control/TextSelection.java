package cn.fandmc.fandui.api.component.control;

/** Immutable anchor/focus selection represented as UTF-16 offsets. */
public record TextSelection(int anchorUtf16, int focusUtf16) {
    public TextSelection {
        if (anchorUtf16 < 0 || focusUtf16 < 0) {
            throw new IllegalArgumentException("Selection offsets must not be negative");
        }
    }

    public int startUtf16() {
        return Math.min(anchorUtf16, focusUtf16);
    }

    public int endUtf16() {
        return Math.max(anchorUtf16, focusUtf16);
    }

    public boolean collapsed() {
        return anchorUtf16 == focusUtf16;
    }
}
