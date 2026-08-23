package cn.fandmc.fandui.api.input;

import cn.fandmc.fandui.internal.validation.Utf16;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Platform-neutral access to well-formed Unicode text on the desktop clipboard.
 * The runtime-provided instance is UI-thread-confined because host clipboard APIs may be.
 */
public interface ClipboardService {
    /** Reads the current clipboard text. */
    String getText();

    /** Replaces the clipboard text. */
    void setText(String text);

    /** Creates an adapter that validates reader results and write inputs as UTF-16. */
    static ClipboardService of(Supplier<String> reader, Consumer<String> writer) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(writer, "writer");
        return new ClipboardService() {
            @Override
            public String getText() {
                String value = Objects.requireNonNull(reader.get(), "Clipboard reader result");
                return Utf16.wellFormed(value, "clipboard text");
            }

            @Override
            public void setText(String text) {
                writer.accept(Utf16.wellFormed(text, "text"));
            }
        };
    }

    /** Creates a thread-safe in-memory clipboard useful for tests and headless consumers. */
    static ClipboardService inMemory() {
        AtomicReference<String> value = new AtomicReference<>("");
        return of(value::get, value::set);
    }
}
