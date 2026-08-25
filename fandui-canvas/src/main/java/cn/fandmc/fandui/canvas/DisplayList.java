package cn.fandmc.fandui.canvas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DisplayList {
    private final List<DisplayCommand> commands;
    private final int maximumClipDepth;
    private final boolean hasBackdropBlur;

    /** Takes exclusive ownership of {@code commands}; callers must not mutate it afterwards. */
    DisplayList(List<DisplayCommand> commands, int maximumClipDepth) {
        Objects.requireNonNull(commands, "commands");
        if (maximumClipDepth < 0 || maximumClipDepth > RecordingCanvas2D.MAX_CLIP_DEPTH) {
            throw new IllegalArgumentException("Invalid maximumClipDepth: " + maximumClipDepth);
        }
        boolean backdropBlur = false;
        for (DisplayCommand command : commands) {
            Objects.requireNonNull(command, "commands contains null");
            backdropBlur |= command instanceof DisplayCommand.BackdropBlur;
        }
        this.commands = Collections.unmodifiableList(commands);
        this.maximumClipDepth = maximumClipDepth;
        this.hasBackdropBlur = backdropBlur;
    }

    public List<DisplayCommand> commands() {
        return commands;
    }

    public int maximumClipDepth() {
        return maximumClipDepth;
    }

    public boolean hasBackdropBlur() {
        return hasBackdropBlur;
    }

    public boolean empty() {
        return commands.isEmpty();
    }

    public static DisplayList combine(List<DisplayList> displayLists) {
        Objects.requireNonNull(displayLists, "displayLists");
        if (displayLists.size() == 1) {
            return Objects.requireNonNull(displayLists.get(0), "displayLists[0]");
        }

        int commandCount = 0;
        int maximumClipDepth = 0;
        for (int index = 0; index < displayLists.size(); index++) {
            DisplayList displayList = Objects.requireNonNull(
                    displayLists.get(index), "displayLists[" + index + "]");
            commandCount = Math.addExact(commandCount, displayList.commands.size());
            maximumClipDepth = Math.max(maximumClipDepth, displayList.maximumClipDepth);
        }

        List<DisplayCommand> combined = new ArrayList<>(commandCount);
        for (DisplayList displayList : displayLists) {
            combined.addAll(displayList.commands);
        }
        return new DisplayList(combined, maximumClipDepth);
    }
}
