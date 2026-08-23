package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.screen.ScreenService;
import cn.fandmc.fandui.api.screen.ScreenSession;
import cn.fandmc.fandui.api.screen.UiScreen;
import cn.fandmc.fandui.api.session.SessionCloseReason;

import java.util.Objects;
import java.util.Optional;

final class CoreScreenService implements ScreenService {
    private final CoreUiRuntime runtime;
    private final ScreenHost host;
    private CoreScreenSession current;

    CoreScreenService(CoreUiRuntime runtime, ScreenHost host) {
        this.runtime = runtime;
        this.host = host;
    }

    @Override
    public ScreenSession open(UiScreen screen) {
        Objects.requireNonNull(screen, "screen");
        runtime.assertUiThread();
        runtime.requireAvailable();

        CoreScreenSession previous = current;
        if (previous != null) {
            previous.requestClose(SessionCloseReason.REPLACED, false);
        }

        CoreScreenSession created = new CoreScreenSession(runtime, host, screen, this::sessionClosed);
        if (!created.active()) {
            return created;
        }
        current = created;
        try {
            host.open(created);
        } catch (RuntimeException | Error exception) {
            created.requestClose(SessionCloseReason.FAILED, false);
            throw exception;
        }
        return created;
    }

    @Override
    public Optional<ScreenSession> current() {
        runtime.assertUiThread();
        return Optional.ofNullable(current).filter(CoreScreenSession::active).map(session -> session);
    }

    CoreScreenSession activeSession() {
        CoreScreenSession session = current;
        return session != null && session.active() ? session : null;
    }

    void closeCurrent(SessionCloseReason reason, boolean notifyHost) {
        CoreScreenSession session = current;
        if (session != null) {
            session.requestClose(reason, notifyHost);
        }
    }

    private void sessionClosed(CoreScreenSession session) {
        if (current == session) {
            current = null;
        }
    }
}
