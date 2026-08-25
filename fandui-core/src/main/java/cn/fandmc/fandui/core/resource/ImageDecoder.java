package cn.fandmc.fandui.core.resource;

import cn.fandmc.fandui.api.resource.ResourceFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/** Selects the bounded image decoder without exposing implementation details to the API. */
final class ImageDecoder {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private ImageDecoder() {
    }

    static PngImageDecoder.DecodedImage decode(byte[] encoded, ResourceFormat requested)
            throws IOException {
        Objects.requireNonNull(requested, "requested");
        return switch (requested) {
            case PNG -> PngImageDecoder.decode(encoded);
            case SVG -> SvgImageDecoder.decode(encoded);
            case AUTO -> detect(encoded);
        };
    }

    private static PngImageDecoder.DecodedImage detect(byte[] encoded) throws IOException {
        if (isPng(encoded)) {
            return PngImageDecoder.decode(encoded);
        }
        if (looksLikeSvg(encoded)) {
            return SvgImageDecoder.decode(encoded);
        }
        // Keep the established PNG error for unknown AUTO resources.
        return PngImageDecoder.decode(encoded);
    }

    private static boolean isPng(byte[] encoded) {
        if (encoded == null || encoded.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (encoded[index] != PNG_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeSvg(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            return false;
        }
        int length = Math.min(encoded.length, 16 * 1024);
        String prefix = new String(encoded, 0, length, StandardCharsets.UTF_8)
                .replace('\uFEFF', ' ')
                .toLowerCase(Locale.ROOT);
        String trimmed = prefix.stripLeading();
        if (trimmed.startsWith("<svg")
                && (trimmed.length() == 4 || Character.isWhitespace(trimmed.charAt(4))
                || trimmed.charAt(4) == '>')) {
            return true;
        }
        // XML declarations, comments and whitespace are legal before the root.
        // The secure DOM parser remains the authority; this is only dispatch.
        return trimmed.indexOf("<svg") >= 0;
    }
}
