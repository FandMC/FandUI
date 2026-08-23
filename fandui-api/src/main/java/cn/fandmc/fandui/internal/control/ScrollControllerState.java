package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.internal.component.ComponentBindings;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.OptionalDouble;

public final class ScrollControllerState {
    private final ChangeListeners listeners = new ChangeListeners();
    private final Deque<Runnable> pendingMutations = new ArrayDeque<>();
    private double offset;
    private OptionalDouble maximumOffset = OptionalDouble.empty();
    private boolean processingMutation;
    private Object owner;

    public ScrollControllerState(double initialOffset) {
        this.offset = requireOffset(initialOffset, "initialOffset");
    }

    public double offset() {
        assertAccessAllowed();
        return offset;
    }

    public OptionalDouble maximumOffset() {
        assertAccessAllowed();
        return maximumOffset;
    }

    public void scrollTo(double requestedOffset) {
        double checked = requireOffset(requestedOffset, "offset");
        mutate(() -> setOffset(clamp(checked)));
    }

    public void scrollBy(double delta) {
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("delta must be finite");
        }
        double requested = offset + delta;
        if (!Double.isFinite(requested)) {
            throw new IllegalArgumentException("Resulting offset must be finite");
        }
        scrollTo(Math.max(0.0, requested));
    }

    public EventRegistration onChange(Runnable listener) {
        assertAccessAllowed();
        return listeners.add(listener);
    }

    public void bind(Object owner, double maximumOffset) {
        Objects.requireNonNull(owner, "owner");
        assertOwnerThread(owner);
        double checkedMaximum = requireOffset(maximumOffset, "maximumOffset");
        mutate(() -> {
            if (this.owner != null) {
                throw new IllegalStateException("ScrollController is already bound");
            }
            this.owner = owner;
            OptionalDouble previousMaximum = this.maximumOffset;
            double previousOffset = offset;
            this.maximumOffset = OptionalDouble.of(checkedMaximum);
            offset = clamp(offset);
            if (!same(previousMaximum, this.maximumOffset) || Double.compare(previousOffset, offset) != 0) {
                listeners.notifyListeners();
            }
        });
    }

    public void updateMaximum(Object owner, double maximumOffset) {
        double checkedMaximum = requireOffset(maximumOffset, "maximumOffset");
        mutate(() -> {
            requireOwner(owner);
            OptionalDouble previousMaximum = this.maximumOffset;
            double previousOffset = offset;
            this.maximumOffset = OptionalDouble.of(checkedMaximum);
            offset = clamp(offset);
            if (!same(previousMaximum, this.maximumOffset) || Double.compare(previousOffset, offset) != 0) {
                listeners.notifyListeners();
            }
        });
    }

    public void unbind(Object owner) {
        assertAccessAllowed();
        mutate(() -> {
            requireOwner(owner);
            this.owner = null;
            this.maximumOffset = OptionalDouble.empty();
            listeners.notifyListeners();
        });
    }

    private void setOffset(double value) {
        if (Double.compare(offset, value) != 0) {
            offset = value;
            listeners.notifyListeners();
        }
    }

    private double clamp(double value) {
        return maximumOffset.isPresent() ? Math.min(value, maximumOffset.getAsDouble()) : value;
    }

    private void requireOwner(Object owner) {
        if (this.owner != owner) {
            throw new IllegalStateException("ScrollController is not bound to this owner");
        }
    }

    private void mutate(Runnable mutation) {
        assertAccessAllowed();
        pendingMutations.addLast(mutation);
        if (processingMutation) {
            return;
        }
        processingMutation = true;
        try {
            while (!pendingMutations.isEmpty()) {
                pendingMutations.removeFirst().run();
            }
        } finally {
            processingMutation = false;
            pendingMutations.clear();
        }
    }

    private static boolean same(OptionalDouble first, OptionalDouble second) {
        return first.isEmpty() && second.isEmpty()
                || first.isPresent() && second.isPresent()
                && Double.compare(first.getAsDouble(), second.getAsDouble()) == 0;
    }

    private static double requireOffset(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private void assertAccessAllowed() {
        assertOwnerThread(owner);
    }

    private static void assertOwnerThread(Object owner) {
        if (owner instanceof UiComponent component) {
            ComponentBindings.assertMutationAllowed(component);
        }
    }
}
