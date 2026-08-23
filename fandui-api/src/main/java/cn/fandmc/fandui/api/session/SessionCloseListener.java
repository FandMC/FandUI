package cn.fandmc.fandui.api.session;

/** UI-thread callback invoked exactly once when its owning session closes. */
@FunctionalInterface
public interface SessionCloseListener {
    void closed(UiSession session, SessionCloseReason reason);
}
