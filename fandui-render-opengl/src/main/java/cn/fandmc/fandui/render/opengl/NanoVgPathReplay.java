package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.canvas.ArcDirection;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.PathVisitor;
import cn.fandmc.fandui.api.canvas.PathWinding;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.CornerRadii;

import java.util.Objects;

import static org.lwjgl.nanovg.NanoVG.*;

final class NanoVgPathReplay implements PathVisitor {
    private final long context;

    private NanoVgPathReplay(long context) {
        this.context = context;
    }

    static void replay(long context, Path path) {
        Objects.requireNonNull(path, "path");
        nvgBeginPath(context);
        path.replay(new NanoVgPathReplay(context));
    }

    @Override
    public void moveTo(float x, float y) {
        nvgMoveTo(context, x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        nvgLineTo(context, x, y);
    }

    @Override
    public void quadTo(float controlX, float controlY, float x, float y) {
        nvgQuadTo(context, controlX, controlY, x, y);
    }

    @Override
    public void bezierTo(
            float control1X,
            float control1Y,
            float control2X,
            float control2Y,
            float x,
            float y) {
        nvgBezierTo(context, control1X, control1Y, control2X, control2Y, x, y);
    }

    @Override
    public void arc(
            float centerX,
            float centerY,
            float radius,
            float startRadians,
            float endRadians,
            ArcDirection direction) {
        nvgArc(
                context,
                centerX,
                centerY,
                radius,
                startRadians,
                endRadians,
                NanoVgMappings.arcDirection(direction));
    }

    @Override
    public void rect(Rect rect) {
        nvgRect(context, rect.x(), rect.y(), rect.width(), rect.height());
    }

    @Override
    public void roundedRect(Rect rect, CornerRadii radii) {
        nvgRoundedRectVarying(
                context,
                rect.x(),
                rect.y(),
                rect.width(),
                rect.height(),
                radii.topLeft(),
                radii.topRight(),
                radii.bottomRight(),
                radii.bottomLeft());
    }

    @Override
    public void close() {
        nvgClosePath(context);
    }

    @Override
    public void winding(PathWinding winding) {
        nvgPathWinding(context, NanoVgMappings.winding(winding));
    }
}
