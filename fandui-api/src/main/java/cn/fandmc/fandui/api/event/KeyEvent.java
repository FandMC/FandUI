package cn.fandmc.fandui.api.event;

import java.util.Objects;
import java.util.Set;

/** Immutable keyboard event with stable key, host scan code, action, and modifiers. */
public final class KeyEvent implements UiEvent {
    private final KeyCode key;
    private final int scanCode;
    private final KeyAction action;
    private final Set<KeyModifier> modifiers;
    private final long timestampNanos;

    public KeyEvent(KeyCode key, KeyAction action, Set<KeyModifier> modifiers, long timestampNanos) {
        this(key, -1, action, modifiers, timestampNanos);
    }

    /**
     * Creates an event with the host-provided physical scan code.
     *
     * @param key stable logical key identity
     * @param scanCode opaque host scan code, or {@code -1} when unavailable
     * @param action press, repeat, or release transition
     * @param modifiers modifier snapshot
     * @param timestampNanos monotonic event timestamp
     */
    public KeyEvent(
            KeyCode key,
            int scanCode,
            KeyAction action,
            Set<KeyModifier> modifiers,
            long timestampNanos
    ) {
        this.key = Objects.requireNonNull(key, "key");
        if (scanCode < -1) {
            throw new IllegalArgumentException("scanCode must be -1 or non-negative");
        }
        this.scanCode = scanCode;
        this.action = Objects.requireNonNull(action, "action");
        this.modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        this.timestampNanos = PointerEvent.requireTimestamp(timestampNanos);
    }

    public KeyCode key() {
        return key;
    }

    /** Returns the opaque host scan code, or {@code -1} when unavailable. */
    public int scanCode() {
        return scanCode;
    }

    public KeyAction action() {
        return action;
    }

    public Set<KeyModifier> modifiers() {
        return modifiers;
    }

    @Override
    public long timestampNanos() {
        return timestampNanos;
    }
}
