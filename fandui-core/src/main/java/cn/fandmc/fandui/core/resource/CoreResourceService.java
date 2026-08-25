package cn.fandmc.fandui.core.resource;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceKind;
import cn.fandmc.fandui.api.resource.ResourceRegistration;
import cn.fandmc.fandui.api.resource.ResourceReloadListener;
import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.resource.ResourceSource;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.text.FontFamily;
import cn.fandmc.fandui.api.text.FontFamilies;
import cn.fandmc.fandui.core.runtime.UiThreadDispatcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Owns stable resource handles and atomically publishes decoded resource generations.
 */
public final class CoreResourceService implements ResourceService, AutoCloseable {
    private static final int MAX_FONT_BYTES = 64 * 1024 * 1024;
    private static final Comparator<ResourceSlot> SLOT_ORDER = Comparator
            .comparing(ResourceSlot::kind)
            .thenComparing(ResourceSlot::key);

    private final UiThreadDispatcher dispatcher;
    private final ExecutorService reloadExecutor;
    private final ConcurrentMap<UiKey, CoreImageRef> images = new ConcurrentHashMap<>();
    private final ConcurrentMap<UiKey, FontFamily> fonts = new ConcurrentHashMap<>();
    private final Map<ResourceSlot, CoreRegistration> registrations = new LinkedHashMap<>();
    private final List<ReloadRegistration> reloadListeners = new ArrayList<>();
    private volatile FontResourceSnapshot fontSnapshot = FontResourceSnapshot.empty(0L);
    private Consumer<FontResourceSnapshot> fontValidator = ignored -> { };
    private volatile ResourceLookup lastLookup = ResourceLookup.empty();
    private long registrationRevision;
    private boolean reloadInProgress;
    private volatile boolean closed;

    public CoreResourceService(UiThreadDispatcher dispatcher) {
        this(dispatcher, newReloadExecutor());
    }

    CoreResourceService(UiThreadDispatcher dispatcher, ExecutorService reloadExecutor) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.reloadExecutor = Objects.requireNonNull(reloadExecutor, "reloadExecutor");
    }

    @Override
    public long generation() {
        return fontSnapshot.generation();
    }

    @Override
    public ImageRef image(UiKey key) {
        Objects.requireNonNull(key, "key");
        requireOpen();
        return images.computeIfAbsent(key, candidate -> new CoreImageRef(this, candidate));
    }

    @Override
    public FontFamily font(UiKey key) {
        Objects.requireNonNull(key, "key");
        requireOpen();
        if (key.equals(FontFamilies.DEFAULT.key())) {
            return FontFamilies.DEFAULT;
        }
        return fonts.computeIfAbsent(key, FontFamily::new);
    }

    @Override
    public ResourceRegistration registerImage(UiKey key, ResourceSource source) {
        return register(ResourceKind.IMAGE, key, source);
    }

    @Override
    public ResourceRegistration registerFont(UiKey key, ResourceSource source) {
        return register(ResourceKind.FONT, key, source);
    }

    @Override
    public EventRegistration onReload(ResourceReloadListener listener) {
        Objects.requireNonNull(listener, "listener");
        assertUiThread();
        requireOpen();
        ReloadRegistration registration = new ReloadRegistration(listener);
        reloadListeners.add(registration);
        return registration;
    }

    @Override
    public long reload() {
        return reload(lastLookup);
    }

    /**
     * Loads and decodes one complete candidate generation on the dedicated reload worker.
     */
    public long reload(ResourceLookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        assertUiThread();
        requireOpen();
        if (reloadInProgress) {
            throw new IllegalStateException("A FandUI resource reload is already in progress");
        }
        reloadInProgress = true;
        lastLookup = lookup;
        ReloadSnapshot snapshot;
        try {
            snapshot = snapshot(lookup);
        } catch (RuntimeException | Error exception) {
            reloadInProgress = false;
            throw exception;
        }
        CompletableFuture<ReloadCandidate> future;
        try {
            future = CompletableFuture.supplyAsync(() -> loadCandidate(snapshot), reloadExecutor);
        } catch (RuntimeException exception) {
            reloadInProgress = false;
            restoreLoadingStates(snapshot);
            throw exception;
        }

        ReloadCandidate candidate;
        try {
            candidate = future.join();
        } catch (CompletionException exception) {
            restoreLoadingStates(snapshot);
            throw new ResourceReloadException(
                    "FandUI resource reload worker failed",
                    unwrap(exception));
        } finally {
            reloadInProgress = false;
        }
        return applyCandidate(candidate);
    }

    /** Advances an empty generation and remains compatible with the initial bridge contract. */
    public long applyReload() {
        return reload(ResourceLookup.empty());
    }

    /** Resolves a framework-owned ready image handle to immutable CPU pixels. */
    public ImageRaster resolveImage(ImageRef image) {
        Objects.requireNonNull(image, "image");
        requireOpen();
        if (!(image instanceof CoreImageRef ref) || ref.owner != this) {
            throw new IllegalArgumentException("ImageRef is not owned by this FandUI runtime");
        }
        ImageRaster raster = ref.snapshot.get().raster;
        if (raster == null) {
            throw new IllegalStateException("Image resource is not ready: " + ref.key);
        }
        return raster;
    }

    public long textureKey(ImageRef image) {
        return resolveImage(image).textureKey();
    }

    public List<RegisteredSource> registeredSources() {
        assertUiThread();
        requireOpen();
        return registrations.values().stream()
                .filter(CoreRegistration::active)
                .map(registration -> new RegisteredSource(
                        registration.kind(),
                        registration.key(),
                        registration.source))
                .toList();
    }

    Optional<byte[]> fontBytes(FontFamily family) {
        Objects.requireNonNull(family, "family");
        byte[] bytes = fontSnapshot.copyFonts().get(family.key());
        return bytes == null ? Optional.empty() : Optional.of(bytes.clone());
    }

    public FontResourceSnapshot fontSnapshot() {
        return fontSnapshot;
    }

    /** Installs the production text implementation's candidate-generation validator. */
    public void setFontValidator(Consumer<FontResourceSnapshot> validator) {
        Objects.requireNonNull(validator, "validator");
        assertUiThread();
        requireOpen();
        fontValidator = validator;
    }

    private ReloadSnapshot snapshot(ResourceLookup lookup) {
        long revision = registrationRevision;
        TreeSet<ResourceSlot> slots = new TreeSet<>(SLOT_ORDER);
        images.keySet().forEach(key -> slots.add(new ResourceSlot(ResourceKind.IMAGE, key)));
        fonts.keySet().forEach(key -> slots.add(new ResourceSlot(ResourceKind.FONT, key)));
        slots.addAll(registrations.keySet());

        List<SourcePlan> plans = new ArrayList<>(slots.size());
        for (ResourceSlot slot : slots) {
            CoreRegistration registration = registrations.get(slot);
            boolean explicit = registration != null && registration.active();
            ResourceSource source = explicit ? registration.source : findSource(lookup, slot);
            plans.add(new SourcePlan(slot, source, explicit));
        }

        Map<UiKey, CoreImageRef> imageRefs = new LinkedHashMap<>();
        for (ResourceSlot slot : slots) {
            if (slot.kind == ResourceKind.IMAGE) {
                imageRefs.put(slot.key, images.computeIfAbsent(
                        slot.key,
                        key -> new CoreImageRef(this, key)));
            }
        }
        Map<UiKey, ImageSnapshot> previousImages = new LinkedHashMap<>();
        imageRefs.forEach((key, ref) -> previousImages.put(key, ref.beginLoading()));
        return new ReloadSnapshot(revision, List.copyOf(plans), Map.copyOf(previousImages));
    }

    private static ResourceSource findSource(ResourceLookup lookup, ResourceSlot slot) {
        Optional<ResourceSource> source = Objects.requireNonNull(
                lookup.find(slot.kind, slot.key),
                "ResourceLookup.find()");
        return source.orElse(null);
    }

    private static ReloadCandidate loadCandidate(ReloadSnapshot snapshot) {
        Map<UiKey, ImageResult> imageResults = new LinkedHashMap<>();
        Map<UiKey, byte[]> fontResults = new LinkedHashMap<>();
        List<ResourceFailure> failures = new ArrayList<>();
        for (SourcePlan plan : snapshot.plans) {
            if (plan.source == null) {
                if (plan.slot.kind == ResourceKind.IMAGE) {
                    imageResults.put(plan.slot.key, ImageResult.missing());
                }
                continue;
            }
            try {
                byte[] bytes = Objects.requireNonNull(
                        plan.source.load(),
                        "ResourceSource.load()");
                if (plan.slot.kind == ResourceKind.IMAGE) {
                    imageResults.put(
                            plan.slot.key,
                            ImageResult.decoded(ImageDecoder.decode(bytes, plan.source.format())));
                } else {
                    if (bytes.length == 0 || bytes.length > MAX_FONT_BYTES) {
                        throw new IOException("Font source has an invalid byte length");
                    }
                    fontResults.put(plan.slot.key, bytes.clone());
                }
            } catch (IOException | RuntimeException exception) {
                ResourceState failureState = missing(exception)
                        ? ResourceState.MISSING
                        : ResourceState.FAILED;
                if (plan.slot.kind == ResourceKind.IMAGE) {
                    imageResults.put(plan.slot.key, ImageResult.failure(failureState, exception));
                }
                if (plan.explicit || failureState == ResourceState.FAILED) {
                    failures.add(new ResourceFailure(plan.slot, exception));
                }
            }
        }
        return new ReloadCandidate(
                snapshot,
                Map.copyOf(imageResults),
                copyFontMap(fontResults),
                List.copyOf(failures));
    }

    private long applyCandidate(ReloadCandidate candidate) {
        assertUiThread();
        requireOpen();
        if (candidate.snapshot.registrationRevision != registrationRevision) {
            restoreLoadingStates(candidate.snapshot);
            throw new ResourceReloadException(
                    "FandUI resource registrations changed during reload",
                    new IllegalStateException("stale resource candidate"));
        }
        if (!candidate.failures.isEmpty()) {
            restoreLoadingStates(candidate.snapshot);
            applyInitialFailureStates(candidate);
            throw candidate.failure();
        }

        long newGeneration;
        try {
            newGeneration = Math.incrementExact(fontSnapshot.generation());
        } catch (ArithmeticException exception) {
            restoreLoadingStates(candidate.snapshot);
            throw new IllegalStateException("FandUI resource generation is exhausted", exception);
        }
        FontResourceSnapshot candidateFonts = FontResourceSnapshot.create(newGeneration, candidate.fonts);
        try {
            fontValidator.accept(candidateFonts);
        } catch (RuntimeException | Error exception) {
            restoreLoadingStates(candidate.snapshot);
            throw new ResourceReloadException("FandUI custom font validation failed", exception);
        }
        for (Map.Entry<UiKey, ImageResult> entry : candidate.images.entrySet()) {
            CoreImageRef ref = images.get(entry.getKey());
            if (ref == null) {
                continue;
            }
            ImageResult result = entry.getValue();
            if (result.decoded != null) {
                PngImageDecoder.DecodedImage decoded = result.decoded;
                ref.ready(new ImageRaster(
                        entry.getKey(),
                        newGeneration,
                        decoded.textureKey(),
                        decoded.cacheKeySha256(),
                        decoded.width(),
                        decoded.height(),
                        decoded.pixels()));
            } else {
                ref.missing();
            }
        }
        long oldGeneration = fontSnapshot.generation();
        fontSnapshot = candidateFonts;
        notifyReloaded(oldGeneration, newGeneration);
        return newGeneration;
    }

    private void applyInitialFailureStates(ReloadCandidate candidate) {
        for (Map.Entry<UiKey, ImageResult> entry : candidate.images.entrySet()) {
            ImageResult result = entry.getValue();
            if (result.failureState == null) {
                continue;
            }
            CoreImageRef ref = images.get(entry.getKey());
            if (ref != null) {
                ref.failIfUnready(result.failureState, result.failure);
            }
        }
    }

    private void restoreLoadingStates(ReloadSnapshot snapshot) {
        for (Map.Entry<UiKey, ImageSnapshot> entry : snapshot.previousImages.entrySet()) {
            CoreImageRef ref = images.get(entry.getKey());
            if (ref != null) {
                ref.restoreIfLoading(entry.getValue());
            }
        }
    }

    private void notifyReloaded(long oldGeneration, long newGeneration) {
        RuntimeException failure = null;
        for (ReloadRegistration registration : List.copyOf(reloadListeners)) {
            RuntimeException listenerFailure = registration.notifyReloaded(oldGeneration, newGeneration);
            if (listenerFailure != null) {
                failure = combine(failure, listenerFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private ResourceRegistration register(ResourceKind kind, UiKey key, ResourceSource source) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        assertUiThread();
        requireOpen();
        ResourceSlot slot = new ResourceSlot(kind, key);
        CoreRegistration existing = registrations.get(slot);
        if (existing != null && existing.active()) {
            throw new IllegalStateException("Resource is already registered: " + kind + " " + key);
        }
        if (kind == ResourceKind.IMAGE) {
            images.computeIfAbsent(key, candidate -> new CoreImageRef(this, candidate));
        } else {
            if (key.equals(FontFamilies.DEFAULT.key())) {
                throw new IllegalArgumentException("The default FandUI font family is reserved");
            }
            fonts.computeIfAbsent(key, FontFamily::new);
        }
        CoreRegistration registration = new CoreRegistration(slot, source);
        registrations.put(slot, registration);
        registrationRevision = incrementRevision(registrationRevision);
        return registration;
    }

    private void removeRegistration(CoreRegistration registration) {
        if (registrations.remove(registration.slot, registration)) {
            registrationRevision = incrementRevision(registrationRevision);
        }
    }

    private static long incrementRevision(long revision) {
        try {
            return Math.incrementExact(revision);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("FandUI resource registration revision is exhausted", exception);
        }
    }

    private void assertUiThread() {
        if (!dispatcher.isUiThread()) {
            throw new IllegalStateException("FandUI resource operation requires the UI thread");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("FandUI resource service is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        assertUiThread();
        closed = true;
        registrations.values().forEach(CoreRegistration::deactivate);
        registrations.clear();
        reloadListeners.forEach(ReloadRegistration::deactivate);
        reloadListeners.clear();
        fontSnapshot = FontResourceSnapshot.empty(fontSnapshot.generation());
        fontValidator = ignored -> { };
        reloadExecutor.shutdownNow();
    }

    public record RegisteredSource(ResourceKind kind, UiKey key, ResourceSource source) {
        public RegisteredSource {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(source, "source");
        }
    }

    private record ResourceSlot(ResourceKind kind, UiKey key) {
        private ResourceSlot {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(key, "key");
        }
    }

    private record SourcePlan(ResourceSlot slot, ResourceSource source, boolean explicit) {
    }

    private record ReloadSnapshot(
            long registrationRevision,
            List<SourcePlan> plans,
            Map<UiKey, ImageSnapshot> previousImages) {
    }

    private record ReloadCandidate(
            ReloadSnapshot snapshot,
            Map<UiKey, ImageResult> images,
            Map<UiKey, byte[]> fonts,
            List<ResourceFailure> failures) {
        private ResourceReloadException failure() {
            ResourceFailure first = failures.get(0);
            ResourceReloadException result = new ResourceReloadException(
                    "FandUI rejected a candidate resource generation after "
                            + failures.size() + " source failure(s); first: " + first.slot,
                    first.cause);
            for (int index = 1; index < failures.size(); index++) {
                ResourceFailure failure = failures.get(index);
                result.addSuppressed(new IOException(
                        "Resource failed: " + failure.slot,
                        failure.cause));
            }
            return result;
        }
    }

    private record ResourceFailure(ResourceSlot slot, Throwable cause) {
    }

    private static final class ImageResult {
        private final PngImageDecoder.DecodedImage decoded;
        private final ResourceState failureState;
        private final Throwable failure;

        private ImageResult(
                PngImageDecoder.DecodedImage decoded,
                ResourceState failureState,
                Throwable failure) {
            this.decoded = decoded;
            this.failureState = failureState;
            this.failure = failure;
        }

        private static ImageResult decoded(PngImageDecoder.DecodedImage decoded) {
            return new ImageResult(Objects.requireNonNull(decoded, "decoded"), null, null);
        }

        private static ImageResult missing() {
            return new ImageResult(null, ResourceState.MISSING, null);
        }

        private static ImageResult failure(ResourceState state, Throwable failure) {
            if (state != ResourceState.MISSING && state != ResourceState.FAILED) {
                throw new IllegalArgumentException("Invalid image failure state");
            }
            return new ImageResult(null, state, Objects.requireNonNull(failure, "failure"));
        }
    }

    private static final class CoreImageRef implements ImageRef {
        private final CoreResourceService owner;
        private final UiKey key;
        private final AtomicReference<ImageSnapshot> snapshot =
                new AtomicReference<>(ImageSnapshot.unresolved());

        private CoreImageRef(CoreResourceService owner, UiKey key) {
            this.owner = owner;
            this.key = key;
        }

        @Override
        public UiKey key() {
            return key;
        }

        @Override
        public ResourceState state() {
            return snapshot.get().state;
        }

        @Override
        public Optional<ImageInfo> info() {
            ImageRaster raster = snapshot.get().raster;
            return raster == null ? Optional.empty() : Optional.of(raster.info());
        }

        @Override
        public Optional<Throwable> failure() {
            return Optional.ofNullable(snapshot.get().failure);
        }

        private ImageSnapshot beginLoading() {
            ImageSnapshot previous = snapshot.get();
            if (previous.state != ResourceState.READY) {
                snapshot.set(ImageSnapshot.loading());
            }
            return previous;
        }

        private void ready(ImageRaster raster) {
            snapshot.set(new ImageSnapshot(ResourceState.READY, raster, null));
        }

        private void missing() {
            snapshot.set(new ImageSnapshot(ResourceState.MISSING, null, null));
        }

        private void failIfUnready(ResourceState state, Throwable failure) {
            snapshot.updateAndGet(current -> current.state == ResourceState.READY
                    ? current
                    : new ImageSnapshot(state, null, failure));
        }

        private void restoreIfLoading(ImageSnapshot previous) {
            snapshot.compareAndSet(ImageSnapshot.loading(), previous);
        }
    }

    private record ImageSnapshot(ResourceState state, ImageRaster raster, Throwable failure) {
        private static final ImageSnapshot UNRESOLVED =
                new ImageSnapshot(ResourceState.UNRESOLVED, null, null);
        private static final ImageSnapshot LOADING =
                new ImageSnapshot(ResourceState.LOADING, null, null);

        private ImageSnapshot {
            Objects.requireNonNull(state, "state");
            if ((state == ResourceState.READY) != (raster != null)) {
                throw new IllegalArgumentException("Only a READY image snapshot may contain pixels");
            }
        }

        private static ImageSnapshot unresolved() {
            return UNRESOLVED;
        }

        private static ImageSnapshot loading() {
            return LOADING;
        }
    }

    private final class CoreRegistration implements ResourceRegistration {
        private final ResourceSlot slot;
        private final ResourceSource source;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private CoreRegistration(ResourceSlot slot, ResourceSource source) {
            this.slot = slot;
            this.source = source;
        }

        @Override
        public UiKey key() {
            return slot.key;
        }

        @Override
        public ResourceKind kind() {
            return slot.kind;
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void close() {
            if (!active.compareAndSet(true, false)) {
                return;
            }
            Runnable remove = () -> removeRegistration(this);
            if (dispatcher.isUiThread()) {
                remove.run();
            } else {
                dispatcher.execute(remove);
            }
        }

        private void deactivate() {
            active.set(false);
        }
    }

    private final class ReloadRegistration implements EventRegistration {
        private final ResourceReloadListener listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private ReloadRegistration(ResourceReloadListener listener) {
            this.listener = listener;
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void close() {
            if (!active.compareAndSet(true, false)) {
                return;
            }
            Runnable remove = () -> reloadListeners.remove(this);
            if (dispatcher.isUiThread()) {
                remove.run();
            } else {
                dispatcher.execute(remove);
            }
        }

        private RuntimeException notifyReloaded(long oldGeneration, long newGeneration) {
            if (!active.get()) {
                return null;
            }
            try {
                listener.reloaded(oldGeneration, newGeneration);
                return null;
            } catch (RuntimeException exception) {
                return exception;
            }
        }

        private void deactivate() {
            active.set(false);
        }
    }

    private static boolean missing(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof FileNotFoundException || current instanceof NoSuchFileException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static RuntimeException combine(RuntimeException first, RuntimeException second) {
        if (first == null) {
            return second;
        }
        first.addSuppressed(second);
        return first;
    }

    private static Map<UiKey, byte[]> copyFontMap(Map<UiKey, byte[]> source) {
        Map<UiKey, byte[]> copy = new LinkedHashMap<>();
        source.forEach((key, bytes) -> copy.put(key, bytes.clone()));
        return Map.copyOf(copy);
    }

    private static ExecutorService newReloadExecutor() {
        ThreadFactory factory = action -> {
            Thread thread = new Thread(action, "FandUI-Resource-Reload");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(factory);
    }
}
