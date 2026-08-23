package cn.fandmc.fandui.api.event;

/** Cause recorded when focus moves, is cleared, or becomes ineligible. */
public enum FocusCause {
    POINTER,
    KEYBOARD,
    PROGRAMMATIC,
    CLEAR,
    INELIGIBLE,
    DETACH,
    SESSION_CLOSED
}
