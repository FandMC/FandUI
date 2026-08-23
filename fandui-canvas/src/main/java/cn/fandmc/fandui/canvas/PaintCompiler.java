package cn.fandmc.fandui.canvas;

import cn.fandmc.fandui.api.style.GradientStop;
import cn.fandmc.fandui.api.style.LinearGradient;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.RadialGradient;
import cn.fandmc.fandui.api.style.SolidPaint;

import java.util.List;
import java.util.Objects;

final class PaintCompiler {
    private PaintCompiler() {
    }

    static DisplayPaint compile(Paint paint) {
        Objects.requireNonNull(paint, "paint");
        if (paint instanceof SolidPaint solid) {
            return new DisplayPaint.Solid(PremultipliedColor.from(solid.color()));
        }
        if (paint instanceof LinearGradient linear) {
            return new DisplayPaint.Linear(linear.start(), linear.end(), compileStops(linear.stops()));
        }
        if (paint instanceof RadialGradient radial) {
            return new DisplayPaint.Radial(
                    radial.center(),
                    radial.innerRadius(),
                    radial.outerRadius(),
                    compileStops(radial.stops()));
        }
        throw new IllegalArgumentException("Unsupported paint implementation: " + paint.getClass().getName());
    }

    private static List<DisplayGradientStop> compileStops(List<GradientStop> stops) {
        return stops.stream()
                .map(stop -> new DisplayGradientStop(stop.offset(), PremultipliedColor.from(stop.color())))
                .toList();
    }
}
