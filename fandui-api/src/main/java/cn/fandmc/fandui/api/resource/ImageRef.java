package cn.fandmc.fandui.api.resource;

import cn.fandmc.fandui.api.UiKey;

import java.util.Optional;

/**
 * Stable, thread-safe image handle whose immutable snapshot changes on successful reloads.
 * A handle never owns its registration or GPU texture and does not need to be closed.
 */
public interface ImageRef {
    UiKey key();

    ResourceState state();

    /** Returns dimensions only while the current snapshot is ready. */
    Optional<ImageInfo> info();

    /** Returns the source or decode failure for the current failed/missing snapshot, when available. */
    default Optional<Throwable> failure() {
        return Optional.empty();
    }
}
