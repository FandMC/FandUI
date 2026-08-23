package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.canvas.CanvasState;
import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import cn.fandmc.fandui.internal.validation.Preconditions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** A resource-backed image with aspect-ratio aware sizing and atlas-region support. */
public final class Image extends UiComponent {
    private ImageRef image;
    private @Nullable Rect source;
    private @Nullable Size preferredSize;
    private ImageFit fit;
    private Alignment alignment;
    private ImageSampling sampling;
    private float opacity;

    private Image(Builder builder) {
        super(builder.key);
        image = builder.image;
        source = builder.source;
        preferredSize = builder.preferredSize;
        fit = builder.fit;
        alignment = builder.alignment;
        sampling = builder.sampling;
        opacity = builder.opacity;
        if (builder.style != null) {
            setStyle(builder.style);
        }
    }

    public static Builder builder(ImageRef image) {
        return new Builder(image);
    }

    public static Image of(ImageRef image) {
        return builder(image).build();
    }

    public ImageRef image() {
        return image;
    }

    public void setImage(ImageRef image) {
        ComponentBindings.assertMutationAllowed(this);
        ImageRef checked = Objects.requireNonNull(image, "image");
        if (this.image != checked) {
            this.image = checked;
            invalidateLayout();
        }
    }

    public Optional<Rect> source() {
        return Optional.ofNullable(source);
    }

    public void setSource(Rect source) {
        ComponentBindings.assertMutationAllowed(this);
        Rect checked = positiveSource(source);
        if (!checked.equals(this.source)) {
            this.source = checked;
            invalidateLayout();
        }
    }

    public void clearSource() {
        ComponentBindings.assertMutationAllowed(this);
        if (source != null) {
            source = null;
            invalidateLayout();
        }
    }

    public Optional<Size> preferredSize() {
        return Optional.ofNullable(preferredSize);
    }

    public void setPreferredSize(Size preferredSize) {
        ComponentBindings.assertMutationAllowed(this);
        Size checked = Objects.requireNonNull(preferredSize, "preferredSize");
        if (!checked.equals(this.preferredSize)) {
            this.preferredSize = checked;
            invalidateLayout();
        }
    }

    public void clearPreferredSize() {
        ComponentBindings.assertMutationAllowed(this);
        if (preferredSize != null) {
            preferredSize = null;
            invalidateLayout();
        }
    }

    public ImageFit fit() {
        return fit;
    }

    public void setFit(ImageFit fit) {
        ComponentBindings.assertMutationAllowed(this);
        ImageFit checked = Objects.requireNonNull(fit, "fit");
        if (this.fit != checked) {
            this.fit = checked;
            invalidatePaint();
        }
    }

    public Alignment alignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        ComponentBindings.assertMutationAllowed(this);
        Alignment checked = Objects.requireNonNull(alignment, "alignment");
        if (this.alignment != checked) {
            this.alignment = checked;
            invalidatePaint();
        }
    }

    public ImageSampling sampling() {
        return sampling;
    }

    public void setSampling(ImageSampling sampling) {
        ComponentBindings.assertMutationAllowed(this);
        ImageSampling checked = Objects.requireNonNull(sampling, "sampling");
        if (this.sampling != checked) {
            this.sampling = checked;
            invalidatePaint();
        }
    }

    public float opacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        ComponentBindings.assertMutationAllowed(this);
        float checked = Preconditions.unit(opacity, "opacity");
        if (Float.compare(this.opacity, checked) != 0) {
            this.opacity = checked;
            invalidatePaint();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Size natural = naturalSize();
        Size desired = preferredSize == null ? natural : preferredSize;
        Size measured = constraints.constrain(desired);
        return scope.layout(measured.width(), measured.height(), placements -> { });
    }

    @Override
    public void paint(PaintScope scope) {
        if (opacity == 0.0f || image.state() != ResourceState.READY) {
            return;
        }
        ImageInfo info = image.info().orElse(null);
        if (info == null) {
            return;
        }
        Rect imageSource = source == null
                ? new Rect(0.0f, 0.0f, info.width(), info.height())
                : checkedSource(source, info);
        Rect destination = destination(imageSource.width(), imageSource.height(), scope.bounds(), fit, alignment);
        if (destination.width() == 0.0f || destination.height() == 0.0f) {
            return;
        }

        CanvasState state = scope.canvas().save();
        try {
            scope.canvas().intersectScissor(scope.bounds());
            if (source == null) {
                scope.canvas().drawImage(image, destination, sampling, opacity);
            } else {
                scope.canvas().drawImage(image, imageSource, destination, sampling, opacity);
            }
        } finally {
            state.close();
        }
    }

    private Size naturalSize() {
        if (source != null) {
            return new Size(source.width(), source.height());
        }
        return image.info()
                .map(info -> new Size(info.width(), info.height()))
                .orElseGet(() -> new Size(0.0f, 0.0f));
    }

    static Rect destination(
            float sourceWidth,
            float sourceHeight,
            Rect bounds,
            ImageFit fit,
            Alignment alignment) {
        Preconditions.nonNegative(sourceWidth, "sourceWidth");
        Preconditions.nonNegative(sourceHeight, "sourceHeight");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(alignment, "alignment");
        if (sourceWidth == 0.0f || sourceHeight == 0.0f
                || bounds.width() == 0.0f || bounds.height() == 0.0f) {
            return new Rect(bounds.x(), bounds.y(), 0.0f, 0.0f);
        }

        float width;
        float height;
        if (fit == ImageFit.FILL) {
            width = bounds.width();
            height = bounds.height();
        } else {
            float containScale = Math.min(bounds.width() / sourceWidth, bounds.height() / sourceHeight);
            float scale = switch (fit) {
                case CONTAIN -> containScale;
                case COVER -> Math.max(bounds.width() / sourceWidth, bounds.height() / sourceHeight);
                case NONE -> 1.0f;
                case SCALE_DOWN -> Math.min(1.0f, containScale);
                case FILL -> throw new AssertionError("FILL handled above");
            };
            width = sourceWidth * scale;
            height = sourceHeight * scale;
        }
        float x = bounds.x() + (bounds.width() - width) * alignment.horizontalFactor();
        float y = bounds.y() + (bounds.height() - height) * alignment.verticalFactor();
        return new Rect(x, y, width, height);
    }

    private static Rect positiveSource(Rect source) {
        Objects.requireNonNull(source, "source");
        if (source.width() <= 0.0f || source.height() <= 0.0f) {
            throw new IllegalArgumentException("Image source dimensions must be positive");
        }
        return source;
    }

    private static Rect checkedSource(Rect source, ImageInfo info) {
        if (source.x() + source.width() > info.width()
                || source.y() + source.height() > info.height()) {
            throw new IllegalStateException("Image source rectangle is outside " + info.width() + "x" + info.height());
        }
        return source;
    }

    public static final class Builder {
        private final ImageRef image;
        private @Nullable UiKey key;
        private @Nullable Rect source;
        private @Nullable Size preferredSize;
        private ImageFit fit = ImageFit.CONTAIN;
        private Alignment alignment = Alignment.CENTER;
        private ImageSampling sampling = ImageSampling.LINEAR;
        private float opacity = 1.0f;
        private @Nullable StyleResolver style;

        private Builder(ImageRef image) {
            this.image = Objects.requireNonNull(image, "image");
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder source(Rect source) {
            this.source = positiveSource(source);
            return this;
        }

        public Builder size(float width, float height) {
            return size(new Size(width, height));
        }

        public Builder size(Size size) {
            this.preferredSize = Objects.requireNonNull(size, "size");
            return this;
        }

        public Builder fit(ImageFit fit) {
            this.fit = Objects.requireNonNull(fit, "fit");
            return this;
        }

        public Builder alignment(Alignment alignment) {
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public Builder sampling(ImageSampling sampling) {
            this.sampling = Objects.requireNonNull(sampling, "sampling");
            return this;
        }

        public Builder opacity(float opacity) {
            this.opacity = Preconditions.unit(opacity, "opacity");
            return this;
        }

        public Builder style(StyleResolver style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public Image build() {
            return new Image(this);
        }
    }
}
