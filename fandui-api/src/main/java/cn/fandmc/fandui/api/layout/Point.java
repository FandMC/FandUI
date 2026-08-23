package cn.fandmc.fandui.api.layout;

import cn.fandmc.fandui.internal.validation.Preconditions;

/** Immutable finite point in logical UI coordinates. */
public record Point(float x, float y) {
    public Point {
        Preconditions.finite(x, "x");
        Preconditions.finite(y, "y");
    }
}
