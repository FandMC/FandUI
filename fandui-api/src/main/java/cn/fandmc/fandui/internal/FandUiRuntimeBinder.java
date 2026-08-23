package cn.fandmc.fandui.internal;

import cn.fandmc.fandui.api.UiRuntime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class FandUiRuntimeBinder {
    private static final AtomicReference<UiRuntime> RUNTIME = new AtomicReference<>();

    private FandUiRuntimeBinder() {
    }

    public static void bind(UiRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (!RUNTIME.compareAndSet(null, runtime)) {
            throw new IllegalStateException("FandUI runtime is already bound");
        }
    }

    public static UiRuntime runtime() {
        UiRuntime runtime = RUNTIME.get();
        if (runtime == null) {
            throw new IllegalStateException("FandUI runtime has not been initialized");
        }
        return runtime;
    }
}
