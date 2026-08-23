package cn.fandmc.fandui.api.animation;

/** Receives eased animation progress on the UI thread. */
@FunctionalInterface
public interface AnimationFrameListener {
    /** Called once per rendered animation frame with progress normally in {@code [0, 1]}. */
    void frame(double progress);
}
