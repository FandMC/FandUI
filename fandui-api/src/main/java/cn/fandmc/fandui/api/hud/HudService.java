package cn.fandmc.fandui.api.hud;

import cn.fandmc.fandui.api.UiKey;

import java.util.List;
import java.util.Optional;

/** UI-thread-confined registry of independently owned HUD layers. */
public interface HudService {
    /**
     * Mounts {@code layer}; the returned registration owns the mount and must be closed.
     *
     * @throws IllegalStateException if the key is already mounted or the runtime is unavailable
     */
    HudRegistration mount(HudLayer layer);

    Optional<HudRegistration> find(UiKey key);

    /** Returns an immutable snapshot ordered by layer order and then key. */
    List<HudRegistration> mounted();
}
