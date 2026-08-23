package cn.fandmc.fandui.text;

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

final class SkijaTextEngine implements AutoCloseable {
    static final int DEFAULT_LAYOUT_CACHE_ENTRIES = 512;
    static final long DEFAULT_RASTER_CACHE_BYTES = 64L * 1024L * 1024L;

    private static final float MAX_UNBOUNDED_LAYOUT_WIDTH = 1_048_576.0f;
    private static final long MAX_RASTER_SURFACE_BYTES = 64L * 1024L * 1024L;
    private static final int PIXEL_PADDING = 1;

    private final Thread ownerThread = Thread.currentThread();
    private final int layoutCacheLimit;
    private final long rasterCacheByteLimit;
    private final FontMgr fontManager;
    private final Typeface sansTypeface;
    private final Typeface emojiTypeface;
    private final TypefaceFontProvider fontProvider;
    private final FontCollection fontCollection;
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
        TypefaceFontProvider createdProvider = null;
        FontCollection createdCollection = null;
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

            createdProvider = new TypefaceFontProvider();
            createdProvider.registerTypeface(createdSans, BundledFontCatalog.SANS_ALIAS);
            createdProvider.registerTypeface(createdEmoji, BundledFontCatalog.EMOJI_ALIAS);

            createdCollection = new FontCollection();
            createdCollection
                    .setAssetFontManager(createdProvider)
                    .setDefaultFontManager(createdProvider, BundledFontCatalog.SANS_ALIAS)
                    .setEnableFallback(true);
        } catch (RuntimeException | Error exception) {
            closeQuietly(createdCollection);
            closeQuietly(createdProvider);
            closeQuietly(createdEmoji);
            closeQuietly(createdSans);
            closeQuietly(createdFontManager);
            throw new IllegalStateException("Failed to initialize bundled Skija fonts", exception);
        }
        fontManager = createdFontManager;
        sansTypeface = createdSans;
        emojiTypeface = createdEmoji;
        fontProvider = createdProvider;
        fontCollection = createdCollection;
    }

    LayoutMetrics layout(TextCacheKey key, TextRequest request) {
        assertUsable();
        LayoutMetrics cached = layoutCache.get(key);
        if (cached != null) {
            return cached;
        }

        LayoutMetrics metrics;
        try (Paragraph paragraph = buildAndLayout(request)) {
            metrics = extractMetrics(request, paragraph);
        }
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
        try (Paragraph paragraph = buildExistingLayout(layout)) {
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

        try (Paragraph paragraph = buildExistingLayout(layout)) {
            Rect caretBounds = caretBounds(paragraph, layout.metrics(), text, caret);
            List<TextRangeGeometry> resultRanges = new ArrayList<>(checkedRanges.size());
            for (TextRange range : checkedRanges) {
                resultRanges.add(new TextRangeGeometry(
                        range,
                        rangeBounds(paragraph, layout.metrics(), range)));
            }
            return new TextGeometry(caretBounds, resultRanges);
        }
    }

    CacheStats cacheStats() {
        assertUsable();
        return new CacheStats(layoutCache.size(), rasterCache.size(), rasterCacheBytes);
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

    private Paragraph buildAndLayout(TextRequest request) {
        boolean unconstrained = request.wrap() == TextWrap.NONE || !Float.isFinite(request.maxWidth());
        Alignment requestedAlignment = alignment(request.alignment());
        Paragraph paragraph = buildParagraph(
                request,
                unconstrained ? Alignment.START : requestedAlignment);
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

    private Paragraph buildExistingLayout(SkijaTextLayout layout) {
        Paragraph paragraph = buildParagraph(
                layout.request(),
                alignment(layout.request().alignment()));
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

    private Paragraph buildParagraph(TextRequest request, Alignment paragraphAlignment) {
        return buildParagraph(request, paragraphAlignment, request.style().color());
    }

    private Paragraph buildParagraph(
            TextRequest request,
            Alignment paragraphAlignment,
            Color paintColor) {
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

            try (ParagraphBuilder builder = new ParagraphBuilder(paragraphStyle, fontCollection)) {
                builder.pushStyle(textStyle);
                builder.addText(request.text());
                builder.popStyle();
                return builder.build();
            }
        }
    }

    private TextRaster rasterize(SkijaTextLayout layout, int scaleUnits) {
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
        byte[] rgba = rasterizeRgba(layout, metrics, scale, imageInfo, maskColor);
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
            rgba = rasterizeRgba(layout, metrics, scale, imageInfo, requestedColor);
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
            Color paintColor) {
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        try (Paragraph paragraph = buildParagraph(
                     layout.request(),
                     alignment(layout.request().alignment()),
                     paintColor);
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
        fontCollection.close();
        fontProvider.close();
        emojiTypeface.close();
        sansTypeface.close();
        fontManager.close();
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

    record CacheStats(int layouts, int rasters, long rasterBytes) {
    }

    private record RasterCacheKey(TextCacheKey layout, int scaleUnits) {
        private RasterCacheKey {
            Objects.requireNonNull(layout, "layout");
        }
    }
}
