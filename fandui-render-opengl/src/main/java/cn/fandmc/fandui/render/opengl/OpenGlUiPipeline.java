package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.canvas.DisplayCommand;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.core.resource.CoreResourceService;
import cn.fandmc.fandui.core.resource.ImageRaster;
import cn.fandmc.fandui.core.runtime.UiSceneFrame;
import cn.fandmc.fandui.text.SkijaTextService;
import cn.fandmc.fandui.text.TextRaster;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongSupplier;

/** Connects immutable UI frames to Skija resources and the stock LWJGL NanoVG GL3 backend. */
public final class OpenGlUiPipeline implements AutoCloseable {
    private final RenderHost host;
    private final ImageRasterResolver imageRasterizer;
    private final ImageTextureStore imageTextures;
    private final LongSupplier resourceGeneration;
    private final TextRasterizer textRasterizer;
    private final TextTextureStore textTextures;
    private final FrameDrawer drawer;

    private Thread renderThread;
    private PendingTextScene pendingText;
    private ReadyScene ready;
    private List<ImageRaster> activeImageRasters;
    private List<TextRaster> activeTextRasters;
    private boolean closed;

    public OpenGlUiPipeline(
            RenderHost host,
            CoreResourceService resources,
            SkijaTextService textService) {
        this(
                host,
                Objects.requireNonNull(resources, "resources")::resolveImage,
                new OpenGlImageTextureCache(),
                resources::generation,
                Objects.requireNonNull(textService, "textService")::raster,
                new OpenGlTextTextureCache(),
                productionDrawer());
    }

    OpenGlUiPipeline(
            RenderHost host,
            ImageRasterResolver imageRasterizer,
            ImageTextureStore imageTextures,
            LongSupplier resourceGeneration,
            TextRasterizer textRasterizer,
            TextTextureStore textTextures,
            FrameDrawer drawer) {
        this.host = Objects.requireNonNull(host, "host");
        this.imageRasterizer = Objects.requireNonNull(imageRasterizer, "imageRasterizer");
        this.imageTextures = Objects.requireNonNull(imageTextures, "imageTextures");
        this.resourceGeneration = Objects.requireNonNull(resourceGeneration, "resourceGeneration");
        this.textRasterizer = Objects.requireNonNull(textRasterizer, "textRasterizer");
        this.textTextures = Objects.requireNonNull(textTextures, "textTextures");
        this.drawer = Objects.requireNonNull(drawer, "drawer");
    }

    public Optional<OpenGlRenderReport> render(UiViewport viewport, List<UiSceneFrame> frames) {
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(frames, "frames");
        requireOpenRenderThread();

        validateFrames(viewport, frames);
        if (frames.isEmpty() || minimized(viewport)) {
            clearPendingText();
            ready = null;
            activateTextures(List.of(), List.of());
            return Optional.empty();
        }

        long generation = resourceGeneration.getAsLong();
        if (generation < 0L) {
            throw new OpenGlRenderException("FandUI resource generation must not be negative");
        }
        ReadyScene currentReady = ready;
        if (currentReady != null && currentReady.key.sameRasterInputs(viewport, frames, generation)) {
            clearPendingText();
            if (!currentReady.key.viewport.equals(viewport)) {
                currentReady = currentReady.retarget(viewport);
                ready = currentReady;
            }
        } else {
            prepare(viewport, frames, generation);
        }

        currentReady = ready;
        if (currentReady == null || !currentReady.key.viewport.equals(viewport)) {
            return Optional.empty();
        }
        activateTextures(currentReady.images.rasters, currentReady.texts.rasters);
        return Optional.of(drawer.draw(
                host,
                currentReady.frameInfo,
                currentReady.displayList,
                currentReady.resources));
    }

    private void prepare(
            UiViewport viewport,
            List<UiSceneFrame> frames,
            long resourceGeneration) {
        PendingTextScene pending = pendingText;
        if (pending == null || !pending.key.sameRasterInputs(viewport, frames, resourceGeneration)) {
            clearPendingText();
            SceneKey key = new SceneKey(viewport, copyDisplayLists(frames), resourceGeneration);
            pending = PendingTextScene.start(key, textRasterizer);
            pendingText = pending;
        } else if (!pending.key.viewport.equals(viewport)) {
            pending.retarget(viewport);
        }
        if (!pending.complete()) {
            return;
        }

        pendingText = null;
        ResolvedTexts texts;
        try {
            texts = pending.finish();
        } catch (RuntimeException exception) {
            throw new OpenGlRenderException("Failed to rasterize FandUI text", unwrap(exception));
        }
        SceneKey key = pending.key;
        ResolvedImages images = resolveImages(key);
        requireDistinctTextureKeys(images.rasters, texts.rasters);
        ready = ReadyScene.create(
                key,
                new OpenGlFrameInfo(
                        key.viewport.logicalWidth(),
                        key.viewport.logicalHeight(),
                        key.viewport.devicePixelRatio()),
                DisplayList.combine(key.displayLists),
                images,
                texts,
                imageTextures,
                textTextures);
    }

    private ResolvedImages resolveImages(SceneKey key) {
        IdentityHashMap<ImageRef, ImageRaster> byImage = new IdentityHashMap<>();
        List<ImageRaster> rasters = new ArrayList<>();
        for (DisplayList displayList : key.displayLists) {
            for (DisplayCommand command : displayList.commands()) {
                ImageRef image = null;
                if (command instanceof DisplayCommand.DrawImage drawImage) {
                    image = drawImage.image();
                } else if (command instanceof DisplayCommand.DrawImageRegion drawImage) {
                    image = drawImage.image();
                }
                if (image == null || byImage.containsKey(image)) {
                    continue;
                }
                ImageRaster raster = imageRasterizer.resolve(image);
                if (raster == null) {
                    throw new OpenGlRenderException("Image raster is unavailable for " + image.key());
                }
                if (raster.resourceGeneration() != key.resourceGeneration) {
                    throw new OpenGlRenderException(
                            "Image raster generation does not match the submitted scene");
                }
                byImage.put(image, raster);
                rasters.add(raster);
            }
        }
        return new ResolvedImages(byImage, List.copyOf(rasters));
    }

    private static void validateFrames(UiViewport viewport, List<UiSceneFrame> frames) {
        for (int index = 0; index < frames.size(); index++) {
            UiSceneFrame frame = frames.get(index);
            if (frame == null) {
                throw new NullPointerException("frames[" + index + "]");
            }
            if (!frame.viewport().equals(viewport)) {
                throw new IllegalArgumentException("All UI scene frames must use the submitted viewport");
            }
        }
    }

    private static List<DisplayList> copyDisplayLists(List<UiSceneFrame> frames) {
        List<DisplayList> displayLists = new ArrayList<>(frames.size());
        for (UiSceneFrame frame : frames) {
            displayLists.add(frame.displayList());
        }
        return List.copyOf(displayLists);
    }

    private static boolean minimized(UiViewport viewport) {
        return viewport.logicalWidth() == 0.0f
                || viewport.logicalHeight() == 0.0f
                || viewport.framebufferWidth() == 0
                || viewport.framebufferHeight() == 0;
    }

    private void requireOpenRenderThread() {
        if (closed) {
            throw new IllegalStateException("OpenGL UI pipeline is closed");
        }
        host.assertRenderThread();
        Thread current = Thread.currentThread();
        if (renderThread == null) {
            renderThread = current;
        } else if (renderThread != current) {
            throw new IllegalStateException("OpenGL UI pipeline is confined to its first Render Thread");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        host.assertRenderThread();
        Thread current = Thread.currentThread();
        if (renderThread != null && renderThread != current) {
            throw new IllegalStateException("OpenGL UI pipeline must close on its Render Thread");
        }
        renderThread = current;
        closed = true;

        RuntimeException primary = null;
        try {
            drawer.close();
        } catch (RuntimeException exception) {
            primary = exception;
        }
        try {
            textTextures.close();
        } catch (RuntimeException exception) {
            primary = append(primary, exception);
        }
        try {
            imageTextures.close();
        } catch (RuntimeException exception) {
            primary = append(primary, exception);
        }
        clearPendingText();
        ready = null;
        activeImageRasters = null;
        activeTextRasters = null;
        if (primary != null) {
            throw primary;
        }
    }

    private static FrameDrawer productionDrawer() {
        NanoVgGl3Renderer renderer = new NanoVgGl3Renderer();
        return new FrameDrawer() {
            @Override
            public OpenGlRenderReport draw(
                    RenderHost host,
                    OpenGlFrameInfo frameInfo,
                    DisplayList displayList,
                    NanoVgRenderResources resources) {
                return renderer.render(host, frameInfo, displayList, resources);
            }

            @Override
            public void close() {
                renderer.close();
            }
        };
    }

    interface FrameDrawer extends AutoCloseable {
        OpenGlRenderReport draw(
                RenderHost host,
                OpenGlFrameInfo frameInfo,
                DisplayList displayList,
                NanoVgRenderResources resources);

        @Override
        default void close() {
        }
    }

    private static final class SceneKey {
        private final UiViewport viewport;
        private final List<DisplayList> displayLists;
        private final long resourceGeneration;

        private SceneKey(
                UiViewport viewport,
                List<DisplayList> displayLists,
                long resourceGeneration) {
            this.viewport = viewport;
            this.displayLists = displayLists;
            this.resourceGeneration = resourceGeneration;
        }

        private boolean sameRasterInputs(
                UiViewport nextViewport,
                List<UiSceneFrame> frames,
                long nextResourceGeneration) {
            if (Float.compare(
                            viewport.devicePixelRatio(),
                            nextViewport.devicePixelRatio()) != 0
                    || resourceGeneration != nextResourceGeneration
                    || displayLists.size() != frames.size()) {
                return false;
            }
            for (int index = 0; index < displayLists.size(); index++) {
                if (displayLists.get(index) != frames.get(index).displayList()) {
                    return false;
                }
            }
            return true;
        }

        private SceneKey retarget(UiViewport nextViewport) {
            return new SceneKey(nextViewport, displayLists, resourceGeneration);
        }
    }

    private record ReadyScene(
            SceneKey key,
            OpenGlFrameInfo frameInfo,
            DisplayList displayList,
            ResolvedImages images,
            ResolvedTexts texts,
            NanoVgRenderResources resources) {
        private static ReadyScene create(
                SceneKey key,
                OpenGlFrameInfo frameInfo,
                DisplayList displayList,
                ResolvedImages images,
                ResolvedTexts texts,
                ImageTextureStore imageTextures,
                TextTextureStore textTextures) {
            NanoVgRenderResources resources = new NanoVgRenderResources() {
                @Override
                public Image resolveImage(ImageRef image, ImageSampling sampling) {
                    ImageRaster raster = images.byImage.get(image);
                    if (raster == null) {
                        throw new OpenGlRenderException("No resolved raster for " + image.key());
                    }
                    OpenGlSampling openGlSampling = sampling == ImageSampling.NEAREST
                            ? OpenGlSampling.NEAREST
                            : OpenGlSampling.LINEAR;
                    OpenGlTexture texture = imageTextures.resolve(raster.textureKey(), openGlSampling)
                            .orElseThrow(() -> new OpenGlRenderException(
                                    "Active FandUI image texture is unavailable: "
                                            + Long.toUnsignedString(raster.textureKey())));
                    return new Image(raster.textureKey(), texture, raster.width(), raster.height());
                }

                @Override
                public Text resolveText(TextLayout layout) {
                    TextRaster raster = texts.byLayout.get(layout);
                    if (raster == null) {
                        throw new OpenGlRenderException("No resolved raster for TextLayout");
                    }
                    Optional<OpenGlTexture> texture = raster.byteSize() == 0
                            ? Optional.empty()
                            : textTextures.resolve(raster.textureKey(), OpenGlSampling.LINEAR);
                    return new Text(raster, texture);
                }
            };
            return new ReadyScene(key, frameInfo, displayList, images, texts, resources);
        }

        private ReadyScene retarget(UiViewport nextViewport) {
            SceneKey nextKey = key.retarget(nextViewport);
            return new ReadyScene(
                    nextKey,
                    new OpenGlFrameInfo(
                            nextKey.viewport.logicalWidth(),
                            nextKey.viewport.logicalHeight(),
                            nextKey.viewport.devicePixelRatio()),
                    displayList,
                    images,
                    texts,
                    resources);
        }
    }

    private static final class PendingTextScene {
        private SceneKey key;
        private final List<TextLayout> layoutOrder;
        private final IdentityHashMap<TextLayout, CompletableFuture<TextRaster>> futures;

        private PendingTextScene(
                SceneKey key,
                List<TextLayout> layoutOrder,
                IdentityHashMap<TextLayout, CompletableFuture<TextRaster>> futures) {
            this.key = key;
            this.layoutOrder = layoutOrder;
            this.futures = futures;
        }

        private static PendingTextScene start(SceneKey key, TextRasterizer rasterizer) {
            IdentityHashMap<TextLayout, CompletableFuture<TextRaster>> futures = new IdentityHashMap<>();
            List<TextLayout> order = new ArrayList<>();
            try {
                for (DisplayList displayList : key.displayLists) {
                    for (DisplayCommand command : displayList.commands()) {
                        if (command instanceof DisplayCommand.DrawText text
                                && !futures.containsKey(text.text())) {
                            CompletableFuture<TextRaster> future = Objects.requireNonNull(
                                    rasterizer.raster(text.text(), key.viewport.devicePixelRatio()),
                                    "textRasterizer.raster()");
                            futures.put(text.text(), future);
                            order.add(text.text());
                        }
                    }
                }
            } catch (RuntimeException | Error failure) {
                futures.values().forEach(future -> future.cancel(false));
                throw failure;
            }
            return new PendingTextScene(key, List.copyOf(order), futures);
        }

        private boolean complete() {
            for (CompletableFuture<TextRaster> future : futures.values()) {
                if (!future.isDone()) {
                    return false;
                }
            }
            return true;
        }

        private void retarget(UiViewport viewport) {
            key = key.retarget(viewport);
        }

        private ResolvedTexts finish() {
            IdentityHashMap<TextLayout, TextRaster> byLayout = new IdentityHashMap<>();
            List<TextRaster> rasters = new ArrayList<>();
            for (TextLayout layout : layoutOrder) {
                TextRaster raster = Objects.requireNonNull(
                        futures.get(layout).join(),
                        "text raster result");
                if (raster.resourceGeneration() != layout.resourceGeneration()) {
                    throw new IllegalStateException("Text raster generation does not match its layout");
                }
                byLayout.put(layout, raster);
                if (raster.byteSize() != 0) {
                    rasters.add(raster);
                }
            }
            return new ResolvedTexts(byLayout, List.copyOf(rasters));
        }

        private void cancel() {
            futures.values().forEach(future -> future.cancel(false));
        }
    }

    private record ResolvedImages(
            IdentityHashMap<ImageRef, ImageRaster> byImage,
            List<ImageRaster> rasters) {
    }

    private record ResolvedTexts(
            IdentityHashMap<TextLayout, TextRaster> byLayout,
            List<TextRaster> rasters) {
    }

    private void clearPendingText() {
        PendingTextScene pending = pendingText;
        pendingText = null;
        if (pending != null) {
            pending.cancel();
        }
    }

    private void activateTextures(
            List<ImageRaster> images,
            List<TextRaster> texts) {
        if (activeImageRasters != images) {
            imageTextures.activate(images);
            activeImageRasters = images;
        }
        if (activeTextRasters != texts) {
            textTextures.activate(texts);
            activeTextRasters = texts;
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void requireDistinctTextureKeys(
            List<ImageRaster> images,
            List<TextRaster> texts) {
        java.util.HashSet<Long> imageKeys = new java.util.HashSet<>();
        images.forEach(image -> imageKeys.add(image.textureKey()));
        for (TextRaster text : texts) {
            if (imageKeys.contains(text.textureKey())) {
                throw new OpenGlRenderException(
                        "Image and text rasters use the same texture key: "
                                + Long.toUnsignedString(text.textureKey()));
            }
        }
    }

    private static RuntimeException append(RuntimeException primary, RuntimeException additional) {
        if (primary == null) {
            return additional;
        }
        primary.addSuppressed(additional);
        return primary;
    }
}
