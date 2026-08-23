package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.style.StyleResolver;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Single-child box that applies style padding, background, border, radii, and alignment.
 * Core applies margin, transform, opacity, clip, and backdrop blur to every component.
 */
public final class Box extends UiContainer {
    private Alignment alignment;

    private Box(Builder builder) {
        super(builder.key);
        this.alignment = builder.alignment;
        if (builder.style != null) {
            setStyle(builder.style);
        }
        add(builder.child);
    }

    public static Builder builder(UiComponent child) {
        return new Builder(child);
    }

    public static Box of(UiComponent child) {
        return builder(child).build();
    }

    public UiComponent child() {
        if (children().isEmpty()) {
            throw new IllegalStateException("Box has no child");
        }
        return children().get(0);
    }

    public void setChild(UiComponent child) {
        Objects.requireNonNull(child, "child");
        if (!children().isEmpty()) {
            remove(0);
        }
        add(child);
    }

    public Alignment alignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        Alignment checked = Objects.requireNonNull(alignment, "alignment");
        if (this.alignment != checked) {
            this.alignment = checked;
            invalidateLayout();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        return SingleChildSupport.measure(child(), alignment, scope, constraints);
    }

    @Override
    public void paint(PaintScope scope) {
        SingleChildSupport.paintBackground(scope);
    }

    @Override
    protected void validateChildAddition(UiComponent child, int index) {
        if (!children().isEmpty()) {
            throw new IllegalStateException("Box accepts exactly one child");
        }
    }

    public static final class Builder {
        private final UiComponent child;
        private @Nullable UiKey key;
        private Alignment alignment = Alignment.TOP_LEFT;
        private @Nullable StyleResolver style;

        private Builder(UiComponent child) {
            this.child = Objects.requireNonNull(child, "child");
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder alignment(Alignment alignment) {
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public Builder style(StyleResolver style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public Box build() {
            return new Box(this);
        }
    }
}
