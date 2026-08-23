package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.input.ClipboardService;
import cn.fandmc.fandui.api.session.UiSession;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.text.TextService;

import java.util.concurrent.CompletableFuture;

/**
 * Stable services available while a component is attached to one active session.
 * The context must not be used after the matching detached callback returns.
 */
public interface ComponentContext {
    UiSession session();

    Theme theme();

    ResourceService resources();

    TextService text();

    ClipboardService clipboard();

    /**
     * Schedules an action for a later UI-thread turn.
     * The future fails if the session/runtime closes first or the action throws.
     */
    CompletableFuture<Void> execute(Runnable action);
}
