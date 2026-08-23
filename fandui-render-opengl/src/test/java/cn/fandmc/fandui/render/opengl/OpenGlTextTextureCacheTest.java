package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.text.TextPixelFormat;
import cn.fandmc.fandui.text.TextRaster;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlTextTextureCacheTest {
    @Test
    void enforcesByteLimitAndEvictsLeastRecentlyUsedInactiveTexture() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlTextTextureCache cache = new OpenGlTextTextureCache(driver, 8L);
        TextRaster first = alphaRaster(1L, 11, Color.rgb(0xFFFFFF));
        TextRaster second = alphaRaster(2L, 12, Color.rgb(0xFFFFFF));
        TextRaster third = alphaRaster(3L, 13, Color.rgb(0xFFFFFF));

        cache.activate(List.of(first));
        int firstId = cache.resolve(1L, OpenGlSampling.LINEAR).orElseThrow().textureId();
        cache.activate(List.of(second));
        int secondId = cache.resolve(2L, OpenGlSampling.LINEAR).orElseThrow().textureId();

        cache.activate(List.of(first));
        cache.activate(List.of(first, third));

        assertEquals(new OpenGlTextTextureCache.CacheStats(2, 8L, 2), cache.stats());
        assertEquals(firstId, cache.resolve(1L, OpenGlSampling.LINEAR).orElseThrow().textureId());
        assertTrue(cache.resolve(3L, OpenGlSampling.LINEAR).isPresent());
        assertTrue(driver.deletedTextureIds.contains(secondId));
        assertFalse(driver.deletedTextureIds.contains(firstId));
        cache.close();
    }

    @Test
    void rejectsAnActiveSetLargerThanTheBudgetBeforeUploading() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlTextTextureCache cache = new OpenGlTextTextureCache(driver, 7L);

        OpenGlRenderException failure = assertThrows(
                OpenGlRenderException.class,
                () -> cache.activate(List.of(
                        alphaRaster(1L, 11, Color.rgb(0xFFFFFF)),
                        alphaRaster(2L, 12, Color.rgb(0xFFFFFF)))));

        assertTrue(failure.getMessage().contains("exceeding the 7 byte cache limit"));
        assertTrue(driver.createdTextureKeys.isEmpty());
        assertEquals(new OpenGlTextTextureCache.CacheStats(0, 0L, 0), cache.stats());
        cache.close();
    }

    @Test
    void reusesAlphaTextureWhenOnlyModulationColorChanges() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlTextTextureCache cache = new OpenGlTextTextureCache(driver, 8L);
        TextRaster blue = alphaRaster(7L, 21, Color.rgb(0x2277FF));
        TextRaster red = alphaRaster(7L, 21, Color.rgb(0xFF3322));

        cache.activate(List.of(blue));
        int textureId = cache.resolve(7L, OpenGlSampling.LINEAR).orElseThrow().textureId();
        cache.activate(List.of(red));

        assertEquals(List.of(7L), driver.createdTextureKeys);
        assertEquals(textureId, cache.resolve(7L, OpenGlSampling.LINEAR).orElseThrow().textureId());
        assertEquals(new OpenGlTextTextureCache.CacheStats(1, 4L, 1), cache.stats());
        cache.close();
    }

    @Test
    void rejectsTextureKeyCollisionWithoutReplacingTheLiveEntry() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlTextTextureCache cache = new OpenGlTextTextureCache(driver, 8L);
        TextRaster original = alphaRaster(9L, 31, Color.rgb(0xFFFFFF));
        TextRaster collision = alphaRaster(9L, 32, Color.rgb(0xFFFFFF));

        cache.activate(List.of(original));
        int originalId = cache.resolve(9L, OpenGlSampling.LINEAR).orElseThrow().textureId();

        OpenGlRenderException failure = assertThrows(
                OpenGlRenderException.class,
                () -> cache.activate(List.of(collision)));

        assertTrue(failure.getMessage().contains("Text texture key collision"));
        assertEquals(List.of(9L), driver.createdTextureKeys);
        assertEquals(originalId, cache.resolve(9L, OpenGlSampling.LINEAR).orElseThrow().textureId());
        cache.close();
    }

    @Test
    void resolvesOnlyActiveLiveTexturesWithLinearSampling() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlTextTextureCache cache = new OpenGlTextTextureCache(driver, 8L);
        TextRaster raster = alphaRaster(4L, 41, Color.rgb(0xFFFFFF));

        cache.activate(List.of(raster));
        int textureId = cache.resolve(4L, OpenGlSampling.LINEAR).orElseThrow().textureId();
        assertTrue(cache.resolve(99L, OpenGlSampling.LINEAR).isEmpty());
        assertThrows(
                OpenGlRenderException.class,
                () -> cache.resolve(4L, OpenGlSampling.NEAREST));

        driver.liveTextureIds.remove(textureId);
        assertThrows(
                OpenGlRenderException.class,
                () -> cache.resolve(4L, OpenGlSampling.LINEAR));
        driver.liveTextureIds.put(textureId, 4L);

        cache.activate(List.of());
        assertTrue(cache.resolve(4L, OpenGlSampling.LINEAR).isEmpty());
        assertEquals(new OpenGlTextTextureCache.CacheStats(1, 4L, 0), cache.stats());
        cache.close();
    }

    @Test
    void confinesOperationsToTheFirstRenderThread() throws InterruptedException {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlTextTextureCache cache = new OpenGlTextTextureCache(driver, 8L);
        cache.activate(List.of(alphaRaster(5L, 51, Color.rgb(0xFFFFFF))));
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread otherThread = new Thread(
                () -> {
                    try {
                        cache.resolve(5L, OpenGlSampling.LINEAR);
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    }
                },
                "wrong-render-thread");
        otherThread.start();
        otherThread.join();

        assertTrue(failure.get() instanceof IllegalStateException);
        assertTrue(failure.get().getMessage().contains("confined to its first Render Thread"));
        assertTrue(cache.resolve(5L, OpenGlSampling.LINEAR).isPresent());
        cache.close();
    }

    @Test
    void cleansPartialUploadsAndClosesIdempotently() {
        FakeTextureDriver driver = new FakeTextureDriver();
        driver.failOnCreateNumber = 2;
        OpenGlTextTextureCache cache = new OpenGlTextTextureCache(driver, 8L);

        assertThrows(
                OpenGlRenderException.class,
                () -> cache.activate(List.of(
                        alphaRaster(1L, 61, Color.rgb(0xFFFFFF)),
                        alphaRaster(2L, 62, Color.rgb(0xFFFFFF)))));
        assertEquals(new OpenGlTextTextureCache.CacheStats(0, 0L, 0), cache.stats());
        assertEquals(1, driver.deletedTextureIds.size());

        driver.failOnCreateNumber = Integer.MAX_VALUE;
        cache.activate(List.of(alphaRaster(3L, 63, Color.rgb(0xFFFFFF))));
        cache.close();
        cache.close();

        assertEquals(2, driver.deletedTextureIds.size());
        assertEquals(new OpenGlTextTextureCache.CacheStats(0, 0L, 0), cache.stats());
        assertThrows(
                IllegalStateException.class,
                () -> cache.activate(List.of(alphaRaster(4L, 64, Color.rgb(0xFFFFFF)))));
    }

    private static TextRaster alphaRaster(long textureKey, int digestSeed, Color modulationColor) {
        byte[] digest = new byte[32];
        Arrays.fill(digest, (byte) digestSeed);
        byte[] pixels = new byte[4];
        Arrays.fill(pixels, (byte) (digestSeed * 3));
        return TextRaster.copyOf(
                1L,
                textureKey,
                digest,
                TextPixelFormat.ALPHA_8,
                4,
                1,
                4,
                2.0f,
                -0.5f,
                -0.5f,
                modulationColor,
                pixels);
    }

    private static final class FakeTextureDriver implements OpenGlTextTextureCache.TextureDriver {
        private final List<Long> createdTextureKeys = new ArrayList<>();
        private final List<Integer> deletedTextureIds = new ArrayList<>();
        private final Map<Integer, Long> liveTextureIds = new LinkedHashMap<>();
        private int nextTextureId = 100;
        private int createCalls;
        private int failOnCreateNumber = Integer.MAX_VALUE;

        @Override
        public int create(TextRaster raster) {
            createCalls++;
            if (createCalls == failOnCreateNumber) {
                throw new OpenGlRenderException("simulated upload failure");
            }
            int textureId = nextTextureId++;
            createdTextureKeys.add(raster.textureKey());
            liveTextureIds.put(textureId, raster.textureKey());
            return textureId;
        }

        @Override
        public void delete(int textureId) {
            deletedTextureIds.add(textureId);
            liveTextureIds.remove(textureId);
        }

        @Override
        public boolean isLive(int textureId) {
            return liveTextureIds.containsKey(textureId);
        }
    }
}
