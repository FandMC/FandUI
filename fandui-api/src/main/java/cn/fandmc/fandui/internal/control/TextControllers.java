package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.component.control.TextController;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class TextControllers {
    private static final Map<TextController, WeakReference<TextControllerState>> STATES = new WeakHashMap<>();

    private TextControllers() {
    }

    public static void register(TextController controller, TextControllerState state) {
        synchronized (STATES) {
            STATES.put(Objects.requireNonNull(controller, "controller"),
                    new WeakReference<>(Objects.requireNonNull(state, "state")));
        }
    }

    public static TextControllerState state(TextController controller) {
        synchronized (STATES) {
            WeakReference<TextControllerState> reference = STATES.get(Objects.requireNonNull(controller, "controller"));
            TextControllerState state = reference == null ? null : reference.get();
            if (state == null) {
                throw new IllegalStateException("Unknown TextController instance");
            }
            return state;
        }
    }
}
