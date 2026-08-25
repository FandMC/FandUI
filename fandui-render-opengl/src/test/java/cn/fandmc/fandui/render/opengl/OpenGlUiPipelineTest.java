package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.session.UiSession;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextLine;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.canvas.DisplayCommand;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.canvas.RecordingCanvas2D;
import cn.fandmc.fandui.core.resource.ImageRaster;
import cn.fandmc.fandui.core.runtime.UiSceneFrame;
import cn.fandmc.fandui.text.TextPixelFormat;
import cn.fandmc.fandui.text.TextRaster;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlUiPipelineTest {
    private static final UiViewport VIEWPORT = new UiViewport(100.0f, 80.0f, 200, 160, 2.0f);
    private static final UiSession SESSION = unusedSession();

    @Test
    void preparesChangedScenesImmediatelyAndReusesTheReadyScene() {
        RecordingDrawer drawer = new RecordingDrawer();
        RecordingImageStore imageStore = new RecordingImageStore();
        RecordingTextStore textStore = new RecordingTextStore();
        OpenGlUiPipeline pipeline = pipeline(drawer, imageStore, textStore);
        DisplayList first = displayList(0.0f);
        DisplayList second = displayList(10.0f);

        assertTrue(pipeline.render(VIEWPORT, List.of(frame(first, VIEWPORT))).isPresent());
        assertSame(first, drawer.displayLists.get(0));
        assertTrue(pipeline.render(VIEWPORT, List.of(frame(first, VIEWPORT))).isPresent());
        assertSame(first, drawer.displayLists.get(1));
        assertSame(drawer.resources.get(0), drawer.resources.get(1));
        assertEquals(1, imageStore.activations);
        assertEquals(1, textStore.activations);

        assertTrue(pipeline.render(VIEWPORT, List.of(frame(second, VIEWPORT))).isPresent());
        assertSame(second, drawer.displayLists.get(2));

        pipeline.render(VIEWPORT, List.of(frame(first, VIEWPORT), frame(second, VIEWPORT)));
        DisplayList combined = drawer.displayLists.get(3);
        assertEquals(
                first.commands().size() + second.commands().size(),
                combined.commands().size());

        assertTrue(pipeline.render(VIEWPORT, List.of()).isEmpty());
        assertTrue(imageStore.active.isEmpty());
        assertTrue(textStore.active.isEmpty());
        pipeline.close();
        assertTrue(drawer.closed);
        assertTrue(imageStore.closed);
        assertTrue(textStore.closed);
    }

    @Test
    void rejectsMixedViewportsAndSkipsMinimizedTargets() {
        RecordingDrawer drawer = new RecordingDrawer();
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                new RecordingTextStore());
        UiViewport other = new UiViewport(120.0f, 80.0f, 240, 160, 2.0f);

        assertThrows(
                IllegalArgumentException.class,
                () -> pipeline.render(VIEWPORT, List.of(frame(displayList(0.0f), other))));
        UiViewport minimized = new UiViewport(0.0f, 0.0f, 0, 0, 1.0f);
        assertTrue(pipeline.render(minimized, List.of(frame(displayList(0.0f), minimized))).isEmpty());
        assertTrue(drawer.displayLists.isEmpty());
        pipeline.close();
    }

    @Test
    void waitsForCompleteTextSetAndKeepsThePreviousReadyFrame() {
        RecordingDrawer drawer = new RecordingDrawer();
        RecordingTextStore textStore = new RecordingTextStore();
        ControllableTextRasterizer rasterizer = new ControllableTextRasterizer();
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                textStore,
                image -> null,
                () -> 1L,
                rasterizer);
        DisplayList previous = displayList(0.0f);
        StubTextLayout first = new StubTextLayout("first", 1L);
        StubTextLayout second = new StubTextLayout("second", 1L);
        DisplayList firstText = textDisplayList(first);
        DisplayList secondText = textDisplayList(second);

        pipeline.render(VIEWPORT, List.of(frame(previous, VIEWPORT)));
        assertSame(previous, drawer.lastDisplayList());

        pipeline.render(VIEWPORT, List.of(frame(firstText, VIEWPORT)));
        assertSame(previous, drawer.lastDisplayList());
        assertTrue(textStore.active.isEmpty());

        rasterizer.complete(first, raster(first, 11L, TextPixelFormat.ALPHA_8));
        pipeline.render(VIEWPORT, List.of(frame(firstText, VIEWPORT)));
        assertSame(firstText, drawer.lastDisplayList());
        assertEquals(Set.of(11L), textStore.activeKeys());

        pipeline.render(VIEWPORT, List.of(frame(secondText, VIEWPORT)));
        assertSame(firstText, drawer.lastDisplayList());
        CompletableFuture<TextRaster> superseded = rasterizer.future(second);

        StubTextLayout third = new StubTextLayout("third", 1L);
        pipeline.render(VIEWPORT, List.of(frame(textDisplayList(third), VIEWPORT)));
        assertTrue(superseded.isCancelled());
        assertSame(firstText, drawer.lastDisplayList());
        pipeline.close();
    }

    @Test
    void keepsPendingTextWhileAnimationPublishesTheLatestDisplayList() {
        RecordingDrawer drawer = new RecordingDrawer();
        ControllableTextRasterizer rasterizer = new ControllableTextRasterizer();
        StubTextLayout layout = new StubTextLayout("animated pending", 1L);
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                new RecordingTextStore(),
                image -> null,
                () -> 1L,
                rasterizer);
        DisplayList first = textDisplayListAt(0.0f, layout);
        DisplayList latest = textDisplayListAt(24.0f, layout);

        assertTrue(pipeline.render(VIEWPORT, List.of(frame(first, VIEWPORT))).isEmpty());
        CompletableFuture<TextRaster> pending = rasterizer.future(layout);
        assertTrue(pipeline.render(VIEWPORT, List.of(frame(latest, VIEWPORT))).isEmpty());
        assertSame(pending, rasterizer.future(layout));
        assertFalse(pending.isCancelled());
        assertEquals(1, rasterizer.requests);

        rasterizer.complete(layout, raster(layout, 41L, TextPixelFormat.ALPHA_8));
        assertTrue(pipeline.render(VIEWPORT, List.of(frame(latest, VIEWPORT))).isPresent());
        assertSame(latest, drawer.lastDisplayList());
        pipeline.close();
    }

    @Test
    void reusesReadyTextResourcesAcrossDisplayListOnlyAnimationFrames() {
        RecordingDrawer drawer = new RecordingDrawer();
        RecordingTextStore textStore = new RecordingTextStore();
        AtomicInteger rasterRequests = new AtomicInteger();
        StubTextLayout layout = new StubTextLayout("animated ready", 1L);
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                textStore,
                image -> null,
                () -> 1L,
                (requested, scale) -> {
                    rasterRequests.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            raster(requested, 42L, TextPixelFormat.ALPHA_8));
                });
        DisplayList first = textDisplayListAt(0.0f, layout);
        DisplayList next = textDisplayListAt(12.0f, layout);

        assertTrue(pipeline.render(VIEWPORT, List.of(frame(first, VIEWPORT))).isPresent());
        assertTrue(pipeline.render(VIEWPORT, List.of(frame(next, VIEWPORT))).isPresent());
        assertSame(next, drawer.lastDisplayList());
        assertSame(drawer.resources.get(0), drawer.resources.get(1));
        assertEquals(1, rasterRequests.get());
        assertEquals(1, textStore.activations);
        pipeline.close();
    }

    @Test
    void framebufferResizeReusesReadyTextAndGuiScaleRerasterizesIt() {
        RecordingDrawer drawer = new RecordingDrawer();
        RecordingTextStore textStore = new RecordingTextStore();
        List<Float> rasterScales = new ArrayList<>();
        StubTextLayout layout = new StubTextLayout("responsive", 1L);
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                textStore,
                image -> null,
                () -> 1L,
                (requested, scale) -> {
                    rasterScales.add(scale);
                    return CompletableFuture.completedFuture(raster(
                            requested,
                            20L + rasterScales.size(),
                            TextPixelFormat.ALPHA_8));
                });
        DisplayList scene = textDisplayList(layout);

        assertTrue(pipeline.render(VIEWPORT, List.of(frame(scene, VIEWPORT))).isPresent());
        UiViewport framebufferResize = new UiViewport(100.0f, 80.0f, 300, 240, 2.0f);
        assertTrue(pipeline.render(
                framebufferResize,
                List.of(frame(scene, framebufferResize))).isPresent());
        assertEquals(List.of(2.0f), rasterScales);
        assertEquals(Set.of(21L), textStore.activeKeys());

        UiViewport guiScaleChange = new UiViewport(100.0f, 80.0f, 300, 240, 3.0f);
        assertTrue(pipeline.render(
                guiScaleChange,
                List.of(frame(scene, guiScaleChange))).isPresent());
        assertEquals(List.of(2.0f, 3.0f), rasterScales);
        assertEquals(Set.of(22L), textStore.activeKeys());
        assertEquals(3.0f, drawer.frameInfos.get(2).devicePixelRatio());
        pipeline.close();
    }

    @Test
    void framebufferResizeRetargetsPendingTextWithoutRestartingIt() {
        RecordingDrawer drawer = new RecordingDrawer();
        ControllableTextRasterizer rasterizer = new ControllableTextRasterizer();
        StubTextLayout layout = new StubTextLayout("pending resize", 1L);
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                new RecordingTextStore(),
                image -> null,
                () -> 1L,
                rasterizer);
        DisplayList scene = textDisplayList(layout);

        assertTrue(pipeline.render(VIEWPORT, List.of(frame(scene, VIEWPORT))).isEmpty());
        CompletableFuture<TextRaster> pending = rasterizer.future(layout);
        UiViewport resized = new UiViewport(100.0f, 80.0f, 320, 256, 2.0f);
        assertTrue(pipeline.render(resized, List.of(frame(scene, resized))).isEmpty());
        assertSame(pending, rasterizer.future(layout));
        assertFalse(pending.isCancelled());

        rasterizer.complete(layout, raster(layout, 31L, TextPixelFormat.ALPHA_8));
        assertTrue(pipeline.render(resized, List.of(frame(scene, resized))).isPresent());
        assertSame(scene, drawer.lastDisplayList());
        pipeline.close();
    }

    @Test
    void reportsTextRasterFailureBeforePublishingTheNewFrame() {
        RecordingDrawer drawer = new RecordingDrawer();
        IllegalStateException cause = new IllegalStateException("raster failed");
        StubTextLayout layout = new StubTextLayout("failure", 1L);
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                new RecordingTextStore(),
                image -> null,
                () -> 1L,
                (ignored, scale) -> CompletableFuture.failedFuture(cause));

        OpenGlRenderException failure = assertThrows(
                OpenGlRenderException.class,
                () -> pipeline.render(VIEWPORT, List.of(frame(textDisplayList(layout), VIEWPORT))));
        assertSame(cause, failure.getCause());
        assertTrue(drawer.displayLists.isEmpty());
        pipeline.close();
    }

    @Test
    void rejectsCollidingTextTextureKeysBeforeDrawing() {
        RecordingDrawer drawer = new RecordingDrawer();
        StubTextLayout first = new StubTextLayout("first collision", 7L);
        StubTextLayout second = new StubTextLayout("second collision", 7L);
        OpenGlTextTextureCache textStore = new OpenGlTextTextureCache(
                new OpenGlTextTextureCache.TextureDriver() {
                    @Override
                    public int create(TextRaster raster) {
                        throw new AssertionError("A colliding active set must fail before upload");
                    }

                    @Override
                    public void delete(int textureId) {
                    }
                },
                64L);
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                textStore,
                image -> null,
                () -> 7L,
                (layout, scale) -> CompletableFuture.completedFuture(raster(
                        layout,
                        19L,
                        layout == first
                                ? TextPixelFormat.ALPHA_8
                                : TextPixelFormat.RGBA_8888_PREMULTIPLIED)));

        OpenGlRenderException failure = assertThrows(
                OpenGlRenderException.class,
                () -> pipeline.render(
                        VIEWPORT,
                        List.of(frame(textDisplayList(first, second), VIEWPORT))));
        assertTrue(failure.getMessage().contains("Conflicting text rasters use key 19"));
        assertTrue(drawer.displayLists.isEmpty());
        pipeline.close();
    }

    @Test
    void resourceGenerationRepublishesAndPinsTheMatchingImage() {
        RecordingDrawer drawer = new RecordingDrawer();
        RecordingImageStore imageStore = new RecordingImageStore();
        AtomicLong generation = new AtomicLong(1L);
        StubImageRef image = new StubImageRef(UiKey.of("test", "textures/panel.png"), 2, 1);
        Map<Long, ImageRaster> rasters = Map.of(
                1L, imageRaster(image.key(), 1L, 31L, 71),
                2L, imageRaster(image.key(), 2L, 32L, 72));
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                imageStore,
                new RecordingTextStore(),
                ref -> rasters.get(generation.get()),
                generation::get,
                (layout, scale) -> CompletableFuture.failedFuture(
                        new AssertionError("No text raster should be requested")));
        DisplayList scene = imageDisplayList(image);

        pipeline.render(VIEWPORT, List.of(frame(scene, VIEWPORT)));
        assertEquals(Set.of(31L), imageStore.activeKeys());
        assertEquals(1L, drawer.lastResources.resolveImage(image, ImageSampling.NEAREST)
                .textureKey() - 30L);

        generation.set(2L);
        pipeline.render(VIEWPORT, List.of(frame(scene, VIEWPORT)));
        assertEquals(Set.of(32L), imageStore.activeKeys());
        assertEquals(32L, drawer.lastResources.resolveImage(image, ImageSampling.NEAREST).textureKey());
        pipeline.close();
    }

    @Test
    void rejectsImageAndTextTextureKeyCollisionBeforeUploadingOrDrawing() {
        RecordingDrawer drawer = new RecordingDrawer();
        RecordingImageStore imageStore = new RecordingImageStore();
        RecordingTextStore textStore = new RecordingTextStore();
        StubImageRef image = new StubImageRef(UiKey.of("test", "textures/collision.png"), 2, 1);
        StubTextLayout text = new StubTextLayout("collision", 1L);
        ImageRaster imageRaster = imageRaster(image.key(), 1L, 55L, 91);
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        canvas.drawImage(
                image,
                new Rect(0.0f, 0.0f, 10.0f, 10.0f),
                ImageSampling.LINEAR,
                1.0f);
        canvas.drawText(text, new Point(0.0f, 10.0f));
        DisplayList scene = canvas.finish();
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                imageStore,
                textStore,
                ref -> imageRaster,
                () -> 1L,
                (layout, scale) -> CompletableFuture.completedFuture(
                        raster(layout, 55L, TextPixelFormat.ALPHA_8)));

        OpenGlRenderException failure = assertThrows(
                OpenGlRenderException.class,
                () -> pipeline.render(VIEWPORT, List.of(frame(scene, VIEWPORT))));
        assertTrue(failure.getMessage().contains("same texture key"));
        assertTrue(imageStore.active.isEmpty());
        assertTrue(textStore.active.isEmpty());
        assertTrue(drawer.displayLists.isEmpty());
        pipeline.close();
    }

    @Test
    void rejectsUnavailableImageBeforePublishingTheScene() {
        RecordingDrawer drawer = new RecordingDrawer();
        StubImageRef image = new StubImageRef(UiKey.of("test", "textures/missing.png"), 2, 1);
        OpenGlUiPipeline pipeline = pipeline(
                drawer,
                new RecordingImageStore(),
                new RecordingTextStore(),
                ref -> null,
                () -> 1L,
                (layout, scale) -> CompletableFuture.failedFuture(new AssertionError("unexpected text")));

        OpenGlRenderException failure = assertThrows(
                OpenGlRenderException.class,
                () -> pipeline.render(VIEWPORT, List.of(frame(imageDisplayList(image), VIEWPORT))));
        assertTrue(failure.getMessage().contains("Image raster is unavailable"));
        assertTrue(drawer.displayLists.isEmpty());
        pipeline.close();
    }

    private static OpenGlUiPipeline pipeline(
            RecordingDrawer drawer,
            ImageTextureStore imageStore,
            TextTextureStore textStore) {
        return pipeline(
                drawer,
                imageStore,
                textStore,
                image -> null,
                () -> 1L,
                (layout, scale) -> CompletableFuture.failedFuture(
                        new AssertionError("No text raster should be requested")));
    }

    private static OpenGlUiPipeline pipeline(
            RecordingDrawer drawer,
            ImageTextureStore imageStore,
            TextTextureStore textStore,
            ImageRasterResolver imageRasterizer,
            AtomicLong generation,
            TextRasterizer textRasterizer) {
        return pipeline(drawer, imageStore, textStore, imageRasterizer, generation::get, textRasterizer);
    }

    private static OpenGlUiPipeline pipeline(
            RecordingDrawer drawer,
            ImageTextureStore imageStore,
            TextTextureStore textStore,
            ImageRasterResolver imageRasterizer,
            java.util.function.LongSupplier generation,
            TextRasterizer textRasterizer) {
        return new OpenGlUiPipeline(
                testHost(),
                imageRasterizer,
                imageStore,
                generation,
                textRasterizer,
                textStore,
                drawer);
    }

    private static RenderHost testHost() {
        return new RenderHost() {
            private final Thread renderThread = Thread.currentThread();

            @Override
            public String name() {
                return "test";
            }

            @Override
            public void assertRenderThread() {
                if (Thread.currentThread() != renderThread) {
                    throw new IllegalStateException("wrong thread");
                }
            }

            @Override
            public Optional<OpenGlTarget> currentTarget() {
                return Optional.empty();
            }

            @Override
            public boolean supportsStateHandoff() {
                return true;
            }

            @Override
            public void restoreStateAfterFandUi() {
            }
        };
    }

    private static DisplayList displayList(float x) {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        canvas.fillRect(
                new Rect(x, 0.0f, 5.0f, 5.0f),
                new SolidPaint(Color.rgb(0xffffff)));
        return canvas.finish();
    }

    private static DisplayList textDisplayList(TextLayout... layouts) {
        return textDisplayListAt(0.0f, layouts);
    }

    private static DisplayList textDisplayListAt(float x, TextLayout... layouts) {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        for (int index = 0; index < layouts.length; index++) {
            canvas.drawText(layouts[index], new Point(x + index * 10.0f, 5.0f));
        }
        return canvas.finish();
    }

    private static DisplayList imageDisplayList(ImageRef image) {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        canvas.drawImage(
                image,
                new Rect(2.0f, 3.0f, 20.0f, 10.0f),
                ImageSampling.NEAREST,
                1.0f);
        return canvas.finish();
    }

    private static ImageRaster imageRaster(
            UiKey key,
            long generation,
            long textureKey,
            int digestSeed) {
        byte[] digest = new byte[32];
        Arrays.fill(digest, (byte) digestSeed);
        byte[] pixels = new byte[8];
        Arrays.fill(pixels, (byte) digestSeed);
        return ImageRaster.copyOf(key, generation, textureKey, digest, 2, 1, pixels);
    }

    private static TextRaster raster(
            TextLayout layout,
            long textureKey,
            TextPixelFormat format) {
        int width = 4;
        int height = 2;
        int rowBytes = width * format.bytesPerPixel();
        byte[] pixels = new byte[rowBytes * height];
        Arrays.fill(pixels, (byte) 0x7f);
        byte[] digest = new byte[32];
        Arrays.fill(digest, (byte) textureKey);
        return TextRaster.copyOf(
                layout.resourceGeneration(),
                textureKey,
                digest,
                format,
                width,
                height,
                rowBytes,
                2.0f,
                -0.5f,
                -0.5f,
                Color.rgb(format == TextPixelFormat.ALPHA_8 ? 0x55AAFF : 0xFFFFFF),
                pixels);
    }

    private static UiSceneFrame frame(DisplayList displayList, UiViewport viewport) {
        return new UiSceneFrame(SESSION, displayList, viewport, 1L);
    }

    private static UiSession unusedSession() {
        return (UiSession) Proxy.newProxyInstance(
                OpenGlUiPipelineTest.class.getClassLoader(),
                new Class<?>[]{UiSession.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class && method.getName().equals("toString")) {
                        return "unused-session";
                    }
                    throw new AssertionError("Unexpected UiSession call: " + method.getName());
                });
    }

    private static final class ControllableTextRasterizer implements TextRasterizer {
        private final IdentityHashMap<TextLayout, CompletableFuture<TextRaster>> futures = new IdentityHashMap<>();
        private int requests;

        @Override
        public CompletableFuture<TextRaster> raster(TextLayout layout, float deviceScale) {
            requests++;
            return futures.computeIfAbsent(layout, ignored -> new CompletableFuture<>());
        }

        private CompletableFuture<TextRaster> future(TextLayout layout) {
            return futures.get(layout);
        }

        private void complete(TextLayout layout, TextRaster raster) {
            assertTrue(futures.get(layout).complete(raster));
        }
    }

    private static final class RecordingDrawer implements OpenGlUiPipeline.FrameDrawer {
        private final List<DisplayList> displayLists = new ArrayList<>();
        private final List<OpenGlFrameInfo> frameInfos = new ArrayList<>();
        private final List<NanoVgRenderResources> resources = new ArrayList<>();
        private NanoVgRenderResources lastResources;
        private boolean closed;

        @Override
        public OpenGlRenderReport draw(
                RenderHost host,
                OpenGlFrameInfo frameInfo,
                DisplayList displayList,
                NanoVgRenderResources resources) {
            displayLists.add(displayList);
            frameInfos.add(frameInfo);
            this.resources.add(resources);
            lastResources = resources;
            for (DisplayCommand command : displayList.commands()) {
                if (command instanceof DisplayCommand.DrawImage image) {
                    resources.resolveImage(image.image(), image.sampling());
                } else if (command instanceof DisplayCommand.DrawImageRegion image) {
                    resources.resolveImage(image.image(), image.sampling());
                } else if (command instanceof DisplayCommand.DrawText text) {
                    resources.resolveText(text.text());
                }
            }
            return new OpenGlRenderReport(
                    OpenGlRenderReport.Status.RENDERED,
                    host.name(),
                    1,
                    Math.round(frameInfo.logicalWidth() * frameInfo.devicePixelRatio()),
                    Math.round(frameInfo.logicalHeight() * frameInfo.devicePixelRatio()),
                    displayList.commands().size(),
                    displayList.commands().size());
        }

        private DisplayList lastDisplayList() {
            return displayLists.get(displayLists.size() - 1);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class RecordingTextStore implements TextTextureStore {
        private List<TextRaster> active = List.of();
        private int activations;
        private boolean closed;

        @Override
        public void activate(List<TextRaster> rasters) {
            activations++;
            active = List.copyOf(rasters);
        }

        @Override
        public Optional<OpenGlTexture> resolve(long textureKey, OpenGlSampling sampling) {
            for (int index = 0; index < active.size(); index++) {
                if (active.get(index).textureKey() == textureKey) {
                    return Optional.of(new OpenGlTexture(index + 1));
                }
            }
            return Optional.empty();
        }

        private Set<Long> activeKeys() {
            java.util.HashSet<Long> keys = new java.util.HashSet<>();
            active.forEach(raster -> keys.add(raster.textureKey()));
            return Set.copyOf(keys);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class RecordingImageStore implements ImageTextureStore {
        private List<ImageRaster> active = List.of();
        private int activations;
        private boolean closed;

        @Override
        public void activate(List<ImageRaster> rasters) {
            activations++;
            active = List.copyOf(rasters);
        }

        @Override
        public Optional<OpenGlTexture> resolve(long textureKey, OpenGlSampling sampling) {
            for (int index = 0; index < active.size(); index++) {
                if (active.get(index).textureKey() == textureKey) {
                    return Optional.of(new OpenGlTexture(index + 1));
                }
            }
            return Optional.empty();
        }

        private Set<Long> activeKeys() {
            java.util.HashSet<Long> keys = new java.util.HashSet<>();
            active.forEach(raster -> keys.add(raster.textureKey()));
            return Set.copyOf(keys);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class StubImageRef implements ImageRef {
        private final UiKey key;
        private final ImageInfo info;

        private StubImageRef(UiKey key, int width, int height) {
            this.key = key;
            info = new ImageInfo(width, height);
        }

        @Override
        public UiKey key() {
            return key;
        }

        @Override
        public ResourceState state() {
            return ResourceState.READY;
        }

        @Override
        public Optional<ImageInfo> info() {
            return Optional.of(info);
        }
    }

    private static final class StubTextLayout implements TextLayout {
        private final TextRequest request;
        private final long resourceGeneration;

        private StubTextLayout(String text, long resourceGeneration) {
            request = TextRequest.builder(text, TextStyle.builder(12.0f).build()).build();
            this.resourceGeneration = resourceGeneration;
        }

        @Override
        public TextRequest request() {
            return request;
        }

        @Override
        public long resourceGeneration() {
            return resourceGeneration;
        }

        @Override
        public Size size() {
            return new Size(10.0f, 5.0f);
        }

        @Override
        public float alphabeticBaseline() {
            return 4.0f;
        }

        @Override
        public float ideographicBaseline() {
            return 4.5f;
        }

        @Override
        public List<TextLine> lines() {
            return List.of(new TextLine(0, request.text().length(), 10.0f, 5.0f, 4.0f));
        }

        @Override
        public int unresolvedGlyphs() {
            return 0;
        }
    }
}
