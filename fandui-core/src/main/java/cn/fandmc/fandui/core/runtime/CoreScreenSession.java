package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.screen.ScreenSession;
import cn.fandmc.fandui.api.screen.UiScreen;
import cn.fandmc.fandui.api.session.SessionCloseReason;
import cn.fandmc.fandui.api.event.UiEvent;

import java.util.Objects;
import java.util.function.Consumer;

public final class CoreScreenSession extends AbstractCoreSession implements ScreenSession {
    private final ScreenHost host;
    private final UiScreen screen;
    private final Consumer<CoreScreenSession> closeCallback;

    CoreScreenSession(
            CoreUiRuntime runtime,
            ScreenHost host,
            UiScreen screen,
            Consumer<CoreScreenSession> closeCallback) {
        super(runtime, screen.root(), screen.theme(), screen.layoutDirection());
        this.host = Objects.requireNonNull(host, "host");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.closeCallback = Objects.requireNonNull(closeCallback, "closeCallback");
        initialize();
    }

    @Override
    public UiScreen screen() {
        return screen;
    }

    public void hostClosed(SessionCloseReason reason) {
        Objects.requireNonNull(reason, "reason");
        runtime().assertUiThread();
        if (reason != SessionCloseReason.ESCAPE && reason != SessionCloseReason.HOST) {
            throw new IllegalArgumentException("Host may only close a screen with ESCAPE or HOST");
        }
        requestClose(reason, reason == SessionCloseReason.ESCAPE);
    }

    @Override
    protected boolean notifyHostOnApiClose() {
        return true;
    }

    @Override
    protected boolean acceptsInput(UiEvent event) {
        return true;
    }

    @Override
    protected void hostCloseRequested() {
        host.close(this);
    }

    @Override
    protected void detachedFromRuntime() {
        closeCallback.accept(this);
    }
}
