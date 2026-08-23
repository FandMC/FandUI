package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.UiCapabilities;
import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.component.Button;
import cn.fandmc.fandui.api.component.ScrollContainer;
import cn.fandmc.fandui.api.component.Spacer;
import cn.fandmc.fandui.api.component.Text;
import cn.fandmc.fandui.api.component.TextInput;
import cn.fandmc.fandui.api.component.control.ScrollController;
import cn.fandmc.fandui.api.component.control.TextController;
import cn.fandmc.fandui.api.component.control.TextSelection;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.event.ScrollEvent;
import cn.fandmc.fandui.api.event.TextCompositionEvent;
import cn.fandmc.fandui.api.event.TextInputEvent;
import cn.fandmc.fandui.api.layout.Axis;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.layout.TextBaseline;
import cn.fandmc.fandui.api.input.ClipboardService;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceRegistration;
import cn.fandmc.fandui.api.resource.ResourceReloadListener;
import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.resource.ResourceSource;
import cn.fandmc.fandui.api.screen.UiScreen;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.VisualState;
import cn.fandmc.fandui.api.text.FontFamily;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextGeometry;
import cn.fandmc.fandui.api.text.TextLine;
import cn.fandmc.fandui.api.text.TextPosition;
import cn.fandmc.fandui.api.text.TextRange;
import cn.fandmc.fandui.api.text.TextRangeGeometry;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextService;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.canvas.DisplayCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveComponentsTest {
    private static final UiViewport VIEWPORT = new UiViewport(100.0f, 40.0f, 200, 80, 2.0f);

    @Test
    void textPublishesCompleteLayoutsOnTheNextUiTurnAndKeepsThePreviousFrame() {
        ControlledTextService textService = new ControlledTextService();
        Fixture fixture = new Fixture(textService);
        Text text = Text.builder("中文 English", TextStyle.builder(16.0f).build()).build();
        CoreScreenSession session = fixture.open(text);

        UiSceneFrame initial = fixture.frame(1L);
        assertEquals(1, textService.requests.size());
        assertEquals(100.0f, textService.requests.get(0).maxWidth());
        assertEquals(0L, drawTextCount(initial));

        TextLayout first = textService.complete(0, new Size(72.0f, 18.0f), 13.0f, 15.0f);
        assertEquals(0L, drawTextCount(fixture.frame(2L)));
        fixture.dispatcher.drain();

        UiSceneFrame published = fixture.frame(3L);
        DisplayCommand.DrawText firstDraw = onlyText(published);
        assertSame(first, firstDraw.text());
        assertEquals(13.0, session.layoutSnapshot().root()
                .baseline(TextBaseline.ALPHABETIC).orElseThrow());
        assertEquals(15.0, session.layoutSnapshot().root()
                .baseline(TextBaseline.IDEOGRAPHIC).orElseThrow());

        text.setText("next");
        UiSceneFrame pending = fixture.frame(4L);
        assertSame(first, onlyText(pending).text());
        assertEquals("next", textService.requests.get(1).text());

        TextLayout second = textService.complete(1, new Size(32.0f, 18.0f), 12.0f, 14.0f);
        fixture.dispatcher.drain();
        assertSame(second, onlyText(fixture.frame(5L)).text());

        text.setText("superseded");
        fixture.frame(6L);
        CompletableFuture<TextLayout> superseded = textService.futures.get(2);
        text.setText("latest");
        fixture.frame(7L);
        assertTrue(superseded.isCancelled());
        assertEquals("latest", textService.requests.get(3).text());

        fixture.runtime.stop();
    }

    @Test
    void textAutomaticallyRestartsLayoutForTheLatestLogicalWidth() {
        ControlledTextService textService = new ControlledTextService();
        Fixture fixture = new Fixture(textService);
        Text text = Text.builder("中文 English responsive", TextStyle.builder(16.0f).build()).build();
        fixture.open(text);

        fixture.frame(1L);
        assertEquals(100.0f, textService.requests.get(0).maxWidth());

        UiViewport narrow = new UiViewport(60.0f, 40.0f, 180, 120, 3.0f);
        fixture.frame(narrow, 2L);
        assertTrue(textService.futures.get(0).isCancelled());
        assertEquals(60.0f, textService.requests.get(1).maxWidth());

        textService.complete(1, new Size(60.0f, 32.0f), 12.0f, 14.0f);
        fixture.dispatcher.drain();
        UiSceneFrame narrowFrame = fixture.frame(narrow, 3L);
        assertEquals(60.0f, onlyText(narrowFrame).text().request().maxWidth());
        fixture.runtime.stop();
    }

    @Test
    void buttonUsesAncestorVisualStateAndActivatesThroughPointerAndKeyboard() {
        Fixture fixture = new Fixture(request -> CompletableFuture.failedFuture(
                new AssertionError("Button test must not request text")));
        AtomicInteger clicks = new AtomicInteger();
        AtomicReference<VisualState> state = new AtomicReference<>(VisualState.defaults());
        Button button = Button.builder(Spacer.builder().width(20.0f).height(10.0f).build())
                .style((theme, visualState) -> {
                    state.set(visualState);
                    return Style.defaults();
                })
                .onClick(clicks::incrementAndGet)
                .build();
        CoreScreenSession session = fixture.open(button);
        fixture.frame(1L);

        Point childPoint = new Point(45.0f, 20.0f);
        session.dispatch(pointer(PointerAction.MOVE, childPoint, Optional.empty(), Set.of()));
        fixture.frame(2L);
        assertTrue(state.get().hovered());

        session.dispatch(pointer(
                PointerAction.DOWN,
                childPoint,
                Optional.of(PointerButton.PRIMARY),
                Set.of(PointerButton.PRIMARY)));
        fixture.frame(3L);
        assertTrue(state.get().pressed());
        assertSame(button, session.focus().focused().orElseThrow());

        session.dispatch(pointer(
                PointerAction.UP,
                childPoint,
                Optional.of(PointerButton.PRIMARY),
                Set.of()));
        assertEquals(1, clicks.get());

        session.dispatch(new KeyEvent(Keys.ENTER, KeyAction.PRESS, Set.of(), 20L));
        assertEquals(2, clicks.get());

        session.dispatch(pointer(
                PointerAction.DOWN,
                childPoint,
                Optional.of(PointerButton.PRIMARY),
                Set.of(PointerButton.PRIMARY)));
        session.dispatch(pointer(
                PointerAction.UP,
                new Point(120.0f, 60.0f),
                Optional.of(PointerButton.PRIMARY),
                Set.of()));
        assertEquals(2, clicks.get());

        button.setEnabled(false);
        session.dispatch(new KeyEvent(Keys.SPACE, KeyAction.PRESS, Set.of(), 30L));
        assertEquals(2, clicks.get());
        fixture.runtime.stop();
    }

    @Test
    void scrollContainerClampsOffsetsClipsHitTestingAndSupportsWheelAndDrag() {
        Fixture fixture = new Fixture(request -> CompletableFuture.failedFuture(
                new AssertionError("Scroll test must not request text")));
        ScrollController controller = ScrollController.create(30.0);
        ScrollContainer scroll = ScrollContainer.builder(
                        Spacer.builder().width(20.0f).height(100.0f).build())
                .axis(Axis.VERTICAL)
                .controller(controller)
                .build();
        CoreScreenSession session = fixture.open(scroll);

        UiSceneFrame initial = fixture.frame(1L);
        assertEquals(60.0, controller.maximumOffset().orElseThrow());
        assertEquals(-30.0f, session.layoutSnapshot().root().children().get(0).position().y());
        assertTrue(initial.displayList().commands().stream()
                .anyMatch(DisplayCommand.IntersectScissor.class::isInstance));

        assertTrue(session.dispatch(new ScrollEvent(-0.0, -1.0, new Point(10.0f, 10.0f), Set.of(), 10L)));
        assertEquals(54.0, controller.offset());
        fixture.frame(2L);
        assertEquals(-54.0f, session.layoutSnapshot().root().children().get(0).position().y());

        assertFalse(session.dispatch(new ScrollEvent(0.0, -1.0, new Point(10.0f, 45.0f), Set.of(), 11L)));
        assertEquals(54.0, controller.offset());

        session.dispatch(pointer(
                PointerAction.DOWN,
                new Point(10.0f, 10.0f),
                Optional.of(PointerButton.PRIMARY),
                Set.of(PointerButton.PRIMARY)));
        session.dispatch(pointer(
                PointerAction.MOVE,
                new Point(10.0f, 0.0f),
                Optional.empty(),
                Set.of(PointerButton.PRIMARY)));
        assertEquals(60.0, controller.offset());
        session.dispatch(pointer(
                PointerAction.UP,
                new Point(10.0f, 0.0f),
                Optional.of(PointerButton.PRIMARY),
                Set.of()));

        fixture.runtime.stop();
        assertTrue(controller.maximumOffset().isEmpty());
        assertEquals(60.0, controller.offset());
    }

    @Test
    void scrollContainerAutomaticallyClampsOffsetAfterViewportResize() {
        Fixture fixture = new Fixture(request -> CompletableFuture.failedFuture(
                new AssertionError("Scroll resize test must not request text")));
        ScrollController controller = ScrollController.create(60.0);
        ScrollContainer scroll = ScrollContainer.builder(
                        Spacer.builder().width(20.0f).height(100.0f).build())
                .axis(Axis.VERTICAL)
                .controller(controller)
                .build();
        CoreScreenSession session = fixture.open(scroll);

        fixture.frame(1L);
        assertEquals(60.0, controller.maximumOffset().orElseThrow());
        assertEquals(-60.0f, session.layoutSnapshot().root().children().get(0).position().y());

        UiViewport taller = new UiViewport(100.0f, 120.0f, 300, 360, 3.0f);
        fixture.frame(taller, 2L);
        assertEquals(0.0, controller.maximumOffset().orElseThrow());
        assertEquals(0.0, controller.offset());
        assertEquals(0.0f, session.layoutSnapshot().root().children().get(0).position().y());
        fixture.runtime.stop();
    }

    @Test
    void textInputEditsUtf16PublishesCompositionAndConfinesItsController() throws InterruptedException {
        ControlledTextService textService = new ControlledTextService();
        Fixture fixture = new Fixture(textService);
        TextController controller = TextController.create("A😀B");
        AtomicReference<String> submitted = new AtomicReference<>();
        TextInput input = TextInput.builder(controller, TextStyle.builder(16.0f).build())
                .onSubmit(submitted::set)
                .build();
        CoreScreenSession session = fixture.open(input);

        fixture.frame(1L);
        textService.complete(0, new Size(40.0f, 16.0f), 12.0f, 14.0f);
        fixture.dispatcher.drain();
        assertEquals("A😀B", onlyText(fixture.frame(2L)).text().request().text());

        Point start = new Point(8.0f, 20.0f);
        assertTrue(session.dispatch(pointer(
                PointerAction.DOWN,
                start,
                Optional.of(PointerButton.PRIMARY),
                Set.of(PointerButton.PRIMARY))));
        fixture.dispatcher.drain();
        assertEquals(new TextSelection(0, 0), controller.selection());
        assertTrue(session.dispatch(pointer(
                PointerAction.UP,
                start,
                Optional.of(PointerButton.PRIMARY),
                Set.of())));
        fixture.dispatcher.drain();

        assertTrue(session.dispatch(new TextInputEvent("中", 20L)));
        assertEquals("中A😀B", controller.text());
        assertEquals(new TextSelection(1, 1), controller.selection());
        fixture.frame(3L);
        textService.complete(1, new Size(48.0f, 16.0f), 12.0f, 14.0f);
        fixture.dispatcher.drain();
        fixture.frame(4L);

        controller.setSelection(new TextSelection(4, 4));
        assertTrue(session.dispatch(new KeyEvent(Keys.BACKSPACE, KeyAction.PRESS, Set.of(), 30L)));
        assertEquals("中AB", controller.text());
        assertEquals(new TextSelection(2, 2), controller.selection());

        controller.setSelection(new TextSelection(3, 3));
        assertTrue(session.dispatch(new TextCompositionEvent(
                true,
                "中文",
                1,
                List.of("中", "文"),
                OptionalInt.of(1),
                40L)));
        assertEquals("中AB", controller.text());
        fixture.frame(5L);
        textService.complete(2, new Size(56.0f, 16.0f), 12.0f, 14.0f);
        fixture.dispatcher.drain();
        UiSceneFrame compositionFrame = fixture.frame(6L);
        assertEquals("中AB中文", onlyText(compositionFrame).text().request().text());

        assertTrue(session.dispatch(new TextInputEvent("中文", 50L)));
        assertEquals("中AB中文", controller.text());
        assertTrue(session.dispatch(new KeyEvent(Keys.ENTER, KeyAction.PRESS, Set.of(), 60L)));
        assertEquals("中AB中文", submitted.get());
        assertTrue(session.dispatch(new KeyEvent(
                Keys.LEFT,
                KeyAction.PRESS,
                Set.of(cn.fandmc.fandui.api.event.KeyModifier.SHIFT),
                70L)));
        assertEquals(new TextSelection(5, 4), controller.selection());

        AtomicReference<Throwable> offThreadFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                controller.setText("off-thread");
            } catch (Throwable failure) {
                offThreadFailure.set(failure);
            }
        });
        worker.start();
        worker.join();
        assertInstanceOf(IllegalStateException.class, offThreadFailure.get());

        fixture.runtime.stop();
        controller.setText("detached");
        assertEquals("detached", controller.text());
    }

    @Test
    void textInputKeepsTheEndCaretVisibleInANarrowViewport() {
        ControlledTextService textService = new ControlledTextService();
        Fixture fixture = new Fixture(textService);
        TextController controller = TextController.create("abcdefghijklmnopqrst");
        TextInput input = TextInput.builder(controller, TextStyle.builder(16.0f).build()).build();
        fixture.open(input);

        fixture.frame(1L);
        textService.complete(0, new Size(160.0f, 16.0f), 12.0f, 14.0f);
        fixture.dispatcher.drain();
        UiSceneFrame frame = fixture.frame(2L);

        assertTrue(frame.displayList().commands().stream()
                .filter(DisplayCommand.Translate.class::isInstance)
                .map(DisplayCommand.Translate.class::cast)
                .anyMatch(translation -> Math.abs(translation.x() + 69.0f) < 0.001f));

        UiViewport wider = new UiViewport(220.0f, 40.0f, 440, 80, 2.0f);
        UiSceneFrame widerFrame = fixture.frame(wider, 3L);
        assertTrue(widerFrame.displayList().commands().stream()
                .filter(DisplayCommand.Translate.class::isInstance)
                .map(DisplayCommand.Translate.class::cast)
                .anyMatch(translation -> Math.abs(translation.x() - 8.0f) < 0.001f));
        fixture.runtime.stop();
    }

    @Test
    void textInputClearsEmptyCompositionCommitsAndCancelsStaleHitTests() {
        ControlledTextService textService = new ControlledTextService();
        Fixture fixture = new Fixture(textService);
        TextController controller = TextController.create("base");
        TextInput input = TextInput.builder(controller, TextStyle.builder(16.0f).build()).build();
        CoreScreenSession session = fixture.open(input);

        fixture.frame(1L);
        textService.complete(0, new Size(32.0f, 16.0f), 12.0f, 14.0f);
        fixture.dispatcher.drain();
        fixture.frame(2L);

        Point end = new Point(40.0f, 20.0f);
        assertTrue(session.dispatch(pointer(
                PointerAction.DOWN,
                end,
                Optional.of(PointerButton.PRIMARY),
                Set.of(PointerButton.PRIMARY))));
        fixture.dispatcher.drain();
        assertTrue(session.dispatch(pointer(
                PointerAction.UP,
                end,
                Optional.of(PointerButton.PRIMARY),
                Set.of())));
        fixture.dispatcher.drain();

        assertTrue(session.dispatch(new TextCompositionEvent(
                true,
                "中文",
                2,
                List.of("中", "文"),
                OptionalInt.of(1),
                20L)));
        assertTrue(session.dispatch(new TextInputEvent("", 21L)));
        assertEquals("base", controller.text());

        textService.deferHitTests = true;
        assertTrue(session.dispatch(pointer(
                PointerAction.DOWN,
                end,
                Optional.of(PointerButton.PRIMARY),
                Set.of(PointerButton.PRIMARY))));
        CompletableFuture<TextPosition> staleHit = textService.hitFutures.get(0);
        controller.setText("changed");

        assertTrue(staleHit.isCancelled());
        assertEquals(new TextSelection(7, 7), controller.selection());
        fixture.runtime.stop();
    }

    @Test
    void textInputSupportsClipboardReadOnlyLengthFilterAndValidation() {
        Fixture fixture = new Fixture(request -> CompletableFuture.failedFuture(
                new AssertionError("Editing test does not need completed text layout")));
        TextController controller = TextController.create("hello");
        TextInput input = TextInput.builder(controller, 16.0f)
                .maxLength(8)
                .filter(value -> value.replaceAll("[^A-Za-z]", ""))
                .validator(value -> !value.contains("BAD"))
                .build();
        CoreScreenSession session = fixture.open(input);
        fixture.frame(1L);
        assertTrue(session.focus().request(input));

        controller.setSelection(new TextSelection(1, 4));
        assertTrue(session.dispatch(commandKey('c')));
        assertEquals("ell", fixture.clipboard.getText());

        assertTrue(session.dispatch(commandKey('x')));
        assertEquals("ho", controller.text());
        fixture.clipboard.setText("A1B2");
        assertTrue(session.dispatch(commandKey('v')));
        assertEquals("hABo", controller.text());

        fixture.clipboard.setText("BAD");
        assertTrue(session.dispatch(commandKey('v')));
        assertEquals("hABo", controller.text());
        fixture.clipboard.setText("LONGTEXT");
        assertTrue(session.dispatch(commandKey('v')));
        assertEquals(8, controller.text().codePointCount(0, controller.text().length()));

        TextController readOnlyController = TextController.create("locked");
        TextInput readOnly = TextInput.builder(readOnlyController, 16.0f).readOnly(true).build();
        session.close();
        CoreScreenSession readOnlySession = fixture.open(readOnly);
        fixture.frame(2L);
        assertTrue(readOnlySession.focus().request(readOnly));
        readOnlyController.selectAll();
        assertTrue(readOnlySession.dispatch(commandKey('x')));
        assertEquals("locked", fixture.clipboard.getText());
        assertEquals("locked", readOnlyController.text());
        assertTrue(readOnlySession.dispatch(new TextInputEvent("changed", 20L)));
        assertEquals("locked", readOnlyController.text());
        fixture.runtime.stop();
    }

    @Test
    void textInputLaysOutPlaceholderAndPasswordMaskWithoutChangingControllerText() {
        ControlledTextService textService = new ControlledTextService();
        Fixture fixture = new Fixture(textService);
        TextController controller = TextController.create();
        TextInput input = TextInput.builder(controller, 16.0f)
                .placeholder("Type here")
                .password(true)
                .build();
        fixture.open(input);

        fixture.frame(1L);
        assertEquals("Type here", textService.requests.get(0).text());
        controller.setText("A😀");
        fixture.frame(2L);
        assertEquals("\u2022\u2022\u2022", textService.requests.get(1).text());
        assertEquals("A😀", controller.text());
        fixture.runtime.stop();
    }

    private static KeyEvent commandKey(char value) {
        return new KeyEvent(
                Keys.letter(value),
                KeyAction.PRESS,
                Set.of(cn.fandmc.fandui.api.event.KeyModifier.CONTROL),
                10L);
    }

    private static long drawTextCount(UiSceneFrame frame) {
        return frame.displayList().commands().stream()
                .filter(DisplayCommand.DrawText.class::isInstance)
                .count();
    }

    private static DisplayCommand.DrawText onlyText(UiSceneFrame frame) {
        return frame.displayList().commands().stream()
                .filter(DisplayCommand.DrawText.class::isInstance)
                .map(DisplayCommand.DrawText.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static PointerEvent pointer(
            PointerAction action,
            Point position,
            Optional<PointerButton> changed,
            Set<PointerButton> buttons) {
        return new PointerEvent(
                action,
                position,
                new Point(0.0f, 0.0f),
                changed,
                buttons,
                action == PointerAction.DOWN ? 1 : 0,
                Set.of(),
                10L);
    }

    private static final class Fixture {
        private final TestDispatcher dispatcher = new TestDispatcher();
        private final ClipboardService clipboard = ClipboardService.inMemory();
        private final CoreUiRuntime runtime;
        private long clock;

        private Fixture(TextService textService) {
            runtime = new CoreUiRuntime(
                    dispatcher,
                    new ScreenHost() {
                        @Override
                        public void open(CoreScreenSession session) {
                        }

                        @Override
                        public void close(CoreScreenSession session) {
                        }
                    },
                    unusedResources(),
                    textService,
                    clipboard,
                    CursorHost.noOp(),
                    UiCapabilities.of(false, true),
                    () -> clock);
            runtime.markAvailable("test");
        }

        private CoreScreenSession open(cn.fandmc.fandui.api.component.UiComponent root) {
            return (CoreScreenSession) runtime.screens().open(UiScreen.builder("test", root).build());
        }

        private UiSceneFrame frame(long time) {
            return frame(VIEWPORT, time);
        }

        private UiSceneFrame frame(UiViewport viewport, long time) {
            clock = time;
            return runtime.renderFrames(viewport, time).get(0);
        }
    }

    private static final class TestDispatcher implements UiThreadDispatcher {
        private final Thread owner = Thread.currentThread();
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();

        @Override
        public boolean isUiThread() {
            return Thread.currentThread() == owner;
        }

        @Override
        public synchronized void execute(Runnable action) {
            queue.add(action);
        }

        private void drain() {
            while (true) {
                Runnable action;
                synchronized (this) {
                    action = queue.poll();
                }
                if (action == null) {
                    return;
                }
                action.run();
            }
        }
    }

    private static final class ControlledTextService implements TextService {
        private final List<TextRequest> requests = new ArrayList<>();
        private final List<CompletableFuture<TextLayout>> futures = new ArrayList<>();
        private final List<CompletableFuture<TextPosition>> hitFutures = new ArrayList<>();
        private boolean deferHitTests;

        @Override
        public CompletableFuture<TextLayout> layout(TextRequest request) {
            requests.add(request);
            CompletableFuture<TextLayout> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        }

        @Override
        public CompletableFuture<TextPosition> hitTest(TextLayout layout, Point position) {
            if (deferHitTests) {
                CompletableFuture<TextPosition> future = new CompletableFuture<>();
                hitFutures.add(future);
                return future;
            }
            String text = layout.request().text();
            int offset = Math.max(0, Math.min(text.length(), Math.round(position.x() / 8.0f)));
            if (offset > 0 && offset < text.length()
                    && Character.isHighSurrogate(text.charAt(offset - 1))
                    && Character.isLowSurrogate(text.charAt(offset))) {
                offset++;
            }
            return CompletableFuture.completedFuture(TextPosition.downstream(offset));
        }

        @Override
        public CompletableFuture<TextGeometry> geometry(
                TextLayout layout,
                TextPosition caret,
                List<TextRange> ranges) {
            float height = layout.size().height();
            List<TextRangeGeometry> result = ranges.stream()
                    .map(range -> new TextRangeGeometry(
                            range,
                            range.collapsed()
                                    ? List.of()
                                    : List.of(new Rect(
                                            range.startUtf16() * 8.0f,
                                            0.0f,
                                            (range.endUtf16() - range.startUtf16()) * 8.0f,
                                            height))))
                    .toList();
            return CompletableFuture.completedFuture(new TextGeometry(
                    new Rect(caret.offsetUtf16() * 8.0f, 0.0f, 0.0f, height),
                    result));
        }

        private TextLayout complete(
                int index,
                Size size,
                float alphabeticBaseline,
                float ideographicBaseline) {
            TextRequest request = requests.get(index);
            TextLayout layout = new StubTextLayout(
                    request,
                    size,
                    alphabeticBaseline,
                    ideographicBaseline,
                    List.of(new TextLine(
                            0,
                            request.text().length(),
                            size.width(),
                            size.height(),
                            alphabeticBaseline)));
            futures.get(index).complete(layout);
            return layout;
        }
    }

    private record StubTextLayout(
            TextRequest request,
            Size size,
            float alphabeticBaseline,
            float ideographicBaseline,
            List<TextLine> lines) implements TextLayout {
        private StubTextLayout {
            lines = List.copyOf(lines);
        }

        @Override
        public long resourceGeneration() {
            return 0L;
        }

        @Override
        public int unresolvedGlyphs() {
            return 0;
        }
    }

    private static ResourceService unusedResources() {
        return new ResourceService() {
            @Override
            public long generation() {
                return 0L;
            }

            @Override
            public ImageRef image(UiKey key) {
                throw new UnsupportedOperationException("image");
            }

            @Override
            public FontFamily font(UiKey key) {
                throw new UnsupportedOperationException("font");
            }

            @Override
            public ResourceRegistration registerImage(UiKey key, ResourceSource source) {
                throw new UnsupportedOperationException("registerImage");
            }

            @Override
            public ResourceRegistration registerFont(UiKey key, ResourceSource source) {
                throw new UnsupportedOperationException("registerFont");
            }

            @Override
            public long reload() {
                return 0L;
            }

            @Override
            public EventRegistration onReload(ResourceReloadListener listener) {
                throw new UnsupportedOperationException("onReload");
            }
        };
    }
}
