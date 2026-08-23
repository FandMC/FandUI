package cn.fandmc.fandui.api.canvas;

import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable fluent builder that produces independent immutable {@link Path} snapshots. */
public final class PathBuilder {
    private final List<Path.Element> elements = new ArrayList<>();
    private float minimumX = Float.POSITIVE_INFINITY;
    private float minimumY = Float.POSITIVE_INFINITY;
    private float maximumX = Float.NEGATIVE_INFINITY;
    private float maximumY = Float.NEGATIVE_INFINITY;

    PathBuilder() {
    }

    public PathBuilder moveTo(float x, float y) {
        include(x, y);
        elements.add(new Path.MoveTo(x, y));
        return this;
    }

    public PathBuilder lineTo(float x, float y) {
        include(x, y);
        elements.add(new Path.LineTo(x, y));
        return this;
    }

    public PathBuilder quadTo(float controlX, float controlY, float x, float y) {
        include(controlX, controlY);
        include(x, y);
        elements.add(new Path.QuadTo(controlX, controlY, x, y));
        return this;
    }

    public PathBuilder bezierTo(
            float control1X,
            float control1Y,
            float control2X,
            float control2Y,
            float x,
            float y) {
        include(control1X, control1Y);
        include(control2X, control2Y);
        include(x, y);
        elements.add(new Path.BezierTo(control1X, control1Y, control2X, control2Y, x, y));
        return this;
    }

    public PathBuilder arc(
            float centerX,
            float centerY,
            float radius,
            float startRadians,
            float endRadians,
            ArcDirection direction) {
        Preconditions.finite(centerX, "centerX");
        Preconditions.finite(centerY, "centerY");
        Preconditions.nonNegative(radius, "radius");
        Preconditions.finite(startRadians, "startRadians");
        Preconditions.finite(endRadians, "endRadians");
        Objects.requireNonNull(direction, "direction");
        include(centerX - radius, centerY - radius);
        include(centerX + radius, centerY + radius);
        elements.add(new Path.Arc(centerX, centerY, radius, startRadians, endRadians, direction));
        return this;
    }

    public PathBuilder rect(Rect rect) {
        Objects.requireNonNull(rect, "rect");
        include(rect.x(), rect.y());
        include(rect.x() + rect.width(), rect.y() + rect.height());
        elements.add(new Path.Rectangle(rect));
        return this;
    }

    public PathBuilder roundedRect(Rect rect, CornerRadii radii) {
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(radii, "radii");
        include(rect.x(), rect.y());
        include(rect.x() + rect.width(), rect.y() + rect.height());
        elements.add(new Path.RoundedRectangle(rect, radii));
        return this;
    }

    public PathBuilder close() {
        elements.add(Path.Close.INSTANCE);
        return this;
    }

    public PathBuilder winding(PathWinding winding) {
        elements.add(new Path.Winding(Objects.requireNonNull(winding, "winding")));
        return this;
    }

    /** Builds a snapshot; subsequent builder changes do not modify the returned path. */
    public Path build() {
        Rect bounds = elements.isEmpty()
                ? new Rect(0.0f, 0.0f, 0.0f, 0.0f)
                : new Rect(minimumX, minimumY, maximumX - minimumX, maximumY - minimumY);
        return new Path(elements, bounds);
    }

    private void include(float x, float y) {
        Preconditions.finite(x, "x");
        Preconditions.finite(y, "y");
        minimumX = Math.min(minimumX, x);
        minimumY = Math.min(minimumY, y);
        maximumX = Math.max(maximumX, x);
        maximumY = Math.max(maximumY, y);
    }
}
