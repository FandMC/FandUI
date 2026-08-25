package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.event.EventRegistration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Small UI-thread publisher used by value controls without exposing framework state. */
public final class ValueChangeListeners<T> {
    private final Object lock = new Object();
    private volatile List<Entry<T>> entries = List.of();

    public EventRegistration add(Consumer<? super T> listener) {
        Entry<T> entry = new Entry<>(this, Objects.requireNonNull(listener, "listener"));
        synchronized (lock) {
            var updated = new java.util.ArrayList<>(entries);
            updated.add(entry);
            entries = List.copyOf(updated);
        }
        return entry;
    }

    public void notifyListeners(T value) {
        for (Entry<T> entry : entries) {
            if (entry.active()) {
                entry.listener.accept(value);
            }
        }
    }

    private void remove(Entry<T> entry) {
        synchronized (lock) {
            var updated = new java.util.ArrayList<>(entries);
            updated.remove(entry);
            entries = List.copyOf(updated);
        }
    }

    private static final class Entry<T> implements EventRegistration {
        private final ValueChangeListeners<T> owner;
        private final Consumer<? super T> listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Entry(ValueChangeListeners<T> owner, Consumer<? super T> listener) {
            this.owner = owner;
            this.listener = listener;
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
    }
}
