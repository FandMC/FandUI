package cn.fandmc.fandui.canvas;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RecordingCanvas2D implements Canvas2D {
    public static final int MAX_CLIP_DEPTH = 8;
    private static final int INITIAL_STATE_CAPACITY = 16;

    private final Thread ownerThread = Thread.currentThread();
    private final List<DisplayCommand> commands = new ArrayList<>();
    private int[] stateIds = new int[INITIAL_STATE_CAPACITY];
    private int[] stateClipDepths = new int[INITIAL_STATE_CAPACITY];
    private float[] stateGlobalAlphas = new float[INITIAL_STATE_CAPACITY];
    private boolean active = true;
    private int nextStateId;
    private int stateDepth;
    private int clipDepth;
    private int maximumClipDepth;
    private float globalAlpha = 1.0f;
    private Paint firstPaint;
    private DisplayPaint firstDisplayPaint;
    private Paint secondPaint;
    private DisplayPaint secondDisplayPaint;
    private Map<Paint, DisplayPaint> displayPaints;
    private String failure;

    private RecordingCanvas2D() {
    }

    public static RecordingCanvas2D begin() {
        return new RecordingCanvas2D();
    }

    public DisplayList finish() {
        ensureOwnerThread();
        if (!active) {
            throw new IllegalStateException("Canvas recording scope is no longer active");
        }
        active = false;
        if (failure != null) {
            throw new DisplayListException(failure);
        }
        if (stateDepth != 0) {
            throw new DisplayListException("Canvas recording ended with " + stateDepth + " unclosed state(s)");
        }
        return new DisplayList(commands, maximumClipDepth);
    }

    public void abort() {
        ensureOwnerThread();
        if (active) {
            active = false;
            commands.clear();
            stateDepth = 0;
        }
    }

    @Override
    public CanvasState save() {
        ensureUsable();
        int stateId = ++nextStateId;
        ensureStateCapacity(stateDepth + 1);
        stateIds[stateDepth] = stateId;
        stateClipDepths[stateDepth] = clipDepth;
        stateGlobalAlphas[stateDepth] = globalAlpha;
        stateDepth++;
        commands.add(DisplayCommand.Save.INSTANCE);
        return new SavedState(this, stateId);
    }

    @Override
    public void translate(float x, float y) {
        ensureUsable();
        commands.add(new DisplayCommand.Translate(x, y));
    }

    @Override
    public void scale(float x, float y) {
        ensureUsable();
        commands.add(new DisplayCommand.Scale(x, y));
    }

    @Override
    public void rotate(float radians) {
        ensureUsable();
        commands.add(new DisplayCommand.Rotate(radians));
    }

    @Override
    public void transform(Transform2D transform) {
        ensureUsable();
        commands.add(new DisplayCommand.Transform(transform));
    }

    @Override
    public void setCompositeOperation(CompositeOperation operation) {
        ensureUsable();
        commands.add(new DisplayCommand.SetCompositeOperation(operation));
    }

    @Override
    public void setGlobalAlpha(float alpha) {
        ensureUsable();
        if (Float.compare(globalAlpha, alpha) != 0) {
            commands.add(new DisplayCommand.SetGlobalAlpha(alpha));
            globalAlpha = alpha;
        }
    }

    public int stateDepth() {
        ensureUsable();
        return stateDepth;
    }

    public void requireStateDepth(int expectedDepth) {
        ensureUsable();
        if (stateDepth != expectedDepth) {
            fail("Canvas callback changed save depth from " + expectedDepth + " to " + stateDepth);
        }
    }

    @Override
    public void scissor(Rect rect) {
        ensureUsable();
        commands.add(new DisplayCommand.Scissor(rect));
    }

    @Override
    public void intersectScissor(Rect rect) {
        ensureUsable();
        commands.add(new DisplayCommand.IntersectScissor(rect));
    }

    @Override
    public void resetScissor() {
        ensureUsable();
        commands.add(DisplayCommand.ResetScissor.INSTANCE);
    }

    @Override
    public void clip(Path path) {
        ensureUsable();
        Objects.requireNonNull(path, "path");
        if (clipDepth == MAX_CLIP_DEPTH) {
            fail("Path clip depth exceeds " + MAX_CLIP_DEPTH);
        }
        clipDepth++;
        maximumClipDepth = Math.max(maximumClipDepth, clipDepth);
        commands.add(new DisplayCommand.Clip(path, clipDepth));
    }

    @Override
    public void backdropBlur(Rect rect, CornerRadii radii, float radius) {
        ensureUsable();
        commands.add(new DisplayCommand.BackdropBlur(rect, radii, radius));
    }

    @Override
    public void fillRect(Rect rect, Paint paint) {
        ensureUsable();
        commands.add(new DisplayCommand.FillRect(rect, compilePaint(paint)));
    }

    @Override
    public void fillRoundedRect(Rect rect, CornerRadii radii, Paint paint) {
        ensureUsable();
        commands.add(new DisplayCommand.FillRoundedRect(rect, radii, compilePaint(paint)));
    }

    @Override
    public void fill(Path path, Paint paint) {
        ensureUsable();
        commands.add(new DisplayCommand.FillPath(path, compilePaint(paint)));
    }

    @Override
    public void stroke(Path path, Paint paint, StrokeStyle style) {
        ensureUsable();
        commands.add(new DisplayCommand.StrokePath(path, compilePaint(paint), style));
    }

    @Override
    public void drawImage(ImageRef image, Rect destination, ImageSampling sampling, float opacity) {
        ensureUsable();
        commands.add(new DisplayCommand.DrawImage(image, destination, sampling, opacity));
    }

    @Override
    public void drawImage(
            ImageRef image,
            Rect source,
            Rect destination,
            ImageSampling sampling,
            float opacity) {
        ensureUsable();
        commands.add(new DisplayCommand.DrawImageRegion(image, source, destination, sampling, opacity));
    }

    @Override
    public void drawText(TextLayout text, Point origin) {
        ensureUsable();
        commands.add(new DisplayCommand.DrawText(text, origin));
    }

    private void restore(int stateId) {
        ensureUsable();
        int currentIndex = stateDepth - 1;
        if (currentIndex < 0 || stateIds[currentIndex] != stateId) {
            fail("Canvas states must be closed in reverse save order");
        }
        stateDepth = currentIndex;
        clipDepth = stateClipDepths[currentIndex];
        globalAlpha = stateGlobalAlphas[currentIndex];
        commands.add(DisplayCommand.Restore.INSTANCE);
    }

    private DisplayPaint compilePaint(Paint paint) {
        Objects.requireNonNull(paint, "paint");
        if (firstPaint != null && firstPaint.equals(paint)) {
            return firstDisplayPaint;
        }
        if (secondPaint != null && secondPaint.equals(paint)) {
            return secondDisplayPaint;
        }
        if (displayPaints != null) {
            DisplayPaint cached = displayPaints.get(paint);
            if (cached != null) {
                return cached;
            }
        }

        DisplayPaint compiled = PaintCompiler.compile(paint);
        if (firstPaint == null) {
            firstPaint = paint;
            firstDisplayPaint = compiled;
        } else if (secondPaint == null) {
            secondPaint = paint;
            secondDisplayPaint = compiled;
        } else {
            if (displayPaints == null) {
                displayPaints = new HashMap<>();
                displayPaints.put(firstPaint, firstDisplayPaint);
                displayPaints.put(secondPaint, secondDisplayPaint);
            }
            displayPaints.put(paint, compiled);
        }
        return compiled;
    }

    private void ensureStateCapacity(int requiredCapacity) {
        if (requiredCapacity <= stateIds.length) {
            return;
        }
        int nextCapacity = Math.multiplyExact(stateIds.length, 2);
        while (nextCapacity < requiredCapacity) {
            nextCapacity = Math.multiplyExact(nextCapacity, 2);
        }
        stateIds = Arrays.copyOf(stateIds, nextCapacity);
        stateClipDepths = Arrays.copyOf(stateClipDepths, nextCapacity);
        stateGlobalAlphas = Arrays.copyOf(stateGlobalAlphas, nextCapacity);
    }

    private void ensureUsable() {
        ensureOwnerThread();
        if (!active) {
            throw new IllegalStateException("Canvas recording scope is no longer active");
        }
        if (failure != null) {
            throw new DisplayListException(failure);
        }
    }

    private void ensureOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Canvas recording is confined to its creating thread");
        }
    }

    private void fail(String message) {
        if (failure == null) {
            failure = message;
        }
        throw new DisplayListException(failure);
    }

    private static final class SavedState implements CanvasState {
        private final RecordingCanvas2D owner;
        private final int stateId;
        private boolean closed;

        private SavedState(RecordingCanvas2D owner, int stateId) {
            this.owner = owner;
            this.stateId = stateId;
        }

        @Override
        public void close() {
            if (!closed) {
                owner.restore(stateId);
                closed = true;
            }
        }
    }
}
