package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.session.UiSession;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.canvas.DisplayList;

import java.util.Objects;

public record UiSceneFrame(
        UiSession session,
        DisplayList displayList,
        UiViewport viewport,
        long frameTimeNanos) {
    public UiSceneFrame {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(displayList, "displayList");
        Objects.requireNonNull(viewport, "viewport");
        if (frameTimeNanos < 0L) {
            throw new IllegalArgumentException("frameTimeNanos must not be negative");
        }
    }
}
