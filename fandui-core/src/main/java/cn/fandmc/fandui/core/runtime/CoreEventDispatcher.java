package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.event.EventContext;
import cn.fandmc.fandui.api.event.EventHandler;
import cn.fandmc.fandui.api.event.EventPhase;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.FocusAction;
import cn.fandmc.fandui.api.event.FocusCause;
import cn.fandmc.fandui.api.event.FocusEvent;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.event.ScrollEvent;
import cn.fandmc.fandui.api.event.TextCompositionEvent;
import cn.fandmc.fandui.api.event.TextInputEvent;
import cn.fandmc.fandui.api.event.UiEvent;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.core.layout.LayoutSnapshot;
import cn.fandmc.fandui.internal.event.EventListeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class CoreEventDispatcher {
    private final AbstractCoreSession session;
    private PointerEvent lastPointer;

    CoreEventDispatcher(AbstractCoreSession session) {
        this.session = session;
    }

    boolean dispatch(UiEvent event) {
        UiComponent target;
        if (event instanceof PointerEvent pointer) {
            lastPointer = pointer;
            UiComponent hit = hitTest(pointer.scenePosition());
            target = session.captured();
            if (target == null) {
                target = hit;
            }
            if (pointer.action() == PointerAction.MOVE) {
                transitionHover(hit, pointer);
                if (!session.active()) {
                    return true;
                }
                if (target != null && !session.contains(target)) {
                    target = session.captured();
                    if (target == null) {
                        target = hitTest(pointer.scenePosition());
                    }
                }
            } else if (pointer.action() == PointerAction.DOWN) {
                session.pressed(target);
            }
            DispatchState state = target == null ? new DispatchState() : dispatchTo(target, pointer);
            if (pointer.action() == PointerAction.DOWN
                    && target != null
                    && !state.defaultPrevented) {
                session.focusManager().request(target, FocusCause.POINTER, pointer.timestampNanos());
            }
            if (pointer.action() == PointerAction.UP || pointer.action() == PointerAction.CANCEL) {
                session.pressed(null);
                session.captured(null);
            }
            return state.consumed;
        }
        if (event instanceof ScrollEvent scroll) {
            target = hitTest(scroll.scenePosition());
        } else if (event instanceof KeyEvent
                || event instanceof TextInputEvent
                || event instanceof TextCompositionEvent) {
            target = session.focusManager().current();
            if (target == null) {
                target = session.root();
            }
        } else {
            target = session.focusManager().current();
            if (target == null) {
                target = session.root();
            }
        }
        return target != null && dispatchTo(target, event).consumed;
    }

    private void transitionHover(UiComponent next, PointerEvent source) {
        UiComponent previous = session.hovered();
        if (previous == next) {
            return;
        }
        if (previous != null && session.contains(previous)) {
            dispatchTo(previous, transition(PointerAction.LEAVE, source));
        }
        if (!session.active()) {
            return;
        }
        if (next != null && !session.contains(next)) {
            next = null;
        }
        session.hovered(next);
        if (next != null) {
            dispatchTo(next, transition(PointerAction.ENTER, source));
        }
    }

    private static PointerEvent transition(PointerAction action, PointerEvent source) {
        return new PointerEvent(
                action,
                source.scenePosition(),
                source.sceneDelta(),
                Optional.empty(),
                source.buttons(),
                0,
                source.modifiers(),
                source.timestampNanos());
    }

    void dispatchFocus(
            UiComponent previous,
            UiComponent next,
            FocusCause cause,
            long timestampNanos) {
        if (previous != null && session.contains(previous)) {
            dispatchTo(previous, new FocusEvent(
                    FocusAction.LOST,
                    cause,
                    Optional.ofNullable(next),
                    timestampNanos));
        }
        if (next != null && session.contains(next)) {
            dispatchTo(next, new FocusEvent(
                    FocusAction.GAINED,
                    cause,
                    Optional.ofNullable(previous),
                    timestampNanos));
        }
    }

    void cancelCapture() {
        UiComponent captured = session.captured();
        if (captured == null) {
            return;
        }
        session.captured(null);
        session.pressed(null);
        if (!session.contains(captured)) {
            return;
        }
        Point position = lastPointer == null ? new Point(0.0f, 0.0f) : lastPointer.scenePosition();
        Set<KeyModifier> modifiers = lastPointer == null ? Set.of() : lastPointer.modifiers();
        dispatchTo(captured, new PointerEvent(
                PointerAction.CANCEL,
                position,
                new Point(0.0f, 0.0f),
                Optional.empty(),
                Set.of(),
                0,
                modifiers,
                session.runtime().now()));
    }

    private UiComponent hitTest(Point scenePosition) {
        LayoutSnapshot layout = session.layoutSnapshot();
        return layout == null ? null : layout.hitTest(scenePosition).orElse(null);
    }

    private DispatchState dispatchTo(UiComponent target, UiEvent event) {
        if (!session.contains(target)) {
            return new DispatchState();
        }
        List<UiComponent> path = pathTo(target);
        DispatchState state = new DispatchState();

        for (int index = 0; index < path.size() - 1; index++) {
            invoke(path.get(index), target, event, EventPhase.CAPTURE, EventRoute.CAPTURE, state);
            if (state.propagationStopped) {
                return state;
            }
        }

        UiComponent targetComponent = path.get(path.size() - 1);
        state.immediateStopped = false;
        invokeHandlers(targetComponent, target, event, EventPhase.TARGET, EventRoute.CAPTURE, state);
        if (!state.immediateStopped) {
            invokeHandlers(targetComponent, target, event, EventPhase.TARGET, EventRoute.BUBBLE, state);
        }
        if (state.propagationStopped) {
            return state;
        }

        for (int index = path.size() - 2; index >= 0; index--) {
            invoke(path.get(index), target, event, EventPhase.BUBBLE, EventRoute.BUBBLE, state);
            if (state.propagationStopped) {
                break;
            }
        }
        return state;
    }

    private void invoke(
            UiComponent current,
            UiComponent target,
            UiEvent event,
            EventPhase phase,
            EventRoute route,
            DispatchState state) {
        state.immediateStopped = false;
        invokeHandlers(current, target, event, phase, route, state);
    }

    private void invokeHandlers(
            UiComponent current,
            UiComponent target,
            UiEvent event,
            EventPhase phase,
            EventRoute route,
            DispatchState state) {
        for (EventHandler<UiEvent> handler : EventListeners.handlers(current, event, route)) {
            CallbackContext context = new CallbackContext(event, target, current, phase, state);
            try {
                context.active = true;
                handler.handle(event, context);
            } finally {
                context.active = false;
            }
            if (!session.active()) {
                state.immediateStopped = true;
                state.propagationStopped = true;
            }
            if (state.immediateStopped) {
                break;
            }
        }
    }

    private List<UiComponent> pathTo(UiComponent target) {
        List<UiComponent> path = new ArrayList<>();
        UiComponent cursor = target;
        while (cursor != null) {
            path.add(cursor);
            if (cursor == session.root()) {
                break;
            }
            cursor = cursor.parent().orElse(null);
        }
        if (path.isEmpty() || path.get(path.size() - 1) != session.root()) {
            throw new IllegalArgumentException("Event target does not belong to the current scene tree");
        }
        Collections.reverse(path);
        return path;
    }

    private final class CallbackContext implements EventContext {
        private final UiEvent event;
        private final UiComponent target;
        private final UiComponent current;
        private final EventPhase phase;
        private final DispatchState state;
        private boolean active;

        private CallbackContext(
                UiEvent event,
                UiComponent target,
                UiComponent current,
                EventPhase phase,
                DispatchState state) {
            this.event = event;
            this.target = target;
            this.current = current;
            this.phase = phase;
            this.state = state;
        }

        @Override
        public EventPhase phase() {
            requireActive();
            return phase;
        }

        @Override
        public UiComponent target() {
            requireActive();
            return target;
        }

        @Override
        public UiComponent currentTarget() {
            requireActive();
            return current;
        }

        @Override
        public Optional<Point> sceneToLocal(Point scenePosition) {
            requireActive();
            return Optional.ofNullable(session.sceneToLocal(current, scenePosition));
        }

        @Override
        public boolean consumed() {
            requireActive();
            return state.consumed;
        }

        @Override
        public void preventDefault() {
            requireActive();
            state.defaultPrevented = true;
        }

        @Override
        public void stopPropagation() {
            requireActive();
            state.propagationStopped = true;
        }

        @Override
        public void stopImmediatePropagation() {
            requireActive();
            state.immediateStopped = true;
            state.propagationStopped = true;
        }

        @Override
        public void consume() {
            requireActive();
            state.consumed = true;
            state.defaultPrevented = true;
            state.propagationStopped = true;
        }

        @Override
        public void requestFocus() {
            requireActive();
            session.focusManager().request(current, focusCause(), event.timestampNanos());
        }

        @Override
        public void capturePointer() {
            requirePointerEvent();
            session.captured(current);
        }

        @Override
        public void releasePointer() {
            requirePointerEvent();
            if (session.captured() == current) {
                session.captured(null);
            }
        }

        private FocusCause focusCause() {
            if (event instanceof PointerEvent) {
                return FocusCause.POINTER;
            }
            if (event instanceof KeyEvent) {
                return FocusCause.KEYBOARD;
            }
            return FocusCause.PROGRAMMATIC;
        }

        private void requirePointerEvent() {
            requireActive();
            if (!(event instanceof PointerEvent)) {
                throw new IllegalStateException("Pointer capture is only valid during a pointer event");
            }
        }

        private void requireActive() {
            if (!active) {
                throw new IllegalStateException("EventContext is only valid during its callback");
            }
        }
    }

    private static final class DispatchState {
        private boolean consumed;
        private boolean defaultPrevented;
        private boolean propagationStopped;
        private boolean immediateStopped;
    }
}
