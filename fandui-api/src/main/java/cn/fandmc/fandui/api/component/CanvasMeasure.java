package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;

/** Measures a {@link CanvasComponent} from constraints, resolved style, and theme. */
@FunctionalInterface
public interface CanvasMeasure {
    Size measure(Constraints constraints, Style style, Theme theme);
}
