package cn.fandmc.fandui.internal.event;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.event.EventHandler;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.UiEvent;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class EventListeners {
    private static final Map<UiComponent, WeakReference<ListenerBucket>> BUCKETS = new WeakHashMap<>();

    private EventListeners() {
    }

    public static void bind(UiComponent component, ListenerBucket bucket) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(bucket, "bucket");
        synchronized (BUCKETS) {
            BUCKETS.put(component, new WeakReference<>(bucket));
        }
    }

    public static List<EventHandler<UiEvent>> handlers(
            UiComponent component,
            UiEvent event,
            EventRoute route) {
        Objects.requireNonNull(component, "component");
        ListenerBucket bucket;
        synchronized (BUCKETS) {
            WeakReference<ListenerBucket> reference = BUCKETS.get(component);
            bucket = reference == null ? null : reference.get();
        }
        return bucket == null ? List.of() : bucket.handlers(event, route);
    }
}
