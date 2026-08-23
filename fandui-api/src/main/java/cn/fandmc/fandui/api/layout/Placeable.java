package cn.fandmc.fandui.api.layout;

import cn.fandmc.fandui.api.component.UiComponent;

import java.util.OptionalDouble;

/** Callback-scoped measured child exposed to its direct parent's placement callback. */
public interface Placeable {
    UiComponent component();

    Size size();

    OptionalDouble baseline(TextBaseline baseline);
}
