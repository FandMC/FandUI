package cn.fandmc.fandui.core.resource;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.resource.ImageInfo;

import java.nio.ByteBuffer;
import java.util.Objects;

/** Immutable premultiplied RGBA8 pixels ready for a render-thread texture upload. */
public final class ImageRaster {
    private final UiKey sourceKey;
    private final long resourceGeneration;
    private final long textureKey;
    private final byte[] cacheKeySha256;
    private final int width;
    private final int height;
    private final int rowBytes;
    private final byte[] pixels;

    ImageRaster(
            UiKey sourceKey,
            long resourceGeneration,
            long textureKey,
            byte[] cacheKeySha256,
            int width,
            int height,
            byte[] pixels) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
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
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        int calculatedRowBytes = Math.multiplyExact(width, 4);
        int calculatedByteSize = Math.multiplyExact(calculatedRowBytes, height);
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length != calculatedByteSize) {
            throw new IllegalArgumentException("Pixel byte count does not match image dimensions");
        }
        this.resourceGeneration = resourceGeneration;
        this.textureKey = textureKey;
        this.cacheKeySha256 = cacheKeySha256.clone();
        this.width = width;
        this.height = height;
        rowBytes = calculatedRowBytes;
        this.pixels = pixels;
    }

    /** Creates an integration-test or alternate-decoder raster with copied pixels. */
    public static ImageRaster copyOf(
            UiKey sourceKey,
            long resourceGeneration,
            long textureKey,
            byte[] cacheKeySha256,
            int width,
            int height,
            byte[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        return new ImageRaster(
                sourceKey,
                resourceGeneration,
                textureKey,
                cacheKeySha256,
                width,
                height,
                pixels.clone());
    }

    public UiKey sourceKey() {
        return sourceKey;
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

    public ImageInfo info() {
        return new ImageInfo(width, height);
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

    public ByteBuffer pixels() {
        return ByteBuffer.wrap(pixels).asReadOnlyBuffer();
    }

    @Override
    public String toString() {
        return "ImageRaster[sourceKey=" + sourceKey
                + ", width=" + width
                + ", height=" + height
                + ", resourceGeneration=" + resourceGeneration
                + ", textureKey=" + Long.toUnsignedString(textureKey) + ']';
    }
}
