package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.component.control.ScrollController;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class ScrollControllers {
    private static final Map<ScrollController, WeakReference<ScrollControllerState>> STATES = new WeakHashMap<>();

    private ScrollControllers() {
    }

    public static void register(ScrollController controller, ScrollControllerState state) {
        synchronized (STATES) {
            STATES.put(Objects.requireNonNull(controller, "controller"),
                    new WeakReference<>(Objects.requireNonNull(state, "state")));
        }
    }

    public static ScrollControllerState state(ScrollController controller) {
        synchronized (STATES) {
            WeakReference<ScrollControllerState> reference = STATES.get(
                    Objects.requireNonNull(controller, "controller"));
            ScrollControllerState state = reference == null ? null : reference.get();
            if (state == null) {
                throw new IllegalStateException("Unknown ScrollController instance");
            }
            return state;
        }
    }
}
