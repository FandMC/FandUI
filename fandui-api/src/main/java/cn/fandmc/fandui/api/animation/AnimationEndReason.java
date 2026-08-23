package cn.fandmc.fandui.api.animation;

/** Terminal reason reported by an {@link AnimationHandle}. */
public enum AnimationEndReason {
    COMPLETED,
    CANCELLED,
    SESSION_CLOSED,
    FAILED
}
