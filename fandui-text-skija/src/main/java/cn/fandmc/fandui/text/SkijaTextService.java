package cn.fandmc.fandui.text;

import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.text.TextGeometry;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextPosition;
import cn.fandmc.fandui.api.text.TextRange;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextService;
import cn.fandmc.fandui.core.resource.FontResourceSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Skija-backed asynchronous text layout and CPU raster service. */
public final class SkijaTextService implements TextService, AutoCloseable {
    private static final int MAX_PENDING_JOBS = 256;

    private final Object lifecycleLock = new Object();
    private final Supplier<FontResourceSnapshot> fontResources;
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();
    private final ThreadPoolExecutor executor;
    private final SkijaTextEngine engine;
    private final Map<TextCacheKey, CompletableFuture<LayoutMetrics>> layoutJobs = new HashMap<>();
    private final Map<RasterJobKey, CompletableFuture<TextRaster>> rasterJobs = new HashMap<>();

    private boolean closed;
    private int pendingJobs;

    public SkijaTextService(LongSupplier resourceGeneration) {
        this(
                generationSnapshot(resourceGeneration),
                SkijaTextEngine.DEFAULT_LAYOUT_CACHE_ENTRIES,
                SkijaTextEngine.DEFAULT_RASTER_CACHE_BYTES);
    }

    public SkijaTextService(Supplier<FontResourceSnapshot> fontResources) {
        this(
                fontResources,
                SkijaTextEngine.DEFAULT_LAYOUT_CACHE_ENTRIES,
                SkijaTextEngine.DEFAULT_RASTER_CACHE_BYTES);
    }

    SkijaTextService(
            LongSupplier resourceGeneration,
            int layoutCacheEntries,
            long rasterCacheBytes) {
        this(generationSnapshot(resourceGeneration), layoutCacheEntries, rasterCacheBytes);
    }

    SkijaTextService(
            Supplier<FontResourceSnapshot> fontResources,
            int layoutCacheEntries,
            long rasterCacheBytes) {
        this.fontResources = Objects.requireNonNull(fontResources, "fontResources");
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "FandUI Text Worker");
            thread.setDaemon(true);
            workerThread.set(thread);
            return thread;
        };
        executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_JOBS + 1),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());

        Future<SkijaTextEngine> startup = executor.submit(
                () -> new SkijaTextEngine(layoutCacheEntries, rasterCacheBytes));
        try {
            engine = startup.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            throw new IllegalStateException("Interrupted while starting the FandUI text worker", exception);
        } catch (ExecutionException exception) {
            executor.shutdownNow();
            throw startupFailure(exception.getCause());
        }
    }

    @Override
    public CompletableFuture<TextLayout> layout(TextRequest request) {
        Objects.requireNonNull(request, "request");
        FontResourceSnapshot resources;
        try {
            resources = Objects.requireNonNull(fontResources.get(), "fontResources.get()");
        } catch (RuntimeException | Error exception) {
            return CompletableFuture.failedFuture(exception);
        }

        TextCacheKey key = TextCacheKey.from(request, resources);
        CompletableFuture<LayoutMetrics> shared;
        synchronized (lifecycleLock) {
            if (closed) {
                return closedFuture();
            }
            shared = layoutJobs.get(key);
            if (shared == null) {
                shared = new CompletableFuture<>();
                layoutJobs.put(key, shared);
                scheduleLayout(key, request, resources, shared);
            }
        }
        CompletableFuture<LayoutMetrics> source = shared;
        return source.thenApply(metrics -> new SkijaTextLayout(this, request, key, metrics, resources));
    }

    @Override
    public CompletableFuture<TextPosition> hitTest(TextLayout layout, Point position) {
        final SkijaTextLayout skijaLayout;
        try {
            skijaLayout = requireOwnedLayout(layout);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        Point checkedPosition = Objects.requireNonNull(position, "position");
        return schedule(() -> engine.hitTest(skijaLayout, checkedPosition));
    }

    @Override
    public CompletableFuture<TextGeometry> geometry(
            TextLayout layout,
            TextPosition caret,
            List<TextRange> ranges) {
        final SkijaTextLayout skijaLayout;
        try {
            skijaLayout = requireOwnedLayout(layout);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        TextPosition checkedCaret = Objects.requireNonNull(caret, "caret");
        List<TextRange> checkedRanges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
        return schedule(() -> engine.geometry(skijaLayout, checkedCaret, checkedRanges));
    }

    public CompletableFuture<TextRaster> raster(TextLayout layout, float deviceScale) {
        final SkijaTextLayout skijaLayout;
        try {
            skijaLayout = requireOwnedLayout(layout);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(
                    exception);
        }

        int scaleUnits;
        try {
            scaleUnits = TextRaster.quantizeScale(deviceScale);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        RasterJobKey key = new RasterJobKey(skijaLayout.cacheKey(), scaleUnits);
        CompletableFuture<TextRaster> shared;
        synchronized (lifecycleLock) {
            if (closed) {
                return closedFuture();
            }
            shared = rasterJobs.get(key);
            if (shared == null) {
                shared = new CompletableFuture<>();
                rasterJobs.put(key, shared);
                scheduleRaster(key, skijaLayout, scaleUnits, shared);
            }
        }
        return shared.thenApply(raster -> raster);
    }

    private <T> CompletableFuture<T> schedule(Job<T> job) {
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (closed) {
                return closedFuture();
            }
            if (!reserveJob(result)) {
                return result;
            }
            executeReserved(result, job);
        }
        return result;
    }

    CompletableFuture<SkijaTextEngine.CacheStats> cacheStats() {
        CompletableFuture<SkijaTextEngine.CacheStats> result = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (closed) {
                return closedFuture();
            }
            if (!reserveJob(result)) {
                return result;
            }
            executeReserved(result, () -> engine.cacheStats());
        }
        return result;
    }

    /** Validates and prepares a candidate custom-font generation on the Skija owner thread. */
    public void validateFonts(FontResourceSnapshot resources) {
        Objects.requireNonNull(resources, "resources");
        try {
            schedule(() -> {
                engine.validateFonts(resources);
                return null;
            }).join();
        } catch (java.util.concurrent.CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private void scheduleLayout(
            TextCacheKey key,
            TextRequest request,
            FontResourceSnapshot resources,
            CompletableFuture<LayoutMetrics> result) {
        if (!reserveJob(result)) {
            layoutJobs.remove(key, result);
            return;
        }
        executeReserved(
                result,
                () -> engine.layout(key, request, resources),
                () -> layoutJobs.remove(key, result));
    }

    private void scheduleRaster(
            RasterJobKey key,
            SkijaTextLayout layout,
            int scaleUnits,
            CompletableFuture<TextRaster> result) {
        if (!reserveJob(result)) {
            rasterJobs.remove(key, result);
            return;
        }
        executeReserved(result, () -> engine.raster(layout, scaleUnits), () -> rasterJobs.remove(key, result));
    }

    private boolean reserveJob(CompletableFuture<?> result) {
        if (pendingJobs == MAX_PENDING_JOBS) {
            result.completeExceptionally(new RejectedExecutionException(
                    "FandUI text worker has " + MAX_PENDING_JOBS + " pending jobs"));
            return false;
        }
        pendingJobs++;
        return true;
    }

    private <T> void executeReserved(CompletableFuture<T> result, Job<T> job) {
        executeReserved(result, job, () -> {
        });
    }

    private <T> void executeReserved(
            CompletableFuture<T> result,
            Job<T> job,
            Runnable removeInFlight) {
        try {
            executor.execute(() -> {
                try {
                    result.complete(job.run());
                } catch (RuntimeException | Error exception) {
                    result.completeExceptionally(exception);
                } finally {
                    synchronized (lifecycleLock) {
                        removeInFlight.run();
                        pendingJobs--;
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            removeInFlight.run();
            pendingJobs--;
            result.completeExceptionally(exception);
        }
    }

    @Override
    public void close() {
        CompletableFuture<Void> shutdown = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            try {
                executor.execute(() -> {
                    try {
                        engine.close();
                        shutdown.complete(null);
                    } catch (RuntimeException | Error exception) {
                        shutdown.completeExceptionally(exception);
                    }
                });
            } catch (RejectedExecutionException exception) {
                shutdown.completeExceptionally(exception);
            }
            executor.shutdown();
        }
        if (Thread.currentThread() == workerThread.get()) {
            return;
        }
        shutdown.join();
    }

    private static IllegalStateException startupFailure(Throwable cause) {
        if (cause instanceof IllegalStateException stateException) {
            return stateException;
        }
        return new IllegalStateException("Failed to start the FandUI text worker", cause);
    }

    private static Supplier<FontResourceSnapshot> generationSnapshot(LongSupplier generation) {
        LongSupplier checked = Objects.requireNonNull(generation, "resourceGeneration");
        return () -> FontResourceSnapshot.empty(checked.getAsLong());
    }

    private static <T> CompletableFuture<T> closedFuture() {
        return CompletableFuture.failedFuture(new IllegalStateException("SkijaTextService is closed"));
    }

    private SkijaTextLayout requireOwnedLayout(TextLayout layout) {
        Objects.requireNonNull(layout, "layout");
        if (!(layout instanceof SkijaTextLayout skijaLayout) || !skijaLayout.belongsTo(this)) {
            throw new IllegalArgumentException("TextLayout was not produced by this SkijaTextService");
        }
        return skijaLayout;
    }

    @FunctionalInterface
    private interface Job<T> {
        T run();
    }

    private record RasterJobKey(TextCacheKey layout, int scaleUnits) {
        private RasterJobKey {
            Objects.requireNonNull(layout, "layout");
        }
    }
}
