package cn.fandmc.fandui.text;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.text.FontFamily;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextLine;
import cn.fandmc.fandui.api.text.TextGeometry;
import cn.fandmc.fandui.api.text.TextDirection;
import cn.fandmc.fandui.api.text.TextOverflow;
import cn.fandmc.fandui.api.text.TextPosition;
import cn.fandmc.fandui.api.text.TextRange;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.api.text.TextWrap;
import cn.fandmc.fandui.api.resource.ResourceSource;
import cn.fandmc.fandui.core.resource.CoreResourceService;
import cn.fandmc.fandui.core.resource.ResourceLookup;
import cn.fandmc.fandui.core.resource.ResourceReloadException;
import cn.fandmc.fandui.core.runtime.UiThreadDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SkijaTextServiceTest {
    private final AtomicLong generation = new AtomicLong();
    private SkijaTextService service;

    @BeforeAll
    void startService() {
        service = new SkijaTextService(generation::get);
    }

    @AfterAll
    void stopService() {
        service.close();
    }

    @Test
    void laysOutBundledChineseEnglishAndEmojiWithUtf16Metrics() {
        String text = "FandUI 中文 English 😀 👩‍💻 ❤️ 🏳️‍🌈";
        TextStyle style = TextStyle.builder(26.0f)
                .families(List.of(new FontFamily(UiKey.of("missing", "preferred"))))
                .locale("zh-CN")
                .build();
        TextRequest request = TextRequest.builder(text, style)
                .maxWidth(210.0f)
                .build();

        TextLayout layout = service.layout(request).join();

        assertSame(request, layout.request());
        assertEquals(generation.get(), layout.resourceGeneration());
        assertEquals(0, layout.unresolvedGlyphs());
        assertTrue(layout.size().width() > 0.0f);
        assertTrue(layout.size().height() > 0.0f);
        assertTrue(layout.alphabeticBaseline() > 0.0f);
        assertTrue(layout.ideographicBaseline() > 0.0f);
        assertTrue(layout.lines().size() >= 2);
        assertEquals(0, layout.lines().get(0).startUtf16());
        assertEquals(text.length(), layout.lines().get(layout.lines().size() - 1).endUtf16());
        for (TextLine line : layout.lines()) {
            assertValidUtf16Boundary(text, line.startUtf16());
            assertValidUtf16Boundary(text, line.endUtf16());
            assertTrue(line.width() >= 0.0f);
            assertTrue(line.height() > 0.0f);
            assertTrue(line.baseline() > 0.0f);
        }
        assertThrows(UnsupportedOperationException.class, () -> layout.lines().clear());
    }

    @Test
    void rasterizesOrdinaryTextToA8AtQuantizedDeviceScale() {
        Color color = new Color(0.2f, 0.65f, 0.4f, 0.6f);
        TextRequest request = TextRequest.builder(
                        "FandUI 普通文字",
                        TextStyle.builder(24.0f).color(color).build())
                .maxWidth(300.0f)
                .build();
        TextLayout layout = service.layout(request).join();

        TextRaster first = service.raster(layout, 1.249f).join();
        TextRaster sameBucket = service.raster(layout, 1.251f).join();
        TextRaster nextBucket = service.raster(layout, 1.27f).join();

        assertEquals(TextPixelFormat.ALPHA_8, first.format());
        assertEquals(1.25f, first.deviceScale());
        assertSame(first, sameBucket);
        assertNotEquals(first.textureKey(), nextBucket.textureKey());
        assertEquals(first.width(), first.rowBytes());
        assertEquals(first.rowBytes() * first.height(), first.byteSize());
        assertEquals(color.red(), first.modulationColor().red());
        assertEquals(color.green(), first.modulationColor().green());
        assertEquals(color.blue(), first.modulationColor().blue());
        assertEquals(1.0f, first.modulationColor().alpha());
        assertTrue(first.originOffsetX() < 0.0f);
        assertTrue(first.originOffsetY() < 0.0f);
        assertTrue(hasNonZeroByte(first.pixels()));
        assertThrows(ReadOnlyBufferException.class, () -> first.pixels().put(0, (byte) 1));
        assertArrayEquals(first.cacheKeySha256(), sameBucket.cacheKeySha256());

        TextRequest recoloredRequest = TextRequest.builder(
                        request.text(),
                        TextStyle.builder(24.0f)
                                .color(new Color(0.8f, 0.1f, 0.7f, color.alpha()))
                                .build())
                .maxWidth(300.0f)
                .build();
        TextRaster recolored = service.raster(service.layout(recoloredRequest).join(), 1.25f).join();
        assertEquals(TextPixelFormat.ALPHA_8, recolored.format());
        assertEquals(first.textureKey(), recolored.textureKey());
        assertArrayEquals(toBytes(first.pixels()), toBytes(recolored.pixels()));
        assertNotEquals(first.modulationColor(), recolored.modulationColor());
    }

    @Test
    void keepsMixedColorEmojiAsPremultipliedRgba() {
        Color textColor = new Color(0.15f, 0.7f, 0.35f, 0.8f);
        TextRequest request = TextRequest.builder(
                        "中文 Text 😀 👩‍💻 🏳️‍🌈",
                        TextStyle.builder(32.0f).color(textColor).build())
                .maxWidth(420.0f)
                .build();
        TextRaster raster = service.raster(service.layout(request).join(), 1.0f).join();

        assertEquals(TextPixelFormat.RGBA_8888_PREMULTIPLIED, raster.format());
        assertEquals(raster.width() * 4, raster.rowBytes());
        assertEquals(Color.rgb(0xFFFFFF), raster.modulationColor());
        ByteBuffer pixels = raster.pixels();
        int visible = 0;
        int palettePixels = 0;
        for (int offset = 0; offset < pixels.remaining(); offset += 4) {
            int red = Byte.toUnsignedInt(pixels.get(offset));
            int green = Byte.toUnsignedInt(pixels.get(offset + 1));
            int blue = Byte.toUnsignedInt(pixels.get(offset + 2));
            int alpha = Byte.toUnsignedInt(pixels.get(offset + 3));
            assertTrue(red <= alpha && green <= alpha && blue <= alpha);
            if (alpha != 0) {
                visible++;
                int expectedRed = Math.round(textColor.red() * alpha);
                int expectedGreen = Math.round(textColor.green() * alpha);
                int expectedBlue = Math.round(textColor.blue() * alpha);
                if (Math.abs(red - expectedRed) > 1
                        || Math.abs(green - expectedGreen) > 1
                        || Math.abs(blue - expectedBlue) > 1) {
                    palettePixels++;
                }
            }
        }
        assertTrue(visible > 0);
        assertTrue(palettePixels > 0);
    }

    @Test
    void deduplicatesSharedWorkWithoutSharingCallerFuturesOrRequests() {
        TextStyle style = TextStyle.builder(20.0f).build();
        TextRequest firstRequest = TextRequest.builder("并发 FandUI 😀", style).build();
        TextRequest secondRequest = TextRequest.builder("并发 FandUI 😀", style).build();

        CompletableFuture<TextLayout> cancelled = service.layout(firstRequest);
        List<CompletableFuture<TextLayout>> futures = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            futures.add(service.layout(index % 2 == 0 ? firstRequest : secondRequest));
        }
        cancelled.cancel(true);

        TextLayout first = futures.get(0).join();
        for (int index = 1; index < futures.size(); index++) {
            assertNotSame(futures.get(index - 1), futures.get(index));
        }
        for (int index = 0; index < futures.size(); index++) {
            TextLayout current = futures.get(index).join();
            assertEquals(first.size(), current.size());
            assertEquals(first.lines(), current.lines());
            assertSame(index % 2 == 0 ? firstRequest : secondRequest, current.request());
        }
    }

    @Test
    void resourceGenerationInvalidatesKeysButOldLayoutsRemainRasterable() {
        TextRequest request = TextRequest.builder(
                "generation 中文 😀",
                TextStyle.builder(22.0f).build()).build();
        TextLayout oldLayout = service.layout(request).join();
        TextRaster oldRaster = service.raster(oldLayout, 1.0f).join();

        long nextGeneration = generation.incrementAndGet();
        TextLayout newLayout = service.layout(request).join();
        TextRaster newRaster = service.raster(newLayout, 1.0f).join();
        TextRaster oldAgain = service.raster(oldLayout, 1.0f).join();

        assertEquals(nextGeneration - 1L, oldLayout.resourceGeneration());
        assertEquals(nextGeneration, newLayout.resourceGeneration());
        assertEquals(oldLayout.size(), newLayout.size());
        assertNotEquals(oldRaster.textureKey(), newRaster.textureKey());
        assertSame(oldRaster, oldAgain);
    }

    @Test
    void layoutRetainsImmutableSnapshotInsteadOfNativeFontEnvironment() {
        assertTrue(Arrays.stream(SkijaTextLayout.class.getDeclaredFields())
                .map(field -> field.getType().getPackageName())
                .noneMatch(packageName -> packageName.startsWith("io.github.humbleui.skija")));
    }

    @Test
    void honorsWrapMaxLinesAndEllipsis() {
        String text = "FandUI wraps long English words and 中文段落 across several lines";
        TextRequest request = TextRequest.builder(text, TextStyle.builder(18.0f).build())
                .maxWidth(105.0f)
                .maxLines(2)
                .wrap(TextWrap.WORD)
                .overflow(TextOverflow.ELLIPSIS)
                .build();

        TextLayout layout = service.layout(request).join();

        assertEquals(0, layout.unresolvedGlyphs());
        assertEquals(2, layout.lines().size());
        assertTrue(layout.lines().get(1).endUtf16() < text.length());
        assertTrue(layout.size().width() <= request.maxWidth() + 0.01f);
    }

    @Test
    void producesImmutableUtf16EditorGeometryOnTheTextWorker() {
        String text = "A😀中文B";
        TextLayout layout = service.layout(TextRequest.builder(
                        text,
                        TextStyle.builder(24.0f).build())
                .maxLines(1)
                .wrap(TextWrap.NONE)
                .build()).join();
        List<TextRange> ranges = List.of(
                new TextRange(0, 1),
                new TextRange(1, 3),
                new TextRange(3, 5),
                new TextRange(5, 6));

        TextGeometry geometry = service.geometry(
                layout,
                TextPosition.downstream(3),
                ranges).join();

        assertTrue(geometry.caretBounds().height() > 0.0f);
        assertEquals(ranges.size(), geometry.ranges().size());
        for (int index = 0; index < ranges.size(); index++) {
            assertEquals(ranges.get(index), geometry.ranges().get(index).range());
            assertFalse(geometry.ranges().get(index).bounds().isEmpty());
        }
        assertThrows(UnsupportedOperationException.class, () -> geometry.ranges().clear());
        assertThrows(UnsupportedOperationException.class, () -> geometry.ranges().get(0).bounds().clear());

        TextPosition farLeft = service.hitTest(layout, new Point(-100.0f, 1.0f)).join();
        TextPosition farRight = service.hitTest(
                layout,
                new Point(layout.size().width() + 100.0f, 1.0f)).join();
        assertEquals(0, farLeft.offsetUtf16());
        assertEquals(text.length(), farRight.offsetUtf16());
        TextPosition emojiHit = service.hitTest(
                layout,
                new Point(
                        geometry.ranges().get(1).bounds().get(0).x()
                                + geometry.ranges().get(1).bounds().get(0).width() * 0.5f,
                        geometry.ranges().get(1).bounds().get(0).y()
                                + geometry.ranges().get(1).bounds().get(0).height() * 0.5f)).join();
        assertValidUtf16Boundary(text, emojiHit.offsetUtf16());
    }

    @Test
    void handlesEmptyAndBidirectionalEditorGeometry() {
        TextLayout empty = service.layout(TextRequest.builder(
                        "",
                        TextStyle.builder(18.0f).build())
                .maxLines(1)
                .wrap(TextWrap.NONE)
                .build()).join();
        TextGeometry emptyGeometry = service.geometry(
                empty,
                TextPosition.downstream(0),
                List.of()).join();
        assertEquals(0.0f, emptyGeometry.caretBounds().x());
        assertEquals(0, service.hitTest(empty, new Point(100.0f, 0.0f)).join().offsetUtf16());

        String bidiText = "אבג abc";
        TextLayout bidi = service.layout(TextRequest.builder(
                        bidiText,
                        TextStyle.builder(24.0f).build())
                .maxLines(1)
                .wrap(TextWrap.NONE)
                .direction(TextDirection.RIGHT_TO_LEFT)
                .build()).join();
        TextGeometry bidiGeometry = service.geometry(
                bidi,
                TextPosition.downstream(0),
                List.of(new TextRange(0, 3), new TextRange(4, 7))).join();
        Rect hebrew = bidiGeometry.ranges().get(0).bounds().get(0);
        Rect latin = bidiGeometry.ranges().get(1).bounds().get(0);
        assertTrue(hebrew.x() > latin.x());

        TextPosition hebrewHit = service.hitTest(bidi, center(hebrew)).join();
        TextPosition latinHit = service.hitTest(bidi, center(latin)).join();
        assertTrue(hebrewHit.offsetUtf16() >= 0 && hebrewHit.offsetUtf16() <= 3);
        assertTrue(latinHit.offsetUtf16() >= 4 && latinHit.offsetUtf16() <= 7);
    }

    @Test
    void reportsBoundedCacheAndRejectsForeignLayoutsAndInvalidScales() {
        TextRequest request = TextRequest.builder("cache", TextStyle.builder(16.0f).build()).build();
        TextLayout layout = service.layout(request).join();
        service.raster(layout, 1.0f).join();

        SkijaTextEngine.CacheStats stats = service.cacheStats().join();
        assertTrue(stats.layouts() > 0);
        assertTrue(stats.rasters() > 0);
        assertTrue(stats.rasterBytes() <= SkijaTextEngine.DEFAULT_RASTER_CACHE_BYTES);

        CompletionException zeroScale = assertThrows(
                CompletionException.class,
                () -> service.raster(layout, 0.0f).join());
        assertInstanceOf(IllegalArgumentException.class, zeroScale.getCause());
        CompletionException hugeScale = assertThrows(
                CompletionException.class,
                () -> service.raster(layout, 100.0f).join());
        assertInstanceOf(IllegalArgumentException.class, hugeScale.getCause());

        ForeignLayout foreignLayout = new ForeignLayout(request);
        CompletionException foreign = assertThrows(
                CompletionException.class,
                () -> service.raster(foreignLayout, 1.0f).join());
        assertInstanceOf(IllegalArgumentException.class, foreign.getCause());
        CompletionException foreignHit = assertThrows(
                CompletionException.class,
                () -> service.hitTest(foreignLayout, new Point(0.0f, 0.0f)).join());
        assertInstanceOf(IllegalArgumentException.class, foreignHit.getCause());
        CompletionException foreignGeometry = assertThrows(
                CompletionException.class,
                () -> service.geometry(foreignLayout, TextPosition.downstream(0), List.of()).join());
        assertInstanceOf(IllegalArgumentException.class, foreignGeometry.getCause());
    }

    @Test
    void boundedCachesEvictAndCloseRejectsNewWork() {
        SkijaTextService local = new SkijaTextService(() -> 0L, 2, 1_024L);
        for (int index = 0; index < 4; index++) {
            TextLayout layout = local.layout(TextRequest.builder(
                    "cache entry " + index,
                    TextStyle.builder(16.0f).build()).build()).join();
            local.raster(layout, 1.0f).join();
        }
        SkijaTextEngine.CacheStats stats = local.cacheStats().join();
        assertTrue(stats.layouts() <= 2);
        assertTrue(stats.rasterBytes() <= 1_024L);

        local.close();
        local.close();

        CompletableFuture<TextLayout> rejected = local.layout(
                TextRequest.builder("after", TextStyle.builder(16.0f).build()).build());
        CompletionException exception = assertThrows(CompletionException.class, rejected::join);
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @Test
    void customFontsParticipateInLayoutAndOldLayoutsSurviveEnvironmentEviction() throws IOException {
        CoreResourceService resources = new CoreResourceService(new DirectDispatcher());
        SkijaTextService local = new SkijaTextService(resources::fontSnapshot);
        resources.setFontValidator(local::validateFonts);
        UiKey key = UiKey.of("test", "fonts/custom-sans.otf");
        FontFamily family = resources.font(key);
        byte[] font = bundledSansBytes();
        AtomicLong sourceRevision = new AtomicLong();
        resources.registerFont(key, () -> Arrays.copyOf(font, font.length + (int) sourceRevision.get()));

        sourceRevision.set(1L);
        assertEquals(1L, resources.reload(ResourceLookup.empty()));
        TextRequest request = TextRequest.builder(
                "Custom font 中文",
                TextStyle.builder(20.0f).families(family).build()).build();
        TextLayout oldLayout = local.layout(request).join();
        assertEquals(0, oldLayout.unresolvedGlyphs());
        assertEquals(1L, oldLayout.resourceGeneration());

        for (int revision = 2; revision <= 6; revision++) {
            sourceRevision.set(revision);
            assertEquals(revision, resources.reload(ResourceLookup.empty()));
        }
        SkijaTextEngine.CacheStats beforeOldRaster = local.cacheStats().join();
        assertTrue(beforeOldRaster.fontEnvironments() <= 4);
        assertTrue(beforeOldRaster.customTypefaces() > 0);

        TextRaster oldRaster = local.raster(oldLayout, 1.0f).join();
        assertEquals(1L, oldRaster.resourceGeneration());
        assertTrue(oldRaster.byteSize() > 0);
        TextPosition oldHit = local.hitTest(oldLayout, new Point(1.0f, 1.0f)).join();
        assertValidUtf16Boundary(request.text(), oldHit.offsetUtf16());
        TextGeometry oldGeometry = local.geometry(
                oldLayout,
                TextPosition.downstream(0),
                List.of(new TextRange(0, 6))).join();
        assertTrue(oldGeometry.caretBounds().height() > 0.0f);
        assertFalse(oldGeometry.ranges().get(0).bounds().isEmpty());
        assertTrue(local.cacheStats().join().fontEnvironments() <= 4);

        local.close();
        resources.close();
    }

    @Test
    void invalidCustomFontRejectsTheWholeGenerationAndPreservesOldLayouts() throws IOException {
        CoreResourceService resources = new CoreResourceService(new DirectDispatcher());
        SkijaTextService local = new SkijaTextService(resources::fontSnapshot);
        resources.setFontValidator(local::validateFonts);
        UiKey key = UiKey.of("test", "fonts/reload.ttf");
        FontFamily family = resources.font(key);
        var valid = resources.registerFont(key, ResourceSource.bytes(bundledSansBytes()));
        assertEquals(1L, resources.reload(ResourceLookup.empty()));
        TextRequest request = TextRequest.builder(
                "stable 中文",
                TextStyle.builder(20.0f).families(family).build()).build();
        TextLayout oldLayout = local.layout(request).join();

        valid.close();
        resources.registerFont(
                key,
                ResourceSource.bytes(new byte[]{1, 2, 3, 4}));

        ResourceReloadException failure = assertThrows(
                ResourceReloadException.class,
                () -> resources.reload(ResourceLookup.empty()));

        assertEquals(1L, resources.generation());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(1L, oldLayout.resourceGeneration());
        assertTrue(local.raster(oldLayout, 1.0f).join().byteSize() > 0);
        assertValidUtf16Boundary(
                request.text(),
                local.hitTest(oldLayout, new Point(2.0f, 2.0f)).join().offsetUtf16());
        assertFalse(local.geometry(
                oldLayout,
                TextPosition.downstream(0),
                List.of(new TextRange(0, request.text().length())))
                .join()
                .ranges()
                .get(0)
                .bounds()
                .isEmpty());
        local.close();
        resources.close();
    }

    private static boolean hasNonZeroByte(ByteBuffer pixels) {
        for (int index = 0; index < pixels.remaining(); index++) {
            if (pixels.get(index) != 0) {
                return true;
            }
        }
        return false;
    }

    private static byte[] bundledSansBytes() throws IOException {
        try (InputStream input = SkijaTextServiceTest.class.getResourceAsStream(BundledFontCatalog.SANS_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing bundled test font");
            }
            return input.readAllBytes();
        }
    }

    private static final class DirectDispatcher implements UiThreadDispatcher {
        @Override
        public boolean isUiThread() {
            return true;
        }

        @Override
        public void execute(Runnable action) {
            action.run();
        }
    }

    private static byte[] toBytes(ByteBuffer pixels) {
        byte[] result = new byte[pixels.remaining()];
        pixels.get(result);
        return result;
    }

    private static void assertValidUtf16Boundary(String text, int offset) {
        assertTrue(offset >= 0 && offset <= text.length());
        assertFalse(offset > 0
                && offset < text.length()
                && Character.isHighSurrogate(text.charAt(offset - 1))
                && Character.isLowSurrogate(text.charAt(offset)));
    }

    private static Point center(Rect rect) {
        return new Point(rect.x() + rect.width() * 0.5f, rect.y() + rect.height() * 0.5f);
    }

    private record ForeignLayout(TextRequest request) implements TextLayout {
        @Override
        public long resourceGeneration() {
            return 0L;
        }

        @Override
        public Size size() {
            return new Size(1.0f, 1.0f);
        }

        @Override
        public float alphabeticBaseline() {
            return 1.0f;
        }

        @Override
        public float ideographicBaseline() {
            return 1.0f;
        }

        @Override
        public List<TextLine> lines() {
            return List.of();
        }

        @Override
        public int unresolvedGlyphs() {
            return 0;
        }
    }
}
