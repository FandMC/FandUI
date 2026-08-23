package cn.fandmc.fandui.api.animation;

import java.util.concurrent.CompletableFuture;

/**
 * Session-owned handle for a running animation.
 *
 * <p>Closing the handle is idempotent and requests cancellation. Session close also
 * terminates every animation owned by that session.</p>
 */
public interface AnimationHandle extends AutoCloseable {
    /** Returns whether the animation may still emit frames. */
    boolean active();

    /** Returns a future completed exactly once with the terminal reason. */
    CompletableFuture<AnimationEndReason> completion();

    /** Cancels the animation when it is still active. */
    @Override
    void close();
}
