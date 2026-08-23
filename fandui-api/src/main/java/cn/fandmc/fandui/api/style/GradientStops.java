package cn.fandmc.fandui.api.style;

import java.util.List;
import java.util.Objects;

final class GradientStops {
    private GradientStops() {
    }

    static List<GradientStop> copy(List<GradientStop> stops) {
        Objects.requireNonNull(stops, "stops");
        List<GradientStop> copy = List.copyOf(stops);
        if (copy.size() < 2) {
            throw new IllegalArgumentException("A gradient requires at least two stops");
        }
        float previous = -1.0f;
        for (GradientStop stop : copy) {
            if (stop.offset() < previous) {
                throw new IllegalArgumentException("Gradient stops must be ordered by offset");
            }
            previous = stop.offset();
        }
        return copy;
    }
}
