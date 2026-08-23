package cn.fandmc.fandui.api;

import cn.fandmc.fandui.internal.FandUiRuntimeBinder;

/** Global access point bound by the active FandUI Fabric bridge during client startup. */
public final class FandUI {
    private FandUI() {
    }

    /**
     * Returns the process-wide runtime.
     *
     * @throws IllegalStateException if the platform bridge has not bound a runtime
     */
    public static UiRuntime runtime() {
        return FandUiRuntimeBinder.runtime();
    }
}
