package cn.fandmc.fandui.api.hud;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.session.UiSession;
import cn.fandmc.fandui.api.event.UiEvent;

/** Live session and ownership handle for one mounted HUD layer. */
public interface HudRegistration extends UiSession {
    UiKey key();

    HudLayer layer();

    /**
     * Routes an explicitly supplied event only when the layer is interactive.
     * Returns whether framework/default handling was consumed.
     */
    boolean dispatch(UiEvent event);
}
