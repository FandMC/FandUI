package cn.fandmc.fandui.render.opengl;

import java.util.Optional;

/** Supplies the current Minecraft-owned OpenGL color target without leaking platform types. */
public interface RenderHost {
    String name();

    void assertRenderThread();

    Optional<OpenGlTarget> currentTarget();

    /**
     * Returns whether this host can restore its renderer state without synchronous OpenGL queries.
     * Implementations are only valid at their documented frame hook.
     */
    default boolean supportsStateHandoff() {
        return false;
    }

    /**
     * Establishes the host's documented canonical state before FandUI uses raw OpenGL calls.
     * The default is sufficient for hosts whose Java-side renderer does not cache OpenGL state.
     */
    default void prepareStateForFandUi() {
    }

    /** Restores real OpenGL state and the host renderer's Java-side state cache after a FandUI pass. */
    default void restoreStateAfterFandUi() {
        throw new UnsupportedOperationException("This render host does not provide an OpenGL state handoff");
    }
}
