package cn.fandmc.fandui.internal.component;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.UiContainer;

public interface ComponentBinding {
    void assertUiThread();

    void invalidateLayout(UiComponent component);

    void invalidatePaint(UiComponent component);

    void childAdded(UiContainer parent, UiComponent child);

    void childRemoved(UiContainer parent, UiComponent child);
}
