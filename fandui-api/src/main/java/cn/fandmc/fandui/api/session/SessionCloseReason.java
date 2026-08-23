package cn.fandmc.fandui.api.session;

/** Terminal cause of a Screen or HUD session. */
public enum SessionCloseReason {
    API,
    ESCAPE,
    REPLACED,
    HOST,
    SHUTDOWN,
    FAILED
}
