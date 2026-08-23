package cn.fandmc.fandui.api.component;

/** Paint callback invoked on the UI thread for a {@link CanvasComponent}. */
@FunctionalInterface
public interface CanvasPainter {
    void paint(PaintScope scope);
}
