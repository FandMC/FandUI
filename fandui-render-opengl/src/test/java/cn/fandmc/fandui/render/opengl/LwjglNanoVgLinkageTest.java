package cn.fandmc.fandui.render.opengl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.nanovg.NanoVG.nvgDegToRad;

class LwjglNanoVgLinkageTest {
    @Test
    void loadsThePublishedNanoVgNativeWithoutAnOpenGlContext() {
        assertEquals(Math.PI, nvgDegToRad(180.0f), 1.0e-6);
    }
}
