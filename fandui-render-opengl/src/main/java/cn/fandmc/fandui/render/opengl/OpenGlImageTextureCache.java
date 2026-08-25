package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.core.resource.ImageRaster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Render-thread-owned, byte-bounded cache of immutable image textures. */
final class OpenGlImageTextureCache implements ImageTextureStore {
    static final long DEFAULT_BYTE_LIMIT = 128L * 1024L * 1024L;

    private final TextureDriver driver;
    private final long byteLimit;
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    private Thread renderThread;
    private List<ImageRaster> activeRasters;
    private Set<Long> activeKeys = Set.of();
    private long textureBytes;
    private boolean closed;

    OpenGlImageTextureCache() {
        this(new LwjglTextureDriver(), DEFAULT_BYTE_LIMIT);
    }

    OpenGlImageTextureCache(TextureDriver driver, long byteLimit) {
        this.driver = Objects.requireNonNull(driver, "driver");
        if (byteLimit < 1L) {
            throw new IllegalArgumentException("byteLimit must be positive");
        }
        this.byteLimit = byteLimit;
    }

    @Override
    public void activate(List<ImageRaster> rasters) {
        Objects.requireNonNull(rasters, "rasters");
        requireOpenRenderThread();
        if (activeRasters == rasters) {
            return;
        }

        LinkedHashMap<Long, ImageRaster> required = new LinkedHashMap<>();
        long requiredBytes = 0L;
        for (int index = 0; index < rasters.size(); index++) {
            ImageRaster raster = Objects.requireNonNull(rasters.get(index), "rasters[" + index + "]");
            ImageRaster previous = required.putIfAbsent(raster.textureKey(), raster);
            if (previous != null) {
                requireSameTexture(previous, raster);
                continue;
            }
            requiredBytes = Math.addExact(requiredBytes, raster.byteSize());
        }
        if (requiredBytes > byteLimit) {
            throw new OpenGlRenderException(
                    "Active FandUI image textures require " + requiredBytes
                            + " bytes, exceeding the " + byteLimit + " byte cache limit");
        }

        long missingBytes = 0L;
        for (ImageRaster raster : required.values()) {
            Entry existing = entries.get(raster.textureKey());
            if (existing == null) {
                missingBytes = Math.addExact(missingBytes, raster.byteSize());
            } else {
                existing.requireCompatible(raster);
            }
        }

        Set<Long> nextActive = Set.copyOf(required.keySet());
        evictFor(missingBytes, nextActive);
        List<Long> created = new ArrayList<>(required.size());
        try {
            for (ImageRaster raster : required.values()) {
                if (entries.containsKey(raster.textureKey())) {
                    continue;
                }
                int textureId = driver.create(raster);
                Entry entry = new Entry(textureId, raster);
                entries.put(raster.textureKey(), entry);
                created.add(raster.textureKey());
                textureBytes = Math.addExact(textureBytes, entry.byteSize);
            }
        } catch (RuntimeException | Error exception) {
            activeRasters = null;
            activeKeys = Set.of();
            for (Long key : created) {
                try {
                    deleteEntry(key);
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
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
            throw new OpenGlRenderException("FandUI image texture cache could not free enough inactive data");
        }
    }

    @Override
    public Optional<OpenGlTexture> resolve(long textureKey, OpenGlSampling sampling) {
        Objects.requireNonNull(sampling, "sampling");
        requireOpenRenderThread();
        if (!activeKeys.contains(textureKey)) {
            return Optional.empty();
        }
        Entry entry = entries.get(textureKey);
        if (entry == null) {
            throw new OpenGlRenderException(
                    "Active FandUI image texture is missing: " + Long.toUnsignedString(textureKey));
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
            throw new IllegalStateException("OpenGL image texture cache is closed");
        }
        requireRenderThread();
    }

    private void requireRenderThread() {
        Thread current = Thread.currentThread();
        if (renderThread == null) {
            renderThread = current;
        } else if (renderThread != current) {
            throw new IllegalStateException("OpenGL image texture cache is confined to its first Render Thread");
        }
    }

    private static void requireSameTexture(ImageRaster first, ImageRaster second) {
        if (first.width() != second.width()
                || first.height() != second.height()
                || first.byteSize() != second.byteSize()
                || !Arrays.equals(first.cacheKeySha256(), second.cacheKeySha256())) {
            throw new OpenGlRenderException(
                    "Conflicting image rasters use key " + Long.toUnsignedString(first.textureKey()));
        }
    }

    record CacheStats(int entries, long bytes, int activeEntries) {
    }

    interface TextureDriver {
        int create(ImageRaster raster);

        void delete(int textureId);
    }

    private static final class Entry {
        private final int textureId;
        private final int width;
        private final int height;
        private final int byteSize;
        private final byte[] digest;

        private Entry(int textureId, ImageRaster raster) {
            if (textureId <= 0) {
                throw new OpenGlRenderException("OpenGL returned an invalid image texture id");
            }
            this.textureId = textureId;
            width = raster.width();
            height = raster.height();
            byteSize = raster.byteSize();
            digest = raster.cacheKeySha256();
        }

        private void requireCompatible(ImageRaster raster) {
            if (width != raster.width()
                    || height != raster.height()
                    || byteSize != raster.byteSize()
                    || !Arrays.equals(digest, raster.cacheKeySha256())) {
                throw new OpenGlRenderException(
                        "Image texture key collision: " + Long.toUnsignedString(raster.textureKey()));
            }
        }
    }

    private static final class LwjglTextureDriver implements TextureDriver {
        @Override
        public int create(ImageRaster raster) {
            return OpenGlTextureUploader.create(
                    raster.width(),
                    raster.height(),
                    raster.byteSize(),
                    raster.pixels(),
                    false,
                    OpenGlSampling.LINEAR,
                    "FandUI image texture");
        }

        @Override
        public void delete(int textureId) {
            OpenGlTextureUploader.delete(textureId);
        }
    }
}
