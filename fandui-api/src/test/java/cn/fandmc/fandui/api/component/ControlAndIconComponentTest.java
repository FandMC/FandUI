package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.icon.IconDefinition;
import cn.fandmc.fandui.api.icon.Icons;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.SolidPaint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlAndIconComponentTest {
    @Test
    void toggleSwitchPublishesBooleanValuesAndSupportsAliases() {
        AtomicReference<Boolean> published = new AtomicReference<>();
        ToggleSwitch control = ToggleSwitch.builder()
                .checked(false)
                .onValueChange(published::set)
                .build();

        control.toggle();
        assertTrue(control.selected());
        assertTrue(control.checked());
        assertEquals(Boolean.TRUE, published.get());

        Switch alias = Switch.of(true);
        alias.setSelected(false);
        assertFalse(alias.selected());
        assertSame(alias.control(), alias.children().get(0));
    }

    @Test
    void dropdownKeepsImmutableTypedOptionsAndRejectsDisabledSelection() {
        Dropdown.Option<String> first = Dropdown.Option.of("a", "Alpha");
        Dropdown.Option<String> disabled = Dropdown.Option.disabled("b", "Beta");
        Dropdown<String> dropdown = Dropdown.builder(List.of(first, disabled))
                .selectedIndex(0)
                .build();

        assertEquals("a", dropdown.value().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> dropdown.options().clear());
        assertThrows(IllegalArgumentException.class, () -> dropdown.setSelectedIndex(1));
        dropdown.setExpanded(true);
        assertTrue(dropdown.expanded());
        dropdown.setExpanded(false);
        assertFalse(dropdown.expanded());
    }

    @Test
    void svgParserBuildsVectorLayersWithoutNativeTypes() {
        IconDefinition definition = IconDefinition.fromSvg("""
                <svg viewBox="0 0 24 24">
                  <path d="M2 2h20v20H2z" fill="#20aee8"/>
                  <circle cx="12" cy="12" r="5" fill="white" stroke="#000000" stroke-width="1"/>
                </svg>
                """);
        assertEquals(24.0f, definition.viewBox().width());
        assertEquals(2, definition.layers().size());
        assertEquals(20.0f, definition.layers().get(0).path().bounds().width());

        Icon icon = Icon.builder(definition)
                .size(48.0f, 32.0f)
                .tint(new SolidPaint(Color.rgb(0xffffff)))
                .build();
        assertEquals(new cn.fandmc.fandui.api.layout.Size(48.0f, 32.0f), icon.preferredSize());
        assertTrue(Icons.CHECK.layers().size() > 0);
        assertThrows(IllegalArgumentException.class, () -> IconDefinition.fromSvg(
                "<!DOCTYPE svg [<!ENTITY xxe SYSTEM 'file:///secret'>]><svg><path d='&xxe;'/></svg>"));
    }

    @Test
    void presetCatalogueCoversEveryPublicIconAndCannotBeModified() throws ReflectiveOperationException {
        Set<String> publicPresets = Arrays.stream(Icons.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType() == IconDefinition.class)
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertEquals(publicPresets, Icons.all().keySet());
        assertTrue(Icons.all().size() >= 58);
        assertSame(Icons.all(), Icons.all());
        for (var entry : Icons.all().entrySet()) {
            assertTrue(entry.getKey().matches("[A-Z0-9_]+"));
            var field = Icons.class.getDeclaredField(entry.getKey());
            assertSame(field.get(null), entry.getValue());
            assertTrue(Float.isFinite(entry.getValue().viewBox().width()));
            assertTrue(Float.isFinite(entry.getValue().viewBox().height()));
            assertTrue(entry.getValue().viewBox().width() > 0.0f);
            assertTrue(entry.getValue().viewBox().height() > 0.0f);
            assertFalse(entry.getValue().layers().isEmpty());
        }
        assertThrows(UnsupportedOperationException.class, () -> Icons.all().clear());
    }

    @Test
    void svgParserNormalizesViewBoxOriginAndMultipliesGroupOpacity() {
        IconDefinition definition = IconDefinition.fromSvg("""
                <svg viewBox="10 20 4 4">
                  <g opacity="0.5"><rect x="10" y="20" width="4" height="4" fill="#ff0000"/></g>
                </svg>
                """);

        assertEquals(0.0f, definition.layers().get(0).path().bounds().x());
        assertEquals(0.0f, definition.layers().get(0).path().bounds().y());
        SolidPaint paint = (SolidPaint) definition.layers().get(0).fill();
        assertEquals(0.5f, paint.color().alpha(), 0.01f);
    }

    @Test
    void svgParserAcceptsRgbAndRgbaPresentationColors() {
        IconDefinition definition = IconDefinition.fromSvg("""
                <svg viewBox="0 0 2 1">
                  <rect width="1" height="1" fill="rgb(0, 128, 255)"/>
                  <rect x="1" width="1" height="1" fill="rgba(255, 0, 0, 0.5)"/>
                </svg>
                """);

        assertEquals(2, definition.layers().size());
        SolidPaint rgb = (SolidPaint) definition.layers().get(0).fill();
        SolidPaint rgba = (SolidPaint) definition.layers().get(1).fill();
        assertEquals(0.5f, rgb.color().green(), 0.01f);
        assertEquals(0.5f, rgba.color().alpha(), 0.01f);
    }

    @Test
    void svgParserAcceptsPixelLengthsAndRejectsMalformedNumericLists() {
        IconDefinition definition = IconDefinition.fromSvg("""
                <svg width="24px" height="12px">
                  <rect x="1px" y="2px" width="10px" height="6px"
                        fill="none" stroke="black" stroke-width="1px"
                        transform="translate(1 0)"/>
                </svg>
                """);

        assertEquals(24.0f, definition.viewBox().width());
        assertEquals(12.0f, definition.viewBox().height());
        assertThrows(IllegalArgumentException.class, () -> IconDefinition.fromSvg(
                "<svg viewBox=\"0@0 2 2\"><rect width=\"1\" height=\"1\"/></svg>"));
        assertThrows(IllegalArgumentException.class, () -> IconDefinition.fromSvg(
                "<svg viewBox=\"0 0 2 2\"><g transform=\"scale(1 2 3)\"><rect width=\"1\" height=\"1\"/></g></svg>"));
    }

    @Test
    void iconDefinitionCanBuildMonochromePath() {
        IconDefinition definition = IconDefinition.monochrome(
                10.0f,
                10.0f,
                Path.builder().rect(new cn.fandmc.fandui.api.layout.Rect(1.0f, 1.0f, 8.0f, 8.0f)).build());
        assertEquals(1, definition.layers().size());
        assertEquals(8.0f, definition.layers().get(0).path().bounds().width());
    }
}
