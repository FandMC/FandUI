package cn.fandmc.fandui.api.animation;

/** Starts animations owned by one active UI session. */
public interface AnimationManager {
    /**
     * Starts an animation and invokes {@code listener} on the UI thread.
     *
     * @throws IllegalStateException if the owning session is closed
     */
    AnimationHandle start(AnimationSpec spec, AnimationFrameListener listener);
}
