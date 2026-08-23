package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.Objects;

/** Immutable metrics and UTF-16 range for one visually laid-out line. */
public final class TextLine {
    private final int startUtf16;
    private final int endUtf16;
    private final float width;
    private final float height;
    private final float baseline;

    public TextLine(int startUtf16, int endUtf16, float width, float height, float baseline) {
        if (startUtf16 < 0 || endUtf16 < startUtf16) {
            throw new IllegalArgumentException("Invalid UTF-16 line range");
        }
        this.startUtf16 = startUtf16;
        this.endUtf16 = endUtf16;
        this.width = Preconditions.nonNegative(width, "width");
        this.height = Preconditions.nonNegative(height, "height");
        this.baseline = Preconditions.nonNegative(baseline, "baseline");
    }

    public int startUtf16() {
        return startUtf16;
    }

    public int endUtf16() {
        return endUtf16;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float baseline() {
        return baseline;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TextLine line)) {
            return false;
        }
        return startUtf16 == line.startUtf16
                && endUtf16 == line.endUtf16
                && Float.compare(width, line.width) == 0
                && Float.compare(height, line.height) == 0
                && Float.compare(baseline, line.baseline) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startUtf16, endUtf16, width, height, baseline);
    }

    @Override
    public String toString() {
        return "TextLine[startUtf16=" + startUtf16 + ", endUtf16=" + endUtf16
                + ", width=" + width + ", height=" + height + ", baseline=" + baseline + ']';
    }
}
