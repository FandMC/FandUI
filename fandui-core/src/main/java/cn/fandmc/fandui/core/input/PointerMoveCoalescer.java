package cn.fandmc.fandui.core.input;

import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.layout.Point;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Collapses platform pointer motion into the latest sample consumed by one UI frame.
 * Button, scroll, and other ordered events must drain pending motion before dispatch.
 */
public final class PointerMoveCoalescer {
    private boolean delivered;
    private float deliveredX;
    private float deliveredY;
    private boolean pending;
    private float pendingX;
    private float pendingY;

    public void offer(double x, double y) {
        float checkedX = coordinate(x, "x");
        float checkedY = coordinate(y, "y");
        pendingX = checkedX;
        pendingY = checkedY;
        pending = true;
    }

    public @Nullable PointerEvent drain(
            PointerInputState pointer,
            Set<KeyModifier> modifiers,
            long timestampNanos) {
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(modifiers, "modifiers");
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must not be negative");
        }
        if (!pending) {
            return null;
        }
        Point position = new Point(pendingX, pendingY);
        Point delta = delivered
                ? new Point(pendingX - deliveredX, pendingY - deliveredY)
                : new Point(0.0f, 0.0f);
        deliveredX = pendingX;
        deliveredY = pendingY;
        delivered = true;
        pending = false;
        return pointer.move(position, delta, modifiers, timestampNanos);
    }

    public void synchronize(Point position) {
        Objects.requireNonNull(position, "position");
        deliveredX = position.x();
        deliveredY = position.y();
        delivered = true;
        pending = false;
    }

    public void clear() {
        delivered = false;
        pending = false;
    }

    private static float coordinate(double value, String name) {
        float result = (float) value;
        if (!Double.isFinite(value) || !Float.isFinite(result)) {
            throw new IllegalArgumentException(name + " must be finite and representable as a float");
        }
        return result;
    }
}
