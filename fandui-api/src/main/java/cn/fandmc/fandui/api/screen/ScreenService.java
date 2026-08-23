package cn.fandmc.fandui.api.screen;

import java.util.Optional;

/** UI-thread-confined owner of the single active FandUI Screen session. */
public interface ScreenService {
    /**
     * Opens a fresh session and replaces any current FandUI Screen.
     * Closing the session restores the host Screen that preceded the first FandUI Screen.
     *
     * @throws IllegalStateException if the runtime is unavailable
     */
    ScreenSession open(UiScreen screen);

    /** Returns the current active FandUI Screen session. */
    Optional<ScreenSession> current();
}
