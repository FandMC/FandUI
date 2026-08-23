package cn.fandmc.fandui.core.scene;

import cn.fandmc.fandui.api.canvas.CanvasState;
import cn.fandmc.fandui.api.canvas.Canvas2D;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.component.ContentClipProvider;
import cn.fandmc.fandui.api.component.PaintScope;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.Transform2D;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.canvas.RecordingCanvas2D;
import cn.fandmc.fandui.core.layout.LayoutNode;
import cn.fandmc.fandui.core.layout.LayoutSnapshot;

import java.util.Objects;

public final class SceneCompiler {
    private static final Transform2D IDENTITY = new Transform2D(1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f);

    public DisplayList compile(LayoutSnapshot layout, long frameTimeNanos) {
        Objects.requireNonNull(layout, "layout");
        if (frameTimeNanos < 0L) {
            throw new IllegalArgumentException("frameTimeNanos must not be negative");
        }
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        try {
            paintNode(layout.root(), canvas, frameTimeNanos, 1.0f);
            return canvas.finish();
        } catch (RuntimeException | Error exception) {
            canvas.abort();
            throw exception;
        }
    }

    private void paintNode(
            LayoutNode node,
            RecordingCanvas2D canvas,
            long frameTimeNanos,
            float inheritedOpacity) {
        if (!node.visible()) {
            return;
        }
        CanvasState nodeState = canvas.save();
        canvas.translate(node.position().x(), node.position().y());
        Style style = node.style();
        if (!style.transform().equals(IDENTITY)) {
            canvas.transform(style.transform());
        }
        float opacity = inheritedOpacity * style.opacity();
        canvas.setGlobalAlpha(opacity);
        Rect bounds = new Rect(0.0f, 0.0f, node.size().width(), node.size().height());
        applyClip(canvas, node, style, bounds);
        if (style.backdropBlurRadius() > 0.0f) {
            canvas.backdropBlur(bounds, style.cornerRadii(), style.backdropBlurRadius());
        }

        CanvasState componentState = canvas.save();
        int expectedDepth = canvas.stateDepth();
        ScopedCanvas2D scopedCanvas = new ScopedCanvas2D(canvas);
        CallbackPaintScope paintScope = new CallbackPaintScope(
                scopedCanvas, bounds, style, node.theme(), frameTimeNanos);
        try {
            node.component().paint(paintScope);
        } catch (RuntimeException exception) {
            throw new SceneCompileException(
                    "Paint callback failed for " + node.component().getClass().getName(),
                    exception);
        } finally {
            paintScope.deactivate();
            scopedCanvas.deactivate();
        }
        canvas.requireStateDepth(expectedDepth);
        componentState.close();

        for (LayoutNode child : node.children()) {
            paintNode(child, canvas, frameTimeNanos, opacity);
        }
        nodeState.close();
    }

    private static void applyClip(RecordingCanvas2D canvas, LayoutNode node, Style style, Rect bounds) {
        if (node.component() instanceof ContentClipProvider provider
                && provider.contentClip() == ClipMode.BOUNDS) {
            canvas.intersectScissor(bounds);
        }
        if (style.clip() == ClipMode.BOUNDS) {
            canvas.intersectScissor(bounds);
        } else if (style.clip() == ClipMode.ROUNDED_BOUNDS) {
            canvas.clip(Path.builder().roundedRect(bounds, style.cornerRadii()).build());
        }
    }

    private static final class CallbackPaintScope implements PaintScope {
        private final ScopedCanvas2D canvas;
        private final Rect bounds;
        private final Style style;
        private final Theme theme;
        private final long frameTimeNanos;
        private boolean active = true;

        private CallbackPaintScope(
                ScopedCanvas2D canvas,
                Rect bounds,
                Style style,
                Theme theme,
                long frameTimeNanos) {
            this.canvas = canvas;
            this.bounds = bounds;
            this.style = style;
            this.theme = theme;
            this.frameTimeNanos = frameTimeNanos;
        }

        private void deactivate() {
            active = false;
        }

        @Override
        public Canvas2D canvas() {
            requireActive();
            return canvas;
        }

        @Override
        public Rect bounds() {
            requireActive();
            return bounds;
        }

        @Override
        public Style style() {
            requireActive();
            return style;
        }

        @Override
        public Theme theme() {
            requireActive();
            return theme;
        }

        @Override
        public long frameTimeNanos() {
            requireActive();
            return frameTimeNanos;
        }

        private void requireActive() {
            if (!active) {
                throw new IllegalStateException("PaintScope is only valid during its callback");
            }
        }
    }
}
