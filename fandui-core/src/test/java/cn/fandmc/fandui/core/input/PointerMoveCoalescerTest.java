package cn.fandmc.fandui.core.input;

import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.layout.Point;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PointerMoveCoalescerTest {
    @Test
    void drainsOnlyTheLatestSampleAndPreservesTotalDelta() {
        PointerInputState pointer = new PointerInputState();
        PointerMoveCoalescer moves = new PointerMoveCoalescer();

        moves.offer(10.0, 20.0);
        moves.offer(15.0, 24.0);
        PointerEvent first = moves.drain(pointer, Set.of(), 2L);
        assertEquals(PointerAction.MOVE, first.action());
        assertEquals(new Point(15.0f, 24.0f), first.scenePosition());
        assertEquals(new Point(0.0f, 0.0f), first.sceneDelta());
        assertEquals(2L, first.timestampNanos());
        assertNull(moves.drain(pointer, Set.of(), 3L));

        pointer.down(new Point(15.0f, 24.0f), PointerButton.PRIMARY, Set.of(), 3L);
        moves.offer(18.0, 30.0);
        moves.offer(30.0, 40.0);
        PointerEvent second = moves.drain(pointer, Set.of(), 5L);
        assertEquals(new Point(30.0f, 40.0f), second.scenePosition());
        assertEquals(new Point(15.0f, 16.0f), second.sceneDelta());
        assertEquals(Set.of(PointerButton.PRIMARY), second.buttons());
    }

    @Test
    void synchronizationDiscardsOlderPendingMotion() {
        PointerMoveCoalescer moves = new PointerMoveCoalescer();
        moves.offer(10.0, 20.0);
        moves.synchronize(new Point(30.0f, 40.0f));
        assertNull(moves.drain(new PointerInputState(), Set.of(), 1L));

        moves.offer(35.0, 47.0);
        PointerEvent event = moves.drain(new PointerInputState(), Set.of(), 2L);
        assertEquals(new Point(5.0f, 7.0f), event.sceneDelta());

        moves.clear();
        moves.offer(50.0, 60.0);
        assertEquals(new Point(0.0f, 0.0f),
                moves.drain(new PointerInputState(), Set.of(), 3L).sceneDelta());
    }

    @Test
    void rejectsCoordinatesThatCannotEnterThePublicFloatModel() {
        PointerMoveCoalescer moves = new PointerMoveCoalescer();
        assertThrows(IllegalArgumentException.class, () -> moves.offer(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> moves.offer(Double.MAX_VALUE, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> moves.drain(new PointerInputState(), Set.of(), -1L));
    }
}
