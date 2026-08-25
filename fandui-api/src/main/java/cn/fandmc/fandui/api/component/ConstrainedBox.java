package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.Size;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Single-child component that combines parent constraints with an additional constraint set. */
public final class ConstrainedBox extends UiContainer {
    private Constraints additionalConstraints;
    private Alignment alignment;

    private ConstrainedBox(Builder builder) {
        super(builder.key, 1, 1);
        this.additionalConstraints = builder.constraints;
        this.alignment = builder.alignment;
        add(builder.child);
    }

    public static Builder builder(UiComponent child, Constraints constraints) {
        return new Builder(child, constraints);
    }

    public UiComponent child() {
        return children().get(0);
    }

    public void setChild(UiComponent child) {
        replace(0, child);
    }

    public Constraints additionalConstraints() {
        return additionalConstraints;
    }

    public void setAdditionalConstraints(Constraints constraints) {
        requireMutationThread();
        Constraints checked = Objects.requireNonNull(constraints, "constraints");
        if (!additionalConstraints.equals(checked)) {
            additionalConstraints = checked;
            invalidateLayout();
        }
    }

    public Alignment alignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        requireMutationThread();
        Alignment checked = Objects.requireNonNull(alignment, "alignment");
        if (this.alignment != checked) {
            this.alignment = checked;
            invalidateLayout();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Constraints effective = intersect(constraints, additionalConstraints);
        Placeable child = scope.measure(child(), effective);
        Size size = constraints.constrain(child.size());
        return scope.layout(size.width(), size.height(), placements -> {
            float x = Math.max(0.0f, size.width() - child.size().width()) * alignment.horizontalFactor();
            float y = Math.max(0.0f, size.height() - child.size().height()) * alignment.verticalFactor();
            placements.place(child, x, y);
        });
    }

    private static Constraints intersect(Constraints parent, Constraints own) {
        float maxWidth = Math.min(parent.maxWidth(), own.maxWidth());
        float maxHeight = Math.min(parent.maxHeight(), own.maxHeight());
        float minWidth = Math.min(maxWidth, Math.max(parent.minWidth(), own.minWidth()));
        float minHeight = Math.min(maxHeight, Math.max(parent.minHeight(), own.minHeight()));
        return new Constraints(minWidth, maxWidth, minHeight, maxHeight);
    }

    public static final class Builder {
        private final UiComponent child;
        private final Constraints constraints;
        private @Nullable UiKey key;
        private Alignment alignment = Alignment.TOP_LEFT;

        private Builder(UiComponent child, Constraints constraints) {
            this.child = Objects.requireNonNull(child, "child");
            this.constraints = Objects.requireNonNull(constraints, "constraints");
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder alignment(Alignment alignment) {
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public ConstrainedBox build() {
            return new ConstrainedBox(this);
        }
    }
}
