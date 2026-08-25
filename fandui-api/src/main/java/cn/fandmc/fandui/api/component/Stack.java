package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.Size;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Lays children on top of one another and supports {@link Positioned} edge constraints. */
public final class Stack extends UiContainer {
    private Alignment alignment;

    private Stack(Builder builder) {
        super(builder.key);
        alignment = builder.alignment;
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

    public static Stack of(UiComponent... children) {
        return builder(children).build();
    }

    public Alignment alignment() {
        return alignment;
    }

    public void setAlignment(Alignment value) {
        requireMutationThread();
        Alignment checked = Objects.requireNonNull(value, "value");
        if (alignment != checked) {
            alignment = checked;
            invalidateLayout();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        List<Item> items = new ArrayList<>(children().size());
        float desiredWidth = 0.0f;
        float desiredHeight = 0.0f;
        for (UiComponent component : children()) {
            Positioned positioned = component instanceof Positioned item ? item : null;
            Placeable placeable = scope.measure(component, constraintsFor(positioned, constraints));
            items.add(new Item(placeable, positioned));
            desiredWidth = Math.max(desiredWidth, extent(
                    positioned == null ? OptionalDouble.empty() : positioned.left(),
                    positioned == null ? OptionalDouble.empty() : positioned.right(),
                    placeable.size().width()));
            desiredHeight = Math.max(desiredHeight, extent(
                    positioned == null ? OptionalDouble.empty() : positioned.top(),
                    positioned == null ? OptionalDouble.empty() : positioned.bottom(),
                    placeable.size().height()));
        }
        Size size = constraints.constrain(new Size(desiredWidth, desiredHeight));
        return scope.layout(size.width(), size.height(), placements -> {
            for (Item item : items) {
                float x = offset(
                        item.positioned == null ? OptionalDouble.empty() : item.positioned.left(),
                        item.positioned == null ? OptionalDouble.empty() : item.positioned.right(),
                        size.width(),
                        item.placeable.size().width(),
                        alignment.horizontalFactor());
                float y = offset(
                        item.positioned == null ? OptionalDouble.empty() : item.positioned.top(),
                        item.positioned == null ? OptionalDouble.empty() : item.positioned.bottom(),
                        size.height(),
                        item.placeable.size().height(),
                        alignment.verticalFactor());
                placements.place(item.placeable, x, y, item.positioned == null ? 0 : item.positioned.zIndex());
            }
        });
    }

    private static Constraints constraintsFor(@Nullable Positioned positioned, Constraints parent) {
        if (positioned == null) {
            return Constraints.loose(parent.maxWidth(), parent.maxHeight());
        }
        float maxWidth = available(parent.maxWidth(), positioned.left(), positioned.right());
        float maxHeight = available(parent.maxHeight(), positioned.top(), positioned.bottom());
        float minWidth = positioned.width().isPresent() ? (float) positioned.width().getAsDouble() : 0.0f;
        float minHeight = positioned.height().isPresent() ? (float) positioned.height().getAsDouble() : 0.0f;
        if (positioned.width().isPresent()) {
            maxWidth = Math.min(maxWidth, minWidth);
        } else if (positioned.left().isPresent() && positioned.right().isPresent() && Float.isFinite(maxWidth)) {
            minWidth = maxWidth;
        }
        if (positioned.height().isPresent()) {
            maxHeight = Math.min(maxHeight, minHeight);
        } else if (positioned.top().isPresent() && positioned.bottom().isPresent() && Float.isFinite(maxHeight)) {
            minHeight = maxHeight;
        }
        minWidth = Math.min(minWidth, maxWidth);
        minHeight = Math.min(minHeight, maxHeight);
        return new Constraints(minWidth, maxWidth, minHeight, maxHeight);
    }

    private static float available(float maximum, OptionalDouble start, OptionalDouble end) {
        if (!Float.isFinite(maximum)) {
            return maximum;
        }
        float value = maximum;
        if (start.isPresent()) {
            value -= (float) start.getAsDouble();
        }
        if (end.isPresent()) {
            value -= (float) end.getAsDouble();
        }
        return Math.max(0.0f, value);
    }

    private static float extent(OptionalDouble start, OptionalDouble end, float childSize) {
        float value = childSize;
        if (start.isPresent()) {
            value += (float) start.getAsDouble();
        }
        if (end.isPresent()) {
            value += (float) end.getAsDouble();
        }
        return value;
    }

    private static float offset(
            OptionalDouble start,
            OptionalDouble end,
            float available,
            float childSize,
            float alignment) {
        if (start.isPresent()) {
            return (float) start.getAsDouble();
        }
        if (end.isPresent()) {
            return Math.max(0.0f, available - (float) end.getAsDouble() - childSize);
        }
        return Math.max(0.0f, available - childSize) * alignment;
    }

    private record Item(Placeable placeable, @Nullable Positioned positioned) {
    }

    public static final class Builder {
        private final List<UiComponent> children = new ArrayList<>();
        private @Nullable UiKey key;
        private Alignment alignment = Alignment.TOP_LEFT;

        private Builder() {
        }

        public Builder key(UiKey value) { key = Objects.requireNonNull(value, "value"); return this; }
        public Builder alignment(Alignment value) { alignment = Objects.requireNonNull(value, "value"); return this; }
        public Builder child(UiComponent value) { children.add(Objects.requireNonNull(value, "value")); return this; }
        public Builder children(List<? extends UiComponent> values) {
            Objects.requireNonNull(values, "values").forEach(this::child);
            return this;
        }
        public Builder children(UiComponent... values) {
            Objects.requireNonNull(values, "values");
            for (UiComponent value : values) {
                child(value);
            }
            return this;
        }
        public Stack build() { return new Stack(this); }
    }
}
