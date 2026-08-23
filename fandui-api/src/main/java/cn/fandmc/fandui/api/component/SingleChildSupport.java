package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Style;

final class SingleChildSupport {
    private SingleChildSupport() {
    }

    static MeasureResult measure(
            UiComponent child,
            Alignment alignment,
            MeasureScope scope,
            Constraints constraints) {
        Style style = scope.style();
        Insets padding = style.padding();
        Placeable placeable = scope.measure(child, subtractPadding(constraints, padding));
        Size desired = new Size(
                placeable.size().width() + horizontal(padding),
                placeable.size().height() + vertical(padding));
        Size size = constraints.constrain(desired);
        return scope.layout(size.width(), size.height(), placements -> {
            float availableWidth = Math.max(0.0f, size.width() - horizontal(padding));
            float availableHeight = Math.max(0.0f, size.height() - vertical(padding));
            float x = padding.left()
                    + Math.max(0.0f, availableWidth - placeable.size().width()) * alignment.horizontalFactor();
            float y = padding.top()
                    + Math.max(0.0f, availableHeight - placeable.size().height()) * alignment.verticalFactor();
            placements.place(placeable, x, y);
        });
    }

    static void paintBackground(PaintScope scope) {
        Style style = scope.style();
        scope.canvas().fillRoundedRect(scope.bounds(), style.cornerRadii(), style.background());
        if (style.border().width() > 0.0f) {
            Path borderPath = Path.builder().roundedRect(scope.bounds(), style.cornerRadii()).build();
            scope.canvas().stroke(
                    borderPath,
                    style.border().paint(),
                    StrokeStyle.width(style.border().width()).build());
        }
    }

    private static Constraints subtractPadding(Constraints constraints, Insets padding) {
        float horizontal = horizontal(padding);
        float vertical = vertical(padding);
        return new Constraints(
                Math.max(0.0f, constraints.minWidth() - horizontal),
                subtractMaximum(constraints.maxWidth(), horizontal),
                Math.max(0.0f, constraints.minHeight() - vertical),
                subtractMaximum(constraints.maxHeight(), vertical));
    }

    private static float subtractMaximum(float maximum, float amount) {
        return Float.isInfinite(maximum) ? maximum : Math.max(0.0f, maximum - amount);
    }

    private static float horizontal(Insets padding) {
        return padding.left() + padding.right();
    }

    private static float vertical(Insets padding) {
        return padding.top() + padding.bottom();
    }
}
