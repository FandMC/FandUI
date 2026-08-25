package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Axis;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.CrossAxisAlignment;
import cn.fandmc.fandui.api.layout.MainAxisAlignment;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.internal.validation.Preconditions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Vertical linear layout with configurable gap, alignment, and flex children. */
public final class Column extends UiContainer {
    private float gap;
    private MainAxisAlignment mainAxisAlignment;
    private CrossAxisAlignment crossAxisAlignment;

    private Column(Builder builder) {
        super(builder.key);
        this.gap = builder.gap;
        this.mainAxisAlignment = builder.mainAxisAlignment;
        this.crossAxisAlignment = builder.crossAxisAlignment;
        if (builder.style != null) {
            setStyle(builder.style);
        }
        for (UiComponent child : builder.children) {
            add(child);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(UiComponent... children) {
        return new Builder().children(children);
    }

    public static Column of(UiComponent... children) {
        return builder(children).build();
    }

    public float gap() {
        return gap;
    }

    public void setGap(float gap) {
        requireMutationThread();
        float checked = Preconditions.nonNegative(gap, "gap");
        if (Float.compare(this.gap, checked) != 0) {
            this.gap = checked;
            invalidateLayout();
        }
    }

    public MainAxisAlignment mainAxisAlignment() {
        return mainAxisAlignment;
    }

    public void setMainAxisAlignment(MainAxisAlignment alignment) {
        requireMutationThread();
        MainAxisAlignment checked = Objects.requireNonNull(alignment, "alignment");
        if (mainAxisAlignment != checked) {
            mainAxisAlignment = checked;
            invalidateLayout();
        }
    }

    public CrossAxisAlignment crossAxisAlignment() {
        return crossAxisAlignment;
    }

    public void setCrossAxisAlignment(CrossAxisAlignment alignment) {
        requireMutationThread();
        CrossAxisAlignment checked = Objects.requireNonNull(alignment, "alignment");
        if (crossAxisAlignment != checked) {
            crossAxisAlignment = checked;
            invalidateLayout();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        return LinearLayout.measure(
                this,
                Axis.VERTICAL,
                gap,
                mainAxisAlignment,
                crossAxisAlignment,
                scope,
                constraints);
    }

    public static final class Builder {
        private final List<UiComponent> children = new ArrayList<>();
        private @Nullable UiKey key;
        private float gap;
        private MainAxisAlignment mainAxisAlignment = MainAxisAlignment.START;
        private CrossAxisAlignment crossAxisAlignment = CrossAxisAlignment.START;
        private @Nullable StyleResolver style;

        private Builder() {
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder child(UiComponent child) {
            children.add(Objects.requireNonNull(child, "child"));
            return this;
        }

        public Builder children(List<? extends UiComponent> children) {
            Objects.requireNonNull(children, "children");
            for (UiComponent child : children) {
                child(child);
            }
            return this;
        }

        public Builder children(UiComponent... children) {
            Objects.requireNonNull(children, "children");
            for (UiComponent child : children) {
                child(child);
            }
            return this;
        }

        public Builder gap(float gap) {
            this.gap = Preconditions.nonNegative(gap, "gap");
            return this;
        }

        public Builder mainAxisAlignment(MainAxisAlignment alignment) {
            this.mainAxisAlignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public Builder crossAxisAlignment(CrossAxisAlignment alignment) {
            this.crossAxisAlignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public Builder style(StyleResolver style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public Column build() {
            return new Column(this);
        }
    }
}
