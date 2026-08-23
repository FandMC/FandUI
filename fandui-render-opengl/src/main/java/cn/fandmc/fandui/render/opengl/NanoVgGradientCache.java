package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.canvas.DisplayGradientStop;
import cn.fandmc.fandui.canvas.PremultipliedColor;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.NVG_IMAGE_PREMULTIPLIED;
import static org.lwjgl.nanovg.NanoVG.nvgCreateImageRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgDeleteImage;

final class NanoVgGradientCache implements AutoCloseable {
    static final long DEFAULT_BYTE_LIMIT = 16L * 1024L * 1024L;
    static final int LINEAR_WIDTH = 257;
    static final int RADIAL_SIZE = 257;

    private final long context;
    private final long byteLimit;
    private final LinkedHashMap<Key, Image> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long textureBytes;
    private long frameGeneration;
    private boolean frameOpen;
    private boolean closed;

    NanoVgGradientCache(long context) {
        this(context, DEFAULT_BYTE_LIMIT);
    }

    NanoVgGradientCache(long context, long byteLimit) {
        if (context == 0L) {
            throw new IllegalArgumentException("NanoVG context must be non-zero");
        }
        if (byteLimit < LINEAR_WIDTH * 4L) {
            throw new IllegalArgumentException("Gradient cache byte limit is too small");
        }
        this.context = context;
        this.byteLimit = byteLimit;
    }

    void beginFrame() {
        requireOpen();
        if (frameOpen) {
            throw new IllegalStateException("NanoVG gradient frame is already open");
        }
        advanceFrameGeneration();
        frameOpen = true;
    }

    Image linearImage(List<DisplayGradientStop> stops) {
        requireFrame();
        return image(new LinearKey(stops));
    }

    Image radialImage(List<DisplayGradientStop> stops, float innerRatio) {
        requireFrame();
        if (!Float.isFinite(innerRatio) || innerRatio < 0.0f || innerRatio > 1.0f) {
            throw new IllegalArgumentException("innerRatio must be between 0 and 1");
        }
        return image(new RadialKey(stops, innerRatio));
    }

    private Image image(Key key) {
        Image existing = entries.get(key);
        if (existing != null) {
            existing.lastUsedFrame = frameGeneration;
            return existing;
        }

        long byteSize = key.byteSize();
        evictFor(byteSize);
        ByteBuffer pixels = MemoryUtil.memAlloc(Math.toIntExact(byteSize));
        try {
            key.writePixels(pixels);
            pixels.flip();
            int imageId = nvgCreateImageRGBA(
                    context,
                    key.width(),
                    key.height(),
                    NVG_IMAGE_PREMULTIPLIED,
                    pixels);
            if (imageId == 0) {
                throw new OpenGlRenderException("NanoVG failed to create a gradient lookup texture");
            }
            Image created = new Image(imageId, byteSize);
            created.lastUsedFrame = frameGeneration;
            entries.put(key, created);
            textureBytes = Math.addExact(textureBytes, byteSize);
            return created;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private void evictFor(long requiredBytes) {
        Iterator<Map.Entry<Key, Image>> iterator = entries.entrySet().iterator();
        while (textureBytes + requiredBytes > byteLimit && iterator.hasNext()) {
            Map.Entry<Key, Image> candidate = iterator.next();
            if (candidate.getValue().usedIn(frameGeneration)) {
                continue;
            }
            nvgDeleteImage(context, candidate.getValue().imageId);
            candidate.getValue().live = false;
            textureBytes -= candidate.getValue().byteSize;
            iterator.remove();
        }
        if (textureBytes + requiredBytes > byteLimit) {
            throw new OpenGlRenderException(
                    "Active NanoVG gradient textures exceed the " + byteLimit + " byte cache limit");
        }
    }

    void endFrame() {
        requireFrame();
        frameOpen = false;
    }

    void abortFrame() {
        if (!closed && frameOpen) {
            frameOpen = false;
        }
    }

    void retain(Image image) {
        requireFrame();
        if (image == null) {
            throw new NullPointerException("image");
        }
        if (!image.live) {
            throw new OpenGlRenderException("Prepared NanoVG gradient image is no longer live");
        }
        image.lastUsedFrame = frameGeneration;
    }

    CacheStats stats() {
        return new CacheStats(entries.size(), textureBytes);
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
        textureBytes = 0L;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("NanoVG gradient cache is closed");
        }
    }

    private void requireFrame() {
        requireOpen();
        if (!frameOpen) {
            throw new IllegalStateException("NanoVG gradients require an active render frame");
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

    record CacheStats(int entries, long bytes) {
    }

    private sealed interface Key permits LinearKey, RadialKey {
        int width();

        int height();

        default long byteSize() {
            return Math.multiplyExact(Math.multiplyExact((long) width(), height()), 4L);
        }

        void writePixels(ByteBuffer target);
    }

    private record LinearKey(List<DisplayGradientStop> stops) implements Key {
        private LinearKey {
            stops = validatedStops(stops);
        }

        @Override
        public int width() {
            return LINEAR_WIDTH;
        }

        @Override
        public int height() {
            return 1;
        }

        @Override
        public void writePixels(ByteBuffer target) {
            writeLinearPixels(stops, target);
        }
    }

    private record RadialKey(List<DisplayGradientStop> stops, float innerRatio) implements Key {
        private RadialKey {
            stops = validatedStops(stops);
        }

        @Override
        public int width() {
            return RADIAL_SIZE;
        }

        @Override
        public int height() {
            return RADIAL_SIZE;
        }

        @Override
        public void writePixels(ByteBuffer target) {
            writeRadialPixels(stops, innerRatio, target);
        }
    }

    static void writeLinearPixels(List<DisplayGradientStop> stops, ByteBuffer target) {
        List<DisplayGradientStop> checked = validatedStops(stops);
        for (int x = 0; x < LINEAR_WIDTH; x++) {
            putColor(target, sample(checked, x / (float) (LINEAR_WIDTH - 1)));
        }
    }

    static void writeRadialPixels(
            List<DisplayGradientStop> stops,
            float innerRatio,
            ByteBuffer target) {
        List<DisplayGradientStop> checked = validatedStops(stops);
        if (!Float.isFinite(innerRatio) || innerRatio < 0.0f || innerRatio > 1.0f) {
            throw new IllegalArgumentException("innerRatio must be between 0 and 1");
        }
        float span = 1.0f - innerRatio;
        for (int y = 0; y < RADIAL_SIZE; y++) {
            float normalizedY = ((y + 0.5f) / RADIAL_SIZE - 0.5f) * 2.0f;
            for (int x = 0; x < RADIAL_SIZE; x++) {
                float normalizedX = ((x + 0.5f) / RADIAL_SIZE - 0.5f) * 2.0f;
                float radius = (float) Math.sqrt(
                        normalizedX * normalizedX + normalizedY * normalizedY);
                float position = span <= 1.0e-6f
                        ? (radius <= innerRatio ? 0.0f : 1.0f)
                        : clamp((radius - innerRatio) / span);
                putColor(target, sample(checked, position));
            }
        }
    }

    private static List<DisplayGradientStop> validatedStops(List<DisplayGradientStop> stops) {
        List<DisplayGradientStop> copy = List.copyOf(stops);
        if (copy.size() < 2) {
            throw new IllegalArgumentException("A gradient requires at least two stops");
        }
        return copy;
    }

    private static PremultipliedColor sample(List<DisplayGradientStop> stops, float position) {
        DisplayGradientStop previous = stops.get(0);
        if (position <= previous.offset()) {
            return previous.color();
        }
        for (int index = 1; index < stops.size(); index++) {
            DisplayGradientStop next = stops.get(index);
            if (position <= next.offset()) {
                float span = next.offset() - previous.offset();
                float amount = span <= 1.0e-6f
                        ? 0.0f
                        : clamp((position - previous.offset()) / span);
                return lerp(previous.color(), next.color(), amount);
            }
            previous = next;
        }
        return previous.color();
    }

    private static PremultipliedColor lerp(
            PremultipliedColor first,
            PremultipliedColor second,
            float amount) {
        float inverse = 1.0f - amount;
        return new PremultipliedColor(
                first.red() * inverse + second.red() * amount,
                first.green() * inverse + second.green() * amount,
                first.blue() * inverse + second.blue() * amount,
                first.alpha() * inverse + second.alpha() * amount);
    }

    private static void putColor(ByteBuffer target, PremultipliedColor color) {
        target.put((byte) Math.round(color.red() * 255.0f));
        target.put((byte) Math.round(color.green() * 255.0f));
        target.put((byte) Math.round(color.blue() * 255.0f));
        target.put((byte) Math.round(color.alpha() * 255.0f));
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    static final class Image {
        private final int imageId;
        private final long byteSize;
        private long lastUsedFrame;
        private boolean live = true;

        private Image(int imageId, long byteSize) {
            this.imageId = imageId;
            this.byteSize = byteSize;
        }

        int imageId() {
            return imageId;
        }

        private boolean usedIn(long generation) {
            return lastUsedFrame == generation;
        }
    }
}
