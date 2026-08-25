package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.event.EventRegistration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChangeListeners {
    private final Object lock = new Object();
    private volatile List<Entry> entries = List.of();

    public EventRegistration add(Runnable listener) {
        Entry entry = new Entry(this, Objects.requireNonNull(listener, "listener"));
        synchronized (lock) {
            var updated = new java.util.ArrayList<>(entries);
            updated.add(entry);
            entries = List.copyOf(updated);
        }
        return entry;
    }

    public void notifyListeners() {
        for (Entry entry : entries) {
            if (entry.active()) {
                entry.listener.run();
            }
        }
    }

    private void remove(Entry entry) {
        synchronized (lock) {
            var updated = new java.util.ArrayList<>(entries);
            updated.remove(entry);
            entries = List.copyOf(updated);
        }
    }

    private static final class Entry implements EventRegistration {
        private final ChangeListeners owner;
        private final Runnable listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Entry(ChangeListeners owner, Runnable listener) {
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
