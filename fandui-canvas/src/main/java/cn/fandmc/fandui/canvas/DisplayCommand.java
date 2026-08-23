package cn.fandmc.fandui.canvas;

import cn.fandmc.fandui.api.canvas.CompositeOperation;
import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Transform2D;
import cn.fandmc.fandui.api.text.TextLayout;

import java.util.Objects;

public sealed interface DisplayCommand permits
        DisplayCommand.Save,
        DisplayCommand.Restore,
        DisplayCommand.Translate,
        DisplayCommand.Scale,
        DisplayCommand.Rotate,
        DisplayCommand.Transform,
        DisplayCommand.SetCompositeOperation,
        DisplayCommand.SetGlobalAlpha,
        DisplayCommand.Scissor,
        DisplayCommand.IntersectScissor,
        DisplayCommand.ResetScissor,
        DisplayCommand.Clip,
        DisplayCommand.BackdropBlur,
        DisplayCommand.FillRect,
        DisplayCommand.FillRoundedRect,
        DisplayCommand.FillPath,
        DisplayCommand.StrokePath,
        DisplayCommand.DrawImage,
        DisplayCommand.DrawImageRegion,
        DisplayCommand.DrawText {
    enum Save implements DisplayCommand {
        INSTANCE
    }

    enum Restore implements DisplayCommand {
        INSTANCE
    }

    record Translate(float x, float y) implements DisplayCommand {
        public Translate {
            requireFinite(x, "x");
            requireFinite(y, "y");
        }
    }

    record Scale(float x, float y) implements DisplayCommand {
        public Scale {
            requireFinite(x, "x");
            requireFinite(y, "y");
        }
    }

    record Rotate(float radians) implements DisplayCommand {
        public Rotate {
            requireFinite(radians, "radians");
        }
    }

    record Transform(Transform2D value) implements DisplayCommand {
        public Transform {
            Objects.requireNonNull(value, "value");
        }
    }

    record SetCompositeOperation(CompositeOperation operation) implements DisplayCommand {
        public SetCompositeOperation {
            Objects.requireNonNull(operation, "operation");
        }
    }

    record SetGlobalAlpha(float alpha) implements DisplayCommand {
        public SetGlobalAlpha {
            requireUnit(alpha, "alpha");
        }
    }

    record Scissor(Rect rect) implements DisplayCommand {
        public Scissor {
            Objects.requireNonNull(rect, "rect");
        }
    }

    record IntersectScissor(Rect rect) implements DisplayCommand {
        public IntersectScissor {
            Objects.requireNonNull(rect, "rect");
        }
    }

    enum ResetScissor implements DisplayCommand {
        INSTANCE
    }

    record Clip(Path path, int depth) implements DisplayCommand {
        public Clip {
            Objects.requireNonNull(path, "path");
            if (depth < 1 || depth > RecordingCanvas2D.MAX_CLIP_DEPTH) {
                throw new IllegalArgumentException("Invalid path clip depth: " + depth);
            }
        }
    }

    record BackdropBlur(Rect rect, CornerRadii radii, float radius) implements DisplayCommand {
        public BackdropBlur {
            Objects.requireNonNull(rect, "rect");
            Objects.requireNonNull(radii, "radii");
            requireFinite(radius, "radius");
            if (radius < 0.0f) {
                throw new IllegalArgumentException("radius must not be negative");
            }
        }
    }

    record FillRect(Rect rect, DisplayPaint paint) implements DisplayCommand {
        public FillRect {
            Objects.requireNonNull(rect, "rect");
            Objects.requireNonNull(paint, "paint");
        }
    }

    record FillRoundedRect(Rect rect, CornerRadii radii, DisplayPaint paint) implements DisplayCommand {
        public FillRoundedRect {
            Objects.requireNonNull(rect, "rect");
            Objects.requireNonNull(radii, "radii");
            Objects.requireNonNull(paint, "paint");
        }
    }

    record FillPath(Path path, DisplayPaint paint) implements DisplayCommand {
        public FillPath {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(paint, "paint");
        }
    }

    record StrokePath(Path path, DisplayPaint paint, StrokeStyle style) implements DisplayCommand {
        public StrokePath {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(paint, "paint");
            Objects.requireNonNull(style, "style");
        }
    }

    record DrawImage(
            ImageRef image,
            Rect destination,
            ImageSampling sampling,
            float opacity) implements DisplayCommand {
        public DrawImage {
            Objects.requireNonNull(image, "image");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(sampling, "sampling");
            requireUnit(opacity, "opacity");
        }
    }

    record DrawImageRegion(
            ImageRef image,
            Rect source,
            Rect destination,
            ImageSampling sampling,
            float opacity) implements DisplayCommand {
        public DrawImageRegion {
            Objects.requireNonNull(image, "image");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(sampling, "sampling");
            requireUnit(opacity, "opacity");
        }
    }

    record DrawText(TextLayout text, Point origin) implements DisplayCommand {
        public DrawText {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(origin, "origin");
        }
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireUnit(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
