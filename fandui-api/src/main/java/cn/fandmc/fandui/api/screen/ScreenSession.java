package cn.fandmc.fandui.api.screen;

import cn.fandmc.fandui.api.session.UiSession;

/** Live session and ownership handle for one opened {@link UiScreen}. */
public interface ScreenSession extends UiSession {
    UiScreen screen();
}
