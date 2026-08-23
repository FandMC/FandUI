package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StyleThemeTest {
    @Test
    void gradientDefensivelyCopiesAndValidatesStops() {
        List<GradientStop> source = new ArrayList<>(List.of(
                new GradientStop(0.0f, Color.rgb(0x112233)),
                new GradientStop(1.0f, Color.rgb(0xaabbcc))));

        LinearGradient gradient = new LinearGradient(new Point(0.0f, 0.0f), new Point(10.0f, 0.0f), source);
        source.clear();

        assertEquals(2, gradient.stops().size());
        assertThrows(UnsupportedOperationException.class, () -> gradient.stops().clear());
        assertThrows(IllegalArgumentException.class, () -> new LinearGradient(
                new Point(0.0f, 0.0f),
                new Point(1.0f, 0.0f),
                List.of(
                        new GradientStop(0.7f, Color.rgb(0)),
                        new GradientStop(0.2f, Color.rgb(0)))));
    }

    @Test
    void styleBuilderCopiesBaseWithoutMutatingIt() {
        Style base = Style.builder().opacity(0.5f).backdropBlur(18.0f).build();
        Style changed = Style.builder(base).opacity(0.75f).build();

        assertNotSame(base, changed);
        assertEquals(0.5f, base.opacity());
        assertEquals(0.75f, changed.opacity());
        assertEquals(18.0f, changed.backdropBlurRadius());
        assertThrows(IllegalArgumentException.class, () -> Style.builder().opacity(1.01f));
        assertThrows(IllegalArgumentException.class, () -> Style.builder().backdropBlur(-1.0f));
        assertThrows(IllegalArgumentException.class, () -> Style.builder().backdropBlur(Float.NaN));
    }

    @Test
    void themeUsesLogicalTokenIdentityAndRejectsConflictingTypes() {
        UiKey key = UiKey.of("fandui", "theme/accent");
        ThemeToken<Color> first = ThemeToken.of(key, Color.class, Color.rgb(0x000000));
        ThemeToken<Color> equivalent = ThemeToken.of(key, Color.class, Color.rgb(0xffffff));
        ThemeToken<String> conflicting = ThemeToken.of(key, String.class, "fallback");
        Color value = Color.rgb(0x336699);

        Theme.Builder builder = Theme.builder().value(first, value);
        Theme theme = builder.build();

        assertEquals(value, theme.value(equivalent));
        assertThrows(IllegalArgumentException.class, () -> builder.value(conflicting, "wrong type"));
    }
}
