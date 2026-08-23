package cn.fandmc.fandui.core.scene;

import cn.fandmc.fandui.api.canvas.Canvas2D;
import cn.fandmc.fandui.api.canvas.CanvasState;
import cn.fandmc.fandui.api.canvas.CompositeOperation;
import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.Transform2D;
import cn.fandmc.fandui.api.text.TextLayout;

import java.util.concurrent.atomic.AtomicBoolean;

final class ScopedCanvas2D implements Canvas2D {
    private final Canvas2D delegate;
    private boolean active = true;

    ScopedCanvas2D(Canvas2D delegate) {
        this.delegate = delegate;
    }

    void deactivate() {
        active = false;
    }

    @Override
    public CanvasState save() {
        requireActive();
        return new ScopedState(this, delegate.save());
    }

    @Override
    public void translate(float x, float y) {
        requireActive();
        delegate.translate(x, y);
    }

    @Override
    public void scale(float x, float y) {
        requireActive();
        delegate.scale(x, y);
    }

    @Override
    public void rotate(float radians) {
        requireActive();
        delegate.rotate(radians);
    }

    @Override
    public void transform(Transform2D transform) {
        requireActive();
        delegate.transform(transform);
    }

    @Override
    public void setCompositeOperation(CompositeOperation operation) {
        requireActive();
        delegate.setCompositeOperation(operation);
    }

    @Override
    public void setGlobalAlpha(float alpha) {
        requireActive();
        delegate.setGlobalAlpha(alpha);
    }

    @Override
    public void scissor(Rect rect) {
        requireActive();
        delegate.scissor(rect);
    }

    @Override
    public void intersectScissor(Rect rect) {
        requireActive();
        delegate.intersectScissor(rect);
    }

    @Override
    public void resetScissor() {
        requireActive();
        delegate.resetScissor();
    }

    @Override
    public void clip(Path path) {
        requireActive();
        delegate.clip(path);
    }

    @Override
    public void backdropBlur(Rect rect, CornerRadii radii, float radius) {
        requireActive();
        delegate.backdropBlur(rect, radii, radius);
    }

    @Override
    public void fillRect(Rect rect, Paint paint) {
        requireActive();
        delegate.fillRect(rect, paint);
    }

    @Override
    public void fillRoundedRect(Rect rect, CornerRadii radii, Paint paint) {
        requireActive();
        delegate.fillRoundedRect(rect, radii, paint);
    }

    @Override
    public void fill(Path path, Paint paint) {
        requireActive();
        delegate.fill(path, paint);
    }

    @Override
    public void stroke(Path path, Paint paint, StrokeStyle style) {
        requireActive();
        delegate.stroke(path, paint, style);
    }

    @Override
    public void drawImage(ImageRef image, Rect destination, ImageSampling sampling, float opacity) {
        requireActive();
        delegate.drawImage(image, destination, sampling, opacity);
    }

    @Override
    public void drawImage(
            ImageRef image,
            Rect source,
            Rect destination,
            ImageSampling sampling,
            float opacity) {
        requireActive();
        delegate.drawImage(image, source, destination, sampling, opacity);
    }

    @Override
    public void drawText(TextLayout text, Point origin) {
        requireActive();
        delegate.drawText(text, origin);
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("Canvas2D is only valid during its paint callback");
        }
    }

    private static final class ScopedState implements CanvasState {
        private final ScopedCanvas2D scope;
        private final CanvasState delegate;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ScopedState(ScopedCanvas2D scope, CanvasState delegate) {
            this.scope = scope;
            this.delegate = delegate;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                scope.requireActive();
                delegate.close();
            }
        }
    }
}
