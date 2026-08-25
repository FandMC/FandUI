package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.core.resource.ImageRaster;
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

class OpenGlImageTextureCacheTest {
    @Test
    void enforcesByteLimitAndEvictsLeastRecentlyUsedInactiveTexture() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlImageTextureCache cache = new OpenGlImageTextureCache(driver, 16L);
        ImageRaster first = raster(1L, 11);
        ImageRaster second = raster(2L, 12);
        ImageRaster third = raster(3L, 13);

        cache.activate(List.of(first));
        int firstId = cache.resolve(1L, OpenGlSampling.LINEAR).orElseThrow().textureId();
        cache.activate(List.of(second));
        int secondId = cache.resolve(2L, OpenGlSampling.LINEAR).orElseThrow().textureId();

        cache.activate(List.of(first));
        cache.activate(List.of(first, third));

        assertEquals(new OpenGlImageTextureCache.CacheStats(2, 16L, 2), cache.stats());
        assertEquals(firstId, cache.resolve(1L, OpenGlSampling.LINEAR).orElseThrow().textureId());
        assertTrue(cache.resolve(3L, OpenGlSampling.NEAREST).isPresent());
        assertTrue(driver.deletedTextureIds.contains(secondId));
        assertFalse(driver.deletedTextureIds.contains(firstId));
        assertTrue(driver.liveTextureIds.containsKey(driver.lastCreatedTextureId()));
        cache.close();
    }

    @Test
    void rejectsOversizedActiveSetAndTextureKeyCollisionsBeforeUpload() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlImageTextureCache cache = new OpenGlImageTextureCache(driver, 15L);

        assertThrows(
                OpenGlRenderException.class,
                () -> cache.activate(List.of(raster(1L, 11), raster(2L, 12))));
        assertTrue(driver.createdTextureKeys.isEmpty());

        ImageRaster original = raster(9L, 31);
        cache.activate(List.of(original));
        int originalId = cache.resolve(9L, OpenGlSampling.LINEAR).orElseThrow().textureId();
        OpenGlRenderException collision = assertThrows(
                OpenGlRenderException.class,
                () -> cache.activate(List.of(raster(9L, 32))));

        assertTrue(collision.getMessage().contains("Image texture key collision"));
        assertEquals(originalId, cache.resolve(9L, OpenGlSampling.LINEAR).orElseThrow().textureId());
        cache.close();
    }

    @Test
    void resolvesOnlyActiveOwnedTexturesWithoutChangingSampling() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlImageTextureCache cache = new OpenGlImageTextureCache(driver, 16L);
        cache.activate(List.of(raster(4L, 41)));

        int textureId = cache.resolve(4L, OpenGlSampling.NEAREST).orElseThrow().textureId();
        assertEquals(
                textureId,
                cache.resolve(4L, OpenGlSampling.LINEAR).orElseThrow().textureId());
        assertTrue(cache.resolve(99L, OpenGlSampling.LINEAR).isEmpty());

        cache.activate(List.of());
        assertTrue(cache.resolve(4L, OpenGlSampling.LINEAR).isEmpty());
        assertEquals(new OpenGlImageTextureCache.CacheStats(1, 8L, 0), cache.stats());
        cache.close();
    }

    @Test
    void confinesOperationsToTheFirstRenderThreadAndClosesIdempotently() throws Exception {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlImageTextureCache cache = new OpenGlImageTextureCache(driver, 16L);
        cache.activate(List.of(raster(5L, 51)));
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread other = new Thread(() -> {
            try {
                cache.resolve(5L, OpenGlSampling.LINEAR);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "wrong-image-render-thread");
        other.start();
        other.join();

        assertTrue(failure.get() instanceof IllegalStateException);
        cache.close();
        cache.close();
        assertEquals(1, driver.deletedTextureIds.size());
        assertThrows(
                IllegalStateException.class,
                () -> cache.activate(List.of(raster(6L, 52))));
    }

    @Test
    void cleansTexturesCreatedBeforeAPartialUploadFailure() {
        FakeTextureDriver driver = new FakeTextureDriver();
        driver.failOnCreateNumber = 2;
        OpenGlImageTextureCache cache = new OpenGlImageTextureCache(driver, 16L);

        assertThrows(
                OpenGlRenderException.class,
                () -> cache.activate(List.of(raster(1L, 61), raster(2L, 62))));

        assertEquals(new OpenGlImageTextureCache.CacheStats(0, 0L, 0), cache.stats());
        assertEquals(1, driver.deletedTextureIds.size());
        cache.close();
    }

    @Test
    void failedReplacementCanReactivateThePreviousRasterSet() {
        FakeTextureDriver driver = new FakeTextureDriver();
        OpenGlImageTextureCache cache = new OpenGlImageTextureCache(driver, 16L);
        ImageRaster previous = raster(1L, 71);
        List<ImageRaster> previousSet = List.of(previous);
        cache.activate(previousSet);
        int previousTexture = cache.resolve(1L, OpenGlSampling.LINEAR).orElseThrow().textureId();
        driver.failOnCreateNumber = 3;

        assertThrows(
                OpenGlRenderException.class,
                () -> cache.activate(List.of(raster(2L, 72), raster(3L, 73))));
        assertTrue(driver.deletedTextureIds.contains(previousTexture));

        driver.failOnCreateNumber = Integer.MAX_VALUE;
        cache.activate(previousSet);

        int restoredTexture = cache.resolve(1L, OpenGlSampling.LINEAR).orElseThrow().textureId();
        assertTrue(restoredTexture != previousTexture);
        assertEquals(new OpenGlImageTextureCache.CacheStats(1, 8L, 1), cache.stats());
        cache.close();
    }

    private static ImageRaster raster(long textureKey, int digestSeed) {
        byte[] digest = new byte[32];
        Arrays.fill(digest, (byte) digestSeed);
        byte[] pixels = new byte[8];
        Arrays.fill(pixels, (byte) (digestSeed * 3));
        return ImageRaster.copyOf(
                UiKey.of("test", "textures/" + digestSeed + ".png"),
                1L,
                textureKey,
                digest,
                2,
                1,
                pixels);
    }

    private static final class FakeTextureDriver implements OpenGlImageTextureCache.TextureDriver {
        private final List<Long> createdTextureKeys = new ArrayList<>();
        private final List<Integer> deletedTextureIds = new ArrayList<>();
        private final Map<Integer, Long> liveTextureIds = new LinkedHashMap<>();
        private int nextTextureId = 100;
        private int createCalls;
        private int failOnCreateNumber = Integer.MAX_VALUE;

        @Override
        public int create(ImageRaster raster) {
            createCalls++;
            if (createCalls == failOnCreateNumber) {
                throw new OpenGlRenderException("simulated image upload failure");
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

        private int lastCreatedTextureId() {
            return nextTextureId - 1;
        }
    }
}
