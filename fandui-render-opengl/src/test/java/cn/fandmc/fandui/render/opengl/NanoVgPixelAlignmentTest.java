package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.layout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NanoVgPixelAlignmentTest {
    private static final Rect TEXTURE = new Rect(-1.0f, -1.0f, 80.0f, 20.0f);

    @Test
    void alignsFractionalTextTranslationToThePhysicalPixelGrid() {
        float[] output = new float[2];

        assertTrue(NanoVgPixelAlignment.textOffset(
                translation(20.25f, 30.5f),
                TEXTURE,
                1.0f,
                output));

        assertArrayEquals(new float[]{-0.25f, 0.5f}, output);
    }

    @Test
    void preservesCoordinatesAlreadyAlignedAtTheCurrentDpr() {
        float[] output = new float[2];

        assertFalse(NanoVgPixelAlignment.textOffset(
                translation(20.5f, 30.0f),
                new Rect(-0.5f, -0.5f, 80.0f, 20.0f),
                2.0f,
                output));

        assertArrayEquals(new float[]{0.0f, 0.0f}, output);
    }

    @Test
    void usesHalfLogicalPixelsAtDprTwo() {
        float[] output = new float[2];

        assertTrue(NanoVgPixelAlignment.textOffset(
                translation(20.25f, 30.25f),
                new Rect(-0.5f, -0.5f, 80.0f, 20.0f),
                2.0f,
                output));

        assertArrayEquals(new float[]{0.25f, 0.25f}, output);
    }

    @Test
    void leavesScaledOrRotatedTextOnItsRequestedTransform() {
        float[] output = new float[2];

        assertFalse(NanoVgPixelAlignment.textOffset(
                new float[]{1.0f, 0.1f, 0.0f, 1.0f, 20.25f, 30.5f},
                TEXTURE,
                1.0f,
                output));
        assertArrayEquals(new float[]{0.0f, 0.0f}, output);
    }

    @Test
    void validatesScratchBuffersAndDpr() {
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgPixelAlignment.textOffset(
                        new float[5], TEXTURE, 1.0f, new float[2]));
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgPixelAlignment.textOffset(
                        translation(0.0f, 0.0f), TEXTURE, 0.0f, new float[2]));
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgPixelAlignment.textOffset(
                        translation(0.0f, 0.0f), TEXTURE, 1.0f, new float[1]));
    }

    private static float[] translation(float x, float y) {
        return new float[]{1.0f, 0.0f, 0.0f, 1.0f, x, y};
    }
}
