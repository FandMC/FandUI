package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.api.layout.Point;

import java.util.List;
import java.util.Objects;

/** Immutable multi-stop linear gradient in local logical coordinates. */
public record LinearGradient(Point start, Point end, List<GradientStop> stops) implements Paint {
    public LinearGradient {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        stops = GradientStops.copy(stops);
    }
}
