package cn.fandmc.fandui.api;

import java.util.Objects;

/** Immutable set of optional input capabilities exposed by the active platform bridge. */
public final class UiCapabilities {
    private final boolean imeComposition;
    private final boolean distinctKeyRepeat;

    private UiCapabilities(boolean imeComposition, boolean distinctKeyRepeat) {
        this.imeComposition = imeComposition;
        this.distinctKeyRepeat = distinctKeyRepeat;
    }

    public static UiCapabilities of(boolean imeComposition, boolean distinctKeyRepeat) {
        return new UiCapabilities(imeComposition, distinctKeyRepeat);
    }

    public boolean imeComposition() {
        return imeComposition;
    }

    public boolean distinctKeyRepeat() {
        return distinctKeyRepeat;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof UiCapabilities capabilities
                && imeComposition == capabilities.imeComposition
                && distinctKeyRepeat == capabilities.distinctKeyRepeat;
    }

    @Override
    public int hashCode() {
        return Objects.hash(imeComposition, distinctKeyRepeat);
    }

    @Override
    public String toString() {
        return "UiCapabilities[imeComposition=" + imeComposition
                + ", distinctKeyRepeat=" + distinctKeyRepeat + ']';
    }
}
