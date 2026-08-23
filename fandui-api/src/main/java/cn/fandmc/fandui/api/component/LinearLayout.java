package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.layout.Axis;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.CrossAxisAlignment;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.MainAxisAlignment;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.TextBaseline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LinearLayout {
    private LinearLayout() {
    }

    static MeasureResult measure(
            UiContainer container,
            Axis axis,
            float gap,
            MainAxisAlignment mainAlignment,
            CrossAxisAlignment crossAlignment,
            MeasureScope scope,
            Constraints constraints) {
        List<UiComponent> children = container.children();
        float totalGap = children.size() > 1 ? gap * (children.size() - 1) : 0.0f;
        float maximumMain = mainMaximum(axis, constraints);
        float maximumCross = crossMaximum(axis, constraints);
        List<Item> items = new ArrayList<>(Collections.nCopies(children.size(), null));
        List<FlexSpec> flexSpecs = new ArrayList<>(Collections.nCopies(children.size(), null));
        float measuredMain = 0.0f;
        float measuredCross = 0.0f;

        for (int index = 0; index < children.size(); index++) {
            UiComponent child = children.get(index);
            FlexSpec flex = flexSpec(child, axis);
            flexSpecs.set(index, flex);
            if (flex != null && Float.isFinite(maximumMain)) {
                continue;
            }
            Item item = measureItem(
                    child,
                    axis,
                    maximumMain,
                    maximumCross,
                    crossMinimum(axis, constraints),
                    crossAlignment,
                    scope);
            items.set(index, item);
            measuredMain = addFinite(measuredMain, item.mainSize, "linear layout main size");
            measuredCross = Math.max(measuredCross, item.crossSize);
        }

        if (Float.isFinite(maximumMain)) {
            float available = Math.max(0.0f, maximumMain - totalGap - measuredMain);
            float[] allocations = flexAllocations(flexSpecs, available);
            for (int index = 0; index < children.size(); index++) {
                FlexSpec flex = flexSpecs.get(index);
                if (flex == null) {
                    continue;
                }
                float allocation = allocations[index];
                float minimum = flex.fit == FlexFit.TIGHT ? allocation : 0.0f;
                Item item = measureItem(
                        children.get(index),
                        axis,
                        minimum,
                        allocation,
                        maximumCross,
                        crossMinimum(axis, constraints),
                        crossAlignment,
                        scope);
                items.set(index, item);
                measuredMain = addFinite(measuredMain, item.mainSize, "linear layout main size");
                measuredCross = Math.max(measuredCross, item.crossSize);
            }
        }

        for (Item item : items) {
            if (item == null) {
                throw new IllegalStateException("Linear layout left a child unmeasured");
            }
        }
        float baseline = baseline(items, axis, crossAlignment);
        if (baseline >= 0.0f) {
            float descent = 0.0f;
            for (Item item : items) {
                var childBaseline = item.placeable.baseline(TextBaseline.ALPHABETIC);
                if (childBaseline.isPresent()) {
                    descent = Math.max(descent, item.crossSize - (float) childBaseline.getAsDouble());
                }
            }
            measuredCross = Math.max(measuredCross, baseline + descent);
        }
        float contentMain = addFinite(measuredMain, totalGap, "linear layout content size");
        float desiredWidth = axis == Axis.HORIZONTAL ? contentMain : measuredCross;
        float desiredHeight = axis == Axis.HORIZONTAL ? measuredCross : contentMain;
        var size = constraints.constrain(new cn.fandmc.fandui.api.layout.Size(desiredWidth, desiredHeight));
        float finalMain = axis == Axis.HORIZONTAL ? size.width() : size.height();
        float finalCross = axis == Axis.HORIZONTAL ? size.height() : size.width();
        Distribution distribution = distribute(mainAlignment, items.size(), gap, Math.max(0.0f, finalMain - contentMain));
        float finalBaseline = baseline;
        return scope.layout(size.width(), size.height(), placements -> {
            float cursor = distribution.offset;
            for (Item item : items) {
                float crossOffset = crossOffset(
                        axis,
                        crossAlignment,
                        scope.direction(),
                        finalCross,
                        item,
                        finalBaseline);
                float mainOffset = axis == Axis.HORIZONTAL && scope.direction() == LayoutDirection.RIGHT_TO_LEFT
                        ? finalMain - cursor - item.mainSize
                        : cursor;
                float x = axis == Axis.HORIZONTAL ? mainOffset : crossOffset;
                float y = axis == Axis.HORIZONTAL ? crossOffset : mainOffset;
                placements.place(item.placeable, x, y);
                cursor += item.mainSize + distribution.gap;
            }
        });
    }

    private static Item measureItem(
            UiComponent child,
            Axis axis,
            float maximumMain,
            float maximumCross,
            float minimumCross,
            CrossAxisAlignment crossAlignment,
            MeasureScope scope) {
        return measureItem(
                child, axis, 0.0f, maximumMain, maximumCross, minimumCross, crossAlignment, scope);
    }

    private static Item measureItem(
            UiComponent child,
            Axis axis,
            float minimumMain,
            float maximumMain,
            float maximumCross,
            float minimumCross,
            CrossAxisAlignment crossAlignment,
            MeasureScope scope) {
        Constraints childConstraints = childConstraints(
                axis, minimumMain, maximumMain, maximumCross, minimumCross, crossAlignment);
        Placeable placeable = scope.measure(child, childConstraints);
        return new Item(placeable, mainSize(axis, placeable), crossSize(axis, placeable));
    }

    private static FlexSpec flexSpec(UiComponent child, Axis axis) {
        if (child instanceof Flexible flexible) {
            if (flexible.grow() == 0.0f && flexible.basis() == 0.0f) {
                return null;
            }
            return new FlexSpec(flexible.grow(), flexible.shrink(), flexible.basis(), flexible.fit());
        }
        if (child instanceof Spacer spacer
                && (axis == Axis.HORIZONTAL ? spacer.expandWidth() : spacer.expandHeight())) {
            float basis = axis == Axis.HORIZONTAL
                    ? spacer.preferredSize().width()
                    : spacer.preferredSize().height();
            return new FlexSpec(1.0f, 1.0f, basis, FlexFit.TIGHT);
        }
        return null;
    }

    private static float[] flexAllocations(List<FlexSpec> specs, float available) {
        float[] result = new float[specs.size()];
        float basisTotal = 0.0f;
        float growTotal = 0.0f;
        float shrinkTotal = 0.0f;
        for (FlexSpec spec : specs) {
            if (spec != null) {
                basisTotal += spec.basis;
                growTotal += spec.grow;
                shrinkTotal += spec.shrink * Math.max(1.0f, spec.basis);
            }
        }
        float free = available - basisTotal;
        for (int index = 0; index < specs.size(); index++) {
            FlexSpec spec = specs.get(index);
            if (spec == null) {
                continue;
            }
            float allocation = spec.basis;
            if (free > 0.0f && growTotal > 0.0f) {
                allocation += free * spec.grow / growTotal;
            } else if (free < 0.0f && shrinkTotal > 0.0f) {
                float weight = spec.shrink * Math.max(1.0f, spec.basis);
                allocation += free * weight / shrinkTotal;
            }
            result[index] = Math.max(0.0f, allocation);
        }
        return result;
    }

    private static Constraints childConstraints(
            Axis axis,
            float minimumMain,
            float maximumMain,
            float maximumCross,
            float minimumCross,
            CrossAxisAlignment crossAlignment) {
        float childMinimumCross = crossAlignment == CrossAxisAlignment.STRETCH
                ? (Float.isFinite(maximumCross) ? maximumCross : minimumCross)
                : 0.0f;
        float childMaximumCross = maximumCross;
        return axis == Axis.HORIZONTAL
                ? new Constraints(minimumMain, maximumMain, childMinimumCross, childMaximumCross)
                : new Constraints(childMinimumCross, childMaximumCross, minimumMain, maximumMain);
    }

    private static float baseline(List<Item> items, Axis axis, CrossAxisAlignment alignment) {
        if (axis != Axis.HORIZONTAL || alignment != CrossAxisAlignment.BASELINE) {
            return -1.0f;
        }
        float result = -1.0f;
        for (Item item : items) {
            var baseline = item.placeable.baseline(TextBaseline.ALPHABETIC);
            if (baseline.isPresent()) {
                result = Math.max(result, (float) baseline.getAsDouble());
            }
        }
        return result;
    }

    private static float crossOffset(
            Axis axis,
            CrossAxisAlignment alignment,
            LayoutDirection direction,
            float available,
            Item item,
            float baseline) {
        float remaining = Math.max(0.0f, available - item.crossSize);
        return switch (alignment) {
            case CENTER -> remaining * 0.5f;
            case END -> axis == Axis.VERTICAL && direction == LayoutDirection.RIGHT_TO_LEFT ? 0.0f : remaining;
            case START, STRETCH -> axis == Axis.VERTICAL && direction == LayoutDirection.RIGHT_TO_LEFT
                    ? remaining
                    : 0.0f;
            case BASELINE -> {
                if (axis == Axis.HORIZONTAL && baseline >= 0.0f) {
                    var childBaseline = item.placeable.baseline(TextBaseline.ALPHABETIC);
                    yield childBaseline.isPresent() ? baseline - (float) childBaseline.getAsDouble() : 0.0f;
                }
                yield axis == Axis.VERTICAL && direction == LayoutDirection.RIGHT_TO_LEFT ? remaining : 0.0f;
            }
        };
    }

    private static Distribution distribute(
            MainAxisAlignment alignment,
            int count,
            float baseGap,
            float extra) {
        return switch (alignment) {
            case START -> new Distribution(0.0f, baseGap);
            case CENTER -> new Distribution(extra * 0.5f, baseGap);
            case END -> new Distribution(extra, baseGap);
            case SPACE_BETWEEN -> count > 1
                    ? new Distribution(0.0f, baseGap + extra / (count - 1))
                    : new Distribution(0.0f, baseGap);
            case SPACE_AROUND -> count > 0
                    ? new Distribution(extra / count * 0.5f, baseGap + extra / count)
                    : new Distribution(0.0f, baseGap);
            case SPACE_EVENLY -> count > 0
                    ? new Distribution(extra / (count + 1), baseGap + extra / (count + 1))
                    : new Distribution(0.0f, baseGap);
        };
    }

    private static float mainMaximum(Axis axis, Constraints constraints) {
        return axis == Axis.HORIZONTAL ? constraints.maxWidth() : constraints.maxHeight();
    }

    private static float crossMaximum(Axis axis, Constraints constraints) {
        return axis == Axis.HORIZONTAL ? constraints.maxHeight() : constraints.maxWidth();
    }

    private static float crossMinimum(Axis axis, Constraints constraints) {
        return axis == Axis.HORIZONTAL ? constraints.minHeight() : constraints.minWidth();
    }

    private static float mainSize(Axis axis, Placeable placeable) {
        return axis == Axis.HORIZONTAL ? placeable.size().width() : placeable.size().height();
    }

    private static float crossSize(Axis axis, Placeable placeable) {
        return axis == Axis.HORIZONTAL ? placeable.size().height() : placeable.size().width();
    }

    private static float addFinite(float first, float second, String name) {
        float result = first + second;
        if (!Float.isFinite(result)) {
            throw new IllegalArgumentException(name + " overflowed");
        }
        return result;
    }

    private record Item(Placeable placeable, float mainSize, float crossSize) {
    }

    private record FlexSpec(float grow, float shrink, float basis, FlexFit fit) {
    }

    private record Distribution(float offset, float gap) {
    }
}
