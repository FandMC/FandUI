package cn.fandmc.fandui.api;

import java.util.Objects;

/**
 * Immutable snapshot of the renderer and borrowed Minecraft UI target observed by FandUI.
 *
 * <p>A target is reported as ready only after a FandUI pass has validated and used it.
 * Zero dimensions and {@link UiColorFormat#UNKNOWN} represent the absence of such a target.</p>
 */
public record UiDiagnostics(
        UiRendererBackend backend,
        String backendName,
        boolean targetReady,
        int framebufferWidth,
        int framebufferHeight,
        UiColorFormat colorFormat,
        int maximumTextureSize,
        boolean stencilClipping,
        boolean backdropBlur,
        String detail
) {
    public UiDiagnostics {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(backendName, "backendName");
        Objects.requireNonNull(colorFormat, "colorFormat");
        Objects.requireNonNull(detail, "detail");
        if (framebufferWidth < 0 || framebufferHeight < 0) {
            throw new IllegalArgumentException("framebuffer dimensions must not be negative");
        }
        if ((framebufferWidth == 0) != (framebufferHeight == 0)) {
            throw new IllegalArgumentException("framebuffer dimensions must both be zero or both be positive");
        }
        if (maximumTextureSize < 0) {
            throw new IllegalArgumentException("maximumTextureSize must not be negative");
        }
        if (targetReady && framebufferWidth == 0) {
            throw new IllegalArgumentException("a ready render target must have positive dimensions");
        }
        if (targetReady && (backend == UiRendererBackend.UNKNOWN || colorFormat == UiColorFormat.UNKNOWN)) {
            throw new IllegalArgumentException("a ready render target must identify its backend and format");
        }
        if (!targetReady && (framebufferWidth != 0 || colorFormat != UiColorFormat.UNKNOWN)) {
            throw new IllegalArgumentException("a non-ready render target cannot publish dimensions or a format");
        }
        if (!targetReady && (stencilClipping || backdropBlur)) {
            throw new IllegalArgumentException("target-dependent features require a ready render target");
        }
    }

    /** Returns an initial snapshot before the platform bridge has identified a renderer. */
    public static UiDiagnostics unknown(String detail) {
        return detached(UiRendererBackend.UNKNOWN, "unknown", 0, detail);
    }

    /** Returns a snapshot with a known backend but no validated render target. */
    public static UiDiagnostics detached(
            UiRendererBackend backend,
            String backendName,
            int maximumTextureSize,
            String detail) {
        return new UiDiagnostics(
                backend,
                backendName,
                false,
                0,
                0,
                UiColorFormat.UNKNOWN,
                maximumTextureSize,
                false,
                false,
                detail);
    }

    /** Returns a validated target snapshot. */
    public static UiDiagnostics ready(
            UiRendererBackend backend,
            String backendName,
            int framebufferWidth,
            int framebufferHeight,
            UiColorFormat colorFormat,
            int maximumTextureSize,
            boolean stencilClipping,
            boolean backdropBlur,
            String detail) {
        return new UiDiagnostics(
                backend,
                backendName,
                true,
                framebufferWidth,
                framebufferHeight,
                colorFormat,
                maximumTextureSize,
                stencilClipping,
                backdropBlur,
                detail);
    }

    /** Preserves backend limits while removing target-dependent state. */
    public UiDiagnostics withoutTarget(String nextDetail) {
        return detached(backend, backendName, maximumTextureSize, nextDetail);
    }
}
