package cn.fandmc.fandui.api.component.control;

import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.internal.control.TextControllerState;
import cn.fandmc.fandui.internal.control.TextControllers;

/**
 * Mutable, UTF-16-indexed text and selection state for one attached {@code TextInput}.
 * Text is well-formed UTF-16, selection offsets are code-point boundaries, and mutations
 * require the UI thread while the controller is bound.
 */
public final class TextController {
    private final TextControllerState state;

    private TextController(String initialText) {
        this.state = new TextControllerState(initialText);
        TextControllers.register(this, state);
    }

    public static TextController create() {
        return create("");
    }

    public static TextController create(String initialText) {
        return new TextController(initialText);
    }

    public String text() {
        return state.text();
    }

    public TextSelection selection() {
        return state.selection();
    }

    public void setText(String text) {
        state.setText(text);
    }

    public void setSelection(TextSelection selection) {
        state.setSelection(selection);
    }

    public void replaceSelection(String replacement) {
        state.replaceSelection(replacement);
    }

    /**
     * Replaces the normalized range and places the caret after the replacement.
     *
     * @param range UTF-16 selection range to replace
     * @param replacement well-formed UTF-16 text
     */
    public void replace(TextSelection range, String replacement) {
        state.replace(range, replacement);
    }

    public void selectAll() {
        state.selectAll();
    }

    public EventRegistration onChange(Runnable listener) {
        return state.onChange(listener);
    }
}
