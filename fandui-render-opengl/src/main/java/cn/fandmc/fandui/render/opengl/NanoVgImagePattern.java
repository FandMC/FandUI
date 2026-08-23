package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.layout.Rect;

import java.util.Objects;

record NanoVgImagePattern(float x, float y, float width, float height) {
    static NanoVgImagePattern from(
            int imageWidth,
            int imageHeight,
            Rect source,
            Rect destination) {
        if (imageWidth < 1 || imageHeight < 1) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (source.width() <= 0.0f || source.height() <= 0.0f) {
            throw new IllegalArgumentException("Image source dimensions must be positive");
        }

        float width = destination.width() * imageWidth / source.width();
        float height = destination.height() * imageHeight / source.height();
        float x = destination.x() - destination.width() * source.x() / source.width();
        float y = destination.y() - destination.height() * source.y() / source.height();
        return new NanoVgImagePattern(x, y, width, height);
    }
}
