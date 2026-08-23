package cn.fandmc.fandui.api;

import java.util.Objects;

/** Immutable runtime state and a diagnostic detail supplied by the active platform bridge. */
public record UiAvailability(UiRuntimeState state, String detail) {
    public UiAvailability {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
    }

    /** Returns whether Screen and HUD sessions may currently be created and rendered. */
    public boolean available() {
        return state == UiRuntimeState.AVAILABLE;
    }
}
