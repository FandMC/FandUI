package cn.fandmc.fandui.core.resource;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ResourceKind;
import cn.fandmc.fandui.api.resource.ResourceSource;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.text.FontFamilies;
import cn.fandmc.fandui.core.runtime.UiThreadDispatcher;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreResourceServiceTest {
    @Test
    void publishesDefensiveFontSnapshotsAndReservesTheDefaultFamily() {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey key = UiKey.of("test", "fonts/custom.ttf");
        byte[] source = new byte[]{1, 2, 3, 4};

        assertSame(FontFamilies.DEFAULT, resources.font(FontFamilies.DEFAULT.key()));
        assertThrows(
                IllegalArgumentException.class,
                () -> resources.registerFont(FontFamilies.DEFAULT.key(), ResourceSource.bytes(source)));
        resources.registerFont(key, ResourceSource.bytes(source));
        assertEquals(1L, resources.reload(ResourceLookup.empty()));

        FontResourceSnapshot snapshot = resources.fontSnapshot();
        assertEquals(1L, snapshot.generation());
        assertFalse(snapshot.contentFingerprint().isBlank());
        byte[] exposed = snapshot.copyFonts().get(key);
        exposed[0] = 99;
        assertArrayEquals(source, snapshot.copyFonts().get(key));
        resources.close();
    }

    @Test
    void internsRefsAndPreservesExactRegistrationOwnershipAcrossThreads() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey key = UiKey.of("test", "textures/panel.png");
        ResourceSource firstSource = ResourceSource.bytes(new byte[]{1});

        assertSame(resources.image(key), resources.image(key));
        assertEquals(ResourceState.UNRESOLVED, resources.image(key).state());
        assertTrue(resources.image(key).info().isEmpty());
        assertSame(resources.font(key), resources.font(key));

        var first = resources.registerImage(key, firstSource);
        assertThrows(
                IllegalStateException.class,
                () -> resources.registerImage(key, ResourceSource.bytes(new byte[]{2})));
        assertEquals(ResourceKind.IMAGE, first.kind());

        Thread closer = new Thread(first::close, "resource-close-test");
        closer.start();
        closer.join();
        assertFalse(first.active());

        var replacement = resources.registerImage(key, ResourceSource.bytes(new byte[]{3}));
        dispatcher.drain();
        assertTrue(replacement.active());
        assertEquals(1, resources.registeredSources().size());
        assertSame(replacement.key(), resources.registeredSources().get(0).key());
        replacement.close();
        assertTrue(resources.registeredSources().isEmpty());
        resources.close();
    }

    @Test
    void advancesGenerationAndNotifiesOnlyActiveListeners() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        List<String> notifications = new ArrayList<>();
        var listener = resources.onReload(
                (oldGeneration, newGeneration) -> notifications.add(oldGeneration + "->" + newGeneration));

        assertEquals(1L, resources.applyReload());
        assertEquals(List.of("0->1"), notifications);

        Thread closer = new Thread(listener::close, "reload-listener-close-test");
        closer.start();
        closer.join();
        assertFalse(listener.active());
        assertEquals(2L, resources.applyReload());
        assertEquals(List.of("0->1"), notifications);
        dispatcher.drain();
        assertEquals(2L, resources.generation());
        resources.close();
    }

    @Test
    void rejectsUiOwnedMutationFromOtherThreads() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        List<Throwable> failures = new ArrayList<>();
        Thread thread = new Thread(() -> {
            try {
                resources.registerFont(
                        UiKey.of("test", "font.ttf"),
                        ResourceSource.bytes(new byte[]{1}));
            } catch (Throwable failure) {
                failures.add(failure);
            }
        }, "resource-wrong-thread-test");
        thread.start();
        thread.join();

        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof IllegalStateException);
        resources.close();
    }

    @Test
    void decodesRegisteredPngOnTheDedicatedWorkerAndPublishesReadyPixels() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey key = UiKey.of("test", "textures/premultiplied.png");
        AtomicReference<Thread> loaderThread = new AtomicReference<>();
        byte[] png = png(0x80FF8040, 0x00123456);
        var image = resources.image(key);
        resources.registerImage(key, () -> {
            loaderThread.set(Thread.currentThread());
            return png.clone();
        });
        List<String> notifications = new ArrayList<>();
        resources.onReload((oldGeneration, newGeneration) ->
                notifications.add(oldGeneration + "->" + newGeneration));

        long generation = resources.reload(ResourceLookup.empty());

        assertEquals(1L, generation);
        assertEquals(ResourceState.READY, image.state());
        assertEquals(Optional.of(new ImageInfo(2, 1)), image.info());
        assertTrue(loaderThread.get() != Thread.currentThread());
        assertEquals("FandUI-Resource-Reload", loaderThread.get().getName());
        ImageRaster raster = resources.resolveImage(image);
        assertEquals(1L, raster.resourceGeneration());
        assertEquals(8, raster.byteSize());
        assertTrue(raster.textureKey() != 0L);
        byte[] pixels = new byte[raster.byteSize()];
        raster.pixels().get(pixels);
        assertArrayEquals(
                new byte[]{
                        (byte) 128, (byte) 64, (byte) 32, (byte) 128,
                        0, 0, 0, 0
                },
                pixels);
        assertEquals(List.of("0->1"), notifications);
        resources.close();
    }

    @Test
    void decodesRegisteredSvgOnTheDedicatedWorkerWithViewBoxNormalization() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey key = UiKey.of("test", "textures/vector.svg");
        var image = resources.image(key);
        resources.registerImage(key, ResourceSource.svg("""
                <svg viewBox="10 20 2 1">
                  <rect x="10" y="20" width="2" height="1" fill="#ff0000"/>
                </svg>
                """));

        resources.reload(ResourceLookup.empty());

        assertEquals(ResourceState.READY, image.state());
        assertEquals(Optional.of(new ImageInfo(2, 1)), image.info());
        ImageRaster raster = resources.resolveImage(image);
        byte[] pixels = new byte[raster.byteSize()];
        raster.pixels().get(pixels);
        assertArrayEquals(new byte[]{(byte) 255, 0, 0, (byte) 255,
                (byte) 255, 0, 0, (byte) 255}, pixels);
        resources.close();
    }

    @Test
    void autoDetectsSvgAndAppliesInheritedOpacity() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey key = UiKey.of("test", "textures/opaque.svg");
        var image = resources.image(key);
        resources.registerImage(key, ResourceSource.bytes("""
                <svg viewBox="0 0 4 4">
                  <g opacity="0.5"><rect width="4" height="4" fill="#ff0000"/></g>
                </svg>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        resources.reload(ResourceLookup.empty());

        byte[] pixels = new byte[resources.resolveImage(image).byteSize()];
        resources.resolveImage(image).pixels().get(pixels);
        int alpha = Byte.toUnsignedInt(pixels[3]);
        assertTrue(alpha >= 126 && alpha <= 129);
        assertEquals(alpha, Byte.toUnsignedInt(pixels[0]));
        resources.close();
    }

    @Test
    void usesExplicitPixelDimensionsWhenSvgAlsoDeclaresAViewBox() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey key = UiKey.of("test", "textures/sized.svg");
        var image = resources.image(key);
        resources.registerImage(key, ResourceSource.svg("""
                <svg width="8px" height="4px" viewBox="0 0 2 1">
                  <rect width="2" height="1" fill="blue"/>
                </svg>
                """));

        resources.reload(ResourceLookup.empty());

        assertEquals(Optional.of(new ImageInfo(8, 4)), image.info());
        resources.close();
    }

    @Test
    void rejectsWholeCandidateAndPreservesPreviouslyReadyImages() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey stableKey = UiKey.of("test", "textures/stable.png");
        UiKey brokenKey = UiKey.of("test", "textures/broken.png");
        var stable = resources.image(stableKey);
        var broken = resources.image(brokenKey);
        var stableRegistration = resources.registerImage(stableKey, ResourceSource.bytes(png(0xFFFFFFFF)));
        resources.reload(ResourceLookup.empty());
        ImageRaster previous = resources.resolveImage(stable);

        stableRegistration.close();
        resources.registerImage(stableKey, ResourceSource.bytes(new byte[]{1, 2, 3}));
        resources.registerImage(brokenKey, ResourceSource.bytes(new byte[]{4, 5, 6}));

        assertThrows(ResourceReloadException.class, () -> resources.reload(ResourceLookup.empty()));

        assertEquals(1L, resources.generation());
        assertEquals(ResourceState.READY, stable.state());
        assertSame(previous, resources.resolveImage(stable));
        assertEquals(ResourceState.FAILED, broken.state());
        assertTrue(broken.info().isEmpty());
        assertTrue(broken.failure().isPresent());
        resources.close();
    }

    @Test
    void resolvesResourcePackFallbackAndPublishesMissingState() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey presentKey = UiKey.of("test", "textures/present.png");
        UiKey missingKey = UiKey.of("test", "textures/missing.png");
        var present = resources.image(presentKey);
        var missing = resources.image(missingKey);
        byte[] source = png(0xFF336699);

        resources.reload((kind, key) -> key.equals(presentKey)
                ? Optional.of(ResourceSource.bytes(source))
                : Optional.empty());

        assertEquals(ResourceState.READY, present.state());
        assertEquals(ResourceState.MISSING, missing.state());
        assertEquals(Optional.of(new ImageInfo(1, 1)), present.info());

        resources.reload();
        assertEquals(2L, resources.generation());
        assertEquals(ResourceState.READY, present.state());
        assertEquals(Optional.of(new ImageInfo(1, 1)), present.info());

        resources.reload(ResourceLookup.empty());
        assertEquals(3L, resources.generation());
        assertEquals(ResourceState.MISSING, present.state());
        assertTrue(present.info().isEmpty());
        resources.close();
    }

    @Test
    void treatsMissingExplicitSourceAsARejectedRequiredResource() {
        TestDispatcher dispatcher = new TestDispatcher();
        CoreResourceService resources = new CoreResourceService(dispatcher);
        UiKey key = UiKey.of("test", "textures/required.png");
        var image = resources.image(key);
        resources.registerImage(key, () -> {
            throw new FileNotFoundException(key.toString());
        });

        assertThrows(ResourceReloadException.class, () -> resources.reload(ResourceLookup.empty()));

        assertEquals(0L, resources.generation());
        assertEquals(ResourceState.MISSING, image.state());
        assertTrue(image.failure().orElseThrow() instanceof FileNotFoundException);
        resources.close();
    }

    private static byte[] png(int... argb) throws IOException {
        BufferedImage image = new BufferedImage(argb.length, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < argb.length; x++) {
            image.setRGB(x, 0, argb[x]);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static final class TestDispatcher implements UiThreadDispatcher {
        private final Thread owner = Thread.currentThread();
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        @Override
        public boolean isUiThread() {
            return Thread.currentThread() == owner;
        }

        @Override
        public synchronized void execute(Runnable action) {
            queued.add(action);
        }

        private void drain() {
            while (true) {
                Runnable action;
                synchronized (this) {
                    action = queued.poll();
                }
                if (action == null) {
                    return;
                }
                action.run();
            }
        }
    }
}
