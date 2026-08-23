package cn.fandmc.fandui.core.input;

import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.Keys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyInputStateTest {
    @Test
    void distinguishesPressRepeatAndReleaseForEachKey() {
        KeyInputState state = new KeyInputState();

        assertEquals(KeyAction.PRESS, state.press(Keys.letter('a')));
        assertEquals(KeyAction.REPEAT, state.press(Keys.letter('a')));
        assertTrue(state.isPressed(Keys.letter('a')));
        assertEquals(KeyAction.RELEASE, state.release(Keys.letter('a')));
        assertFalse(state.isPressed(Keys.letter('a')));
        assertEquals(KeyAction.PRESS, state.press(Keys.letter('a')));
    }

    @Test
    void releaseOfUnknownKeyIsIdempotentAndClearResetsAllKeys() {
        KeyInputState state = new KeyInputState();

        assertEquals(KeyAction.RELEASE, state.release(Keys.ENTER));
        state.press(Keys.ENTER);
        state.press(Keys.TAB);
        state.clear();

        assertFalse(state.isPressed(Keys.ENTER));
        assertFalse(state.isPressed(Keys.TAB));
        assertEquals(KeyAction.PRESS, state.press(Keys.ENTER));
    }
}
