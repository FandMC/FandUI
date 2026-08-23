package cn.fandmc.fandui.internal.demo;

import cn.fandmc.fandui.api.UiCapabilities;
import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.event.ScrollEvent;
import cn.fandmc.fandui.api.event.TextInputEvent;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.screen.ScreenBackground;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextService;
import cn.fandmc.fandui.canvas.DisplayCommand;
import cn.fandmc.fandui.core.layout.LayoutEngine;
import cn.fandmc.fandui.core.layout.LayoutSnapshot;
import cn.fandmc.fandui.core.resource.CoreResourceService;
import cn.fandmc.fandui.core.runtime.CoreScreenSession;
import cn.fandmc.fandui.core.runtime.CoreUiRuntime;
import cn.fandmc.fandui.core.runtime.ScreenHost;
import cn.fandmc.fandui.core.runtime.UiThreadDispatcher;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FandUiDemoScreenTest {
    private static final UiViewport VIEWPORT = new UiViewport(800.0f, 600.0f, 1600, 1200, 2.0f);

    @Test
    void embeddedArtworkRegistersAndReloads() {
        String previous = System.getProperty(FandUiDemoScreen.ENABLE_PROPERTY);
        System.setProperty(FandUiDemoScreen.ENABLE_PROPERTY, "true");
        try (Fixture fixture = new Fixture()) {
            var installed = FandUiDemoScreen.installIfEnabled(fixture.runtime);

            assertTrue(installed.isPresent());
            assertEquals(1L, fixture.resources.applyReload());
            ImageRef artwork = fixture.resources.image(FandUiDemoScreen.ARTWORK_KEY);
            assertEquals(ResourceState.READY, artwork.state());
            assertEquals(Optional.of(new ImageInfo(24, 16)), artwork.info());
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void layoutAndDisplayListCoverResponsiveVisuals() {
        var definition = FandUiDemoScreen.createDefinition(new ReadyImage());
        LayoutSnapshot desktop = layout(definition, 800.0f, 600.0f);
        LayoutSnapshot compact = layout(definition, 320.0f, 180.0f);

        assertEquals(ScreenBackground.DEFAULT, definition.screen().background());
        assertFalse(definition.screen().pausesGame());
        assertEquals(
                new Rect(90.0f, 85.0f, 620.0f, 430.0f),
                bounds(desktop, FandUiDemoScreen.PANEL_KEY));
        assertEquals(
                new Rect(12.0f, 12.0f, 296.0f, 156.0f),
                bounds(compact, FandUiDemoScreen.PANEL_KEY));

        var displayList = new cn.fandmc.fandui.core.scene.SceneCompiler().compile(desktop, 1L);
        assertTrue(displayList.hasBackdropBlur());
        assertTrue(displayList.maximumClipDepth() >= 3);
        assertTrue(displayList.commands().stream().anyMatch(DisplayCommand.DrawImage.class::isInstance));
        assertTrue(displayList.commands().stream().anyMatch(DisplayCommand.FillRoundedRect.class::isInstance));
        assertTrue(displayList.commands().stream().anyMatch(DisplayCommand.StrokePath.class::isInstance));
        assertTrue(displayList.commands().stream().filter(DisplayCommand.Clip.class::isInstance).count() >= 3L);
    }

    @Test
    void buttonInputAndScrollInteractThroughCoreSession() {
        try (Fixture fixture = new Fixture()) {
            var definition = FandUiDemoScreen.createDefinition(new ReadyImage());
            CoreScreenSession session = assertInstanceOf(
                    CoreScreenSession.class,
                    fixture.runtime.screens().open(definition.screen()));
            assertEquals(1, fixture.runtime.renderFrames(VIEWPORT, 1L).size());
            LayoutSnapshot layout = layout(definition, VIEWPORT.logicalWidth(), VIEWPORT.logicalHeight());

            Point action = center(layout.node(definition.action()).orElseThrow().sceneBounds());
            assertTrue(session.dispatch(pointer(PointerAction.DOWN, action, true, 2L)));
            assertTrue(session.dispatch(pointer(PointerAction.UP, action, false, 3L)));
            assertEquals("已加入 1 次 · Joined 1", definition.status().text());

            Point input = center(layout.node(definition.input()).orElseThrow().sceneBounds());
            assertTrue(session.dispatch(pointer(PointerAction.DOWN, input, true, 4L)));
            assertTrue(session.dispatch(pointer(PointerAction.UP, input, false, 5L)));
            assertTrue(session.dispatch(new TextInputEvent("世界", 6L)));
            assertEquals("蔚蓝群岛世界", definition.textController().text());

            assertTrue(definition.scrollController().maximumOffset().orElseThrow() > 0.0);
            Point scroll = center(layout.node(definition.scroll()).orElseThrow().sceneBounds());
            assertTrue(session.dispatch(new ScrollEvent(0.0, -1.0, scroll, Set.of(), 7L)));
            assertTrue(definition.scrollController().offset() > 0.0);
        }
    }

    private static LayoutSnapshot layout(
            FandUiDemoScreen.Definition definition,
            float width,
            float height) {
        return new LayoutEngine().layout(
                definition.screen().root(),
                Constraints.tight(width, height),
                LayoutDirection.LEFT_TO_RIGHT,
                definition.screen().theme());
    }

    private static PointerEvent pointer(PointerAction action, Point position, boolean pressed, long timestamp) {
        return new PointerEvent(
                action,
                position,
                new Point(0.0f, 0.0f),
                Optional.of(PointerButton.PRIMARY),
                pressed ? Set.of(PointerButton.PRIMARY) : Set.of(),
                1,
                Set.of(),
                timestamp);
    }

    private static Point center(Rect rect) {
        return new Point(rect.x() + rect.width() * 0.5f, rect.y() + rect.height() * 0.5f);
    }

    private static Rect bounds(LayoutSnapshot layout, UiKey key) {
        return layout.paintOrder().stream()
                .filter(node -> node.component().key().filter(key::equals).isPresent())
                .findFirst()
                .orElseThrow()
                .sceneBounds();
    }

    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(FandUiDemoScreen.ENABLE_PROPERTY);
        } else {
            System.setProperty(FandUiDemoScreen.ENABLE_PROPERTY, previous);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final DirectDispatcher dispatcher = new DirectDispatcher();
        private final CoreResourceService resources = new CoreResourceService(dispatcher);
        private final CoreUiRuntime runtime = new CoreUiRuntime(
                dispatcher,
                new NoOpScreenHost(),
                resources,
                new PendingTextService(),
                cn.fandmc.fandui.api.input.ClipboardService.inMemory(),
                cn.fandmc.fandui.core.runtime.CursorHost.noOp(),
                UiCapabilities.of(false, false),
                () -> 1L);

        private Fixture() {
            runtime.markAvailable("test");
        }

        @Override
        public void close() {
            runtime.stop();
            resources.close();
        }
    }

    private static final class DirectDispatcher implements UiThreadDispatcher {
        @Override
        public boolean isUiThread() {
            return true;
        }

        @Override
        public void execute(Runnable action) {
            action.run();
        }
    }

    private static final class NoOpScreenHost implements ScreenHost {
        @Override
        public void open(CoreScreenSession session) {
        }

        @Override
        public void close(CoreScreenSession session) {
        }
    }

    private static final class PendingTextService implements TextService {
        @Override
        public CompletableFuture<TextLayout> layout(TextRequest request) {
            return new CompletableFuture<>();
        }
    }

    private static final class ReadyImage implements ImageRef {
        @Override
        public UiKey key() {
            return FandUiDemoScreen.ARTWORK_KEY;
        }

        @Override
        public ResourceState state() {
            return ResourceState.READY;
        }

        @Override
        public Optional<ImageInfo> info() {
            return Optional.of(new ImageInfo(24, 16));
        }
    }
}
