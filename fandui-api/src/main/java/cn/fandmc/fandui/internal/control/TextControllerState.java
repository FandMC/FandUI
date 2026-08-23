package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.component.control.TextSelection;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import cn.fandmc.fandui.internal.validation.Utf16;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class TextControllerState {
    private final ChangeListeners listeners = new ChangeListeners();
    private final Deque<Runnable> pendingMutations = new ArrayDeque<>();
    private String text;
    private TextSelection selection;
    private boolean processingMutation;
    private Object owner;

    public TextControllerState(String initialText) {
        this.text = Utf16.wellFormed(initialText, "initialText");
        this.selection = new TextSelection(text.length(), text.length());
    }

    public String text() {
        assertAccessAllowed();
        return text;
    }

    public TextSelection selection() {
        assertAccessAllowed();
        return selection;
    }

    public void setText(String value) {
        String checked = Utf16.wellFormed(value, "text");
        mutate(() -> {
            TextSelection nextSelection = new TextSelection(checked.length(), checked.length());
            if (!text.equals(checked) || !selection.equals(nextSelection)) {
                text = checked;
                selection = nextSelection;
                listeners.notifyListeners();
            }
        });
    }

    public void setSelection(TextSelection value) {
        Objects.requireNonNull(value, "selection");
        mutate(() -> {
            validateSelection(value);
            if (!selection.equals(value)) {
                selection = value;
                listeners.notifyListeners();
            }
        });
    }

    public void replaceSelection(String replacement) {
        String checked = Utf16.wellFormed(replacement, "replacement");
        mutate(() -> applyReplacement(selection, checked));
    }

    public void replace(TextSelection range, String replacement) {
        Objects.requireNonNull(range, "range");
        String checked = Utf16.wellFormed(replacement, "replacement");
        mutate(() -> {
            validateSelection(range);
            applyReplacement(range, checked);
        });
    }

    public void selectAll() {
        setSelection(new TextSelection(0, text.length()));
    }

    public EventRegistration onChange(Runnable listener) {
        assertAccessAllowed();
        return listeners.add(listener);
    }

    public void bind(Object owner) {
        Objects.requireNonNull(owner, "owner");
        assertOwnerThread(owner);
        if (this.owner != null) {
            throw new IllegalStateException("TextController is already bound");
        }
        this.owner = owner;
    }

    public void unbind(Object owner) {
        assertAccessAllowed();
        if (this.owner != owner) {
            throw new IllegalStateException("TextController is not bound to this owner");
        }
        this.owner = null;
    }

    private void validateSelection(TextSelection value) {
        Utf16.requireBoundary(text, value.anchorUtf16(), "anchorUtf16");
        Utf16.requireBoundary(text, value.focusUtf16(), "focusUtf16");
    }

    private void mutate(Runnable mutation) {
        assertAccessAllowed();
        pendingMutations.addLast(mutation);
        if (processingMutation) {
            return;
        }
        processingMutation = true;
        try {
            while (!pendingMutations.isEmpty()) {
                pendingMutations.removeFirst().run();
            }
        } finally {
            processingMutation = false;
            pendingMutations.clear();
        }
    }

    private void applyReplacement(TextSelection range, String replacement) {
        int start = range.startUtf16();
        int end = range.endUtf16();
        String updated = text.substring(0, start) + replacement + text.substring(end);
        int caret = start + replacement.length();
        TextSelection updatedSelection = new TextSelection(caret, caret);
        if (!text.equals(updated) || !selection.equals(updatedSelection)) {
            text = updated;
            selection = updatedSelection;
            listeners.notifyListeners();
        }
    }

    private void assertAccessAllowed() {
        assertOwnerThread(owner);
    }

    private static void assertOwnerThread(Object owner) {
        if (owner instanceof UiComponent component) {
            ComponentBindings.assertMutationAllowed(component);
        }
    }
}
