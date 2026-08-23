package cn.fandmc.fandui.api.component.control;

import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.internal.control.ScrollControllerState;
import cn.fandmc.fandui.internal.control.ScrollControllers;

import java.util.OptionalDouble;

/**
 * Mutable scroll offset that can be owned by one attached {@code ScrollContainer}.
 * Mutations require the UI thread while bound; listener handles do not own the controller.
 */
public final class ScrollController {
    private final ScrollControllerState state;

    private ScrollController(double initialOffset) {
        this.state = new ScrollControllerState(initialOffset);
        ScrollControllers.register(this, state);
    }

    public static ScrollController create() {
        return create(0.0);
    }

    public static ScrollController create(double initialOffset) {
        return new ScrollController(initialOffset);
    }

    public double offset() {
        return state.offset();
    }

    public OptionalDouble maximumOffset() {
        return state.maximumOffset();
    }

    public void scrollTo(double offset) {
        state.scrollTo(offset);
    }

    public void scrollBy(double delta) {
        state.scrollBy(delta);
    }

    public EventRegistration onChange(Runnable listener) {
        return state.onChange(listener);
    }
}
