package cn.fandmc.fandui.api.layout;

/** Callback-scoped sink for assigning positions to every measured direct child. */
public interface PlacementScope {
    void place(Placeable child, float x, float y);

    void place(Placeable child, float x, float y, int zIndex);
}
