package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.internal.validation.Preconditions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.OptionalDouble;

/** Supplies edge offsets, an optional size, and a z-index to a parent {@link Stack}. */
public final class Positioned extends UiContainer {
    private @Nullable Float left;
    private @Nullable Float top;
    private @Nullable Float right;
    private @Nullable Float bottom;
    private @Nullable Float width;
    private @Nullable Float height;
    private int zIndex;
    private Alignment alignment;

    private Positioned(Builder builder) {
        super(builder.key);
        left = builder.left;
        top = builder.top;
        right = builder.right;
        bottom = builder.bottom;
        width = builder.width;
        height = builder.height;
        zIndex = builder.zIndex;
        alignment = builder.alignment;
        add(builder.child);
    }

    public static Builder builder(UiComponent child) {
        return new Builder(child);
    }

    public UiComponent child() {
        return children().get(0);
    }

    public OptionalDouble left() { return optional(left); }
    public OptionalDouble top() { return optional(top); }
    public OptionalDouble right() { return optional(right); }
    public OptionalDouble bottom() { return optional(bottom); }
    public OptionalDouble width() { return optional(width); }
    public OptionalDouble height() { return optional(height); }
    public int zIndex() { return zIndex; }
    public Alignment alignment() { return alignment; }

    public void setLeft(@Nullable Float value) { left = changed(left, value, "left"); }
    public void setTop(@Nullable Float value) { top = changed(top, value, "top"); }
    public void setRight(@Nullable Float value) { right = changed(right, value, "right"); }
    public void setBottom(@Nullable Float value) { bottom = changed(bottom, value, "bottom"); }
    public void setWidth(@Nullable Float value) { width = changed(width, value, "width"); }
    public void setHeight(@Nullable Float value) { height = changed(height, value, "height"); }

    public void setZIndex(int value) {
        if (zIndex != value) {
            zIndex = value;
            invalidateLayout();
        }
    }

    public void setAlignment(Alignment value) {
        Alignment checked = Objects.requireNonNull(value, "value");
        if (alignment != checked) {
            alignment = checked;
            invalidateLayout();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        return SingleChildSupport.measure(child(), alignment, scope, constraints);
    }

    @Override
    protected void validateChildAddition(UiComponent child, int index) {
        if (!children().isEmpty()) {
            throw new IllegalStateException("Positioned accepts exactly one child");
        }
    }

    private @Nullable Float changed(@Nullable Float previous, @Nullable Float value, String name) {
        Float checked = value == null ? null : Preconditions.nonNegative(value, name);
        if (!Objects.equals(previous, checked)) {
            invalidateLayout();
        }
        return checked;
    }

    private static OptionalDouble optional(@Nullable Float value) {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public static final class Builder {
        private final UiComponent child;
        private @Nullable UiKey key;
        private @Nullable Float left;
        private @Nullable Float top;
        private @Nullable Float right;
        private @Nullable Float bottom;
        private @Nullable Float width;
        private @Nullable Float height;
        private int zIndex;
        private Alignment alignment = Alignment.TOP_LEFT;

        private Builder(UiComponent child) {
            this.child = Objects.requireNonNull(child, "child");
        }

        public Builder key(UiKey value) { key = Objects.requireNonNull(value, "value"); return this; }
        public Builder left(float value) { left = checked(value, "left"); return this; }
        public Builder top(float value) { top = checked(value, "top"); return this; }
        public Builder right(float value) { right = checked(value, "right"); return this; }
        public Builder bottom(float value) { bottom = checked(value, "bottom"); return this; }
        public Builder width(float value) { width = checked(value, "width"); return this; }
        public Builder height(float value) { height = checked(value, "height"); return this; }
        public Builder zIndex(int value) { zIndex = value; return this; }
        public Builder alignment(Alignment value) { alignment = Objects.requireNonNull(value, "value"); return this; }

        public Positioned build() { return new Positioned(this); }

        private static float checked(float value, String name) {
            return Preconditions.nonNegative(value, name);
        }
    }
}
