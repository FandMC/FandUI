package cn.fandmc.fandui.text;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.text.FontFamilies;
import cn.fandmc.fandui.api.text.FontSlant;
import cn.fandmc.fandui.api.text.TextAlignment;
import cn.fandmc.fandui.api.text.TextDirection;
import cn.fandmc.fandui.api.text.TextAffinity;
import cn.fandmc.fandui.api.text.TextGeometry;
import cn.fandmc.fandui.api.text.TextLine;
import cn.fandmc.fandui.api.text.TextOverflow;
import cn.fandmc.fandui.api.text.TextPosition;
import cn.fandmc.fandui.api.text.TextRange;
import cn.fandmc.fandui.api.text.TextRangeGeometry;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextWrap;
import cn.fandmc.fandui.core.resource.FontResourceSnapshot;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.FontWidth;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.paragraph.Alignment;
import io.github.humbleui.skija.paragraph.Direction;
import io.github.humbleui.skija.paragraph.FontCollection;
import io.github.humbleui.skija.paragraph.LineMetrics;
import io.github.humbleui.skija.paragraph.Paragraph;
import io.github.humbleui.skija.paragraph.ParagraphBuilder;
import io.github.humbleui.skija.paragraph.ParagraphStyle;
import io.github.humbleui.skija.paragraph.PositionWithAffinity;
import io.github.humbleui.skija.paragraph.RectHeightMode;
import io.github.humbleui.skija.paragraph.RectWidthMode;
import io.github.humbleui.skija.paragraph.TextBox;
import io.github.humbleui.skija.paragraph.TypefaceFontProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class SkijaTextEngine implements AutoCloseable {
    static final int DEFAULT_LAYOUT_CACHE_ENTRIES = 512;
    static final long DEFAULT_RASTER_CACHE_BYTES = 64L * 1024L * 1024L;

    private static final float MAX_UNBOUNDED_LAYOUT_WIDTH = 1_048_576.0f;
    private static final long MAX_RASTER_SURFACE_BYTES = 64L * 1024L * 1024L;
    private static final int PIXEL_PADDING = 1;
    private static final int MAX_FONT_ENVIRONMENTS = 4;

    private final Thread ownerThread = Thread.currentThread();
    private final int layoutCacheLimit;
    private final long rasterCacheByteLimit;
    private final FontMgr fontManager;
    private final Typeface sansTypeface;
    private final Typeface emojiTypeface;
    /** Serializes environment lookup, lease acquisition, LRU retirement, and cache inspection. */
    private final Object fontEnvironmentLock = new Object();
    private final LinkedHashMap<String, FontEnvironment> fontEnvironments =
            new LinkedHashMap<>(4, 0.75f, true);
    private final LinkedHashMap<TextCacheKey, LayoutMetrics> layoutCache =
            new LinkedHashMap<>(32, 0.75f, true);
    private final LinkedHashMap<RasterCacheKey, TextRaster> rasterCache =
            new LinkedHashMap<>(16, 0.75f, true);

    private long rasterCacheBytes;
    private boolean closed;

    SkijaTextEngine(int layoutCacheLimit, long rasterCacheByteLimit) {
        if (layoutCacheLimit < 1) {
            throw new IllegalArgumentException("layoutCacheLimit must be positive");
        }
        if (rasterCacheByteLimit < 1L) {
            throw new IllegalArgumentException("rasterCacheByteLimit must be positive");
        }
        this.layoutCacheLimit = layoutCacheLimit;
        this.rasterCacheByteLimit = rasterCacheByteLimit;

        FontMgr createdFontManager = null;
        Typeface createdSans = null;
        Typeface createdEmoji = null;
        try {
            createdFontManager = FontMgr.getDefault();
            createdSans = loadTypeface(
                    createdFontManager,
                    BundledFontCatalog.SANS_RESOURCE,
                    BundledFontCatalog.SANS_SHA256,
                    BundledFontCatalog.SANS_ALIAS);
            createdEmoji = loadTypeface(
                    createdFontManager,
                    BundledFontCatalog.EMOJI_RESOURCE,
                    BundledFontCatalog.EMOJI_SHA256,
                    BundledFontCatalog.EMOJI_ALIAS);
            requireGlyph(createdSans, 'A', BundledFontCatalog.SANS_ALIAS);
            requireGlyph(createdSans, 0x4E2D, BundledFontCatalog.SANS_ALIAS);
            requireGlyph(createdEmoji, 0x1F600, BundledFontCatalog.EMOJI_ALIAS);

        } catch (RuntimeException | Error exception) {
            closeQuietly(createdEmoji);
            closeQuietly(createdSans);
            closeQuietly(createdFontManager);
            throw new IllegalStateException("Failed to initialize bundled Skija fonts", exception);
        }
        fontManager = createdFontManager;
        sansTypeface = createdSans;
        emojiTypeface = createdEmoji;
        try {
            FontResourceSnapshot empty = FontResourceSnapshot.empty(0L);
            fontEnvironments.put(
                    empty.contentFingerprint(),
                    FontEnvironment.create(fontManager, sansTypeface, emojiTypeface, empty.copyFonts()));
        } catch (RuntimeException | Error exception) {
            closeQuietly(emojiTypeface);
            closeQuietly(sansTypeface);
            closeQuietly(fontManager);
            throw new IllegalStateException("Failed to initialize bundled Skija font environment", exception);
        }
    }

    LayoutMetrics layout(
            TextCacheKey key,
            TextRequest request,
            FontResourceSnapshot resources) {
        assertUsable();
        LayoutMetrics cached = layoutCache.get(key);
        if (cached != null) {
            return cached;
        }

        LayoutMetrics metrics = withEnvironment(resources, environment -> {
            try (Paragraph paragraph = buildAndLayout(request, environment)) {
                return extractMetrics(request, paragraph);
            }
        });
        layoutCache.put(key, metrics);
        trimLayoutCache();
        return metrics;
    }

    TextRaster raster(SkijaTextLayout layout, int scaleUnits) {
        assertUsable();
        RasterCacheKey rasterKey = new RasterCacheKey(layout.cacheKey(), scaleUnits);
        TextRaster cached = rasterCache.get(rasterKey);
        if (cached != null) {
            return cached;
        }

        TextRaster raster = rasterize(layout, scaleUnits);
        if (raster.byteSize() <= rasterCacheByteLimit) {
            TextRaster replaced = rasterCache.put(rasterKey, raster);
            if (replaced != null) {
                rasterCacheBytes -= replaced.byteSize();
            }
            rasterCacheBytes += raster.byteSize();
            trimRasterCache();
        }
        return raster;
    }

    TextPosition hitTest(SkijaTextLayout layout, Point position) {
        assertUsable();
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(position, "position");
        return withEnvironment(layout.fontResources(), environment -> {
            try (Paragraph paragraph = buildExistingLayout(layout, environment)) {
                PositionWithAffinity result = paragraph.getGlyphPositionAtCoordinate(
                        position.x() + layout.metrics().paintLeft(),
                        position.y());
                int offset = checkedUtf16Index(
                        result.getPosition(),
                        layout.request().text(),
                        "hit-test position");
                TextAffinity affinity = switch (result.getAffinity()) {
                    case UPSTREAM -> TextAffinity.UPSTREAM;
                    case DOWNSTREAM -> TextAffinity.DOWNSTREAM;
                };
                return new TextPosition(offset, affinity);
            }
        });
    }

    TextGeometry geometry(
            SkijaTextLayout layout,
            TextPosition caret,
            List<TextRange> ranges) {
        assertUsable();
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(caret, "caret");
        List<TextRange> checkedRanges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
        String text = layout.request().text();
        checkedUtf16Index(caret.offsetUtf16(), text, "caret position");
        for (TextRange range : checkedRanges) {
            checkedUtf16Index(range.startUtf16(), text, "range start");
            checkedUtf16Index(range.endUtf16(), text, "range end");
        }

        return withEnvironment(layout.fontResources(), environment -> {
            try (Paragraph paragraph = buildExistingLayout(layout, environment)) {
                Rect caretBounds = caretBounds(paragraph, layout.metrics(), text, caret);
                List<TextRangeGeometry> resultRanges = new ArrayList<>(checkedRanges.size());
                for (TextRange range : checkedRanges) {
                    resultRanges.add(new TextRangeGeometry(
                            range,
                            rangeBounds(paragraph, layout.metrics(), range)));
                }
                return new TextGeometry(caretBounds, resultRanges);
            }
        });
    }

    CacheStats cacheStats() {
        assertUsable();
        synchronized (fontEnvironmentLock) {
            int customTypefaces = fontEnvironments.values().stream()
                    .mapToInt(environment -> environment.customTypefaces.size())
                    .sum();
            return new CacheStats(
                    layoutCache.size(),
                    rasterCache.size(),
                    rasterCacheBytes,
                    fontEnvironments.size(),
                    customTypefaces);
        }
    }

    void validateFonts(FontResourceSnapshot resources) {
        assertUsable();
        withEnvironment(
                Objects.requireNonNull(resources, "resources"),
                environment -> null);
    }

    private LayoutMetrics extractMetrics(TextRequest request, Paragraph paragraph) {
        LineMetrics[] nativeLines = paragraph.getLineMetrics();
        List<TextLine> lines = new ArrayList<>(nativeLines.length);
        double minimumLeft = Double.POSITIVE_INFINITY;
        double maximumRight = Double.NEGATIVE_INFINITY;
        for (LineMetrics line : nativeLines) {
            int start = checkedUtf16Index(line.getStartIndex(), request.text(), "line start");
            int end = checkedUtf16Index(line.getEndIndex(), request.text(), "line end");
            if (end < start) {
                throw new IllegalStateException("Skija returned a reversed line range");
            }
            float width = finiteNonNegative(line.getWidth(), "line width");
            float height = finiteNonNegative(line.getLineHeight(), "line height");
            float baseline = finiteNonNegative(line.getBaseline(), "line baseline");
            lines.add(new TextLine(start, end, width, height, baseline));
            minimumLeft = Math.min(minimumLeft, line.getLeft());
            maximumRight = Math.max(maximumRight, line.getRight());
        }

        float paintLeft = nativeLines.length == 0 ? 0.0f : finite(minimumLeft, "line left");
        float width = nativeLines.length == 0
                ? 0.0f
                : finiteNonNegative(maximumRight - minimumLeft, "text width");
        if (request.wrap() == TextWrap.NONE && Float.isFinite(request.maxWidth())) {
            width = Math.min(width, request.maxWidth());
        }
        float height = finiteNonNegative(paragraph.getHeight(), "paragraph height");
        int unresolvedGlyphs = paragraph.getUnresolvedGlyphsCount();
        if (request.text().isEmpty() && unresolvedGlyphs < 0) {
            unresolvedGlyphs = 0;
        }
        return new LayoutMetrics(
                new Size(width, height),
                finiteNonNegative(paragraph.getAlphabeticBaseline(), "alphabetic baseline"),
                finiteNonNegative(paragraph.getIdeographicBaseline(), "ideographic baseline"),
                lines,
                unresolvedGlyphs,
                finiteNonNegative(paragraph.getMaxWidth(), "paragraph width"),
                paintLeft);
    }

    private Paragraph buildAndLayout(TextRequest request, FontEnvironment environment) {
        boolean unconstrained = request.wrap() == TextWrap.NONE || !Float.isFinite(request.maxWidth());
        Alignment requestedAlignment = alignment(request.alignment());
        Paragraph paragraph = buildParagraph(
                request,
                unconstrained ? Alignment.START : requestedAlignment,
                environment);
        try {
            if (!unconstrained) {
                paragraph.layout(request.maxWidth());
                return paragraph;
            }

            paragraph.layout(MAX_UNBOUNDED_LAYOUT_WIDTH);
            float longestLine = request.text().isEmpty()
                    ? Math.max(0.0f, finite(paragraph.getLongestLine(), "longest line"))
                    : finiteNonNegative(paragraph.getLongestLine(), "longest line");
            float desiredWidth = Math.max(
                    finiteNonNegative(paragraph.getMaxIntrinsicWidth(), "maximum intrinsic width"),
                    longestLine);
            if (desiredWidth >= MAX_UNBOUNDED_LAYOUT_WIDTH - 1.0f) {
                throw new IllegalArgumentException(
                        "Unconstrained text exceeds the supported logical width");
            }
            paragraph.updateAlignment(requestedAlignment);
            // SkParagraph can wrap the final cluster at the exact intrinsic-width boundary.
            paragraph.layout(Math.nextUp(desiredWidth + 1.0f));
            return paragraph;
        } catch (RuntimeException | Error exception) {
            paragraph.close();
            throw exception;
        }
    }

    private Paragraph buildExistingLayout(SkijaTextLayout layout, FontEnvironment environment) {
        Paragraph paragraph = buildParagraph(
                layout.request(),
                alignment(layout.request().alignment()),
                environment);
        try {
            paragraph.layout(layout.metrics().paragraphWidth());
            return paragraph;
        } catch (RuntimeException | Error exception) {
            paragraph.close();
            throw exception;
        }
    }

    private static Rect caretBounds(
            Paragraph paragraph,
            LayoutMetrics metrics,
            String text,
            TextPosition caret) {
        int offset = caret.offsetUtf16();
        boolean preferPrevious = caret.affinity() == TextAffinity.UPSTREAM;
        Rect result = preferPrevious
                ? caretFromPrevious(paragraph, metrics, text, offset)
                : caretFromNext(paragraph, metrics, text, offset);
        if (result == null) {
            result = preferPrevious
                    ? caretFromNext(paragraph, metrics, text, offset)
                    : caretFromPrevious(paragraph, metrics, text, offset);
        }
        if (result != null) {
            return result;
        }
        return new Rect(
                metrics.paintLeft() == 0.0f ? 0.0f : -metrics.paintLeft(),
                0.0f,
                0.0f,
                Math.max(metrics.size().height(), 0.0f));
    }

    private static Rect caretFromNext(
            Paragraph paragraph,
            LayoutMetrics metrics,
            String text,
            int offset) {
        if (offset >= text.length()) {
            return null;
        }
        int end = offset + Character.charCount(text.codePointAt(offset));
        TextBox[] boxes = paragraph.getRectsForRange(
                offset,
                end,
                RectHeightMode.MAX,
                RectWidthMode.TIGHT);
        return boxes.length == 0 ? null : caretFromBox(boxes[0], metrics.paintLeft(), true);
    }

    private static Rect caretFromPrevious(
            Paragraph paragraph,
            LayoutMetrics metrics,
            String text,
            int offset) {
        if (offset <= 0) {
            return null;
        }
        int start = offset - Character.charCount(text.codePointBefore(offset));
        TextBox[] boxes = paragraph.getRectsForRange(
                start,
                offset,
                RectHeightMode.MAX,
                RectWidthMode.TIGHT);
        return boxes.length == 0
                ? null
                : caretFromBox(boxes[boxes.length - 1], metrics.paintLeft(), false);
    }

    private static Rect caretFromBox(TextBox box, float paintLeft, boolean atStart) {
        io.github.humbleui.types.Rect nativeBounds = box.getRect();
        boolean leftToRight = box.getDirection() == Direction.LTR;
        float x = atStart == leftToRight ? nativeBounds.getLeft() : nativeBounds.getRight();
        float top = finite(nativeBounds.getTop(), "caret top");
        float bottom = finite(nativeBounds.getBottom(), "caret bottom");
        if (bottom < top) {
            throw new IllegalStateException("Skija returned reversed caret bounds");
        }
        return new Rect(
                finite(x - paintLeft, "caret x"),
                top,
                0.0f,
                finiteNonNegative(bottom - top, "caret height"));
    }

    private static List<Rect> rangeBounds(
            Paragraph paragraph,
            LayoutMetrics metrics,
            TextRange range) {
        if (range.collapsed()) {
            return List.of();
        }
        TextBox[] boxes = paragraph.getRectsForRange(
                range.startUtf16(),
                range.endUtf16(),
                RectHeightMode.MAX,
                RectWidthMode.TIGHT);
        List<Rect> bounds = new ArrayList<>(boxes.length);
        for (TextBox box : boxes) {
            io.github.humbleui.types.Rect nativeBounds = box.getRect();
            float left = finite(nativeBounds.getLeft() - metrics.paintLeft(), "range left");
            float top = finite(nativeBounds.getTop(), "range top");
            float right = finite(nativeBounds.getRight() - metrics.paintLeft(), "range right");
            float bottom = finite(nativeBounds.getBottom(), "range bottom");
            if (right < left || bottom < top) {
                throw new IllegalStateException("Skija returned reversed range bounds");
            }
            bounds.add(new Rect(
                    left,
                    top,
                    finiteNonNegative(right - left, "range width"),
                    finiteNonNegative(bottom - top, "range height")));
        }
        return List.copyOf(bounds);
    }

    private Paragraph buildParagraph(
            TextRequest request,
            Alignment paragraphAlignment,
            FontEnvironment environment) {
        return buildParagraph(request, paragraphAlignment, request.style().color(), environment);
    }

    private Paragraph buildParagraph(
            TextRequest request,
            Alignment paragraphAlignment,
            Color paintColor,
            FontEnvironment environment) {
        var requestStyle = request.style();
        try (io.github.humbleui.skija.paragraph.TextStyle textStyle =
                     new io.github.humbleui.skija.paragraph.TextStyle();
             ParagraphStyle paragraphStyle = new ParagraphStyle()) {
            textStyle
                    .setFontFamilies(fontFamilies(request))
                    .setFontSize(requestStyle.fontSize())
                    .setFontStyle(new FontStyle(
                            requestStyle.weight().value(),
                            FontWidth.NORMAL,
                            slant(requestStyle.slant())))
                    .setColor(packedColor(paintColor))
                    .setLetterSpacing(requestStyle.letterSpacing())
                    .setWordSpacing(requestStyle.wordSpacing())
                    .setLocale(requestStyle.locale());
            if (requestStyle.lineHeight() > 0.0f) {
                textStyle.setHeight(requestStyle.lineHeight() / requestStyle.fontSize());
            }

            paragraphStyle
                    .setTextStyle(textStyle)
                    .setDirection(direction(request.direction(), request.text()))
                    .setAlignment(paragraphAlignment)
                    .setMaxLinesCount(request.maxLines());
            if (request.overflow() == TextOverflow.ELLIPSIS) {
                paragraphStyle.setEllipsis("\u2026");
            }

            try (ParagraphBuilder builder = new ParagraphBuilder(paragraphStyle, environment.collection)) {
                builder.pushStyle(textStyle);
                builder.addText(request.text());
                builder.popStyle();
                return builder.build();
            }
        }
    }

    private TextRaster rasterize(SkijaTextLayout layout, int scaleUnits) {
        return withEnvironment(layout.fontResources(), environment ->
                rasterize(layout, scaleUnits, environment));
    }

    private TextRaster rasterize(
            SkijaTextLayout layout,
            int scaleUnits,
            FontEnvironment environment) {
        float scale = TextRaster.scaleFromUnits(scaleUnits);
        LayoutMetrics metrics = layout.metrics();
        if (metrics.size().width() == 0.0f || metrics.size().height() == 0.0f) {
            return createRaster(
                    layout,
                    scaleUnits,
                    TextPixelFormat.ALPHA_8,
                    0,
                    0,
                    0.0f,
                    0.0f,
                    alphaModulation(layout.request().style().color()),
                    new byte[0]);
        }

        int width = pixelExtent(metrics.size().width(), scale, "width");
        int height = pixelExtent(metrics.size().height(), scale, "height");
        long surfaceBytes = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (surfaceBytes > MAX_RASTER_SURFACE_BYTES) {
            throw new IllegalArgumentException(
                    "Text raster exceeds the " + MAX_RASTER_SURFACE_BYTES + " byte surface limit");
        }

        ImageInfo imageInfo = new ImageInfo(
                width,
                height,
                ColorType.RGBA_8888,
                ColorAlphaType.PREMUL);
        Color requestedColor = layout.request().style().color();
        Color maskColor = new Color(1.0f, 1.0f, 1.0f, requestedColor.alpha());
        byte[] rgba = rasterizeRgba(layout, metrics, scale, imageInfo, maskColor, environment);
        if (isMonochrome(rgba, maskColor)) {
            byte[] alpha = new byte[Math.multiplyExact(width, height)];
            for (int pixel = 0; pixel < alpha.length; pixel++) {
                alpha[pixel] = rgba[pixel * 4 + 3];
            }
            return createRaster(
                    layout,
                    scaleUnits,
                    TextPixelFormat.ALPHA_8,
                    width,
                    height,
                    -PIXEL_PADDING / scale,
                    -PIXEL_PADDING / scale,
                    alphaModulation(requestedColor),
                    alpha);
        }

        if (!requestedColor.equals(maskColor)) {
            rgba = rasterizeRgba(layout, metrics, scale, imageInfo, requestedColor, environment);
        }
        return createRaster(
                layout,
                scaleUnits,
                TextPixelFormat.RGBA_8888_PREMULTIPLIED,
                width,
                height,
                -PIXEL_PADDING / scale,
                -PIXEL_PADDING / scale,
                Color.rgb(0xFFFFFF),
                rgba);
    }

    private byte[] rasterizeRgba(
            SkijaTextLayout layout,
            LayoutMetrics metrics,
            float scale,
            ImageInfo imageInfo,
            Color paintColor,
            FontEnvironment environment) {
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        try (Paragraph paragraph = buildParagraph(
                     layout.request(),
                     alignment(layout.request().alignment()),
                     paintColor,
                     environment);
             Surface surface = Surface.makeRaster(imageInfo);
             Pixmap pixmap = new Pixmap()) {
            paragraph.layout(metrics.paragraphWidth());
            surface.getCanvas().clear(0x00000000);
            surface.getCanvas().translate(PIXEL_PADDING, PIXEL_PADDING);
            surface.getCanvas().scale(scale, scale);
            paragraph.paint(surface.getCanvas(), -metrics.paintLeft(), 0.0f);
            if (!surface.peekPixels(pixmap)) {
                throw new IllegalStateException("Skija raster surface did not expose CPU pixels");
            }
            return copyRgbaPixels(pixmap, width, height);
        }
    }

    private TextRaster createRaster(
            SkijaTextLayout layout,
            int scaleUnits,
            TextPixelFormat format,
            int width,
            int height,
            float originOffsetX,
            float originOffsetY,
            Color modulationColor,
            byte[] pixels) {
        byte[] digest = layout.cacheKey().rasterDigest(scaleUnits, format);
        return new TextRaster(
                layout.resourceGeneration(),
                textureKey(digest),
                digest,
                format,
                width,
                height,
                Math.multiplyExact(width, format.bytesPerPixel()),
                TextRaster.scaleFromUnits(scaleUnits),
                originOffsetX,
                originOffsetY,
                modulationColor,
                pixels);
    }

    private static byte[] copyRgbaPixels(Pixmap pixmap, int width, int height) {
        int sourceRowBytes = pixmap.getRowBytes();
        int packedRowBytes = Math.multiplyExact(width, 4);
        if (sourceRowBytes < packedRowBytes) {
            throw new IllegalStateException("Skija returned a short RGBA row stride");
        }
        ByteBuffer source = pixmap.getBuffer();
        byte[] result = new byte[Math.multiplyExact(packedRowBytes, height)];
        for (int y = 0; y < height; y++) {
            int sourceOffset = Math.multiplyExact(y, sourceRowBytes);
            int targetOffset = Math.multiplyExact(y, packedRowBytes);
            for (int x = 0; x < packedRowBytes; x++) {
                result[targetOffset + x] = source.get(sourceOffset + x);
            }
        }
        return result;
    }

    private static boolean isMonochrome(byte[] rgba, Color requestedColor) {
        for (int offset = 0; offset < rgba.length; offset += 4) {
            int red = Byte.toUnsignedInt(rgba[offset]);
            int green = Byte.toUnsignedInt(rgba[offset + 1]);
            int blue = Byte.toUnsignedInt(rgba[offset + 2]);
            int alpha = Byte.toUnsignedInt(rgba[offset + 3]);
            if (red > alpha || green > alpha || blue > alpha) {
                throw new IllegalStateException("Skija returned non-premultiplied RGBA pixels");
            }
            if (!approximately(red, Math.round(requestedColor.red() * alpha))
                    || !approximately(green, Math.round(requestedColor.green() * alpha))
                    || !approximately(blue, Math.round(requestedColor.blue() * alpha))) {
                return false;
            }
        }
        return true;
    }

    private static boolean approximately(int actual, int expected) {
        return Math.abs(actual - expected) <= 1;
    }

    private static int pixelExtent(float logicalExtent, float scale, String name) {
        double scaled = Math.ceil(logicalExtent * scale);
        if (!Double.isFinite(scaled) || scaled > Integer.MAX_VALUE - 2.0) {
            throw new IllegalArgumentException("Text raster " + name + " is too large");
        }
        return Math.addExact((int) scaled, PIXEL_PADDING * 2);
    }

    private static Color alphaModulation(Color requestedColor) {
        return new Color(
                requestedColor.red(),
                requestedColor.green(),
                requestedColor.blue(),
                1.0f);
    }

    private static long textureKey(byte[] digest) {
        ByteBuffer bytes = ByteBuffer.wrap(digest);
        long first = bytes.getLong();
        if (first != 0L) {
            return first;
        }
        long second = bytes.getLong();
        return second == 0L ? 1L : second;
    }

    private static String[] fontFamilies(TextRequest request) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        request.style().families().forEach(family -> {
            if (family.equals(FontFamilies.DEFAULT)) {
                aliases.add(BundledFontCatalog.SANS_ALIAS);
                aliases.add(BundledFontCatalog.EMOJI_ALIAS);
            } else {
                aliases.add(family.key().toString());
            }
        });
        aliases.add(BundledFontCatalog.SANS_ALIAS);
        aliases.add(BundledFontCatalog.EMOJI_ALIAS);
        return aliases.toArray(String[]::new);
    }

    @SuppressWarnings("try")
    private <T> T withEnvironment(
            FontResourceSnapshot resources,
            EnvironmentWork<T> work) {
        EnvironmentLease lease = acquireEnvironment(resources);
        try {
            return work.run(lease.environment());
        } finally {
            try {
                lease.close();
            } finally {
                // A lease can defer an LRU close. Revisit the map after every native operation.
                trimFontEnvironments();
            }
        }
    }

    /**
     * Looks up (or creates) an environment and acquires its lease while holding the cache lock.
     * This closes the race where a future concurrent caller could retire an environment between
     * lookup and lease acquisition.
     */
    private EnvironmentLease acquireEnvironment(FontResourceSnapshot resources) {
        Objects.requireNonNull(resources, "resources");
        synchronized (fontEnvironmentLock) {
            String fingerprint = resources.contentFingerprint();
            FontEnvironment environment = fontEnvironments.get(fingerprint);
            if (environment != null && environment.isRetired()) {
                fontEnvironments.remove(fingerprint);
                environment = null;
            }

            if (environment == null) {
                environment = FontEnvironment.create(
                        fontManager,
                        sansTypeface,
                        emojiTypeface,
                        resources.copyFonts());
                fontEnvironments.put(fingerprint, environment);
            }
            FontEnvironment.Lease lease = environment.acquire();
            try {
                trimFontEnvironmentsLocked();
                return new EnvironmentLease(environment, lease);
            } catch (RuntimeException | Error exception) {
                try {
                    lease.close();
                } catch (RuntimeException | Error closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
                throw exception;
            }
        }
    }

    private void trimFontEnvironments() {
        synchronized (fontEnvironmentLock) {
            trimFontEnvironmentsLocked();
        }
    }

    private void trimFontEnvironmentsLocked() {
        while (fontEnvironments.size() > MAX_FONT_ENVIRONMENTS) {
            Map.Entry<String, FontEnvironment> candidate = null;
            for (Map.Entry<String, FontEnvironment> entry : fontEnvironments.entrySet()) {
                if (!entry.getValue().isActive()) {
                    candidate = entry;
                    break;
                }
            }
            if (candidate == null) {
                // Every resident environment is leased by an in-flight native operation. Keep
                // the temporary overage; the next lease release will revisit the LRU.
                return;
            }
            fontEnvironments.remove(candidate.getKey());
            candidate.getValue().requestClose();
        }
    }

    private static final class EnvironmentLease implements AutoCloseable {
        private final FontEnvironment environment;
        private final FontEnvironment.Lease lease;

        private EnvironmentLease(FontEnvironment environment, FontEnvironment.Lease lease) {
            this.environment = environment;
            this.lease = lease;
        }

        private FontEnvironment environment() {
            return environment;
        }

        @Override
        public void close() {
            lease.close();
        }
    }

    @FunctionalInterface
    private interface EnvironmentWork<T> {
        T run(FontEnvironment environment);
    }

    private static Alignment alignment(TextAlignment alignment) {
        return switch (alignment) {
            case START -> Alignment.START;
            case CENTER -> Alignment.CENTER;
            case END -> Alignment.END;
            case JUSTIFY -> Alignment.JUSTIFY;
        };
    }

    private static Direction direction(TextDirection direction, String text) {
        return switch (direction) {
            case LEFT_TO_RIGHT -> Direction.LTR;
            case RIGHT_TO_LEFT -> Direction.RTL;
            case AUTO -> new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
                    ? Direction.LTR
                    : Direction.RTL;
        };
    }

    private static io.github.humbleui.skija.FontSlant slant(FontSlant slant) {
        return switch (slant) {
            case UPRIGHT -> io.github.humbleui.skija.FontSlant.UPRIGHT;
            case ITALIC -> io.github.humbleui.skija.FontSlant.ITALIC;
            case OBLIQUE -> io.github.humbleui.skija.FontSlant.OBLIQUE;
        };
    }

    private static int packedColor(Color color) {
        return channel(color.alpha()) << 24
                | channel(color.red()) << 16
                | channel(color.green()) << 8
                | channel(color.blue());
    }

    private static int channel(float value) {
        return Math.round(value * 255.0f);
    }

    private static Typeface loadTypeface(
            FontMgr fontManager,
            String resource,
            String expectedSha256,
            String displayName) {
        byte[] bytes = readResource(resource);
        String actualHash = HexFormat.of().withUpperCase().formatHex(sha256(bytes));
        if (!MessageDigest.isEqual(
                actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expectedSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalStateException(
                    displayName + " hash mismatch: expected " + expectedSha256 + ", got " + actualHash);
        }
        try (Data data = Data.makeFromBytes(bytes)) {
            Typeface typeface = fontManager.makeFromData(data);
            if (typeface == null) {
                throw new IllegalStateException(displayName + " was not recognized as a font");
            }
            return typeface;
        }
    }

    private static byte[] readResource(String resource) {
        try (InputStream input = SkijaTextEngine.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled font resource: " + resource);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read bundled font resource: " + resource, exception);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireGlyph(Typeface typeface, int codePoint, String displayName) {
        if (typeface.getUTF32Glyph(codePoint) == 0) {
            throw new IllegalStateException(
                    displayName + " does not contain required glyph U+"
                            + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT));
        }
    }

    private static int checkedUtf16Index(long value, String text, String name) {
        int index;
        try {
            index = Math.toIntExact(value);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Skija " + name + " exceeds the Java string range", exception);
        }
        if (index < 0 || index > text.length()) {
            throw new IllegalStateException("Skija " + name + " is outside the Java UTF-16 range");
        }
        if (index > 0 && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index))) {
            throw new IllegalStateException("Skija " + name + " splits a UTF-16 surrogate pair");
        }
        return index;
    }

    private static float finiteNonNegative(double value, String name) {
        float result = finite(value, name);
        if (result < 0.0f) {
            throw new IllegalStateException("Skija returned a negative " + name);
        }
        return result;
    }

    private static float finite(double value, String name) {
        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) {
            throw new IllegalStateException("Skija returned an invalid " + name);
        }
        return (float) value;
    }

    private void trimLayoutCache() {
        while (layoutCache.size() > layoutCacheLimit) {
            Iterator<TextCacheKey> iterator = layoutCache.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private void trimRasterCache() {
        Iterator<Map.Entry<RasterCacheKey, TextRaster>> iterator = rasterCache.entrySet().iterator();
        while (rasterCacheBytes > rasterCacheByteLimit && iterator.hasNext()) {
            Map.Entry<RasterCacheKey, TextRaster> eldest = iterator.next();
            rasterCacheBytes -= eldest.getValue().byteSize();
            iterator.remove();
        }
    }

    private void assertUsable() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Skija text engine is confined to its worker thread");
        }
        if (closed) {
            throw new IllegalStateException("Skija text engine is closed");
        }
    }

    @Override
    public void close() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Skija text engine must be closed by its worker thread");
        }
        if (closed) {
            return;
        }
        closed = true;
        layoutCache.clear();
        rasterCache.clear();
        rasterCacheBytes = 0L;
        RuntimeException failure = null;
        synchronized (fontEnvironmentLock) {
            for (FontEnvironment environment : fontEnvironments.values()) {
                try {
                    environment.close();
                } catch (RuntimeException exception) {
                    failure = append(failure, exception);
                }
            }
            fontEnvironments.clear();
        }
        try {
            emojiTypeface.close();
        } catch (RuntimeException exception) {
            failure = append(failure, exception);
        }
        try {
            sansTypeface.close();
        } catch (RuntimeException exception) {
            failure = append(failure, exception);
        }
        try {
            fontManager.close();
        } catch (RuntimeException exception) {
            failure = append(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Preserve the initialization failure that triggered cleanup.
        }
    }

    private static RuntimeException append(RuntimeException current, RuntimeException additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
    }

    private static final class FontEnvironment implements AutoCloseable {
        private final TypefaceFontProvider provider;
        private final FontCollection collection;
        private final List<Typeface> customTypefaces;
        private final AtomicInteger activeUsers = new AtomicInteger();
        private boolean closeRequested;
        private boolean closed;

        private FontEnvironment(
                TypefaceFontProvider provider,
                FontCollection collection,
                List<Typeface> customTypefaces) {
            this.provider = provider;
            this.collection = collection;
            this.customTypefaces = customTypefaces;
        }

        private Lease acquire() {
            synchronized (this) {
                if (closeRequested || closed) {
                    throw new IllegalStateException("Font environment has already been retired");
                }
                activeUsers.incrementAndGet();
                return new Lease(this);
            }
        }

        private boolean isActive() {
            return activeUsers.get() != 0;
        }

        private synchronized boolean isRetired() {
            return closeRequested || closed;
        }

        private void requestClose() {
            boolean closeNow = false;
            synchronized (this) {
                closeRequested = true;
                if (!closed && activeUsers.get() == 0) {
                    closed = true;
                    closeNow = true;
                }
            }
            if (closeNow) {
                closeNative();
            }
        }

        private void release() {
            boolean closeNow = false;
            synchronized (this) {
                int remaining = activeUsers.decrementAndGet();
                if (remaining < 0) {
                    activeUsers.incrementAndGet();
                    throw new IllegalStateException("Font environment lease was released twice");
                }
                if (!closed && closeRequested && remaining == 0) {
                    closed = true;
                    closeNow = true;
                }
            }
            if (closeNow) {
                closeNative();
            }
        }

        private static FontEnvironment create(
                FontMgr fontManager,
                Typeface sansTypeface,
                Typeface emojiTypeface,
                Map<UiKey, byte[]> customFonts) {
            TypefaceFontProvider provider = null;
            FontCollection collection = null;
            List<Typeface> customTypefaces = new ArrayList<>();
            try {
                provider = new TypefaceFontProvider();
                provider.registerTypeface(sansTypeface, BundledFontCatalog.SANS_ALIAS);
                provider.registerTypeface(emojiTypeface, BundledFontCatalog.EMOJI_ALIAS);
                for (Map.Entry<UiKey, byte[]> entry : customFonts.entrySet()) {
                    Typeface typeface;
                    try (Data data = Data.makeFromBytes(entry.getValue())) {
                        typeface = fontManager.makeFromData(data);
                    }
                    if (typeface == null || typeface.getGlyphsCount() == 0) {
                        closeQuietly(typeface);
                        throw new IllegalArgumentException(
                                "Custom font was not recognized: " + entry.getKey());
                    }
                    customTypefaces.add(typeface);
                    provider.registerTypeface(typeface, entry.getKey().toString());
                }

                collection = new FontCollection();
                collection
                        .setAssetFontManager(provider)
                        .setDefaultFontManager(provider, BundledFontCatalog.SANS_ALIAS)
                        .setEnableFallback(true);
                return new FontEnvironment(provider, collection, List.copyOf(customTypefaces));
            } catch (RuntimeException | Error exception) {
                closeQuietly(collection);
                closeQuietly(provider);
                for (int index = customTypefaces.size() - 1; index >= 0; index--) {
                    closeQuietly(customTypefaces.get(index));
                }
                throw exception;
            }
        }

        @Override
        public void close() {
            requestClose();
        }

        private void closeNative() {
            RuntimeException failure = null;
            try {
                collection.close();
            } catch (RuntimeException exception) {
                failure = exception;
            }
            try {
                provider.close();
            } catch (RuntimeException exception) {
                failure = append(failure, exception);
            }
            for (int index = customTypefaces.size() - 1; index >= 0; index--) {
                try {
                    customTypefaces.get(index).close();
                } catch (RuntimeException exception) {
                    failure = append(failure, exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private static final class Lease implements AutoCloseable {
            private final FontEnvironment owner;
            private final AtomicBoolean closed = new AtomicBoolean();

            private Lease(FontEnvironment owner) {
                this.owner = owner;
            }

            @Override
            public void close() {
                if (!closed.compareAndSet(false, true)) {
                    return;
                }
                owner.release();
            }
        }
    }

    record CacheStats(
            int layouts,
            int rasters,
            long rasterBytes,
            int fontEnvironments,
            int customTypefaces) {
    }

    private record RasterCacheKey(TextCacheKey layout, int scaleUnits) {
        private RasterCacheKey {
            Objects.requireNonNull(layout, "layout");
        }
    }
}
