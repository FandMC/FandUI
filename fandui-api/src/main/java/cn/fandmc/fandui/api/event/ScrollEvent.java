package cn.fandmc.fandui.api.event;

import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.internal.validation.Preconditions;

import java.util.Objects;
import java.util.Set;

/** Immutable high-level scroll delta in logical line units. */
public final class ScrollEvent implements UiEvent {
    private final double horizontalLines;
    private final double verticalLines;
    private final Point scenePosition;
    private final Set<KeyModifier> modifiers;
    private final long timestampNanos;

    public ScrollEvent(
            double horizontalLines,
            double verticalLines,
            Point scenePosition,
            Set<KeyModifier> modifiers,
            long timestampNanos) {
        this.horizontalLines = Preconditions.finite(horizontalLines, "horizontalLines");
        this.verticalLines = Preconditions.finite(verticalLines, "verticalLines");
        this.scenePosition = Objects.requireNonNull(scenePosition, "scenePosition");
        this.modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        this.timestampNanos = PointerEvent.requireTimestamp(timestampNanos);
    }

    public double horizontalLines() {
        return horizontalLines;
    }

    public double verticalLines() {
        return verticalLines;
    }

    public Point scenePosition() {
        return scenePosition;
    }

    public Set<KeyModifier> modifiers() {
        return modifiers;
    }

    @Override
    public long timestampNanos() {
        return timestampNanos;
    }
}
