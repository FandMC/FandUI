package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.animation.AnimationEndReason;
import cn.fandmc.fandui.api.animation.AnimationFrameListener;
import cn.fandmc.fandui.api.animation.AnimationHandle;
import cn.fandmc.fandui.api.animation.AnimationManager;
import cn.fandmc.fandui.api.animation.AnimationSpec;
import cn.fandmc.fandui.api.session.SessionCloseReason;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class CoreAnimationManager implements AnimationManager {
    private final AbstractCoreSession session;
    private final List<Handle> animations = new ArrayList<>();

    CoreAnimationManager(AbstractCoreSession session) {
        this.session = session;
    }

    @Override
    public AnimationHandle start(AnimationSpec spec, AnimationFrameListener listener) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(listener, "listener");
        session.requireActiveOperation();
        Handle handle = new Handle(spec, listener, session.runtime().now());
        animations.add(handle);
        session.enterCallback();
        try {
            handle.emit(0.0);
        } catch (RuntimeException | Error exception) {
            handle.finish(AnimationEndReason.FAILED);
            session.requestClose(SessionCloseReason.FAILED, true);
            throw exception;
        } finally {
            session.exitCallback();
        }
        return handle;
    }

    void tick(long nowNanos) {
        for (Handle handle : List.copyOf(animations)) {
            if (!handle.active()) {
                animations.remove(handle);
                continue;
            }
            try {
                handle.tick(nowNanos);
            } catch (RuntimeException | Error exception) {
                handle.finish(AnimationEndReason.FAILED);
                session.requestClose(SessionCloseReason.FAILED, true);
                throw exception;
            }
        }
    }

    void closeForSession() {
        for (Handle handle : List.copyOf(animations)) {
            handle.finish(AnimationEndReason.SESSION_CLOSED);
        }
        animations.clear();
    }

    private final class Handle implements AnimationHandle {
        private final AnimationSpec spec;
        private final AnimationFrameListener listener;
        private final long startNanos;
        private final long delayNanos;
        private final long durationNanos;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final CompletableFuture<AnimationEndReason> completion = new CompletableFuture<>();
        private long lastProgressBits = Long.MIN_VALUE;

        private Handle(AnimationSpec spec, AnimationFrameListener listener, long startNanos) {
            this.spec = spec;
            this.listener = listener;
            this.startNanos = startNanos;
            this.delayNanos = saturatedNanos(spec.delay());
            this.durationNanos = saturatedNanos(spec.duration());
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public CompletableFuture<AnimationEndReason> completion() {
            return completion.copy();
        }

        @Override
        public void close() {
            if (finish(AnimationEndReason.CANCELLED)) {
                session.runtime().runCleanup(() -> animations.remove(this));
            }
        }

        private void tick(long nowNanos) {
            long elapsed = saturatedDifference(nowNanos, startNanos);
            if (elapsed < delayNanos) {
                return;
            }
            long running = elapsed - delayNanos;
            long totalDuration = spec.infinite()
                    ? Long.MAX_VALUE
                    : saturatedMultiply(durationNanos, spec.iterations());
            if (!spec.infinite() && running >= totalDuration) {
                emit(1.0);
                finish(AnimationEndReason.COMPLETED);
                animations.remove(this);
                return;
            }

            long iteration = running / durationNanos;
            double linear = (double) (running % durationNanos) / (double) durationNanos;
            if (spec.alternate() && (iteration & 1L) != 0L) {
                linear = 1.0 - linear;
            }
            emit(linear);
        }

        private void emit(double linearProgress) {
            double progress = spec.easing().transform(linearProgress);
            if (!Double.isFinite(progress)) {
                throw new IllegalStateException("Animation easing returned a non-finite value");
            }
            long bits = Double.doubleToLongBits(progress);
            if (bits == lastProgressBits) {
                return;
            }
            listener.frame(progress);
            lastProgressBits = bits;
            session.animationFrameProduced();
        }

        private boolean finish(AnimationEndReason reason) {
            if (!active.compareAndSet(true, false)) {
                return false;
            }
            completion.complete(reason);
            return true;
        }
    }

    private static long saturatedNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedDifference(long value, long start) {
        if (value <= start) {
            return 0L;
        }
        long result = value - start;
        return result < 0L ? Long.MAX_VALUE : result;
    }

    private static long saturatedMultiply(long value, int multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }
}
