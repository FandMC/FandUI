package cn.fandmc.fandui.api.animation;

import java.time.Duration;
import java.util.Objects;

/** Immutable timing, repetition, easing, and direction specification for an animation. */
public final class AnimationSpec {
    private final Duration duration;
    private final Duration delay;
    private final Easing easing;
    private final int iterations;
    private final boolean infinite;
    private final boolean alternate;

    private AnimationSpec(Builder builder) {
        this.duration = builder.duration;
        this.delay = builder.delay;
        this.easing = builder.easing;
        this.iterations = builder.iterations;
        this.infinite = builder.infinite;
        this.alternate = builder.alternate;
    }

    public static Builder duration(Duration duration) {
        return new Builder(duration);
    }

    public Duration duration() {
        return duration;
    }

    public Duration delay() {
        return delay;
    }

    public Easing easing() {
        return easing;
    }

    public int iterations() {
        return iterations;
    }

    public boolean infinite() {
        return infinite;
    }

    public boolean alternate() {
        return alternate;
    }

    public static final class Builder {
        private final Duration duration;
        private Duration delay = Duration.ZERO;
        private Easing easing = Easings.LINEAR;
        private int iterations = 1;
        private boolean infinite;
        private boolean alternate;

        private Builder(Duration duration) {
            this.duration = requirePositive(Objects.requireNonNull(duration, "duration"), "duration");
        }

        public Builder delay(Duration delay) {
            Objects.requireNonNull(delay, "delay");
            if (delay.isNegative()) {
                throw new IllegalArgumentException("delay must not be negative");
            }
            this.delay = delay;
            return this;
        }

        public Builder easing(Easing easing) {
            this.easing = Objects.requireNonNull(easing, "easing");
            return this;
        }

        public Builder iterations(int iterations) {
            if (iterations < 1) {
                throw new IllegalArgumentException("iterations must be at least 1");
            }
            this.iterations = iterations;
            this.infinite = false;
            return this;
        }

        public Builder infinite() {
            this.iterations = 1;
            this.infinite = true;
            return this;
        }

        public Builder alternate(boolean value) {
            this.alternate = value;
            return this;
        }

        public AnimationSpec build() {
            validateEndpoint(easing, 0.0);
            validateEndpoint(easing, 1.0);
            return new AnimationSpec(this);
        }

        private static void validateEndpoint(Easing easing, double endpoint) {
            double value = easing.transform(endpoint);
            if (!Double.isFinite(value) || Double.compare(value, endpoint) != 0) {
                throw new IllegalArgumentException("Easing must preserve endpoint " + endpoint);
            }
        }

        private static Duration requirePositive(Duration duration, String name) {
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return duration;
        }
    }
}
