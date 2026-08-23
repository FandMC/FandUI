package cn.fandmc.fandui.api.event;

import cn.fandmc.fandui.api.component.UiComponent;

import java.util.Objects;
import java.util.Optional;

/** Immutable focus transition routed to the affected component. */
public final class FocusEvent implements UiEvent {
    private final FocusAction action;
    private final FocusCause cause;
    private final Optional<UiComponent> related;
    private final long timestampNanos;

    public FocusEvent(
            FocusAction action,
            FocusCause cause,
            Optional<UiComponent> related,
            long timestampNanos) {
        this.action = Objects.requireNonNull(action, "action");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.related = Objects.requireNonNull(related, "related");
        this.timestampNanos = PointerEvent.requireTimestamp(timestampNanos);
    }

    public FocusAction action() {
        return action;
    }

    public FocusCause cause() {
        return cause;
    }

    public Optional<UiComponent> related() {
        return related;
    }

    @Override
    public long timestampNanos() {
        return timestampNanos;
    }
}
