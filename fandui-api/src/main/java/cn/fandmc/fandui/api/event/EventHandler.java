package cn.fandmc.fandui.api.event;

/** UI-thread callback for one registered routed event type. */
@FunctionalInterface
public interface EventHandler<E extends UiEvent> {
    void handle(E event, EventContext context);
}
