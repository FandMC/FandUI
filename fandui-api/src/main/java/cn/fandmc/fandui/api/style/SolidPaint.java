package cn.fandmc.fandui.api.style;

import java.util.Objects;

/** Immutable solid-color paint. */
public record SolidPaint(Color color) implements Paint {
    public SolidPaint {
        Objects.requireNonNull(color, "color");
    }
}
