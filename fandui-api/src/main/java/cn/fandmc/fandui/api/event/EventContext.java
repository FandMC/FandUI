package cn.fandmc.fandui.api.event;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.layout.Point;

import java.util.Optional;

/**
 * Callback-scoped control surface for one routed event delivery.
 * It becomes invalid when the event handler returns and must not be retained.
 */
public interface EventContext {
    EventPhase phase();

    UiComponent target();

    UiComponent currentTarget();

    Optional<Point> sceneToLocal(Point scenePosition);

    boolean consumed();

    /** Prevents framework default handling while allowing propagation to continue. */
    void preventDefault();

    /** Stops delivery after the current target and phase finish. */
    void stopPropagation();

    /** Stops all remaining handlers immediately. */
    void stopImmediatePropagation();

    /** Prevents default handling and stops propagation. */
    void consume();

    void requestFocus();

    /** Captures subsequent pointer events for the current target until released or cancelled. */
    void capturePointer();

    void releasePointer();
}
