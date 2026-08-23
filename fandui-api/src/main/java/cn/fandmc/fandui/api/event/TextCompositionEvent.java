package cn.fandmc.fandui.api.event;

import cn.fandmc.fandui.internal.validation.Utf16;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Immutable IME composition update with UTF-16 selection offsets. */
public final class TextCompositionEvent implements UiEvent {
    private final boolean active;
    private final String fullText;
    private final int caretUtf16;
    private final List<String> blocks;
    private final OptionalInt focusedBlock;
    private final long timestampNanos;

    public TextCompositionEvent(
            boolean active,
            String fullText,
            int caretUtf16,
            List<String> blocks,
            OptionalInt focusedBlock,
            long timestampNanos) {
        this.active = active;
        this.fullText = Utf16.wellFormed(fullText, "fullText");
        Utf16.requireBoundary(this.fullText, caretUtf16, "caretUtf16");
        this.caretUtf16 = caretUtf16;
        Objects.requireNonNull(blocks, "blocks");
        this.blocks = blocks.stream().map(block -> Utf16.wellFormed(block, "block")).toList();
        if (!String.join("", this.blocks).equals(this.fullText)) {
            throw new IllegalArgumentException("Composition blocks must concatenate to fullText");
        }
        this.focusedBlock = Objects.requireNonNull(focusedBlock, "focusedBlock");
        if (focusedBlock.isPresent()
                && (focusedBlock.getAsInt() < 0 || focusedBlock.getAsInt() >= this.blocks.size())) {
            throw new IllegalArgumentException("focusedBlock is outside the block list");
        }
        if (!active && (!this.fullText.isEmpty() || caretUtf16 != 0 || !this.blocks.isEmpty()
                || focusedBlock.isPresent())) {
            throw new IllegalArgumentException("Inactive composition must use the normalized empty state");
        }
        this.timestampNanos = PointerEvent.requireTimestamp(timestampNanos);
    }

    public boolean active() {
        return active;
    }

    public String fullText() {
        return fullText;
    }

    public int caretUtf16() {
        return caretUtf16;
    }

    public List<String> blocks() {
        return blocks;
    }

    public OptionalInt focusedBlock() {
        return focusedBlock;
    }

    @Override
    public long timestampNanos() {
        return timestampNanos;
    }
}
