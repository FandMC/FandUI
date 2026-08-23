package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.event.EventRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChangeListeners {
    private final List<Entry> entries = new ArrayList<>();

    public EventRegistration add(Runnable listener) {
        Entry entry = new Entry(this, Objects.requireNonNull(listener, "listener"));
        synchronized (entries) {
            entries.add(entry);
        }
        return entry;
    }

    public void notifyListeners() {
        List<Entry> snapshot;
        synchronized (entries) {
            snapshot = List.copyOf(entries);
        }
        for (Entry entry : snapshot) {
            if (entry.active()) {
                entry.listener.run();
            }
        }
    }

    private void remove(Entry entry) {
        synchronized (entries) {
            entries.remove(entry);
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
