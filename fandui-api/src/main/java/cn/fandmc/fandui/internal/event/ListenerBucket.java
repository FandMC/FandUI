package cn.fandmc.fandui.internal.event;

import cn.fandmc.fandui.api.event.EventHandler;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.UiEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ListenerBucket {
    private final List<Entry<?>> entries = new ArrayList<>();

    public <E extends UiEvent> EventRegistration register(
            Class<E> type,
            EventRoute route,
            EventHandler<E> handler) {
        Entry<E> entry = new Entry<>(this, type, route, handler);
        synchronized (entries) {
            entries.add(entry);
        }
        return entry;
    }

    public List<EventHandler<UiEvent>> handlers(UiEvent event, EventRoute route) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(route, "route");
        List<EventHandler<UiEvent>> result = new ArrayList<>();
        synchronized (entries) {
            for (Entry<?> entry : entries) {
                if (entry.active() && entry.route == route && entry.type.isInstance(event)) {
                    result.add(entry.erasedHandler());
                }
            }
        }
        return List.copyOf(result);
    }

    private void remove(Entry<?> entry) {
        synchronized (entries) {
            entries.remove(entry);
        }
    }

    private static final class Entry<E extends UiEvent> implements EventRegistration {
        private final ListenerBucket owner;
        private final Class<E> type;
        private final EventRoute route;
        private final EventHandler<E> handler;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Entry(ListenerBucket owner, Class<E> type, EventRoute route, EventHandler<E> handler) {
            this.owner = owner;
            this.type = Objects.requireNonNull(type, "type");
            this.route = Objects.requireNonNull(route, "route");
            this.handler = Objects.requireNonNull(handler, "handler");
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) {
                owner.remove(this);
            }
        }

        @SuppressWarnings("unchecked")
        private EventHandler<UiEvent> erasedHandler() {
            return (EventHandler<UiEvent>) handler;
        }
    }
}
