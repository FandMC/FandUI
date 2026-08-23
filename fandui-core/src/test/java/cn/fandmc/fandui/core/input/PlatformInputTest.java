package cn.fandmc.fandui.core.input;

import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.core.runtime.MonotonicClock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformInputTest {
    @Test
    void mapsStableGlfwKeysButtonsAndModifierBits() {
        assertSame(Keys.letter('a'), GlfwInputMapper.key(65));
        assertSame(Keys.digit(9), GlfwInputMapper.key(57));
        assertSame(Keys.function(12), GlfwInputMapper.key(301));
        assertSame(Keys.ENTER, GlfwInputMapper.key(335));
        assertSame(Keys.UNKNOWN, GlfwInputMapper.key(-1));
        assertEquals("key.glfw.334", GlfwInputMapper.key(334).canonicalName());
        assertSame(PointerButton.SECONDARY, GlfwInputMapper.button(1));
        assertEquals(
                Set.of(KeyModifier.SHIFT, KeyModifier.ALT, KeyModifier.CAPS_LOCK),
                GlfwInputMapper.modifiers(0x0015));
        assertEquals(
                Set.of(KeyModifier.SHIFT, KeyModifier.CONTROL),
                GlfwInputMapper.modifiers(true, true, false));
    }

    @Test
    void pointerStatePublishesImmutablePostActionButtonSnapshots() {
        PointerInputState state = new PointerInputState();
        var down = state.down(
                new Point(1.0f, 2.0f),
                PointerButton.PRIMARY,
                Set.of(),
                1L);
        var move = state.move(
                new Point(2.0f, 3.0f),
                new Point(1.0f, 1.0f),
                Set.of(),
                2L);
        var up = state.up(new Point(2.0f, 3.0f), PointerButton.PRIMARY, Set.of(), 3L);

        assertEquals(Set.of(PointerButton.PRIMARY), down.buttons());
        assertEquals(Set.of(PointerButton.PRIMARY), move.buttons());
        assertTrue(up.buttons().isEmpty());
        assertEquals(Set.of(PointerButton.PRIMARY), down.buttons());
    }

    @Test
    void pointerStateComputesClickCountsAcrossAllHostVersions() {
        PointerInputState state = new PointerInputState();
        var first = state.down(new Point(10.0f, 10.0f), PointerButton.PRIMARY, Set.of(), 1_000_000_000L);
        state.up(new Point(10.0f, 10.0f), PointerButton.PRIMARY, Set.of(), 1_010_000_000L);
        var second = state.down(new Point(11.0f, 11.0f), PointerButton.PRIMARY, Set.of(), 1_200_000_000L);
        state.up(new Point(11.0f, 11.0f), PointerButton.PRIMARY, Set.of(), 1_210_000_000L);
        var distant = state.down(new Point(30.0f, 30.0f), PointerButton.PRIMARY, Set.of(), 1_300_000_000L);

        assertEquals(1, first.clickCount());
        assertEquals(2, second.clickCount());
        assertEquals(1, distant.clickCount());
    }

    @Test
    void legacyUtf16AssemblerCombinesPairsAndNormalizesIsolatedSurrogates() {
        Utf16InputAssembler assembler = new Utf16InputAssembler();

        assertTrue(assembler.accept('\ud83d').isEmpty());
        assertEquals(List.of("\ud83d\ude00"), assembler.accept('\ude00'));
        assertEquals(List.of("\ufffd"), assembler.accept('\udc00'));
        assertTrue(assembler.accept('\ud83d').isEmpty());
        assertEquals(List.of("\ufffd", "A"), assembler.accept('A'));
        assertTrue(assembler.flush().isEmpty());
        assertTrue(assembler.accept('\ud83d').isEmpty());
        assertEquals(List.of("\ufffd"), assembler.flush());
    }

    @Test
    void monotonicClockIsNonNegativeAndNeverMovesBackward() {
        MonotonicClock clock = new MonotonicClock();
        long first = clock.getAsLong();
        long second = clock.getAsLong();

        assertFalse(first < 0L);
        assertTrue(second >= first);
    }
}
