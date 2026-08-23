package cn.fandmc.fandui.api;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.hud.HudLayer;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.screen.ScreenBackground;
import cn.fandmc.fandui.api.screen.UiScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDefinitionTest {
    @Test
    void runtimeFacadeFailsFastBeforeBootstrap() {
        assertThrows(IllegalStateException.class, FandUI::runtime);
    }

    @Test
    void availabilityAndCapabilitiesAreImmutableValues() {
        UiAvailability starting = new UiAvailability(UiRuntimeState.STARTING, "bootstrap");
        UiAvailability available = new UiAvailability(UiRuntimeState.AVAILABLE, "ready");

        assertFalse(starting.available());
        assertTrue(available.available());
        assertEquals(UiCapabilities.of(true, false), UiCapabilities.of(true, false));
        assertSame(starting, new UiUnavailableException(starting).availability());
    }

    @Test
    void screenAndHudBuildersUseStableDefaults() {
        UiComponent root = new EmptyComponent();
        UiScreen screen = UiScreen.builder("Demo", root).build();
        HudLayer layer = HudLayer.builder(UiKey.of("demo", "main"), root).build();

        assertSame(root, screen.root());
        assertFalse(screen.pausesGame());
        assertTrue(screen.closesOnEscape());
        assertEquals(ScreenBackground.DEFAULT, screen.background());
        assertEquals(LayoutDirection.LEFT_TO_RIGHT, screen.layoutDirection());
        assertEquals(0, layer.order());
        assertEquals(LayoutDirection.LEFT_TO_RIGHT, layer.layoutDirection());
        assertSame(root, layer.root());
    }

    private static final class EmptyComponent extends UiComponent {
        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            return scope.layout(constraints.minWidth(), constraints.minHeight(), placements -> { });
        }
    }
}
