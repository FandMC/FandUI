package cn.fandmc.fandui.api.canvas;

/** Lexical handle that restores one {@link Canvas2D#save()} frame when closed. */
public interface CanvasState extends AutoCloseable {
    /** Restores the saved state; repeated close calls have no additional effect. */
    @Override
    void close();
}
