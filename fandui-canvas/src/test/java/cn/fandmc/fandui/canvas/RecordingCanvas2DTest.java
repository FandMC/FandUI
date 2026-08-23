package cn.fandmc.fandui.canvas;

import cn.fandmc.fandui.api.canvas.CanvasState;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.SolidPaint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordingCanvas2DTest {
    @Test
    void recordsAnImmutablePremultipliedDisplayList() {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        canvas.fillRect(
                new Rect(1.0f, 2.0f, 30.0f, 40.0f),
                new SolidPaint(new Color(1.0f, 0.5f, 0.25f, 0.5f)));

        DisplayList displayList = canvas.finish();
        DisplayCommand.FillRect command = assertInstanceOf(
                DisplayCommand.FillRect.class,
                displayList.commands().get(0));
        DisplayPaint.Solid paint = assertInstanceOf(DisplayPaint.Solid.class, command.paint());

        assertEquals(new PremultipliedColor(0.5f, 0.25f, 0.125f, 0.5f), paint.color());
        assertThrows(UnsupportedOperationException.class, () -> displayList.commands().clear());
        assertThrows(IllegalStateException.class, () -> canvas.translate(1.0f, 1.0f));
    }

    @Test
    void recordsBackdropBlurAsAnImmutableBackendNeutralCommand() {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        Rect bounds = new Rect(4.0f, 6.0f, 80.0f, 40.0f);
        CornerRadii radii = CornerRadii.all(12.0f);

        canvas.backdropBlur(bounds, radii, 18.0f);
        DisplayList displayList = canvas.finish();
        DisplayCommand.BackdropBlur blur = assertInstanceOf(
                DisplayCommand.BackdropBlur.class,
                displayList.commands().get(0));

        assertEquals(bounds, blur.rect());
        assertEquals(radii, blur.radii());
        assertEquals(18.0f, blur.radius());
        assertEquals(true, displayList.hasBackdropBlur());
        assertThrows(IllegalArgumentException.class,
                () -> new DisplayCommand.BackdropBlur(bounds, radii, Float.POSITIVE_INFINITY));
    }

    @Test
    void saveHandleRestoresExactlyOnceInLifoOrder() {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        CanvasState outer = canvas.save();
        CanvasState inner = canvas.save();
        inner.close();
        inner.close();
        outer.close();

        DisplayList displayList = canvas.finish();

        assertEquals(4, displayList.commands().size());
        assertEquals(DisplayCommand.Save.INSTANCE, displayList.commands().get(0));
        assertEquals(DisplayCommand.Restore.INSTANCE, displayList.commands().get(3));
    }

    @Test
    void rejectsUnclosedAndOutOfOrderStates() {
        RecordingCanvas2D unclosed = RecordingCanvas2D.begin();
        unclosed.save();
        assertThrows(DisplayListException.class, unclosed::finish);

        RecordingCanvas2D outOfOrder = RecordingCanvas2D.begin();
        CanvasState outer = outOfOrder.save();
        outOfOrder.save();
        assertThrows(DisplayListException.class, outer::close);
        assertThrows(DisplayListException.class, outOfOrder::finish);
    }

    @Test
    void enforcesEightPathClipLevelsAndTracksMaximum() {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        Path path = Path.builder().rect(new Rect(0.0f, 0.0f, 10.0f, 10.0f)).build();
        for (int depth = 0; depth < RecordingCanvas2D.MAX_CLIP_DEPTH; depth++) {
            canvas.clip(path);
        }

        DisplayList displayList = canvas.finish();
        assertEquals(RecordingCanvas2D.MAX_CLIP_DEPTH, displayList.maximumClipDepth());

        RecordingCanvas2D overflow = RecordingCanvas2D.begin();
        for (int depth = 0; depth < RecordingCanvas2D.MAX_CLIP_DEPTH; depth++) {
            overflow.clip(path);
        }
        assertThrows(DisplayListException.class, () -> overflow.clip(path));
        assertThrows(DisplayListException.class, overflow::finish);
    }

    @Test
    void combinesCompleteDisplayListsInOrderWithoutChangingSingleIdentity() {
        RecordingCanvas2D firstCanvas = RecordingCanvas2D.begin();
        firstCanvas.fillRect(
                new Rect(0.0f, 0.0f, 5.0f, 5.0f),
                new SolidPaint(Color.rgb(0xffffff)));
        DisplayList first = firstCanvas.finish();

        RecordingCanvas2D secondCanvas = RecordingCanvas2D.begin();
        secondCanvas.clip(Path.builder().rect(new Rect(0.0f, 0.0f, 10.0f, 10.0f)).build());
        secondCanvas.fillRect(
                new Rect(1.0f, 1.0f, 2.0f, 2.0f),
                new SolidPaint(Color.rgb(0x000000)));
        DisplayList second = secondCanvas.finish();

        DisplayList combined = DisplayList.combine(List.of(first, second));

        assertEquals(first.commands().size() + second.commands().size(), combined.commands().size());
        assertEquals(second.maximumClipDepth(), combined.maximumClipDepth());
        assertEquals(false, combined.hasBackdropBlur());
        assertSame(first.commands().get(0), combined.commands().get(0));
        assertSame(first, DisplayList.combine(List.of(first)));
        assertEquals(0, DisplayList.combine(List.of()).commands().size());
        assertThrows(NullPointerException.class, () -> DisplayList.combine(List.of(first, null)));
    }
}
