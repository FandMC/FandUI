package cn.fandmc.fandui.api.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextRequestTest {
    @Test
    void acceptsWellFormedSupplementaryTextAndRejectsUnpairedSurrogates() {
        TextStyle style = TextStyle.builder(16.0f).build();
        String valid = "FandUI \uD83D\uDE00";

        assertEquals(valid, TextRequest.builder(valid, style).build().text());
        assertThrows(
                IllegalArgumentException.class,
                () -> TextRequest.builder("broken \uD83D", style).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> TextRequest.builder("broken \uDE00", style).build());
    }
}
