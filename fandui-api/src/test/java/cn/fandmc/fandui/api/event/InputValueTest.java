package cn.fandmc.fandui.api.event;

import cn.fandmc.fandui.api.layout.Point;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputValueTest {
    @Test
    void pointerEventDefensivelyCopiesSets() {
        Set<PointerButton> buttons = new HashSet<>(Set.of(PointerButton.PRIMARY));
        PointerEvent event = new PointerEvent(
                PointerAction.DOWN,
                new Point(10.0f, 20.0f),
                new Point(0.0f, 0.0f),
                Optional.of(PointerButton.PRIMARY),
                buttons,
                1,
                Set.of(KeyModifier.SHIFT),
                5L);
        buttons.clear();

        assertEquals(Set.of(PointerButton.PRIMARY), event.buttons());
        assertThrows(UnsupportedOperationException.class, () -> event.buttons().clear());
    }

    @Test
    void validatesCompositionUtf16BoundariesAndNormalizedClear() {
        String emoji = "A\ud83d\ude00B";

        assertThrows(IllegalArgumentException.class, () -> new TextCompositionEvent(
                true, emoji, 2, List.of(emoji), OptionalInt.of(0), 1L));
        TextCompositionEvent clear = new TextCompositionEvent(
                false, "", 0, List.of(), OptionalInt.empty(), 2L);

        assertEquals("", clear.fullText());
        assertThrows(IllegalArgumentException.class, () -> new TextInputEvent("\ud83d", 1L));
    }

    @Test
    void keyFactoriesAreCanonicalAndInterned() {
        assertSame(Keys.letter('A'), Keys.letter('a'));
        assertEquals("key.keypad.7", Keys.keypadDigit(7).canonicalName());
        assertThrows(IllegalArgumentException.class, () -> Keys.function(26));
    }

    @Test
    void keyEventPreservesOpaqueScanCodeAndLegacyConstructorDefault() {
        KeyEvent legacy = new KeyEvent(Keys.ENTER, KeyAction.PRESS, Set.of(), 3L);
        KeyEvent physical = new KeyEvent(Keys.ENTER, 42, KeyAction.REPEAT, Set.of(), 4L);

        assertEquals(-1, legacy.scanCode());
        assertEquals(42, physical.scanCode());
        assertThrows(IllegalArgumentException.class,
                () -> new KeyEvent(Keys.ENTER, -2, KeyAction.PRESS, Set.of(), 5L));
    }
}
