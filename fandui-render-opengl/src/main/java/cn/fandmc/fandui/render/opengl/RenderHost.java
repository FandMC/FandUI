package cn.fandmc.fandui.render.opengl;

import java.util.Optional;

/** Supplies the current Minecraft-owned OpenGL color target without leaking platform types. */
public interface RenderHost {
    String name();

    void assertRenderThread();

    Optional<OpenGlTarget> currentTarget();
}

