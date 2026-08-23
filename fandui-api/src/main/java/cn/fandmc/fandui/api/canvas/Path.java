package cn.fandmc.fandui.api.canvas;

import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.CornerRadii;

import java.util.List;
import java.util.Objects;

/** An immutable sequence of vector path commands with conservative local bounds. */
public final class Path {
    private final List<Element> elements;
    private final Rect bounds;

    Path(List<Element> elements, Rect bounds) {
        this.elements = List.copyOf(elements);
        this.bounds = bounds;
    }

    public static PathBuilder builder() {
        return new PathBuilder();
    }

    public Rect bounds() {
        return bounds;
    }

    /** Replays every command to {@code visitor} without exposing mutable path state. */
    public void replay(PathVisitor visitor) {
        Objects.requireNonNull(visitor, "visitor");
        for (Element element : elements) {
            if (element instanceof MoveTo move) {
                visitor.moveTo(move.x(), move.y());
            } else if (element instanceof LineTo line) {
                visitor.lineTo(line.x(), line.y());
            } else if (element instanceof QuadTo quad) {
                visitor.quadTo(quad.controlX(), quad.controlY(), quad.x(), quad.y());
            } else if (element instanceof BezierTo bezier) {
                visitor.bezierTo(
                        bezier.control1X(),
                        bezier.control1Y(),
                        bezier.control2X(),
                        bezier.control2Y(),
                        bezier.x(),
                        bezier.y());
            } else if (element instanceof Arc arc) {
                visitor.arc(
                        arc.centerX(),
                        arc.centerY(),
                        arc.radius(),
                        arc.startRadians(),
                        arc.endRadians(),
                        arc.direction());
            } else if (element instanceof Rectangle rectangle) {
                visitor.rect(rectangle.rect());
            } else if (element instanceof RoundedRectangle roundedRectangle) {
                visitor.roundedRect(roundedRectangle.rect(), roundedRectangle.radii());
            } else if (element == Close.INSTANCE) {
                visitor.close();
            } else if (element instanceof Winding winding) {
                visitor.winding(winding.winding());
            } else {
                throw new IllegalStateException("Unknown path element: " + element.getClass().getName());
            }
        }
    }

    List<Element> elements() {
        return elements;
    }

    sealed interface Element permits MoveTo, LineTo, QuadTo, BezierTo, Arc, Rectangle, RoundedRectangle,
            Close, Winding {
    }

    record MoveTo(float x, float y) implements Element {
    }

    record LineTo(float x, float y) implements Element {
    }

    record QuadTo(float controlX, float controlY, float x, float y) implements Element {
    }

    record BezierTo(
            float control1X,
            float control1Y,
            float control2X,
            float control2Y,
            float x,
            float y) implements Element {
    }

    record Arc(
            float centerX,
            float centerY,
            float radius,
            float startRadians,
            float endRadians,
            ArcDirection direction) implements Element {
    }

    record Rectangle(Rect rect) implements Element {
    }

    record RoundedRectangle(Rect rect, CornerRadii radii) implements Element {
    }

    enum Close implements Element {
        INSTANCE
    }

    record Winding(PathWinding winding) implements Element {
    }
}
