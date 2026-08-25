package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.canvas.CanvasState;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.icon.IconDefinition;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Renders an immutable vector icon definition without texture allocation. */
public final class Icon extends UiComponent {
    private static final Paint DEFAULT_TINT = new SolidPaint(Color.rgb(0xffffff));

    private IconDefinition definition;
    private Size preferredSize;
    private Alignment alignment;
    private @Nullable Paint tint;

    private Icon(Builder builder) {
        super(builder.key);
        definition = builder.definition;
        preferredSize = builder.preferredSize;
        alignment = builder.alignment;
        tint = builder.tint;
        if (builder.style != null) {
            setStyle(builder.style);
        }
    }

    public static Builder builder(IconDefinition definition) {
        return new Builder(definition);
    }

    public static Icon of(IconDefinition definition) {
        return builder(definition).build();
    }

    public IconDefinition definition() {
        return definition;
    }

    public void setDefinition(IconDefinition definition) {
        ComponentBindings.assertMutationAllowed(this);
        IconDefinition checked = Objects.requireNonNull(definition, "definition");
        if (this.definition != checked) {
            this.definition = checked;
            invalidateLayout();
        }
    }

    public Size preferredSize() {
        return preferredSize;
    }

    public void setPreferredSize(Size preferredSize) {
        ComponentBindings.assertMutationAllowed(this);
        Size checked = Objects.requireNonNull(preferredSize, "preferredSize");
        if (!checked.equals(this.preferredSize)) {
            this.preferredSize = checked;
            invalidateLayout();
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

    public @Nullable Paint tint() {
        return tint;
    }

    public void setTint(@Nullable Paint tint) {
        ComponentBindings.assertMutationAllowed(this);
        if (!Objects.equals(this.tint, tint)) {
            this.tint = tint;
            invalidatePaint();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Size desired = preferredSize == null ? definition.viewBox() : preferredSize;
        Size size = constraints.constrain(desired);
        return scope.layout(size.width(), size.height(), placements -> { });
    }

    @Override
    public void paint(PaintScope scope) {
        Size viewBox = definition.viewBox();
        Rect bounds = scope.bounds();
        if (bounds.width() == 0.0f || bounds.height() == 0.0f) {
            return;
        }
        float scale = Math.min(bounds.width() / viewBox.width(), bounds.height() / viewBox.height());
        if (!Float.isFinite(scale) || scale <= 0.0f) {
            return;
        }
        float drawnWidth = viewBox.width() * scale;
        float drawnHeight = viewBox.height() * scale;
        CanvasState state = scope.canvas().save();
        try {
            scope.canvas().intersectScissor(bounds);
            scope.canvas().translate(
                    (bounds.width() - drawnWidth) * alignment.horizontalFactor(),
                    (bounds.height() - drawnHeight) * alignment.verticalFactor());
            scope.canvas().scale(scale, scale);
            for (IconDefinition.Layer layer : definition.layers()) {
                Paint fill = layer.fill();
                Paint stroke = layer.stroke();
                Paint color = tint == null ? DEFAULT_TINT : tint;
                if (fill != null) {
                    scope.canvas().fill(layer.path(), tint == null ? fill : color);
                }
                if (stroke != null) {
                    scope.canvas().stroke(layer.path(), tint == null ? stroke : color, layer.strokeStyle());
                }
            }
        } finally {
            state.close();
        }
    }

    /** Fluent icon component builder. */
    public static final class Builder {
        private final IconDefinition definition;
        private @Nullable UiKey key;
        private Size preferredSize;
        private Alignment alignment = Alignment.CENTER;
        private @Nullable Paint tint;
        private @Nullable StyleResolver style;

        private Builder(IconDefinition definition) {
            this.definition = Objects.requireNonNull(definition, "definition");
            this.preferredSize = definition.viewBox();
        }

        public Builder key(UiKey value) {
            key = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder size(float width, float height) {
            return size(new Size(width, height));
        }

        public Builder size(Size value) {
            preferredSize = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder alignment(Alignment value) {
            alignment = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder tint(@Nullable Paint value) {
            tint = value;
            return this;
        }

        public Builder style(StyleResolver value) {
            style = Objects.requireNonNull(value, "value");
            return this;
        }

        public Icon build() {
            return new Icon(this);
        }
    }
}
