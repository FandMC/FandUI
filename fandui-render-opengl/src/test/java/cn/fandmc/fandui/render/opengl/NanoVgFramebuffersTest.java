package cn.fandmc.fandui.render.opengl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NanoVgFramebuffersTest {
    @Test
    void accountsForRootStencilLayersAndSharedMask() {
        long pixels = 1920L * 1080L;

        assertEquals(pixels * 4L, NanoVgFramebuffers.requiredBytes(1920, 1080, 0));
        assertEquals(pixels * 12L, NanoVgFramebuffers.requiredBytes(1920, 1080, 0, true));
        assertEquals(pixels * 20L, NanoVgFramebuffers.requiredBytes(1920, 1080, 1));
        assertEquals(pixels * 28L, NanoVgFramebuffers.requiredBytes(1920, 1080, 2));
    }

    @Test
    void rejectsInvalidOrOverflowingRequestsBeforeCallingOpenGl() {
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgFramebuffers.requiredBytes(0, 1080, 0));
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgFramebuffers.requiredBytes(1920, 1080, -1));
        assertThrows(OpenGlRenderException.class,
                () -> NanoVgFramebuffers.requiredBytes(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
