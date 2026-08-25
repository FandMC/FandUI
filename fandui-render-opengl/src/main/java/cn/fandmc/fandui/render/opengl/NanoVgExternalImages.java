package cn.fandmc.fandui.render.opengl;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.lwjgl.nanovg.NanoVG.NVG_IMAGE_NEAREST;
import static org.lwjgl.nanovg.NanoVG.NVG_IMAGE_PREMULTIPLIED;
import static org.lwjgl.nanovg.NanoVG.nvgDeleteImage;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_IMAGE_NODELETE;
import static org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle;

final class NanoVgExternalImages implements AutoCloseable {
    private final long context;
    private final LinkedHashMap<Key, Image> entries = new LinkedHashMap<>();
    private long frameGeneration;
    private boolean frameOpen;
    private boolean closed;

    NanoVgExternalImages(long context) {
        if (context == 0L) {
            throw new IllegalArgumentException("NanoVG context must be non-zero");
        }
        this.context = context;
    }

    void beginFrame() {
        requireOpen();
        if (frameOpen) {
            throw new IllegalStateException("NanoVG external image frame is already open");
        }
        advanceFrameGeneration();
        frameOpen = true;
    }

    Image resolve(
            long resourceKey,
            OpenGlTexture texture,
            int width,
            int height,
            OpenGlSampling sampling,
            boolean alphaOnly) {
        requireFrame();
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(sampling, "sampling");
        if (resourceKey == 0L) {
            throw new OpenGlRenderException("NanoVG texture key 0 is reserved");
        }
        if (width < 1 || height < 1) {
            throw new OpenGlRenderException("NanoVG external image dimensions must be positive");
        }
        Key key = new Key(resourceKey, sampling, alphaOnly);
        Image existing = entries.get(key);
        if (existing != null && !existing.matches(texture.textureId(), width, height)) {
            if (existing.usedIn(frameGeneration)) {
                throw new OpenGlRenderException(
                        "NanoVG texture binding changed during the active frame: "
                                + Long.toUnsignedString(resourceKey));
            }
            nvgDeleteImage(context, existing.imageId);
            existing.live = false;
            entries.remove(key);
            existing = null;
        }
        if (existing == null) {
            int flags = NVG_IMAGE_NODELETE | NVG_IMAGE_PREMULTIPLIED;
            if (sampling == OpenGlSampling.NEAREST) {
                flags |= NVG_IMAGE_NEAREST;
            }
            int imageId = nvglCreateImageFromHandle(
                    context,
                    texture.textureId(),
                    width,
                    height,
                    flags);
            if (imageId == 0) {
                throw new OpenGlRenderException("NanoVG rejected an external OpenGL texture");
            }
            existing = new Image(imageId, texture.textureId(), width, height);
            entries.put(key, existing);
        }
        existing.lastUsedFrame = frameGeneration;
        return existing;
    }

    void retain(Image image) {
        requireFrame();
        Objects.requireNonNull(image, "image");
        if (!image.live) {
            throw new OpenGlRenderException("Prepared NanoVG external image is no longer live");
        }
        image.lastUsedFrame = frameGeneration;
    }

    void endFrame() {
        requireFrame();
        Iterator<Map.Entry<Key, Image>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, Image> candidate = iterator.next();
            if (candidate.getValue().usedIn(frameGeneration)) {
                continue;
            }
            nvgDeleteImage(context, candidate.getValue().imageId);
            candidate.getValue().live = false;
            iterator.remove();
        }
        frameOpen = false;
    }

    void abortFrame() {
        if (!closed && frameOpen) {
            frameOpen = false;
        }
    }

    int size() {
        return entries.size();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        frameOpen = false;
        for (Image entry : entries.values()) {
            nvgDeleteImage(context, entry.imageId);
            entry.live = false;
        }
        entries.clear();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("NanoVG external image registry is closed");
        }
    }

    private void requireFrame() {
        requireOpen();
        if (!frameOpen) {
            throw new IllegalStateException("NanoVG external images require an active render frame");
        }
    }

    private void advanceFrameGeneration() {
        if (frameGeneration == Long.MAX_VALUE) {
            for (Image image : entries.values()) {
                image.lastUsedFrame = 0L;
            }
            frameGeneration = 1L;
        } else {
            frameGeneration++;
        }
    }

    private record Key(long resourceKey, OpenGlSampling sampling, boolean alphaOnly) {
    }

    static final class Image {
        private final int imageId;
        private final int textureId;
        private final int width;
        private final int height;
        private long lastUsedFrame;
        private boolean live = true;

        private Image(int imageId, int textureId, int width, int height) {
            this.imageId = imageId;
            this.textureId = textureId;
            this.width = width;
            this.height = height;
        }

        int imageId() {
            return imageId;
        }

        private boolean matches(int nextTexture, int nextWidth, int nextHeight) {
            return textureId == nextTexture && width == nextWidth && height == nextHeight;
        }

        private boolean usedIn(long generation) {
            return lastUsedFrame == generation;
        }
    }
}
