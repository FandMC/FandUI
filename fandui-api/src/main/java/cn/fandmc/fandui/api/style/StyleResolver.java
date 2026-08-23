package cn.fandmc.fandui.api.style;

import java.util.Objects;

/** Resolves an immutable style on the UI thread for one component and visual state. */
@FunctionalInterface
public interface StyleResolver {
    /** Must be deterministic for the supplied immutable theme and state. */
    Style resolve(Theme theme, VisualState state);

    /** Wraps one immutable style as a resolver. */
    static StyleResolver fixed(Style style) {
        Objects.requireNonNull(style, "style");
        return (theme, state) -> style;
    }
}
