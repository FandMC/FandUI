package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.text.TextPixelFormat;
import cn.fandmc.fandui.text.TextRaster;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_UNPACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_IMAGES;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL30.GL_RED;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/** Render-thread-owned, byte-bounded cache of immutable text block textures. */
final class OpenGlTextTextureCache implements TextTextureStore {
    static final long DEFAULT_BYTE_LIMIT = 64L * 1024L * 1024L;

    private final TextureDriver driver;
    private final long byteLimit;
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    private Thread renderThread;
    private List<TextRaster> activeRasters;
    private Set<Long> activeKeys = Set.of();
    private long textureBytes;
    private boolean closed;

    OpenGlTextTextureCache() {
        this(new LwjglTextureDriver(), DEFAULT_BYTE_LIMIT);
    }

    OpenGlTextTextureCache(TextureDriver driver, long byteLimit) {
        this.driver = Objects.requireNonNull(driver, "driver");
        if (byteLimit < 1L) {
            throw new IllegalArgumentException("byteLimit must be positive");
        }
        this.byteLimit = byteLimit;
    }

    @Override
    public void activate(List<TextRaster> rasters) {
        Objects.requireNonNull(rasters, "rasters");
        requireOpenRenderThread();
        if (activeRasters == rasters) {
            return;
        }

        LinkedHashMap<Long, TextRaster> required = new LinkedHashMap<>();
        long requiredBytes = 0L;
        for (int index = 0; index < rasters.size(); index++) {
            TextRaster raster = Objects.requireNonNull(rasters.get(index), "rasters[" + index + "]");
            if (raster.byteSize() == 0) {
                continue;
            }
            TextRaster previous = required.putIfAbsent(raster.textureKey(), raster);
            if (previous != null) {
                requireSameTexture(previous, raster);
                continue;
            }
            requiredBytes = Math.addExact(requiredBytes, raster.byteSize());
        }
        if (requiredBytes > byteLimit) {
            throw new OpenGlRenderException(
                    "Active FandUI text textures require " + requiredBytes
                            + " bytes, exceeding the " + byteLimit + " byte cache limit");
        }

        long missingBytes = 0L;
        for (TextRaster raster : required.values()) {
            Entry existing = entries.get(raster.textureKey());
            if (existing == null) {
                missingBytes = Math.addExact(missingBytes, raster.byteSize());
            } else {
                existing.requireCompatible(raster);
            }
        }

        Set<Long> nextActive = Set.copyOf(required.keySet());
        evictFor(missingBytes, nextActive);
        List<Long> created = new ArrayList<>();
        try {
            for (TextRaster raster : required.values()) {
                if (entries.containsKey(raster.textureKey())) {
                    continue;
                }
                int textureId = driver.create(raster);
                Entry entry = new Entry(textureId, raster);
                entries.put(raster.textureKey(), entry);
                textureBytes = Math.addExact(textureBytes, entry.byteSize);
                created.add(raster.textureKey());
            }
        } catch (RuntimeException exception) {
            for (Long key : created) {
                deleteEntry(key);
            }
            throw exception;
        }
        activeKeys = nextActive;
        activeRasters = rasters;
    }

    private void evictFor(long missingBytes, Set<Long> required) {
        var iterator = entries.entrySet().iterator();
        while (textureBytes + missingBytes > byteLimit && iterator.hasNext()) {
            Map.Entry<Long, Entry> candidate = iterator.next();
            if (required.contains(candidate.getKey())) {
                continue;
            }
            driver.delete(candidate.getValue().textureId);
            textureBytes -= candidate.getValue().byteSize;
            iterator.remove();
        }
        if (textureBytes + missingBytes > byteLimit) {
            throw new OpenGlRenderException("FandUI text texture cache could not free enough inactive data");
        }
    }

    @Override
    public Optional<OpenGlTexture> resolve(long textureKey, OpenGlSampling sampling) {
        Objects.requireNonNull(sampling, "sampling");
        requireOpenRenderThread();
        if (!activeKeys.contains(textureKey)) {
            return Optional.empty();
        }
        if (sampling != OpenGlSampling.LINEAR) {
            throw new OpenGlRenderException("Text textures require linear sampling");
        }
        Entry entry = entries.get(textureKey);
        if (entry == null) {
            throw new OpenGlRenderException(
                    "Active FandUI text texture is missing: " + Long.toUnsignedString(textureKey));
        }
        if (!driver.isLive(entry.textureId)) {
            throw new OpenGlRenderException("FandUI text texture is not live: " + entry.textureId);
        }
        return Optional.of(new OpenGlTexture(entry.textureId));
    }

    CacheStats stats() {
        return new CacheStats(entries.size(), textureBytes, activeKeys.size());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireRenderThread();
        closed = true;
        for (Entry entry : entries.values()) {
            driver.delete(entry.textureId);
        }
        entries.clear();
        activeRasters = null;
        activeKeys = Set.of();
        textureBytes = 0L;
    }

    private void deleteEntry(long key) {
        Entry removed = entries.remove(key);
        if (removed != null) {
            driver.delete(removed.textureId);
            textureBytes -= removed.byteSize;
        }
    }

    private void requireOpenRenderThread() {
        if (closed) {
            throw new IllegalStateException("OpenGL text texture cache is closed");
        }
        requireRenderThread();
    }

    private void requireRenderThread() {
        Thread current = Thread.currentThread();
        if (renderThread == null) {
            renderThread = current;
        } else if (renderThread != current) {
            throw new IllegalStateException("OpenGL text texture cache is confined to its first Render Thread");
        }
    }

    private static void requireSameTexture(TextRaster first, TextRaster second) {
        if (first.format() != second.format()
                || first.width() != second.width()
                || first.height() != second.height()
                || first.byteSize() != second.byteSize()
                || !Arrays.equals(first.cacheKeySha256(), second.cacheKeySha256())) {
            throw new OpenGlRenderException(
                    "Conflicting text rasters use key " + Long.toUnsignedString(first.textureKey()));
        }
    }

    record CacheStats(int entries, long bytes, int activeEntries) {
    }

    interface TextureDriver {
        int create(TextRaster raster);

        void delete(int textureId);

        boolean isLive(int textureId);
    }

    private static final class Entry {
        private final int textureId;
        private final TextPixelFormat format;
        private final int width;
        private final int height;
        private final int byteSize;
        private final byte[] digest;

        private Entry(int textureId, TextRaster raster) {
            if (textureId <= 0) {
                throw new OpenGlRenderException("OpenGL returned an invalid text texture id");
            }
            this.textureId = textureId;
            format = raster.format();
            width = raster.width();
            height = raster.height();
            byteSize = raster.byteSize();
            digest = raster.cacheKeySha256();
        }

        private void requireCompatible(TextRaster raster) {
            if (format != raster.format()
                    || width != raster.width()
                    || height != raster.height()
                    || byteSize != raster.byteSize()
                    || !Arrays.equals(digest, raster.cacheKeySha256())) {
                throw new OpenGlRenderException(
                        "Text texture key collision: " + Long.toUnsignedString(raster.textureKey()));
            }
        }
    }

    private static final class LwjglTextureDriver implements TextureDriver {
        @Override
        public int create(TextRaster raster) {
            return OpenGlTextureUploader.create(
                    raster.width(),
                    raster.height(),
                    raster.byteSize(),
                    raster.pixels(),
                    raster.format() == TextPixelFormat.ALPHA_8,
                    OpenGlSampling.LINEAR,
                    "FandUI text texture");
        }

        @Override
        public void delete(int textureId) {
            OpenGlTextureUploader.delete(textureId);
        }

        @Override
        public boolean isLive(int textureId) {
            return OpenGlTextureUploader.isLive(textureId);
        }
    }
}
