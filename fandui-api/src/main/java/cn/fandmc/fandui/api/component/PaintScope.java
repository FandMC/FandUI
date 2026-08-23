package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.canvas.Canvas2D;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;

/**
 * Callback-scoped view of a component's paint environment.
 *
 * <p>The scope and its {@link #canvas()} become invalid when painting returns and must
 * not be retained. Bounds use component-local logical pixels.</p>
 */
public interface PaintScope {
    Canvas2D canvas();

    Rect bounds();

    Style style();

    Theme theme();

    long frameTimeNanos();
}
