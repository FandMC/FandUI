package cn.fandmc.fandui.canvas;

import cn.fandmc.fandui.api.layout.Point;

import java.util.List;
import java.util.Objects;

public sealed interface DisplayPaint permits DisplayPaint.Solid, DisplayPaint.Linear, DisplayPaint.Radial {
    record Solid(PremultipliedColor color) implements DisplayPaint {
        public Solid {
            Objects.requireNonNull(color, "color");
        }
    }

    record Linear(Point start, Point end, List<DisplayGradientStop> stops) implements DisplayPaint {
        public Linear {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            stops = copyStops(stops);
        }
    }

    record Radial(
            Point center,
            float innerRadius,
            float outerRadius,
            List<DisplayGradientStop> stops) implements DisplayPaint {
        public Radial {
            Objects.requireNonNull(center, "center");
            if (!Float.isFinite(innerRadius) || !Float.isFinite(outerRadius)
                    || innerRadius < 0.0f || outerRadius < innerRadius) {
                throw new IllegalArgumentException("Invalid radial gradient radii");
            }
            stops = copyStops(stops);
        }
    }

    private static List<DisplayGradientStop> copyStops(List<DisplayGradientStop> stops) {
        Objects.requireNonNull(stops, "stops");
        List<DisplayGradientStop> copy = List.copyOf(stops);
        if (copy.size() < 2) {
            throw new IllegalArgumentException("A gradient requires at least two stops");
        }
        float previous = -1.0f;
        for (DisplayGradientStop stop : copy) {
            if (stop.offset() < previous) {
                throw new IllegalArgumentException("Gradient stops must be ordered");
            }
            previous = stop.offset();
        }
        return copy;
    }
}
