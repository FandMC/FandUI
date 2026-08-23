package cn.fandmc.fandui.api.event;

/** Marker interface for immutable events routed through a UI component tree. */
public interface UiEvent {
    long timestampNanos();
}
