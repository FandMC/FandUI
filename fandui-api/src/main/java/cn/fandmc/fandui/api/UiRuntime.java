package cn.fandmc.fandui.api;

import cn.fandmc.fandui.api.hud.HudService;
import cn.fandmc.fandui.api.input.ClipboardService;
import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.screen.ScreenService;
import cn.fandmc.fandui.api.text.TextService;

import java.util.concurrent.CompletableFuture;

/**
 * Platform-neutral facade for the active FandUI client runtime.
 *
 * <p>Service accessors and immutable availability snapshots are thread-safe. Screen,
 * HUD, session, controller, component-tree, focus, animation, and mutable resource
 * operations require the UI thread. Use {@link #execute(Runnable)} when the caller
 * is on another thread.</p>
 */
public interface UiRuntime {
    /** Returns a current immutable availability snapshot. */
    UiAvailability availability();

    /** Returns capabilities detected for the active platform bridge. */
    UiCapabilities capabilities();

    /**
     * Returns a current immutable renderer diagnostic snapshot.
     *
     * <p>The default preserves compatibility with custom runtime implementations compiled
     * before renderer diagnostics were added.</p>
     */
    default UiDiagnostics diagnostics() {
        return UiDiagnostics.unknown(availability().detail());
    }

    /** Returns whether the current thread is the FandUI UI thread. */
    boolean isUiThread();

    /**
     * Runs {@code action} on the UI thread and completes with its result.
     *
     * <p>An action submitted from the UI thread runs immediately. The returned future
     * fails with the action's exception, a dispatcher failure, or when the runtime has
     * stopped before the action executes.</p>
     */
    CompletableFuture<Void> execute(Runnable action);

    /** Returns the UI-thread-confined Screen service. */
    ScreenService screens();

    /** Returns the UI-thread-confined HUD service. */
    HudService hud();

    /** Returns the resource service. See {@link ResourceService} for per-method threading. */
    ResourceService resources();

    /** Returns the asynchronous, thread-safe text service. */
    TextService text();

    /** Returns the platform clipboard service. */
    ClipboardService clipboard();
}
