package cn.fandmc.fandui.api;

/** Lifecycle and renderer availability states of the process-wide FandUI runtime. */
public enum UiRuntimeState {
    STARTING,
    AVAILABLE,
    RENDERER_UNAVAILABLE,
    FAILED,
    STOPPED
}
