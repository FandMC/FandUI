package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.api.layout.Rect;

import java.util.List;
import java.util.Objects;

/** Immutable caret and range geometry produced without exposing a native paragraph. */
public record TextGeometry(Rect caretBounds, List<TextRangeGeometry> ranges) {
    public TextGeometry {
        Objects.requireNonNull(caretBounds, "caretBounds");
        ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
    }
}
