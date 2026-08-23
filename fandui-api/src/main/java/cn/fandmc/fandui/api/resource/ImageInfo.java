package cn.fandmc.fandui.api.resource;

import java.util.Objects;

/** Immutable decoded image dimensions in source pixels. */
public final class ImageInfo {
    private final int width;
    private final int height;

    public ImageInfo(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ImageInfo info && width == info.width && height == info.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @Override
    public String toString() {
        return "ImageInfo[width=" + width + ", height=" + height + ']';
    }
}
