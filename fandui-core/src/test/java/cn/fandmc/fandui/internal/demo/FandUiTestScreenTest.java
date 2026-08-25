package cn.fandmc.fandui.internal.demo;

import cn.fandmc.fandui.api.UiCapabilities;
import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.event.ScrollEvent;
import cn.fandmc.fandui.api.event.TextInputEvent;
import cn.fandmc.fandui.api.icon.Icons;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextService;
import cn.fandmc.fandui.canvas.DisplayCommand;
import cn.fandmc.fandui.canvas.DisplayPaint;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FandUiTestScreenTest {
    private static final UiViewport VIEWPORT = new UiViewport(960.0f, 720.0f, 1920, 1440, 2.0f);

    @Test
    void installsAndDecodesTheSvgResourceOnTheReloadWorker() {
        String previous = System.getProperty(FandUiTestScreen.ENABLE_PROPERTY);
        System.setProperty(FandUiTestScreen.ENABLE_PROPERTY, "true");
        try (Fixture fixture = new Fixture()) {
            assertTrue(FandUiTestScreen.installIfEnabled(fixture.runtime).isPresent());
            assertEquals(1L, fixture.resources.applyReload());

            ImageRef image = fixture.resources.image(FandUiTestScreen.RESOURCE_IMAGE_KEY);
            assertEquals(ResourceState.READY, image.state());
            assertEquals(Optional.of(new ImageInfo(128, 72)), image.info());
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void responsiveLayoutCompilesEveryVisualPrimitive() {
        try (Fixture fixture = new Fixture()) {
            FandUiTestScreen.Definition definition = FandUiTestScreen.createDefinition(
                    fixture.runtime,
                    new ReadyImage());
            LayoutSnapshot desktop = layout(definition, 960.0f, 720.0f);
            LayoutSnapshot compact = layout(definition, 360.0f, 240.0f);

            Rect desktopPanel = bounds(desktop, FandUiTestScreen.PANEL_KEY);
            Rect compactPanel = bounds(compact, FandUiTestScreen.PANEL_KEY);
            assertTrue(desktopPanel.width() <= 760.0f && desktopPanel.height() <= 590.0f);
            assertTrue(compactPanel.x() >= 10.0f && compactPanel.y() >= 10.0f);
            assertTrue(compactPanel.x() + compactPanel.width() <= 350.0f);
            assertTrue(compactPanel.y() + compactPanel.height() <= 230.0f);

            var displayList = new cn.fandmc.fandui.core.scene.SceneCompiler().compile(desktop, 1L);
            assertTrue(displayList.hasBackdropBlur());
            assertTrue(displayList.maximumClipDepth() >= 3);
            assertTrue(displayList.commands().stream().anyMatch(DisplayCommand.DrawImage.class::isInstance));
            assertTrue(displayList.commands().stream().anyMatch(DisplayCommand.FillPath.class::isInstance));
            assertTrue(displayList.commands().stream().anyMatch(DisplayCommand.StrokePath.class::isInstance));
            assertTrue(displayList.commands().stream().anyMatch(command ->
                    command instanceof DisplayCommand.FillRoundedRect fill
                            && fill.paint() instanceof DisplayPaint.Linear));
        }
    }

    @Test
    void controlsInputAndScrollUpdateLiveState() {
        try (Fixture fixture = new Fixture()) {
            FandUiTestScreen.Definition definition = FandUiTestScreen.createDefinition(
                    fixture.runtime,
                    new ReadyImage());
            CoreScreenSession session = assertInstanceOf(
                    CoreScreenSession.class,
                    fixture.runtime.screens().open(definition.screen()));
            assertEquals(1, fixture.runtime.renderFrames(VIEWPORT, 1L).size());
            LayoutSnapshot snapshot = layout(definition, VIEWPORT.logicalWidth(), VIEWPORT.logicalHeight());

            click(session, center(snapshot.node(definition.run()).orElseThrow().sceneBounds()), 2L);
            assertEquals(0.1, definition.progress().progress(), 0.0001);
            assertTrue(definition.status().text().contains("layout,input,svg,clip,gradient,blur"));

            click(session, center(snapshot.node(definition.checkbox()).orElseThrow().sceneBounds()), 4L);
            assertFalse(definition.checkbox().checked());
            click(session, center(snapshot.node(definition.toggle()).orElseThrow().sceneBounds()), 6L);
            assertFalse(definition.toggle().selected());

            definition.slider().setValue(75.0);
            assertEquals(0.75, definition.progress().progress(), 0.0001);
            definition.dropdown().setSelectedIndex(3);
            assertEquals(Optional.of("quality"), definition.dropdown().value());

            Point input = center(snapshot.node(definition.input()).orElseThrow().sceneBounds());
            click(session, input, 8L);
            assertTrue(session.dispatch(new TextInputEvent("中文", 10L)));
            assertEquals("FandUI SVG 测试中文", definition.textController().text());

            UiViewport compactViewport = new UiViewport(960.0f, 520.0f, 1920, 1040, 2.0f);
            assertEquals(1, fixture.runtime.renderFrames(compactViewport, 11L).size());
            snapshot = layout(definition, compactViewport.logicalWidth(), compactViewport.logicalHeight());
            assertTrue(definition.pageScrollController().maximumOffset().orElseThrow() > 0.0);
            Point page = center(snapshot.node(definition.pageScroll()).orElseThrow().sceneBounds());
            assertTrue(session.dispatch(new ScrollEvent(0.0, -1.0, page, Set.of(), 12L)));
            assertTrue(definition.pageScrollController().offset() > 0.0);

            definition.pageScrollController().scrollTo(
                    definition.pageScrollController().maximumOffset().orElseThrow());
            assertEquals(1, fixture.runtime.renderFrames(compactViewport, 13L).size());
            snapshot = layout(definition, compactViewport.logicalWidth(), compactViewport.logicalHeight());
            assertTrue(definition.scrollController().maximumOffset().orElseThrow() > 0.0);
            Point scroll = center(snapshot.node(definition.scroll()).orElseThrow().sceneBounds());
            assertTrue(session.dispatch(new ScrollEvent(0.0, -1.0, scroll, Set.of(), 14L)));
            assertTrue(definition.scrollController().offset() > 0.0);
        }
    }

    @Test
    void iconGalleryButtonPublishesOneNavigationRequest() {
        AtomicInteger opens = new AtomicInteger();
        try (Fixture fixture = new Fixture()) {
            FandUiTestScreen.Definition definition = FandUiTestScreen.createDefinition(
                    fixture.runtime,
                    new ReadyImage(),
                    opens::incrementAndGet);
            CoreScreenSession session = assertInstanceOf(
                    CoreScreenSession.class,
                    fixture.runtime.screens().open(definition.screen()));
            assertEquals(1, fixture.runtime.renderFrames(VIEWPORT, 1L).size());
            LayoutSnapshot snapshot = layout(definition, VIEWPORT.logicalWidth(), VIEWPORT.logicalHeight());

            click(session, center(snapshot.node(definition.iconGallery()).orElseThrow().sceneBounds()), 2L);

            assertEquals(1, opens.get());
        }
    }

    @Test
    void iconGalleryPreviewsEveryPresetAndRemainsScrollableAtCompactSizes() {
        AtomicInteger backs = new AtomicInteger();
        try (Fixture fixture = new Fixture()) {
            FandUiTestScreen.IconGalleryDefinition gallery =
                    FandUiTestScreen.createIconGalleryDefinition(backs::incrementAndGet);
            assertEquals(Icons.all().size(), gallery.icons().size());
            assertThrows(UnsupportedOperationException.class, () -> gallery.icons().clear());

            LayoutSnapshot desktop = layout(gallery.screen(), 960.0f, 720.0f);
            LayoutSnapshot compactLayout = layout(gallery.screen(), 360.0f, 240.0f);
            Rect desktopPanel = bounds(desktop, FandUiTestScreen.ICON_GALLERY_PANEL_KEY);
            Rect compactPanel = bounds(compactLayout, FandUiTestScreen.ICON_GALLERY_PANEL_KEY);
            assertTrue(desktopPanel.width() <= 760.0f && desktopPanel.height() <= 590.0f);
            assertTrue(compactPanel.x() >= 10.0f && compactPanel.y() >= 10.0f);
            assertTrue(compactPanel.x() + compactPanel.width() <= 350.0f);
            assertTrue(compactPanel.y() + compactPanel.height() <= 230.0f);
            assertTrue(gallery.icons().stream().allMatch(icon -> compactLayout.node(icon).isPresent()));

            CoreScreenSession session = assertInstanceOf(
                    CoreScreenSession.class,
                    fixture.runtime.screens().open(gallery.screen()));
            UiViewport compactViewport = new UiViewport(360.0f, 240.0f, 720, 480, 2.0f);
            assertEquals(1, fixture.runtime.renderFrames(compactViewport, 1L).size());
            LayoutSnapshot activeCompact = layout(gallery.screen(), 360.0f, 240.0f);
            assertTrue(gallery.scrollController().maximumOffset().orElseThrow() > 0.0);

            click(session, center(activeCompact.node(gallery.back()).orElseThrow().sceneBounds()), 2L);
            assertEquals(1, backs.get());
            Point scroll = center(activeCompact.node(gallery.scroll()).orElseThrow().sceneBounds());
            assertTrue(session.dispatch(new ScrollEvent(0.0, -1.0, scroll, Set.of(), 4L)));
            assertTrue(gallery.scrollController().offset() > 0.0);
        }
    }

    private static LayoutSnapshot layout(FandUiTestScreen.Definition definition, float width, float height) {
        return layout(definition.screen(), width, height);
    }

    private static LayoutSnapshot layout(
            cn.fandmc.fandui.api.screen.UiScreen screen,
            float width,
            float height) {
        return new LayoutEngine().layout(
                screen.root(),
                Constraints.tight(width, height),
                LayoutDirection.LEFT_TO_RIGHT,
                screen.theme());
    }

    private static void click(CoreScreenSession session, Point point, long timestamp) {
        assertTrue(session.dispatch(pointer(PointerAction.DOWN, point, true, timestamp)));
        assertTrue(session.dispatch(pointer(PointerAction.UP, point, false, timestamp + 1L)));
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
            System.clearProperty(FandUiTestScreen.ENABLE_PROPERTY);
        } else {
            System.setProperty(FandUiTestScreen.ENABLE_PROPERTY, previous);
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
            return FandUiTestScreen.RESOURCE_IMAGE_KEY;
        }

        @Override
        public ResourceState state() {
            return ResourceState.READY;
        }

        @Override
        public Optional<ImageInfo> info() {
            return Optional.of(new ImageInfo(128, 72));
        }
    }
}
