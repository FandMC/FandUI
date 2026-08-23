package cn.fandmc.fandui.api.canvas;

import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.Transform2D;
import cn.fandmc.fandui.api.text.TextLayout;

/**
 * Platform-neutral, premultiplied-alpha 2D drawing surface used during component painting.
 *
 * <p>A canvas is callback-scoped: it must not be retained after
 * {@link cn.fandmc.fandui.api.component.UiComponent#paint(
 * cn.fandmc.fandui.api.component.PaintScope)} returns. Coordinates are logical UI pixels.
 * Drawing is recorded into an immutable display list; no native or Minecraft handle is
 * exposed. State changes are nested with {@link #save()} and restored by closing the
 * returned handle.</p>
 */
public interface Canvas2D {
    /** Saves all canvas state and returns an idempotent lexical restore handle. */
    CanvasState save();

    void translate(float x, float y);

    void scale(float x, float y);

    void rotate(float radians);

    void transform(Transform2D transform);

    void setCompositeOperation(CompositeOperation operation);

    void setGlobalAlpha(float alpha);

    void scissor(Rect rect);

    void intersectScissor(Rect rect);

    void resetScissor();

    /** Intersects the current clip with an arbitrary immutable path. */
    void clip(Path path);

    /** Records a backdrop blur sampling content already rendered behind {@code rect}. */
    void backdropBlur(Rect rect, CornerRadii radii, float radius);

    void fillRect(Rect rect, Paint paint);

    void fillRoundedRect(Rect rect, CornerRadii radii, Paint paint);

    void fill(Path path, Paint paint);

    void stroke(Path path, Paint paint, StrokeStyle style);

    void drawImage(ImageRef image, Rect destination, ImageSampling sampling, float opacity);

    void drawImage(ImageRef image, Rect source, Rect destination, ImageSampling sampling, float opacity);

    /** Draws an already completed text layout at a logical-pixel origin. */
    void drawText(TextLayout text, Point origin);
}
