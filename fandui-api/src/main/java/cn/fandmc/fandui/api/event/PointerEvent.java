package cn.fandmc.fandui.api.event;

import cn.fandmc.fandui.api.layout.Point;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable pointer event expressed in session scene coordinates. */
public final class PointerEvent implements UiEvent {
    private final PointerAction action;
    private final Point scenePosition;
    private final Point sceneDelta;
    private final Optional<PointerButton> changedButton;
    private final Set<PointerButton> buttons;
    private final int clickCount;
    private final Set<KeyModifier> modifiers;
    private final long timestampNanos;

    public PointerEvent(
            PointerAction action,
            Point scenePosition,
            Point sceneDelta,
            Optional<PointerButton> changedButton,
            Set<PointerButton> buttons,
            int clickCount,
            Set<KeyModifier> modifiers,
            long timestampNanos) {
        this.action = Objects.requireNonNull(action, "action");
        this.scenePosition = Objects.requireNonNull(scenePosition, "scenePosition");
        this.sceneDelta = Objects.requireNonNull(sceneDelta, "sceneDelta");
        this.changedButton = Objects.requireNonNull(changedButton, "changedButton");
        this.buttons = Set.copyOf(Objects.requireNonNull(buttons, "buttons"));
        if (clickCount < 0) {
            throw new IllegalArgumentException("clickCount must not be negative");
        }
        this.clickCount = clickCount;
        this.modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        this.timestampNanos = requireTimestamp(timestampNanos);
    }

    public PointerAction action() {
        return action;
    }

    public Point scenePosition() {
        return scenePosition;
    }

    public Point sceneDelta() {
        return sceneDelta;
    }

    public Optional<PointerButton> changedButton() {
        return changedButton;
    }

    public Set<PointerButton> buttons() {
        return buttons;
    }

    public int clickCount() {
        return clickCount;
    }

    public Set<KeyModifier> modifiers() {
        return modifiers;
    }

    @Override
    public long timestampNanos() {
        return timestampNanos;
    }

    static long requireTimestamp(long timestampNanos) {
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must not be negative");
        }
        return timestampNanos;
    }
}
