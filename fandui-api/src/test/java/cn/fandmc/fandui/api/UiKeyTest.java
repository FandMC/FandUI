package cn.fandmc.fandui.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiKeyTest {
    @Test
    void parsesAndFormatsNamespacedKeys() {
        UiKey key = UiKey.parse("fandui:textures/ui/panel.png");

        assertEquals("fandui", key.namespace());
        assertEquals("textures/ui/panel.png", key.value());
        assertEquals("fandui:textures/ui/panel.png", key.toString());
    }

    @Test
    void rejectsMalformedKeys() {
        assertThrows(IllegalArgumentException.class, () -> UiKey.parse("missing_separator"));
        assertThrows(IllegalArgumentException.class, () -> UiKey.parse("a:b:c"));
        assertThrows(IllegalArgumentException.class, () -> UiKey.of("Uppercase", "value"));
        assertThrows(IllegalArgumentException.class, () -> UiKey.of("valid", "bad value"));
    }
}
