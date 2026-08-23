package cn.fandmc.fandui.api.text;

/** A normalized half-open UTF-16 range. */
public record TextRange(int startUtf16, int endUtf16) {
    public TextRange {
        if (startUtf16 < 0 || endUtf16 < startUtf16) {
            throw new IllegalArgumentException("Invalid UTF-16 text range");
        }
    }

    public boolean collapsed() {
        return startUtf16 == endUtf16;
    }
}
