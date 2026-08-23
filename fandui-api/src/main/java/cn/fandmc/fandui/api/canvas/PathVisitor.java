package cn.fandmc.fandui.api.canvas;

import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.CornerRadii;

/**
 * Receives the immutable commands stored in a {@link Path} in insertion order.
 *
 * <p>This interface lets renderer implementations consume paths without exposing
 * a renderer-specific or native representation through the FandUI API.</p>
 */
public interface PathVisitor {
    void moveTo(float x, float y);

    void lineTo(float x, float y);

    void quadTo(float controlX, float controlY, float x, float y);

    void bezierTo(
            float control1X,
            float control1Y,
            float control2X,
            float control2Y,
            float x,
            float y);

    void arc(
            float centerX,
            float centerY,
            float radius,
            float startRadians,
            float endRadians,
            ArcDirection direction);

    void rect(Rect rect);

    void roundedRect(Rect rect, CornerRadii radii);

    void close();

    void winding(PathWinding winding);
}
