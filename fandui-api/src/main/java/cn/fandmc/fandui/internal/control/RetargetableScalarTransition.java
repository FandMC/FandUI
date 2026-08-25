package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.animation.AnimationHandle;
import cn.fandmc.fandui.api.animation.AnimationSpec;
import cn.fandmc.fandui.api.component.ComponentContext;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.function.DoubleConsumer;

/** A scalar transition whose active animation follows the latest target without queuing updates. */
public final class RetargetableScalarTransition {
    private static final AnimationSpec DRIVER = AnimationSpec.duration(Duration.ofSeconds(1)).infinite().build();
    private static final long NOMINAL_FRAME_NANOS = 16_666_667L;
    private static final long MAX_FRAME_STEP_NANOS = 33_333_334L;
    private static final double SETTLE_DIVISOR = 128.0;

    private final double halfLifeNanos;
    private final DoubleConsumer frameConsumer;
    private double value;
    private double target;
    private double settleScale;
    private long lastFrameNanos = Long.MIN_VALUE;
    private boolean retargetedSinceSample;
    private @Nullable ComponentContext context;
    private @Nullable AnimationHandle handle;

    public RetargetableScalarTransition(
            double initialValue,
            Duration responseDuration,
            DoubleConsumer frameConsumer) {
        value = finite(initialValue, "initialValue");
        target = value;
        halfLifeNanos = responseNanos(responseDuration) / 7.0;
        this.frameConsumer = Objects.requireNonNull(frameConsumer, "frameConsumer");
    }

    public double value() {
        return value;
    }

    public void attach(ComponentContext context) {
        if (this.context != null) {
            throw new IllegalStateException("Transition is already attached");
        }
        this.context = Objects.requireNonNull(context, "context");
    }

    public void detach(ComponentContext context) {
        if (this.context != context) {
            throw new IllegalStateException("Transition detached from an unexpected context");
        }
        cancel();
        this.context = null;
        value = target;
        settleScale = 0.0;
        lastFrameNanos = Long.MIN_VALUE;
        retargetedSinceSample = false;
    }

    public void setTarget(double target) {
        double checked = finite(target, "target");
        if (Double.compare(this.target, checked) == 0) {
            return;
        }
        this.target = checked;
        retargetedSinceSample = true;

        ComponentContext attached = context;
        if (attached == null) {
            apply(checked);
            return;
        }
        if (Double.compare(value, checked) == 0) {
            cancel();
            return;
        }

        settleScale = Math.max(settleScale, Math.abs(checked - value));
        AnimationHandle current = handle;
        if (current == null || !current.active()) {
            start(attached);
        }
    }

    public void snapTo(double target) {
        double checked = finite(target, "target");
        this.target = checked;
        cancel();
        settleScale = 0.0;
        lastFrameNanos = Long.MIN_VALUE;
        retargetedSinceSample = false;
        apply(checked);
    }

    public double sample(long frameTimeNanos) {
        if (frameTimeNanos < 0L) {
            throw new IllegalArgumentException("frameTimeNanos must not be negative");
        }
        boolean retargeted = retargetedSinceSample;
        retargetedSinceSample = false;
        if (Double.compare(value, target) == 0) {
            cancel();
            return value;
        }

        long elapsed = lastFrameNanos == Long.MIN_VALUE
                ? NOMINAL_FRAME_NANOS
                : Math.min(MAX_FRAME_STEP_NANOS, positiveDifference(frameTimeNanos, lastFrameNanos));
        lastFrameNanos = frameTimeNanos;
        if (elapsed > 0L) {
            double retention = Math.pow(0.5, elapsed / halfLifeNanos);
            value = target + (value - target) * retention;
        }
        if (!retargeted && Math.abs(target - value) <= settleScale / SETTLE_DIVISOR) {
            value = target;
            settleScale = 0.0;
            cancel();
        }
        return value;
    }

    private void start(ComponentContext context) {
        lastFrameNanos = Long.MIN_VALUE;
        handle = context.session().animations().start(DRIVER, ignored -> frameConsumer.accept(value));
    }

    private void apply(double next) {
        if (Double.compare(value, next) == 0) {
            return;
        }
        value = next;
        frameConsumer.accept(next);
    }

    private void cancel() {
        AnimationHandle current = handle;
        handle = null;
        if (current != null) {
            current.close();
        }
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static long responseNanos(Duration duration) {
        Objects.requireNonNull(duration, "responseDuration");
        long nanos;
        try {
            nanos = duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("responseDuration is too large", exception);
        }
        if (nanos <= 0L) {
            throw new IllegalArgumentException("responseDuration must be positive");
        }
        return nanos;
    }

    private static long positiveDifference(long value, long previous) {
        if (value <= previous) {
            return 0L;
        }
        long difference = value - previous;
        return difference < 0L ? Long.MAX_VALUE : difference;
    }
}
