package cn.fandmc.fandui.text;

import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.text.TextLine;

import java.util.List;

record LayoutMetrics(
        Size size,
        float alphabeticBaseline,
        float ideographicBaseline,
        List<TextLine> lines,
        int unresolvedGlyphs,
        float paragraphWidth,
        float paintLeft) {
    LayoutMetrics {
        lines = List.copyOf(lines);
        if (unresolvedGlyphs < 0) {
            throw new IllegalArgumentException("unresolvedGlyphs must not be negative");
        }
        if (!Float.isFinite(paragraphWidth) || paragraphWidth < 0.0f) {
            throw new IllegalArgumentException("paragraphWidth must be finite and non-negative");
        }
        if (!Float.isFinite(paintLeft)) {
            throw new IllegalArgumentException("paintLeft must be finite");
        }
    }
}
