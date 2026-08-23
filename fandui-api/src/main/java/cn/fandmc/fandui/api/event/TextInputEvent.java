package cn.fandmc.fandui.api.event;

import cn.fandmc.fandui.internal.validation.Utf16;

/** Immutable committed Unicode text input event. */
public final class TextInputEvent implements UiEvent {
    private final String text;
    private final long timestampNanos;

    public TextInputEvent(String text, long timestampNanos) {
        this.text = Utf16.wellFormed(text, "text");
        this.timestampNanos = PointerEvent.requireTimestamp(timestampNanos);
    }

    public String text() {
        return text;
    }

    @Override
    public long timestampNanos() {
        return timestampNanos;
    }
}
