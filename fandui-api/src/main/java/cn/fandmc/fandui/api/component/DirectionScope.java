package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Overrides layout direction for one component subtree. */
public final class DirectionScope extends UiContainer {
    private LayoutDirection direction;

    private DirectionScope(Builder builder) {
        super(builder.key, 1, 1);
        direction = builder.direction;
        add(builder.child);
    }

    public static Builder builder(LayoutDirection direction, UiComponent child) {
        return new Builder(direction, child);
    }

    public static DirectionScope of(LayoutDirection direction, UiComponent child) {
        return builder(direction, child).build();
    }

    public UiComponent child() { return children().get(0); }
    public void setChild(UiComponent child) { replace(0, child); }
    public LayoutDirection direction() { return direction; }

    public void setDirection(LayoutDirection value) {
        requireMutationThread();
        LayoutDirection checked = Objects.requireNonNull(value, "value");
        if (direction != checked) {
            direction = checked;
            invalidateLayout();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        return SingleChildSupport.measure(child(), Alignment.TOP_LEFT, scope, constraints);
    }

    public static final class Builder {
        private final UiComponent child;
        private @Nullable UiKey key;
        private final LayoutDirection direction;

        private Builder(LayoutDirection direction, UiComponent child) {
            this.direction = Objects.requireNonNull(direction, "direction");
            this.child = Objects.requireNonNull(child, "child");
        }

        public Builder key(UiKey value) { key = Objects.requireNonNull(value, "value"); return this; }
        public DirectionScope build() { return new DirectionScope(this); }
    }
}
