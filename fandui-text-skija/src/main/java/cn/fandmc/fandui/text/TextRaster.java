package cn.fandmc.fandui.text;

import cn.fandmc.fandui.api.style.Color;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Immutable CPU pixels ready for a render-thread texture upload. */
public final class TextRaster {
    static final int SCALE_UNITS_PER_PIXEL = 64;
    static final int MAX_SCALE_UNITS = 64 * SCALE_UNITS_PER_PIXEL;

    private final long resourceGeneration;
    private final long textureKey;
    private final byte[] cacheKeySha256;
    private final TextPixelFormat format;
    private final int width;
    private final int height;
    private final int rowBytes;
    private final float deviceScale;
    private final float originOffsetX;
    private final float originOffsetY;
    private final Color modulationColor;
    private final byte[] pixels;

    TextRaster(
            long resourceGeneration,
            long textureKey,
            byte[] cacheKeySha256,
            TextPixelFormat format,
            int width,
            int height,
            int rowBytes,
            float deviceScale,
            float originOffsetX,
            float originOffsetY,
            Color modulationColor,
            byte[] pixels) {
        if (resourceGeneration < 0L) {
            throw new IllegalArgumentException("resourceGeneration must not be negative");
        }
        if (textureKey == 0L) {
            throw new IllegalArgumentException("textureKey 0 is reserved");
        }
        Objects.requireNonNull(cacheKeySha256, "cacheKeySha256");
        if (cacheKeySha256.length != 32) {
            throw new IllegalArgumentException("cacheKeySha256 must contain 32 bytes");
        }
        this.format = Objects.requireNonNull(format, "format");
        if (width < 0 || height < 0 || rowBytes < 0) {
            throw new IllegalArgumentException("Raster dimensions must not be negative");
        }
        if ((width == 0) != (height == 0)) {
            throw new IllegalArgumentException("An empty raster must have zero width and height");
        }
        int expectedRowBytes = Math.multiplyExact(width, format.bytesPerPixel());
        if (rowBytes != expectedRowBytes) {
            throw new IllegalArgumentException("rowBytes does not match the tightly packed pixel format");
        }
        int expectedBytes = Math.multiplyExact(rowBytes, height);
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length != expectedBytes) {
            throw new IllegalArgumentException("Pixel byte count does not match raster dimensions");
        }
        if (!Float.isFinite(deviceScale) || deviceScale <= 0.0f) {
            throw new IllegalArgumentException("deviceScale must be finite and positive");
        }
        if (!Float.isFinite(originOffsetX) || !Float.isFinite(originOffsetY)) {
            throw new IllegalArgumentException("Raster origin offsets must be finite");
        }
        this.resourceGeneration = resourceGeneration;
        this.textureKey = textureKey;
        this.cacheKeySha256 = cacheKeySha256.clone();
        this.width = width;
        this.height = height;
        this.rowBytes = rowBytes;
        this.deviceScale = deviceScale;
        this.originOffsetX = originOffsetX;
        this.originOffsetY = originOffsetY;
        this.modulationColor = Objects.requireNonNull(modulationColor, "modulationColor");
        this.pixels = pixels;
    }

    /** Creates an integration-test or alternate-backend raster with defensively copied pixels. */
    public static TextRaster copyOf(
            long resourceGeneration,
            long textureKey,
            byte[] cacheKeySha256,
            TextPixelFormat format,
            int width,
            int height,
            int rowBytes,
            float deviceScale,
            float originOffsetX,
            float originOffsetY,
            Color modulationColor,
            byte[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        return new TextRaster(
                resourceGeneration,
                textureKey,
                cacheKeySha256,
                format,
                width,
                height,
                rowBytes,
                deviceScale,
                originOffsetX,
                originOffsetY,
                modulationColor,
                pixels.clone());
    }

    public long resourceGeneration() {
        return resourceGeneration;
    }

    public long textureKey() {
        return textureKey;
    }

    public byte[] cacheKeySha256() {
        return cacheKeySha256.clone();
    }

    public TextPixelFormat format() {
        return format;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int rowBytes() {
        return rowBytes;
    }

    public int byteSize() {
        return pixels.length;
    }

    public float deviceScale() {
        return deviceScale;
    }

    public float originOffsetX() {
        return originOffsetX;
    }

    public float originOffsetY() {
        return originOffsetY;
    }

    /**
     * ALPHA_8 pixels use this RGB tint; RGBA pixels use opaque white because their
     * color and alpha are already baked into the premultiplied texture.
     */
    public Color modulationColor() {
        return modulationColor;
    }

    public ByteBuffer pixels() {
        return ByteBuffer.wrap(pixels).asReadOnlyBuffer();
    }

    static int quantizeScale(float deviceScale) {
        if (!Float.isFinite(deviceScale) || deviceScale <= 0.0f) {
            throw new IllegalArgumentException("deviceScale must be finite and positive");
        }
        long units = Math.round(deviceScale * SCALE_UNITS_PER_PIXEL);
        if (units < 1L || units > MAX_SCALE_UNITS) {
            throw new IllegalArgumentException("deviceScale is outside the supported range");
        }
        return (int) units;
    }

    static float scaleFromUnits(int scaleUnits) {
        if (scaleUnits < 1 || scaleUnits > MAX_SCALE_UNITS) {
            throw new IllegalArgumentException("Invalid quantized device scale");
        }
        return scaleUnits / (float) SCALE_UNITS_PER_PIXEL;
    }

    @Override
    public String toString() {
        return "TextRaster[format=" + format
                + ", width=" + width
                + ", height=" + height
                + ", deviceScale=" + deviceScale
                + ", resourceGeneration=" + resourceGeneration
                + ", textureKey=" + Long.toUnsignedString(textureKey) + ']';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TextRaster raster)) {
            return false;
        }
        return resourceGeneration == raster.resourceGeneration
                && textureKey == raster.textureKey
                && width == raster.width
                && height == raster.height
                && rowBytes == raster.rowBytes
                && Float.compare(deviceScale, raster.deviceScale) == 0
                && Float.compare(originOffsetX, raster.originOffsetX) == 0
                && Float.compare(originOffsetY, raster.originOffsetY) == 0
                && format == raster.format
                && modulationColor.equals(raster.modulationColor)
                && Arrays.equals(cacheKeySha256, raster.cacheKeySha256)
                && Arrays.equals(pixels, raster.pixels);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                resourceGeneration,
                textureKey,
                format,
                width,
                height,
                rowBytes,
                deviceScale,
                originOffsetX,
                originOffsetY,
                modulationColor);
        result = 31 * result + Arrays.hashCode(cacheKeySha256);
        result = 31 * result + Arrays.hashCode(pixels);
        return result;
    }
}
