package cn.fandmc.fandui.core.layout;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.UiContainer;
import cn.fandmc.fandui.api.component.HitTestBehavior;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Transform2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutEngineTest {
    private static final Constraints VIEWPORT = Constraints.tight(new Size(100.0f, 80.0f));

    @Test
    void freezesBoundsAndUsesZThenOriginalChildOrder() {
        TestContainer root = new TestContainer();
        FixedLeaf first = new FixedLeaf(20.0f, 20.0f);
        FixedLeaf second = new FixedLeaf(30.0f, 30.0f);
        root.add(first);
        root.add(second);
        root.placements = List.of(
                new PlacementSpec(second, 4.0f, 6.0f, 3),
                new PlacementSpec(first, 2.0f, 3.0f, 3));

        LayoutSnapshot snapshot = new LayoutEngine().layout(root, VIEWPORT, LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(List.of(first, second), snapshot.root().children().stream()
                .map(LayoutNode::component)
                .toList());
        assertEquals(new Rect(2.0f, 3.0f, 20.0f, 20.0f), snapshot.root().children().get(0).sceneBounds());
        assertSame(second, snapshot.hitTest(new Point(10.0f, 10.0f)).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.paintOrder().clear());
    }

    @Test
    void excludesMeasuredButUnplacedChildrenFromSnapshot() {
        TestContainer root = new TestContainer();
        FixedLeaf placed = new FixedLeaf(10.0f, 10.0f);
        FixedLeaf omitted = new FixedLeaf(10.0f, 10.0f);
        root.add(placed);
        root.add(omitted);
        root.placements = List.of(new PlacementSpec(placed, 0.0f, 0.0f, 0));

        LayoutSnapshot snapshot = new LayoutEngine().layout(root, VIEWPORT, LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(List.of(placed), snapshot.root().children().stream().map(LayoutNode::component).toList());
    }

    @Test
    void rejectsMeasuringAChildTwiceOrAComponentOutsideTheDirectTree() {
        FixedLeaf child = new FixedLeaf(10.0f, 10.0f);
        FixedLeaf foreign = new FixedLeaf(10.0f, 10.0f);
        InvalidContainer duplicate = new InvalidContainer(child, child);
        duplicate.add(child);
        InvalidContainer outside = new InvalidContainer(foreign, null);

        assertThrows(LayoutException.class, () -> new LayoutEngine().layout(
                duplicate, VIEWPORT, LayoutDirection.LEFT_TO_RIGHT));
        assertThrows(LayoutException.class, () -> new LayoutEngine().layout(
                outside, VIEWPORT, LayoutDirection.LEFT_TO_RIGHT));
    }

    @Test
    void rejectsMeasureResultsNotOwnedByTheCurrentScope() {
        UiComponent invalid = new UiComponent() {
            @Override
            public MeasureResult measure(MeasureScope scope, Constraints constraints) {
                return () -> new Size(10.0f, 10.0f);
            }
        };

        assertThrows(LayoutException.class, () -> new LayoutEngine().layout(
                invalid,
                new Constraints(0.0f, 100.0f, 0.0f, 100.0f),
                LayoutDirection.LEFT_TO_RIGHT));
    }

    @Test
    void hitTestingAndCoordinateConversionUseTheComposedTransform() {
        TestContainer root = new TestContainer();
        FixedLeaf child = new FixedLeaf(20.0f, 10.0f);
        child.setStyle(Style.builder()
                .transform(Transform2D.translation(20.0f, 5.0f).concatenate(Transform2D.scale(2.0f, 2.0f)))
                .build());
        root.add(child);
        root.placements = List.of(new PlacementSpec(child, 10.0f, 10.0f, 0));

        LayoutSnapshot snapshot = new LayoutEngine().layout(root, VIEWPORT, LayoutDirection.LEFT_TO_RIGHT);
        LayoutNode node = snapshot.node(child).orElseThrow();

        assertEquals(new Rect(30.0f, 15.0f, 40.0f, 20.0f), node.sceneBounds());
        assertSame(child, snapshot.hitTest(new Point(50.0f, 25.0f)).orElseThrow());
        assertTrue(snapshot.hitTest(new Point(15.0f, 15.0f)).orElseThrow() != child);
        assertEquals(new Point(10.0f, 5.0f), snapshot.sceneToLocal(child, new Point(50.0f, 25.0f)).orElseThrow());
        assertEquals(new Point(50.0f, 25.0f), snapshot.localToScene(child, new Point(10.0f, 5.0f)).orElseThrow());
    }

    @Test
    void transformedAncestorClipAndSingularTransformsAreNotHit() {
        TestContainer root = new TestContainer();
        TestContainer clipped = new TestContainer();
        clipped.setStyle(Style.builder()
                .clip(ClipMode.ROUNDED_BOUNDS)
                .cornerRadius(10.0f)
                .transform(Transform2D.translation(20.0f, 0.0f))
                .build());
        FixedLeaf child = new FixedLeaf(20.0f, 20.0f);
        clipped.add(child);
        clipped.placements = List.of(new PlacementSpec(child, 0.0f, 0.0f, 0));
        root.add(clipped);
        root.placements = List.of(new PlacementSpec(clipped, 0.0f, 0.0f, 0));

        LayoutSnapshot clippedSnapshot = new LayoutEngine().layout(
                root, Constraints.tight(20.0f, 20.0f), LayoutDirection.LEFT_TO_RIGHT);
        assertTrue(clippedSnapshot.hitTest(new Point(20.5f, 0.5f)).isEmpty());
        assertSame(child, clippedSnapshot.hitTest(new Point(30.0f, 10.0f)).orElseThrow());

        FixedLeaf singular = new FixedLeaf(10.0f, 10.0f);
        singular.setStyle(Style.builder().transform(Transform2D.scale(0.0f, 1.0f)).build());
        LayoutSnapshot singularSnapshot = new LayoutEngine().layout(
                singular, Constraints.tight(10.0f, 10.0f), LayoutDirection.LEFT_TO_RIGHT);
        assertTrue(singularSnapshot.hitTest(new Point(0.0f, 5.0f)).isEmpty());
        assertTrue(singularSnapshot.sceneToLocal(singular, new Point(0.0f, 5.0f)).isEmpty());
    }

    @Test
    void hitTestBehaviorCanPassThroughOrIgnoreDecorativeSubtrees() {
        TestContainer root = new TestContainer();
        FixedLeaf behind = new FixedLeaf(20.0f, 20.0f);
        TestContainer decoration = new TestContainer();
        FixedLeaf decorationChild = new FixedLeaf(20.0f, 20.0f);
        decoration.add(decorationChild);
        decoration.placements = List.of(new PlacementSpec(decorationChild, 0.0f, 0.0f, 0));
        root.add(behind);
        root.add(decoration);
        root.placements = List.of(
                new PlacementSpec(behind, 0.0f, 0.0f, 0),
                new PlacementSpec(decoration, 0.0f, 0.0f, 1));

        decoration.setHitTestBehavior(HitTestBehavior.IGNORE_SUBTREE);
        LayoutSnapshot ignored = new LayoutEngine().layout(
                root, Constraints.tight(20.0f, 20.0f), LayoutDirection.LEFT_TO_RIGHT);
        assertSame(behind, ignored.hitTest(new Point(5.0f, 5.0f)).orElseThrow());

        decoration.setHitTestBehavior(HitTestBehavior.PASS_THROUGH);
        decorationChild.setHitTestBehavior(HitTestBehavior.PASS_THROUGH);
        LayoutSnapshot passed = new LayoutEngine().layout(
                root, Constraints.tight(20.0f, 20.0f), LayoutDirection.LEFT_TO_RIGHT);
        assertSame(behind, passed.hitTest(new Point(5.0f, 5.0f)).orElseThrow());
    }

    private static final class FixedLeaf extends UiComponent {
        private final Size preferred;

        private FixedLeaf(float width, float height) {
            this.preferred = new Size(width, height);
        }

        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            Size size = constraints.constrain(preferred);
            return scope.layout(size.width(), size.height(), placements -> { });
        }
    }

    private static final class TestContainer extends UiContainer {
        private List<PlacementSpec> placements = List.of();

        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            List<MeasuredPlacement> measured = new ArrayList<>();
            for (UiComponent child : children()) {
                Placeable placeable = scope.measure(
                        child,
                        new Constraints(0.0f, constraints.maxWidth(), 0.0f, constraints.maxHeight()));
                PlacementSpec spec = placements.stream()
                        .filter(candidate -> candidate.component == child)
                        .findFirst()
                        .orElse(null);
                if (spec != null) {
                    measured.add(new MeasuredPlacement(placeable, spec));
                }
            }
            return scope.layout(constraints.maxWidth(), constraints.maxHeight(), placementScope -> {
                for (int index = measured.size() - 1; index >= 0; index--) {
                    MeasuredPlacement item = measured.get(index);
                    placementScope.place(
                            item.placeable,
                            item.spec.x,
                            item.spec.y,
                            item.spec.zIndex);
                }
            });
        }
    }

    private static final class InvalidContainer extends UiContainer {
        private final UiComponent first;
        private final UiComponent second;

        private InvalidContainer(UiComponent first, UiComponent second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            scope.measure(first, Constraints.loose(new Size(10.0f, 10.0f)));
            if (second != null) {
                scope.measure(second, Constraints.loose(new Size(10.0f, 10.0f)));
            }
            return scope.layout(constraints.maxWidth(), constraints.maxHeight(), placements -> { });
        }
    }

    private record PlacementSpec(UiComponent component, float x, float y, int zIndex) {
    }

    private record MeasuredPlacement(Placeable placeable, PlacementSpec spec) {
    }
}
