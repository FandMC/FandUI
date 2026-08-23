package cn.fandmc.fandui.core.layout;

import cn.fandmc.fandui.api.component.Box;
import cn.fandmc.fandui.api.component.CanvasComponent;
import cn.fandmc.fandui.api.component.DirectionScope;
import cn.fandmc.fandui.api.component.FlexFit;
import cn.fandmc.fandui.api.component.Flexible;
import cn.fandmc.fandui.api.component.Positioned;
import cn.fandmc.fandui.api.component.Row;
import cn.fandmc.fandui.api.component.Spacer;
import cn.fandmc.fandui.api.component.Stack;
import cn.fandmc.fandui.api.component.ThemeScope;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.UiContainer;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.MainAxisAlignment;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.layout.TextBaseline;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.ThemeToken;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardComponentLayoutTest {
    @Test
    void marginOccupiesParentSpaceButDoesNotExpandComponentBounds() {
        Spacer child = Spacer.of(10.0f, 6.0f);
        child.setStyle(Style.builder().margin(new Insets(2.0f, 3.0f, 4.0f, 5.0f)).build());
        Row row = Row.of(child, Spacer.of(7.0f, 6.0f));

        LayoutSnapshot snapshot = new LayoutEngine().layout(
                row,
                Constraints.loose(100.0f, 100.0f),
                LayoutDirection.LEFT_TO_RIGHT);

        LayoutNode first = snapshot.root().children().get(0);
        LayoutNode second = snapshot.root().children().get(1);
        assertEquals(new Rect(2.0f, 3.0f, 10.0f, 6.0f), first.sceneBounds());
        assertEquals(16.0f, second.position().x());
        assertEquals(new Size(23.0f, 14.0f), snapshot.root().size());
    }

    @Test
    void boxMeasuresResolvedPaddingAndAlignsItsChild() {
        Spacer child = Spacer.builder().width(10.0f).height(6.0f).build();
        Style style = Style.builder()
                .padding(new Insets(2.0f, 3.0f, 4.0f, 5.0f))
                .build();
        Box box = Box.builder(child)
                .alignment(Alignment.BOTTOM_RIGHT)
                .style(StyleResolver.fixed(style))
                .build();

        LayoutSnapshot snapshot = new LayoutEngine().layout(
                box,
                new Constraints(0.0f, 100.0f, 0.0f, 100.0f),
                LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(new Size(16.0f, 14.0f), snapshot.root().size());
        assertEquals(new Rect(2.0f, 3.0f, 10.0f, 6.0f), snapshot.root().children().get(0).sceneBounds());
        assertEquals(style, snapshot.root().style());
    }

    @Test
    void rowDistributesExtraSpaceAndMirrorsStartInRtl() {
        Spacer first = Spacer.builder().width(10.0f).height(4.0f).build();
        Spacer second = Spacer.builder().width(10.0f).height(4.0f).build();
        Row row = Row.builder()
                .child(first)
                .child(second)
                .gap(2.0f)
                .mainAxisAlignment(MainAxisAlignment.SPACE_BETWEEN)
                .build();
        Constraints constraints = Constraints.tight(new Size(40.0f, 10.0f));

        LayoutSnapshot leftToRight = new LayoutEngine().layout(
                row, constraints, LayoutDirection.LEFT_TO_RIGHT);
        LayoutSnapshot rightToLeft = new LayoutEngine().layout(
                row, constraints, LayoutDirection.RIGHT_TO_LEFT);

        assertEquals(0.0f, leftToRight.root().children().get(0).position().x());
        assertEquals(30.0f, leftToRight.root().children().get(1).position().x());
        assertEquals(30.0f, rightToLeft.root().children().get(0).position().x());
        assertEquals(0.0f, rightToLeft.root().children().get(1).position().x());
    }

    @Test
    void expandedSpacerAndFlexibleChildrenReceiveRemainingSpaceAfterFixedChildren() {
        Spacer left = Spacer.of(10.0f, 4.0f);
        Spacer expanded = Spacer.expanded();
        Spacer right = Spacer.of(10.0f, 4.0f);
        Row row = Row.of(left, expanded, right);

        LayoutSnapshot spacerLayout = new LayoutEngine().layout(
                row, Constraints.tight(100.0f, 10.0f), LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(10.0f, spacerLayout.node(left).orElseThrow().size().width());
        assertEquals(80.0f, spacerLayout.node(expanded).orElseThrow().size().width());
        assertEquals(90.0f, spacerLayout.node(right).orElseThrow().position().x());

        Flexible first = Flexible.builder(Spacer.of(1.0f, 4.0f))
                .grow(1.0f)
                .fit(FlexFit.TIGHT)
                .build();
        Flexible second = Flexible.builder(Spacer.of(1.0f, 4.0f))
                .grow(2.0f)
                .fit(FlexFit.TIGHT)
                .build();
        LayoutSnapshot weighted = new LayoutEngine().layout(
                Row.of(first, second), Constraints.tight(90.0f, 10.0f), LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(30.0f, weighted.node(first).orElseThrow().size().width(), 0.001f);
        assertEquals(60.0f, weighted.node(second).orElseThrow().size().width(), 0.001f);
    }

    @Test
    void stackPositionsChildrenAndPreservesExplicitZOrder() {
        Spacer background = Spacer.of(40.0f, 20.0f);
        Positioned foreground = Positioned.builder(Spacer.of(10.0f, 5.0f))
                .right(3.0f)
                .bottom(2.0f)
                .zIndex(4)
                .build();
        Stack stack = Stack.builder(background, foreground).build();

        LayoutSnapshot snapshot = new LayoutEngine().layout(
                stack, Constraints.tight(40.0f, 20.0f), LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(new Point(27.0f, 13.0f), snapshot.node(foreground).orElseThrow().position());
        assertEquals(4, snapshot.node(foreground).orElseThrow().zIndex());
        assertEquals(new Rect(27.0f, 13.0f, 10.0f, 5.0f),
                snapshot.node(foreground).orElseThrow().sceneBounds());
    }

    @Test
    void themeAndDirectionScopesOverrideOnlyTheirSubtrees() {
        ThemeToken<Float> spacing = ThemeToken.of(
                cn.fandmc.fandui.api.UiKey.of("test", "spacing"), Float.class, 1.0f);
        Spacer themed = Spacer.of(4.0f, 4.0f);
        themed.setStyle((theme, state) -> Style.builder().margin(theme.value(spacing)).build());
        Theme local = Theme.builder().value(spacing, 3.0f).build();
        ThemeScope themeScope = ThemeScope.builder(themed).theme(local).build();

        Spacer first = Spacer.of(10.0f, 4.0f);
        Spacer second = Spacer.of(10.0f, 4.0f);
        DirectionScope rtl = DirectionScope.of(
                LayoutDirection.RIGHT_TO_LEFT,
                Row.of(first, second));
        Row root = Row.of(themeScope, rtl);

        LayoutSnapshot snapshot = new LayoutEngine().layout(
                root, Constraints.loose(100.0f, 20.0f), LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(local.value(spacing), snapshot.node(themed).orElseThrow().theme().value(spacing));
        assertEquals(LayoutDirection.RIGHT_TO_LEFT, snapshot.node(first).orElseThrow().direction());
        assertEquals(10.0f, snapshot.node(first).orElseThrow().position().x());
        assertEquals(0.0f, snapshot.node(second).orElseThrow().position().x());
    }

    @Test
    void canvasMeasureResultIsConstrainedByItsParent() {
        CanvasComponent component = CanvasComponent.builder(
                (constraints, style, theme) -> new Size(500.0f, 300.0f),
                scope -> { })
                .build();

        LayoutSnapshot snapshot = new LayoutEngine().layout(
                component,
                new Constraints(0.0f, 80.0f, 0.0f, 60.0f),
                LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(new Size(80.0f, 60.0f), snapshot.root().size());
    }

    @Test
    void textBaselinesFlowThroughPlaceablesAndFrozenNodes() {
        BaselineLeaf child = new BaselineLeaf();
        BaselineParent parent = new BaselineParent(child);

        LayoutSnapshot snapshot = new LayoutEngine().layout(
                parent,
                new Constraints(0.0f, 100.0f, 0.0f, 100.0f),
                LayoutDirection.LEFT_TO_RIGHT);

        assertEquals(OptionalDouble.of(7.0), parent.observedAlphabetic);
        assertEquals(7.0, snapshot.root().children().get(0)
                .baseline(TextBaseline.ALPHABETIC).orElseThrow());
        assertEquals(8.0, snapshot.root().children().get(0)
                .baseline(TextBaseline.IDEOGRAPHIC).orElseThrow());
    }

    private static final class BaselineLeaf extends UiComponent {
        @Override
        public cn.fandmc.fandui.api.layout.MeasureResult measure(
                cn.fandmc.fandui.api.layout.MeasureScope scope,
                Constraints constraints) {
            return scope.layout(
                    20.0f,
                    10.0f,
                    Map.of(TextBaseline.ALPHABETIC, 7.0f, TextBaseline.IDEOGRAPHIC, 8.0f),
                    placements -> { });
        }
    }

    private static final class BaselineParent extends UiContainer {
        private OptionalDouble observedAlphabetic = OptionalDouble.empty();

        private BaselineParent(UiComponent child) {
            add(child);
        }

        @Override
        public cn.fandmc.fandui.api.layout.MeasureResult measure(
                cn.fandmc.fandui.api.layout.MeasureScope scope,
                Constraints constraints) {
            var child = scope.measure(children().get(0), new Constraints(0.0f, 20.0f, 0.0f, 10.0f));
            observedAlphabetic = child.baseline(TextBaseline.ALPHABETIC);
            return scope.layout(20.0f, 10.0f, placements -> placements.place(child, 0.0f, 0.0f));
        }
    }
}
