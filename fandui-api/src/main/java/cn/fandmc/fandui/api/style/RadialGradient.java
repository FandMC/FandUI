package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.List;
import java.util.Objects;

/** Immutable multi-stop radial gradient in local logical coordinates. */
public record RadialGradient(
        Point center,
        float innerRadius,
        float outerRadius,
        List<GradientStop> stops) implements Paint {
    public RadialGradient {
        Objects.requireNonNull(center, "center");
        Preconditions.nonNegative(innerRadius, "innerRadius");
        Preconditions.nonNegative(outerRadius, "outerRadius");
        if (innerRadius > outerRadius) {
            throw new IllegalArgumentException("innerRadius must not exceed outerRadius");
        }
        stops = GradientStops.copy(stops);
    }
}
