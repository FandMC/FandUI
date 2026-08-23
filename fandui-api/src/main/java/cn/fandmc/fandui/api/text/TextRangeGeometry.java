package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.api.layout.Rect;

import java.util.List;
import java.util.Objects;

/** Immutable visual bounds for one requested text range. */
public record TextRangeGeometry(TextRange range, List<Rect> bounds) {
    public TextRangeGeometry {
        Objects.requireNonNull(range, "range");
        bounds = List.copyOf(Objects.requireNonNull(bounds, "bounds"));
    }
}
