package cn.fandmc.fandui.core.runtime;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Supplies a process-local, non-negative monotonic timeline. */
public final class MonotonicClock implements LongSupplier {
    private final long originNanos = System.nanoTime();
    private final AtomicLong last = new AtomicLong();

    @Override
    public long getAsLong() {
        long elapsed = System.nanoTime() - originNanos;
        if (elapsed < 0L) {
            elapsed = Long.MAX_VALUE;
        }
        return last.accumulateAndGet(elapsed, Math::max);
    }
}
