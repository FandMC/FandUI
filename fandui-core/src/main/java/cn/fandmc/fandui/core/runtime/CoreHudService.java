package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.hud.HudLayer;
import cn.fandmc.fandui.api.hud.HudRegistration;
import cn.fandmc.fandui.api.hud.HudService;
import cn.fandmc.fandui.api.session.SessionCloseReason;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class CoreHudService implements HudService {
    private static final Comparator<CoreHudRegistration> ORDER = Comparator
            .comparingInt((CoreHudRegistration registration) -> registration.layer().order())
            .thenComparing(registration -> registration.key().toString());

    private final CoreUiRuntime runtime;
    private final Map<UiKey, CoreHudRegistration> registrations = new LinkedHashMap<>();
    private List<CoreHudRegistration> activeSnapshot = List.of();
    private boolean snapshotDirty;

    CoreHudService(CoreUiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public HudRegistration mount(HudLayer layer) {
        Objects.requireNonNull(layer, "layer");
        runtime.assertUiThread();
        runtime.requireAvailable();
        CoreHudRegistration existing = registrations.get(layer.key());
        if (existing != null && existing.active()) {
            throw new IllegalStateException("HUD key is already mounted: " + layer.key());
        }
        CoreHudRegistration created = new CoreHudRegistration(runtime, layer, this::registrationClosed);
        if (created.active()) {
            registrations.put(layer.key(), created);
            snapshotDirty = true;
        }
        return created;
    }

    @Override
    public Optional<HudRegistration> find(UiKey key) {
        Objects.requireNonNull(key, "key");
        runtime.assertUiThread();
        return Optional.ofNullable(registrations.get(key))
                .filter(CoreHudRegistration::active)
                .map(registration -> registration);
    }

    @Override
    public List<HudRegistration> mounted() {
        runtime.assertUiThread();
        return activeRegistrations().stream().map(registration -> (HudRegistration) registration).toList();
    }

    List<CoreHudRegistration> activeRegistrations() {
        if (!snapshotDirty) {
            return activeSnapshot;
        }
        List<CoreHudRegistration> active = new ArrayList<>();
        for (CoreHudRegistration registration : registrations.values()) {
            if (registration.active()) {
                active.add(registration);
            }
        }
        active.sort(ORDER);
        activeSnapshot = List.copyOf(active);
        snapshotDirty = false;
        return activeSnapshot;
    }

    void closeAll(SessionCloseReason reason) {
        for (CoreHudRegistration registration : List.copyOf(registrations.values())) {
            registration.requestClose(reason, false);
        }
    }

    private void registrationClosed(CoreHudRegistration registration) {
        if (registrations.remove(registration.key(), registration)) {
            snapshotDirty = true;
        }
    }
}
