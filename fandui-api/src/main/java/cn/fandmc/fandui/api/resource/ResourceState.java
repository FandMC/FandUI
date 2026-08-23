package cn.fandmc.fandui.api.resource;

/** Current immutable snapshot state of a stable resource handle. */
public enum ResourceState {
    UNRESOLVED,
    LOADING,
    READY,
    MISSING,
    FAILED
}
