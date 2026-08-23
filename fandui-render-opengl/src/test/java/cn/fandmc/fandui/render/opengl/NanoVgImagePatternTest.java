package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.layout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NanoVgImagePatternTest {
    @Test
    void mapsAFullImageDirectlyOntoItsDestination() {
        NanoVgImagePattern pattern = NanoVgImagePattern.from(
                200,
                100,
                new Rect(0.0f, 0.0f, 200.0f, 100.0f),
                new Rect(10.0f, 20.0f, 80.0f, 40.0f));

        assertEquals(new NanoVgImagePattern(10.0f, 20.0f, 80.0f, 40.0f), pattern);
    }

    @Test
    void expandsAnAtlasRegionIntoTheUnderlyingImagePattern() {
        NanoVgImagePattern pattern = NanoVgImagePattern.from(
                256,
                128,
                new Rect(64.0f, 32.0f, 128.0f, 64.0f),
                new Rect(20.0f, 10.0f, 64.0f, 32.0f));

        assertEquals(new NanoVgImagePattern(-12.0f, -6.0f, 128.0f, 64.0f), pattern);
    }

    @Test
    void rejectsDegenerateInputs() {
        Rect source = new Rect(0.0f, 0.0f, 1.0f, 1.0f);
        Rect destination = new Rect(0.0f, 0.0f, 1.0f, 1.0f);
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgImagePattern.from(0, 1, source, destination));
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgImagePattern.from(1, 1, new Rect(0.0f, 0.0f, 0.0f, 1.0f), destination));
    }
}
