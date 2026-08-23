package cn.fandmc.fandui.internal.demo;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.canvas.DisplayCommand;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.core.layout.LayoutEngine;
import cn.fandmc.fandui.core.scene.SceneCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BlurStressHudTest {
    @Test
    void blursOnlyTheRightHalfWithoutForegroundDrawing() {
        var layer = BlurStressHud.createLayer();
        var layout = new LayoutEngine().layout(
                layer.root(),
                Constraints.tight(320.0f, 180.0f),
                LayoutDirection.LEFT_TO_RIGHT,
                layer.theme());

        var blurNode = layout.paintOrder().stream()
                .filter(node -> node.style().backdropBlurRadius() > 0.0f)
                .findFirst()
                .orElseThrow();
        assertEquals(new Rect(160.0f, 0.0f, 160.0f, 180.0f), blurNode.sceneBounds());
        assertEquals(BlurStressHud.BLUR_RADIUS, blurNode.style().backdropBlurRadius());

        DisplayList displayList = new SceneCompiler().compile(layout, 1L);
        List<DisplayCommand.BackdropBlur> blurCommands = displayList.commands().stream()
                .filter(DisplayCommand.BackdropBlur.class::isInstance)
                .map(DisplayCommand.BackdropBlur.class::cast)
                .toList();

        assertEquals(1, blurCommands.size());
        assertEquals(new Rect(0.0f, 0.0f, 160.0f, 180.0f), blurCommands.get(0).rect());
        assertEquals(BlurStressHud.BLUR_RADIUS, blurCommands.get(0).radius());
        assertFalse(displayList.commands().stream().anyMatch(BlurStressHudTest::drawsForeground));
    }

    @Test
    void recomputesTheHalfScreenBoundsForEveryViewport() {
        UiComponent root = BlurStressHud.createLayer().root();

        assertEquals(new Rect(160.0f, 0.0f, 160.0f, 180.0f), blurBounds(root, 320.0f, 180.0f));
        assertEquals(new Rect(320.0f, 0.0f, 320.0f, 360.0f), blurBounds(root, 640.0f, 360.0f));
        assertEquals(new Rect(160.5f, 0.0f, 160.5f, 101.0f), blurBounds(root, 321.0f, 101.0f));
    }

    private static Rect blurBounds(UiComponent root, float width, float height) {
        return new LayoutEngine().layout(
                        root,
                        Constraints.tight(width, height),
                        LayoutDirection.LEFT_TO_RIGHT)
                .paintOrder().stream()
                .filter(node -> node.style().backdropBlurRadius() > 0.0f)
                .findFirst()
                .orElseThrow()
                .sceneBounds();
    }

    private static boolean drawsForeground(DisplayCommand command) {
        return command instanceof DisplayCommand.FillRect
                || command instanceof DisplayCommand.FillRoundedRect
                || command instanceof DisplayCommand.FillPath
                || command instanceof DisplayCommand.StrokePath
                || command instanceof DisplayCommand.DrawImage
                || command instanceof DisplayCommand.DrawImageRegion
                || command instanceof DisplayCommand.DrawText;
    }
}
