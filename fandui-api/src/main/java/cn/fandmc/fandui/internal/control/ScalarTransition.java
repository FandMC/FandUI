package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.animation.AnimationHandle;
import cn.fandmc.fandui.api.animation.AnimationSpec;
import cn.fandmc.fandui.api.component.ComponentContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.DoubleConsumer;

/** Session-backed scalar transition shared by standard animated controls. */
public final class ScalarTransition {
    private final AnimationSpec spec;
    private final DoubleConsumer frameConsumer;
    private double value;
    private double target;
    private @Nullable ComponentContext context;
    private @Nullable AnimationHandle handle;

    public ScalarTransition(double initialValue, AnimationSpec spec, DoubleConsumer frameConsumer) {
        value = finite(initialValue, "initialValue");
        target = value;
        this.spec = Objects.requireNonNull(spec, "spec");
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
    }

    public void setTarget(double target) {
        double checked = finite(target, "target");
        this.target = checked;
        cancel();

        ComponentContext attached = context;
        double start = value;
        if (attached == null || Double.compare(start, checked) == 0) {
            apply(checked);
            return;
        }
        handle = attached.session().animations().start(
                spec,
                progress -> apply(start + (checked - start) * progress));
    }

    private void apply(double next) {
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
}
