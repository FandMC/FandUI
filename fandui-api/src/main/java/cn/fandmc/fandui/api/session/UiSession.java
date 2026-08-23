package cn.fandmc.fandui.api.session;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.animation.AnimationManager;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.focus.FocusManager;
import cn.fandmc.fandui.api.style.Theme;

import java.util.Optional;

/**
 * UI-thread-confined lifetime for one attached component tree.
 *
 * <p>The session owns attachment, focus, event routing, and all animations started
 * through it. Closing is idempotent, detaches the tree, completes animations, and
 * invokes close listeners exactly once.</p>
 */
public interface UiSession extends AutoCloseable {
    UiComponent root();

    boolean active();

    UiViewport viewport();

    FocusManager focus();

    Theme theme();

    AnimationManager animations();

    Optional<UiComponent> find(UiKey key);

    /** Invalidates layout and paint for the entire session. */
    void invalidate();

    Optional<SessionCloseReason> closeReason();

    /** Registers a close callback and returns an independently closable listener handle. */
    EventRegistration onClose(SessionCloseListener listener);

    /** Closes with {@link SessionCloseReason#API}. */
    @Override
    void close();
}
