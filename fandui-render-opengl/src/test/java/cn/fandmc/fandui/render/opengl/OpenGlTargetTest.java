package cn.fandmc.fandui.render.opengl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OpenGlTargetTest {
    @Test
    void acceptsValidBorrowedTarget() {
        assertDoesNotThrow(() -> new OpenGlTarget(7, 0, 1920, 1080, 3));
    }

    @Test
    void rejectsInvalidHandlesAndDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new OpenGlTarget(0, 0, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new OpenGlTarget(1, -1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new OpenGlTarget(1, 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new OpenGlTarget(1, 0, 1, 0, 0));
    }
}

