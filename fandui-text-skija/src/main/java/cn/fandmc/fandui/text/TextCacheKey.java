package cn.fandmc.fandui.text;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.text.FontFamily;
import cn.fandmc.fandui.api.text.FontSlant;
import cn.fandmc.fandui.api.text.TextAlignment;
import cn.fandmc.fandui.api.text.TextDirection;
import cn.fandmc.fandui.api.text.TextOverflow;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextWrap;
import cn.fandmc.fandui.core.resource.FontResourceSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

record TextCacheKey(
        long resourceGeneration,
        String fontResourceFingerprint,
        String text,
        List<UiKey> families,
        int fontSizeBits,
        int weight,
        FontSlant slant,
        int redBits,
        int greenBits,
        int blueBits,
        int alphaBits,
        int lineHeightBits,
        int letterSpacingBits,
        int wordSpacingBits,
        String locale,
        int maxWidthBits,
        int maxLines,
        TextWrap wrap,
        TextOverflow overflow,
        TextAlignment alignment,
        TextDirection direction) {
    private static final byte[] RASTER_KEY_VERSION =
            "FandUI text-block raster v1 / Skija 0.143.17".getBytes(StandardCharsets.US_ASCII);

    TextCacheKey {
        families = List.copyOf(families);
    }

    static TextCacheKey from(TextRequest request, FontResourceSnapshot resources) {
        var style = request.style();
        return new TextCacheKey(
                resources.generation(),
                resources.contentFingerprint(),
                request.text(),
                style.families().stream().map(FontFamily::key).toList(),
                Float.floatToIntBits(style.fontSize()),
                style.weight().value(),
                style.slant(),
                Float.floatToIntBits(style.color().red()),
                Float.floatToIntBits(style.color().green()),
                Float.floatToIntBits(style.color().blue()),
                Float.floatToIntBits(style.color().alpha()),
                Float.floatToIntBits(style.lineHeight()),
                Float.floatToIntBits(style.letterSpacing()),
                Float.floatToIntBits(style.wordSpacing()),
                style.locale(),
                Float.floatToIntBits(request.maxWidth()),
                request.maxLines(),
                request.wrap(),
                request.overflow(),
                request.alignment(),
                request.direction());
    }

    byte[] rasterDigest(int scaleUnits, TextPixelFormat format) {
        MessageDigest digest = sha256();
        putBytes(digest, RASTER_KEY_VERSION);
        putString(digest, BundledFontCatalog.SANS_SHA256);
        putString(digest, BundledFontCatalog.EMOJI_SHA256);
        putLong(digest, resourceGeneration);
        putString(digest, fontResourceFingerprint);
        putString(digest, text);
        putInt(digest, families.size());
        for (UiKey family : families) {
            putString(digest, family.namespace());
            putString(digest, family.value());
        }
        putInt(digest, fontSizeBits);
        putInt(digest, weight);
        putString(digest, slant.name());
        if (format == TextPixelFormat.RGBA_8888_PREMULTIPLIED) {
            putInt(digest, redBits);
            putInt(digest, greenBits);
            putInt(digest, blueBits);
        }
        putInt(digest, alphaBits);
        putInt(digest, lineHeightBits);
        putInt(digest, letterSpacingBits);
        putInt(digest, wordSpacingBits);
        putString(digest, locale);
        putInt(digest, maxWidthBits);
        putInt(digest, maxLines);
        putString(digest, wrap.name());
        putString(digest, overflow.name());
        putString(digest, alignment.name());
        putString(digest, direction.name());
        putInt(digest, scaleUnits);
        putString(digest, format.name());
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void putString(MessageDigest digest, String value) {
        putBytes(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(MessageDigest digest, byte[] value) {
        putInt(digest, value.length);
        digest.update(value);
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void putLong(MessageDigest digest, long value) {
        putInt(digest, (int) (value >>> 32));
        putInt(digest, (int) value);
    }
}
