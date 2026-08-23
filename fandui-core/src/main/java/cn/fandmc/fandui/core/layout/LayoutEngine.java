package cn.fandmc.fandui.core.layout;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.UiContainer;
import cn.fandmc.fandui.api.component.DirectionScope;
import cn.fandmc.fandui.api.component.ThemeScope;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.PlacementScope;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.layout.TextBaseline;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.Transform2D;
import cn.fandmc.fandui.api.style.VisualState;
import cn.fandmc.fandui.api.component.ContentClipProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Function;

public final class LayoutEngine {
    private final Map<UiComponent, Boolean> measuredComponents = new IdentityHashMap<>();

    public LayoutSnapshot layout(
            UiComponent root,
            Constraints constraints,
            LayoutDirection direction) {
        return layout(root, constraints, direction, Theme.defaults());
    }

    public LayoutSnapshot layout(
            UiComponent root,
            Constraints constraints,
            LayoutDirection direction,
            Theme theme) {
        return layout(
                root,
                constraints,
                direction,
                theme,
                component -> VisualState.of(false, false, false, !component.enabled()));
    }

    public LayoutSnapshot layout(
            UiComponent root,
            Constraints constraints,
            LayoutDirection direction,
            Theme theme,
            Function<UiComponent, VisualState> visualStates) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(visualStates, "visualStates");
        measuredComponents.clear();
        MeasuredNode measuredRoot = measure(root, constraints, direction, theme, visualStates);
        LayoutNode rootNode = freeze(measuredRoot, 0.0f, 0.0f, Transform2D.identity(), 0);
        return new LayoutSnapshot(rootNode);
    }

    private MeasuredNode measure(
            UiComponent component,
            Constraints constraints,
            LayoutDirection direction,
            Theme theme,
            Function<UiComponent, VisualState> visualStates) {
        if (measuredComponents.put(component, Boolean.TRUE) != null) {
            throw new LayoutException("Component was measured more than once in the same pass: " + component);
        }
        Theme effectiveTheme = component instanceof ThemeScope scope
                ? (scope.inheritsParent() ? theme.mergedWith(scope.themeOverride()) : scope.themeOverride())
                : theme;
        LayoutDirection effectiveDirection = component instanceof DirectionScope scope
                ? scope.direction()
                : direction;
        Style resolvedStyle;
        try {
            resolvedStyle = Objects.requireNonNull(
                    component.style().resolve(
                            effectiveTheme,
                            Objects.requireNonNull(
                                    visualStates.apply(component),
                                    "Resolved component visual state")),
                    "Resolved component style");
        } catch (RuntimeException exception) {
            throw new LayoutException("Style resolver failed for " + component.getClass().getName(), exception);
        }
        Constraints componentConstraints = subtractInsets(constraints, resolvedStyle.margin());
        Scope scope = new Scope(
                component,
                componentConstraints,
                effectiveDirection,
                effectiveTheme,
                resolvedStyle,
                visualStates);
        MeasureResult returned;
        try {
            returned = Objects.requireNonNull(
                    component.measure(scope, componentConstraints),
                    "Component measure result");
        } catch (LayoutException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LayoutException("Measure callback failed for " + component.getClass().getName(), exception);
        }
        if (scope.result == null || returned != scope.result) {
            throw new LayoutException("MeasureResult must be created by the current MeasureScope");
        }
        Size outerSize = addInsets(scope.result.size, resolvedStyle.margin());
        return new MeasuredNode(
                component,
                scope.result.size,
                outerSize,
                scope.result.placements,
                scope.result.baselines,
                resolvedStyle,
                effectiveTheme,
                effectiveDirection);
    }

    private LayoutNode freeze(
            MeasuredNode measured,
            float localX,
            float localY,
            Transform2D parentToScene,
            int zIndex) {
        Insets margin = measured.style.margin();
        float nodeLocalX = localX + margin.left();
        float nodeLocalY = localY + margin.top();
        Transform2D localToScene;
        try {
            localToScene = parentToScene
                    .concatenate(Transform2D.translation(nodeLocalX, nodeLocalY))
                    .concatenate(measured.style.transform());
        } catch (IllegalArgumentException exception) {
            throw new LayoutException("Component transform overflowed for "
                    + measured.component.getClass().getName(), exception);
        }
        List<PlacedChild> placements = new ArrayList<>(measured.placements);
        placements.sort(Comparator.comparingInt(PlacedChild::zIndex)
                .thenComparingInt(placed -> placed.placeable.childOrder));
        List<LayoutNode> children = new ArrayList<>(placements.size());
        for (PlacedChild placed : placements) {
            children.add(freeze(
                    placed.placeable.measured,
                    placed.x,
                    placed.y,
                    localToScene,
                    placed.zIndex));
        }
        Rect sceneBounds = transformedBounds(localToScene, measured.size);
        return new LayoutNode(
                measured.component,
                new Point(nodeLocalX, nodeLocalY),
                measured.size,
                sceneBounds,
                zIndex,
                effectiveClip(measured.component, measured.style),
                measured.baselines,
                measured.style,
                measured.theme,
                measured.direction,
                localToScene,
                children);
    }

    private static Rect transformedBounds(Transform2D transform, Size size) {
        float x0 = transform.tx();
        float y0 = transform.ty();
        float x1 = transform.m00() * size.width() + transform.tx();
        float y1 = transform.m01() * size.width() + transform.ty();
        float x2 = transform.m10() * size.height() + transform.tx();
        float y2 = transform.m11() * size.height() + transform.ty();
        float x3 = transform.m00() * size.width() + transform.m10() * size.height() + transform.tx();
        float y3 = transform.m01() * size.width() + transform.m11() * size.height() + transform.ty();
        float left = Math.min(Math.min(x0, x1), Math.min(x2, x3));
        float top = Math.min(Math.min(y0, y1), Math.min(y2, y3));
        float right = Math.max(Math.max(x0, x1), Math.max(x2, x3));
        float bottom = Math.max(Math.max(y0, y1), Math.max(y2, y3));
        if (!Float.isFinite(left) || !Float.isFinite(top)
                || !Float.isFinite(right) || !Float.isFinite(bottom)) {
            throw new LayoutException("Transformed component bounds overflowed");
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    private static Constraints subtractInsets(Constraints constraints, Insets insets) {
        float horizontal = insets.left() + insets.right();
        float vertical = insets.top() + insets.bottom();
        return new Constraints(
                Math.max(0.0f, constraints.minWidth() - horizontal),
                subtractMaximum(constraints.maxWidth(), horizontal),
                Math.max(0.0f, constraints.minHeight() - vertical),
                subtractMaximum(constraints.maxHeight(), vertical));
    }

    private static Size addInsets(Size size, Insets insets) {
        return new Size(
                size.width() + insets.left() + insets.right(),
                size.height() + insets.top() + insets.bottom());
    }

    private static float subtractMaximum(float maximum, float amount) {
        return Float.isInfinite(maximum) ? maximum : Math.max(0.0f, maximum - amount);
    }

    private static ClipMode effectiveClip(UiComponent component, Style style) {
        if (style.clip() != ClipMode.NONE) {
            return style.clip();
        }
        return component instanceof ContentClipProvider provider
                ? provider.contentClip()
                : ClipMode.NONE;
    }

    private final class Scope implements MeasureScope {
        private final UiComponent component;
        private final Constraints constraints;
        private final LayoutDirection direction;
        private final Theme theme;
        private final Style style;
        private final Function<UiComponent, VisualState> visualStates;
        private final List<UiComponent> directChildren;
        private final Map<UiComponent, Integer> childOrder = new IdentityHashMap<>();
        private final Map<UiComponent, PlaceableImpl> measuredChildren = new IdentityHashMap<>();
        private Phase phase = Phase.MEASURING;
        private Result result;

        private Scope(
                UiComponent component,
                Constraints constraints,
                LayoutDirection direction,
                Theme theme,
                Style style,
                Function<UiComponent, VisualState> visualStates) {
            this.component = component;
            this.constraints = constraints;
            this.direction = direction;
            this.theme = theme;
            this.style = style;
            this.visualStates = visualStates;
            this.directChildren = component instanceof UiContainer container ? container.children() : List.of();
            for (int index = 0; index < directChildren.size(); index++) {
                childOrder.put(directChildren.get(index), index);
            }
        }

        @Override
        public Placeable measure(UiComponent child, Constraints childConstraints) {
            requirePhase(Phase.MEASURING, "Children can only be measured before layout placement");
            Objects.requireNonNull(child, "child");
            Objects.requireNonNull(childConstraints, "constraints");
            Integer order = childOrder.get(child);
            if (order == null) {
                throw new LayoutException("MeasureScope can only measure direct children");
            }
            if (measuredChildren.containsKey(child)) {
                throw new LayoutException("A child can only be measured once per pass");
            }
            MeasuredNode measured = LayoutEngine.this.measure(
                    child, childConstraints, direction, theme, visualStates);
            PlaceableImpl placeable = new PlaceableImpl(this, measured, order);
            measuredChildren.put(child, placeable);
            return placeable;
        }

        @Override
        public MeasureResult layout(float width, float height, Consumer<PlacementScope> placements) {
            return layout(width, height, Map.of(), placements);
        }

        @Override
        public MeasureResult layout(
                float width,
                float height,
                Map<TextBaseline, Float> baselines,
                Consumer<PlacementScope> placements) {
            requirePhase(Phase.MEASURING, "layout may only be called once per measure pass");
            Objects.requireNonNull(baselines, "baselines");
            Objects.requireNonNull(placements, "placements");
            Size size = new Size(width, height);
            if (size.width() < constraints.minWidth() || size.width() > constraints.maxWidth()
                    || size.height() < constraints.minHeight() || size.height() > constraints.maxHeight()) {
                throw new LayoutException("Measured size " + size + " violates " + constraints);
            }
            Map<TextBaseline, Float> checkedBaselines = validateBaselines(baselines, size.height());
            phase = Phase.PLACING;
            Placement placement = new Placement(this);
            try {
                placements.accept(placement);
            } finally {
                placement.active = false;
                phase = Phase.COMPLETE;
            }
            result = new Result(this, size, placement.placedChildren, checkedBaselines);
            return result;
        }

        private static Map<TextBaseline, Float> validateBaselines(
                Map<TextBaseline, Float> baselines,
                float height) {
            if (baselines.isEmpty()) {
                return Map.of();
            }
            java.util.EnumMap<TextBaseline, Float> checked = new java.util.EnumMap<>(TextBaseline.class);
            baselines.forEach((baseline, value) -> {
                Objects.requireNonNull(baseline, "baseline key");
                Objects.requireNonNull(value, "baseline value");
                if (!Float.isFinite(value) || value < 0.0f || value > height) {
                    throw new LayoutException("Baseline " + baseline + " is outside measured height " + height);
                }
                checked.put(baseline, value);
            });
            return Map.copyOf(checked);
        }

        @Override
        public LayoutDirection direction() {
            return direction;
        }

        @Override
        public Style style() {
            return style;
        }

        @Override
        public Theme theme() {
            return theme;
        }

        private void requirePhase(Phase expected, String message) {
            if (phase != expected) {
                throw new LayoutException(message);
            }
        }
    }

    private static final class Placement implements PlacementScope {
        private final Scope owner;
        private final List<PlacedChild> placedChildren = new ArrayList<>();
        private final Map<PlaceableImpl, Boolean> placed = new IdentityHashMap<>();
        private boolean active = true;

        private Placement(Scope owner) {
            this.owner = owner;
        }

        @Override
        public void place(Placeable child, float x, float y) {
            place(child, x, y, 0);
        }

        @Override
        public void place(Placeable child, float x, float y, int zIndex) {
            if (!active) {
                throw new LayoutException("PlacementScope is no longer active");
            }
            if (!(child instanceof PlaceableImpl placeable) || placeable.owner != owner) {
                throw new LayoutException("Placeable belongs to a different measure scope");
            }
            if (placed.put(placeable, Boolean.TRUE) != null) {
                throw new LayoutException("A measured child can only be placed once");
            }
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                throw new LayoutException("Placement coordinates must be finite");
            }
            placedChildren.add(new PlacedChild(placeable, x, y, zIndex));
        }
    }

    private static final class PlaceableImpl implements Placeable {
        private final Scope owner;
        private final MeasuredNode measured;
        private final int childOrder;

        private PlaceableImpl(Scope owner, MeasuredNode measured, int childOrder) {
            this.owner = owner;
            this.measured = measured;
            this.childOrder = childOrder;
        }

        @Override
        public UiComponent component() {
            return measured.component;
        }

        @Override
        public Size size() {
            return measured.outerSize;
        }

        @Override
        public OptionalDouble baseline(TextBaseline baseline) {
            Objects.requireNonNull(baseline, "baseline");
            Float value = measured.baselines.get(baseline);
            return value == null
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(value + measured.style.margin().top());
        }
    }

    private static final class Result implements MeasureResult {
        private final Scope owner;
        private final Size size;
        private final List<PlacedChild> placements;
        private final Map<TextBaseline, Float> baselines;

        private Result(
                Scope owner,
                Size size,
                List<PlacedChild> placements,
                Map<TextBaseline, Float> baselines) {
            this.owner = owner;
            this.size = size;
            this.placements = List.copyOf(placements);
            this.baselines = Map.copyOf(baselines);
        }

        @Override
        public Size size() {
            return size;
        }
    }

    private record MeasuredNode(
            UiComponent component,
            Size size,
            Size outerSize,
            List<PlacedChild> placements,
            Map<TextBaseline, Float> baselines,
            Style style,
            Theme theme,
            LayoutDirection direction) {
        private MeasuredNode {
            placements = List.copyOf(placements);
            baselines = Map.copyOf(baselines);
        }
    }

    private record PlacedChild(PlaceableImpl placeable, float x, float y, int zIndex) {
    }

    private enum Phase {
        MEASURING,
        PLACING,
        COMPLETE
    }
}
