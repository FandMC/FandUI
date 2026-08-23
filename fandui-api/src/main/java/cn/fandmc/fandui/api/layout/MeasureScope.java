package cn.fandmc.fandui.api.layout;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Callback-scoped environment for measuring direct children exactly once and defining placement.
 * The scope and returned placeables must not be retained after measurement completes.
 */
public interface MeasureScope {
    /** Measures one direct child under the supplied constraints. */
    Placeable measure(UiComponent child, Constraints constraints);

    /** Creates a result and defers child placement until the layout pass. */
    MeasureResult layout(float width, float height, Consumer<PlacementScope> placements);

    default MeasureResult layout(
            float width,
            float height,
            Map<TextBaseline, Float> baselines,
            Consumer<PlacementScope> placements) {
        Objects.requireNonNull(baselines, "baselines");
        return layout(width, height, placements);
    }

    LayoutDirection direction();

    Style style();

    Theme theme();
}
