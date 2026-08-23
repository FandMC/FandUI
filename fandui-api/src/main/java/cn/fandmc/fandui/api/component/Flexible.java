package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.internal.validation.Preconditions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Supplies grow, shrink, and basis data to a parent {@link Row} or {@link Column}. */
public final class Flexible extends UiContainer {
    private float grow;
    private float shrink;
    private float basis;
    private FlexFit fit;
    private Alignment alignment;

    private Flexible(Builder builder) {
        super(builder.key);
        this.grow = builder.grow;
        this.shrink = builder.shrink;
        this.basis = builder.basis;
        this.fit = builder.fit;
        this.alignment = builder.alignment;
        add(builder.child);
    }

    public static Builder builder(UiComponent child) {
        return new Builder(child);
    }

    public static Flexible expanded(UiComponent child) {
        return builder(child).grow(1.0f).fit(FlexFit.TIGHT).build();
    }

    public UiComponent child() {
        return children().get(0);
    }

    public float grow() {
        return grow;
    }

    public void setGrow(float value) {
        float checked = Preconditions.nonNegative(value, "grow");
        if (Float.compare(grow, checked) != 0) {
            grow = checked;
            invalidateLayout();
        }
    }

    public float shrink() {
        return shrink;
    }

    public void setShrink(float value) {
        float checked = Preconditions.nonNegative(value, "shrink");
        if (Float.compare(shrink, checked) != 0) {
            shrink = checked;
            invalidateLayout();
        }
    }

    public float basis() {
        return basis;
    }

    public void setBasis(float value) {
        float checked = Preconditions.nonNegative(value, "basis");
        if (Float.compare(basis, checked) != 0) {
            basis = checked;
            invalidateLayout();
        }
    }

    public FlexFit fit() {
        return fit;
    }

    public void setFit(FlexFit value) {
        FlexFit checked = Objects.requireNonNull(value, "value");
        if (fit != checked) {
            fit = checked;
            invalidateLayout();
        }
    }

    public Alignment alignment() {
        return alignment;
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
            throw new IllegalStateException("Flexible accepts exactly one child");
        }
    }

    public static final class Builder {
        private final UiComponent child;
        private @Nullable UiKey key;
        private float grow;
        private float shrink = 1.0f;
        private float basis;
        private FlexFit fit = FlexFit.LOOSE;
        private Alignment alignment = Alignment.TOP_LEFT;

        private Builder(UiComponent child) {
            this.child = Objects.requireNonNull(child, "child");
        }

        public Builder key(UiKey value) {
            key = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder grow(float value) {
            grow = Preconditions.nonNegative(value, "grow");
            return this;
        }

        public Builder shrink(float value) {
            shrink = Preconditions.nonNegative(value, "shrink");
            return this;
        }

        public Builder basis(float value) {
            basis = Preconditions.nonNegative(value, "basis");
            return this;
        }

        public Builder fit(FlexFit value) {
            fit = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder alignment(Alignment value) {
            alignment = Objects.requireNonNull(value, "value");
            return this;
        }

        public Flexible build() {
            return new Flexible(this);
        }
    }
}
