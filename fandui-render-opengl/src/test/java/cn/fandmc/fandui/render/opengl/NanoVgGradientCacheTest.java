package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.canvas.DisplayGradientStop;
import cn.fandmc.fandui.canvas.PremultipliedColor;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NanoVgGradientCacheTest {
    private static final List<DisplayGradientStop> STOPS = List.of(
            new DisplayGradientStop(0.0f, new PremultipliedColor(0.0f, 0.0f, 0.0f, 0.0f)),
            new DisplayGradientStop(0.5f, new PremultipliedColor(0.5f, 0.0f, 0.0f, 0.5f)),
            new DisplayGradientStop(1.0f, new PremultipliedColor(0.0f, 0.0f, 1.0f, 1.0f)));

    @Test
    void writesExactPremultipliedLinearStopsAndInterpolation() {
        ByteBuffer pixels = ByteBuffer.allocate(NanoVgGradientCache.LINEAR_WIDTH * 4);

        NanoVgGradientCache.writeLinearPixels(STOPS, pixels);

        assertPixel(pixels, 0, 0, 0, 0, 0);
        assertPixel(pixels, 64, 64, 0, 0, 64);
        assertPixel(pixels, 128, 128, 0, 0, 128);
        assertPixel(pixels, 192, 64, 0, 128, 191);
        assertPixel(pixels, 256, 0, 0, 255, 255);
    }

    @Test
    void radialLookupUsesTheFirstColorAtCenterAndClampsCorners() {
        ByteBuffer pixels = ByteBuffer.allocate(
                NanoVgGradientCache.RADIAL_SIZE * NanoVgGradientCache.RADIAL_SIZE * 4);

        NanoVgGradientCache.writeRadialPixels(STOPS, 0.25f, pixels);

        int center = NanoVgGradientCache.RADIAL_SIZE / 2;
        assertPixel(pixels, center, center, 0, 0, 0, 0);
        assertPixel(pixels, 0, 0, 0, 0, 255, 255);
    }

    @Test
    void rejectsInvalidStandaloneLookupInputs() {
        ByteBuffer pixels = ByteBuffer.allocate(NanoVgGradientCache.LINEAR_WIDTH * 4);
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgGradientCache.writeLinearPixels(List.of(STOPS.get(0)), pixels));
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgGradientCache.writeRadialPixels(STOPS, Float.NaN, pixels));
    }

    private static void assertPixel(
            ByteBuffer pixels,
            int index,
            int red,
            int green,
            int blue,
            int alpha) {
        assertArrayEquals(
                new int[]{red, green, blue, alpha},
                new int[]{
                        Byte.toUnsignedInt(pixels.get(index * 4)),
                        Byte.toUnsignedInt(pixels.get(index * 4 + 1)),
                        Byte.toUnsignedInt(pixels.get(index * 4 + 2)),
                        Byte.toUnsignedInt(pixels.get(index * 4 + 3))});
    }

    private static void assertPixel(
            ByteBuffer pixels,
            int x,
            int y,
            int red,
            int green,
            int blue,
            int alpha) {
        int index = y * NanoVgGradientCache.RADIAL_SIZE + x;
        assertPixel(pixels, index, red, green, blue, alpha);
    }
}
