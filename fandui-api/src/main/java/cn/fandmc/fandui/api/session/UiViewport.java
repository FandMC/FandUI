package cn.fandmc.fandui.api.session;

import cn.fandmc.fandui.internal.validation.Preconditions;

/** Immutable logical and framebuffer dimensions for the current UI frame. */
public record UiViewport(
        float logicalWidth,
        float logicalHeight,
        int framebufferWidth,
        int framebufferHeight,
        float devicePixelRatio) {
    public UiViewport {
        Preconditions.nonNegative(logicalWidth, "logicalWidth");
        Preconditions.nonNegative(logicalHeight, "logicalHeight");
        if (framebufferWidth < 0 || framebufferHeight < 0) {
            throw new IllegalArgumentException("Framebuffer dimensions must not be negative");
        }
        Preconditions.finite(devicePixelRatio, "devicePixelRatio");
        if (devicePixelRatio <= 0.0f) {
            throw new IllegalArgumentException("devicePixelRatio must be positive");
        }
    }
}
