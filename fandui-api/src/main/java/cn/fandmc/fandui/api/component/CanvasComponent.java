package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.StyleResolver;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Lightweight leaf component backed by caller-provided measure and paint callbacks. */
public final class CanvasComponent extends UiComponent {
    private CanvasMeasure measure;
    private CanvasPainter painter;

    private CanvasComponent(Builder builder) {
        super(builder.key);
        this.measure = builder.measure;
        this.painter = builder.painter;
        if (builder.style != null) {
            setStyle(builder.style);
        }
    }

    public static Builder builder(CanvasMeasure measure, CanvasPainter painter) {
        return new Builder(measure, painter);
    }

    public void setMeasure(CanvasMeasure measure) {
        CanvasMeasure checked = Objects.requireNonNull(measure, "measure");
        if (this.measure != checked) {
            this.measure = checked;
            invalidateLayout();
        }
    }

    public void setPainter(CanvasPainter painter) {
        CanvasPainter checked = Objects.requireNonNull(painter, "painter");
        if (this.painter != checked) {
            this.painter = checked;
            invalidatePaint();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Size requested = Objects.requireNonNull(
                measure.measure(constraints, scope.style(), scope.theme()),
                "Canvas measure result");
        Size size = constraints.constrain(requested);
        return scope.layout(size.width(), size.height(), placements -> { });
    }

    @Override
    public void paint(PaintScope scope) {
        painter.paint(scope);
    }

    public static final class Builder {
        private final CanvasMeasure measure;
        private final CanvasPainter painter;
        private @Nullable UiKey key;
        private @Nullable StyleResolver style;

        private Builder(CanvasMeasure measure, CanvasPainter painter) {
            this.measure = Objects.requireNonNull(measure, "measure");
            this.painter = Objects.requireNonNull(painter, "painter");
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder style(StyleResolver style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public CanvasComponent build() {
            return new CanvasComponent(this);
        }
    }
}
