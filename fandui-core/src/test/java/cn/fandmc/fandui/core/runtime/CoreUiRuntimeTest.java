package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.UiCapabilities;
import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.UiRuntimeState;
import cn.fandmc.fandui.api.UiUnavailableException;
import cn.fandmc.fandui.api.animation.AnimationEndReason;
import cn.fandmc.fandui.api.animation.AnimationHandle;
import cn.fandmc.fandui.api.animation.AnimationSpec;
import cn.fandmc.fandui.api.component.ComponentContext;
import cn.fandmc.fandui.api.component.PaintScope;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.UiContainer;
import cn.fandmc.fandui.api.event.EventContext;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.hud.HudInputMode;
import cn.fandmc.fandui.api.input.CursorShape;
import cn.fandmc.fandui.api.hud.HudLayer;
import cn.fandmc.fandui.api.hud.HudRegistration;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceRegistration;
import cn.fandmc.fandui.api.resource.ResourceReloadListener;
import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.resource.ResourceSource;
import cn.fandmc.fandui.api.screen.UiScreen;
import cn.fandmc.fandui.api.session.SessionCloseReason;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.text.FontFamily;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextService;
import cn.fandmc.fandui.canvas.DisplayList;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreUiRuntimeTest {
    private static final UiViewport VIEWPORT = new UiViewport(100.0f, 80.0f, 200, 160, 2.0f);

    @Test
    void screenReplacementDetachesExactlyOnceAndAllowsReuse() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        TestLeaf firstRoot = new TestLeaf(UiKey.of("test", "first"), 100.0f, 80.0f);
        TestLeaf secondRoot = new TestLeaf(UiKey.of("test", "second"), 100.0f, 80.0f);
        CoreScreenSession first = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.builder("First", firstRoot).build());
        List<SessionCloseReason> firstReasons = new ArrayList<>();
        first.onClose((session, reason) -> firstReasons.add(reason));

        CoreScreenSession second = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.builder("Second", secondRoot).build());

        assertFalse(first.active());
        assertEquals(List.of(SessionCloseReason.REPLACED), firstReasons);
        assertEquals(1, firstRoot.attached);
        assertEquals(1, firstRoot.detached);
        assertSame(second, fixture.runtime.screens().current().orElseThrow());
        assertEquals(2, fixture.screenHost.opened.size());
        assertTrue(fixture.screenHost.closed.isEmpty());

        first.close();
        assertEquals(List.of(SessionCloseReason.REPLACED), firstReasons);
        second.hostClosed(SessionCloseReason.HOST);
        assertFalse(second.active());
        assertTrue(fixture.runtime.screens().current().isEmpty());

        CoreScreenSession reopened = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.builder("First again", firstRoot).build());
        assertTrue(reopened.active());
        assertEquals(2, firstRoot.attached);
        reopened.hostClosed(SessionCloseReason.ESCAPE);
        assertEquals(List.of(reopened), fixture.screenHost.closed);
    }

    @Test
    void hudUsesStableOrderAndOldCrossThreadHandleCannotRemoveReplacement() throws Exception {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        HudRegistration b = fixture.runtime.hud().mount(layer("b", 0, new TestLeaf(null, 1.0f, 1.0f)));
        HudRegistration a = fixture.runtime.hud().mount(layer("a", 0, new TestLeaf(null, 1.0f, 1.0f)));
        HudRegistration c = fixture.runtime.hud().mount(layer("c", -1, new TestLeaf(null, 1.0f, 1.0f)));
        CoreHudService hud = (CoreHudService) fixture.runtime.hud();
        List<CoreHudRegistration> firstSnapshot = hud.activeRegistrations();

        assertEquals(List.of("c", "a", "b"), fixture.runtime.hud().mounted().stream()
                .map(registration -> registration.key().value())
                .toList());
        assertSame(firstSnapshot, hud.activeRegistrations());
        assertThrows(IllegalStateException.class, () -> fixture.runtime.hud().mount(
                layer("a", 5, new TestLeaf(null, 1.0f, 1.0f))));

        Thread closer = new Thread(b::close, "hud-close-test");
        closer.start();
        closer.join();
        assertFalse(b.active());
        HudRegistration replacement = fixture.runtime.hud().mount(
                layer("b", 3, new TestLeaf(null, 1.0f, 1.0f)));
        fixture.dispatcher.drain();

        assertSame(replacement, fixture.runtime.hud().find(UiKey.of("test", "b")).orElseThrow());
        assertNotSame(firstSnapshot, hud.activeRegistrations());
        assertEquals(SessionCloseReason.API, b.closeReason().orElseThrow());
        assertEquals(List.of("c", "a", "b"), fixture.runtime.hud().mounted().stream()
                .map(registration -> registration.key().value())
                .toList());
        a.close();
        c.close();
        replacement.close();
    }

    @Test
    void frameCacheIsAtomicAcrossInvalidationResizeAndPaintFailure() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        TestLeaf root = new TestLeaf(null, 100.0f, 80.0f);
        fixture.runtime.screens().open(UiScreen.builder("Frame", root).build());

        UiSceneFrame first = fixture.runtime.renderFrames(VIEWPORT, 1L).get(0);
        UiSceneFrame cached = fixture.runtime.renderFrames(VIEWPORT, 2L).get(0);
        assertSame(first.displayList(), cached.displayList());
        assertEquals(1, root.painted);

        root.invalidateFromTest();
        UiSceneFrame repainted = fixture.runtime.renderFrames(VIEWPORT, 3L).get(0);
        assertNotSame(first.displayList(), repainted.displayList());
        assertEquals(2, root.painted);

        UiViewport resized = new UiViewport(120.0f, 90.0f, 240, 180, 2.0f);
        UiSceneFrame resizedFrame = fixture.runtime.renderFrames(resized, 4L).get(0);
        assertEquals(resized, resizedFrame.viewport());
        assertEquals(3, root.painted);

        DisplayList lastComplete = resizedFrame.displayList();
        root.failPaint = true;
        root.invalidateFromTest();
        List<UiSceneFrame> failedFrame = fixture.runtime.renderFrames(resized, 5L);
        assertEquals(1, failedFrame.size());
        assertSame(lastComplete, failedFrame.get(0).displayList());
        assertTrue(fixture.runtime.screens().current().isEmpty());
        assertEquals(SessionCloseReason.FAILED, failedFrame.get(0).session().closeReason().orElseThrow());
    }

    @Test
    void viewportChangesSynchronizeAutomaticallyAndOnlyLogicalSizeRelayouts() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        TestLeaf root = new TestLeaf(null, 1.0f, 1.0f);
        CoreScreenSession session = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.builder("Responsive", root).build());

        UiSceneFrame initial = fixture.runtime.renderFrames(VIEWPORT, 1L).get(0);
        assertEquals(new Size(100.0f, 80.0f), session.layoutSnapshot().root().size());
        assertEquals(1, root.measured);
        assertEquals(1, root.painted);

        UiViewport framebufferResize = new UiViewport(100.0f, 80.0f, 300, 240, 2.0f);
        UiSceneFrame framebufferFrame = fixture.runtime.renderFrames(framebufferResize, 2L).get(0);
        assertEquals(framebufferResize, session.viewport());
        assertSame(initial.displayList(), framebufferFrame.displayList());
        assertEquals(1, root.measured);
        assertEquals(1, root.painted);

        UiViewport guiScaleChange = new UiViewport(100.0f, 80.0f, 300, 240, 3.0f);
        UiSceneFrame guiScaleFrame = fixture.runtime.renderFrames(guiScaleChange, 3L).get(0);
        assertEquals(guiScaleChange, session.viewport());
        assertSame(initial.displayList(), guiScaleFrame.displayList());
        assertEquals(1, root.measured);
        assertEquals(1, root.painted);

        UiViewport logicalResize = new UiViewport(160.0f, 90.0f, 480, 270, 3.0f);
        UiSceneFrame logicalFrame = fixture.runtime.renderFrames(logicalResize, 4L).get(0);
        assertEquals(logicalResize, session.viewport());
        assertEquals(new Size(160.0f, 90.0f), session.layoutSnapshot().root().size());
        assertNotSame(initial.displayList(), logicalFrame.displayList());
        assertEquals(2, root.measured);
        assertEquals(2, root.painted);

        UiViewport rapidResize = new UiViewport(220.0f, 120.0f, 660, 360, 3.0f);
        UiSceneFrame finalFrame = fixture.runtime.renderFrames(rapidResize, 5L).get(0);
        assertEquals(rapidResize, session.viewport());
        assertEquals(new Size(220.0f, 120.0f), session.layoutSnapshot().root().size());
        assertEquals(3, root.measured);
        assertEquals(3, root.painted);
        assertNotSame(logicalFrame.displayList(), finalFrame.displayList());
    }

    @Test
    void failedResizeNeverPairsThePreviousDisplayListWithTheNewViewport() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        TestLeaf root = new TestLeaf(null, 1.0f, 1.0f);
        CoreScreenSession session = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.builder("Resize failure", root).build());
        fixture.runtime.renderFrames(VIEWPORT, 1L);

        root.failPaint = true;
        UiViewport resized = new UiViewport(120.0f, 90.0f, 240, 180, 2.0f);
        List<UiSceneFrame> failed = fixture.runtime.renderFrames(resized, 2L);

        assertTrue(failed.isEmpty());
        assertEquals(VIEWPORT, session.viewport());
        assertEquals(new Size(100.0f, 80.0f), session.layoutSnapshot().root().size());
        assertEquals(SessionCloseReason.FAILED, session.closeReason().orElseThrow());
        assertTrue(fixture.runtime.screens().current().isEmpty());
    }

    @Test
    void resourceGenerationChangeInvalidatesTheCachedLayoutAndDisplayList() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        TestLeaf root = new TestLeaf(null, 100.0f, 80.0f);
        fixture.runtime.screens().open(UiScreen.of("Resources", root));

        UiSceneFrame first = fixture.runtime.renderFrames(VIEWPORT, 1L).get(0);
        assertSame(first.displayList(), fixture.runtime.renderFrames(VIEWPORT, 2L).get(0).displayList());

        fixture.resources.generation.incrementAndGet();
        UiSceneFrame reloaded = fixture.runtime.renderFrames(VIEWPORT, 3L).get(0);

        assertNotSame(first.displayList(), reloaded.displayList());
        assertEquals(2, root.painted);
    }

    @Test
    void eventRoutingExpiresContextsAndMaintainsFocusAndPointerCapture() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        TestContainer root = new TestContainer(null);
        TestLeaf child = new TestLeaf(UiKey.of("test", "child"), 20.0f, 20.0f);
        child.setFocusable(true);
        root.add(child);
        CoreScreenSession session = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.builder("Events", root).build());
        fixture.runtime.renderFrames(VIEWPORT, 1L);

        List<String> order = new ArrayList<>();
        AtomicReference<EventContext> expired = new AtomicReference<>();
        root.on(PointerEvent.class, EventRoute.CAPTURE, (event, context) -> {
            if (event.action() == PointerAction.MOVE) {
                order.add("root-capture");
                expired.set(context);
            }
        });
        child.on(PointerEvent.class, EventRoute.CAPTURE, (event, context) -> {
            if (event.action() == PointerAction.MOVE) {
                order.add("child-capture");
            }
            if (event.action() == PointerAction.MOVE
                    && event.scenePosition().equals(new Point(15.0f, 15.0f))) {
                assertEquals(new Point(5.0f, 5.0f), context.sceneToLocal(event.scenePosition()).orElseThrow());
            }
        });
        child.on(PointerEvent.class, EventRoute.BUBBLE, (event, context) -> {
            if (event.action() == PointerAction.MOVE) {
                order.add("child-bubble");
            }
        });
        root.on(PointerEvent.class, EventRoute.BUBBLE, (event, context) -> {
            if (event.action() == PointerAction.MOVE) {
                order.add("root-bubble");
            }
        });

        assertFalse(session.dispatch(pointer(PointerAction.MOVE, 15.0f, 15.0f)));
        assertEquals(List.of("root-capture", "child-capture", "child-bubble", "root-bubble"), order);
        assertThrows(IllegalStateException.class, () -> expired.get().phase());

        EventRegistration capture = child.on(PointerEvent.class, EventRoute.BUBBLE, (event, context) -> {
            if (event.action() == PointerAction.DOWN) {
                context.requestFocus();
                context.capturePointer();
                context.consume();
            }
        });
        assertTrue(session.dispatch(pointer(PointerAction.DOWN, 15.0f, 15.0f)));
        assertSame(child, session.focus().focused().orElseThrow());
        order.clear();
        session.dispatch(pointer(PointerAction.MOVE, 90.0f, 70.0f));
        assertTrue(order.contains("child-bubble"));
        session.dispatch(pointer(PointerAction.UP, 90.0f, 70.0f));

        AtomicLong cancellations = new AtomicLong();
        child.on(PointerEvent.class, EventRoute.BUBBLE, (event, context) -> {
            if (event.action() == PointerAction.CANCEL) {
                cancellations.incrementAndGet();
            }
        });
        session.dispatch(pointer(PointerAction.DOWN, 15.0f, 15.0f));
        root.remove(child);
        assertEquals(1L, cancellations.get());
        assertTrue(session.focus().focused().isEmpty());
        capture.close();
    }

    @Test
    void focusClearsImmediatelyWhenComponentOrAncestorBecomesIneligible() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        TestContainer root = new TestContainer(null);
        TestLeaf child = new TestLeaf(UiKey.of("test", "focus-invariant"), 20.0f, 20.0f);
        child.setFocusable(true);
        root.add(child);
        CoreScreenSession session = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.of("Focus invariant", root));
        fixture.runtime.renderFrames(VIEWPORT, 1L);

        assertTrue(session.focus().request(child));
        child.setEnabled(false);
        assertTrue(session.focus().focused().isEmpty());

        child.setEnabled(true);
        assertTrue(session.focus().request(child));
        root.setVisible(false);
        assertTrue(session.focus().focused().isEmpty());
    }

    @Test
    void pointerMoveDerivesEnterLeaveAndUpdatesThePlatformCursor() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        TestContainer root = new TestContainer(null);
        TestLeaf first = new TestLeaf(null, 20.0f, 20.0f);
        TestLeaf second = new TestLeaf(null, 20.0f, 20.0f);
        first.setCursor(CursorShape.TEXT);
        second.setCursor(CursorShape.POINTER);
        root.add(first);
        root.add(second);
        List<String> transitions = new ArrayList<>();
        first.on(PointerEvent.class, EventRoute.BUBBLE,
                (event, context) -> transitions.add("first:" + event.action()));
        second.on(PointerEvent.class, EventRoute.BUBBLE,
                (event, context) -> transitions.add("second:" + event.action()));
        CoreScreenSession session = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.of("Pointer transitions", root));
        fixture.runtime.renderFrames(VIEWPORT, 1L);

        session.dispatch(pointer(PointerAction.MOVE, 15.0f, 15.0f));
        session.dispatch(pointer(PointerAction.MOVE, 45.0f, 15.0f));

        assertEquals(List.of(
                "first:ENTER", "first:MOVE", "first:LEAVE",
                "second:ENTER", "second:MOVE"), transitions);
        assertEquals(List.of(CursorShape.TEXT, CursorShape.POINTER), fixture.cursorHost.changes);
    }

    @Test
    void hudInputIsClickThroughByDefaultAndExplicitWhenInteractive() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        AtomicLong received = new AtomicLong();
        TestLeaf passiveRoot = new TestLeaf(null, 100.0f, 80.0f);
        passiveRoot.on(PointerEvent.class, EventRoute.BUBBLE, (event, context) -> received.incrementAndGet());
        HudRegistration passive = fixture.runtime.hud().mount(HudLayer.builder(
                UiKey.of("test", "passive"), passiveRoot).build());
        fixture.runtime.renderFrames(VIEWPORT, 1L);
        assertFalse(passive.dispatch(pointer(PointerAction.DOWN, 5.0f, 5.0f)));
        assertEquals(0L, received.get());

        passive.close();
        TestLeaf activeRoot = new TestLeaf(null, 100.0f, 80.0f);
        activeRoot.on(PointerEvent.class, EventRoute.BUBBLE, (event, context) -> {
            received.incrementAndGet();
            context.consume();
        });
        HudRegistration active = fixture.runtime.hud().mount(HudLayer.builder(
                        UiKey.of("test", "active"), activeRoot)
                .inputMode(HudInputMode.INTERACTIVE)
                .build());
        fixture.runtime.renderFrames(VIEWPORT, 2L);
        assertTrue(active.dispatch(pointer(PointerAction.DOWN, 5.0f, 5.0f)));
        assertEquals(1L, received.get());
    }

    @Test
    void animationUsesMonotonicFramesAndSessionScopedCompletion() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        CoreScreenSession session = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.builder("Animation", new TestLeaf(null, 100.0f, 80.0f)).build());
        List<Double> progress = new ArrayList<>();
        AnimationHandle animation = session.animations().start(
                AnimationSpec.duration(Duration.ofNanos(100)).iterations(2).alternate(true).build(),
                progress::add);

        fixture.clock.set(50L);
        fixture.runtime.renderFrames(VIEWPORT, 50L);
        fixture.clock.set(100L);
        fixture.runtime.renderFrames(VIEWPORT, 100L);
        fixture.clock.set(150L);
        fixture.runtime.renderFrames(VIEWPORT, 150L);
        fixture.clock.set(200L);
        fixture.runtime.renderFrames(VIEWPORT, 200L);

        assertEquals(List.of(0.0, 0.5, 1.0, 0.5, 1.0), progress);
        assertFalse(animation.active());
        assertEquals(AnimationEndReason.COMPLETED, animation.completion().join());

        AnimationHandle pending = session.animations().start(
                AnimationSpec.duration(Duration.ofSeconds(1)).build(), value -> { });
        session.close();
        assertEquals(AnimationEndReason.SESSION_CLOSED, pending.completion().join());
    }

    @Test
    void availabilityAndExecutionRespectUiThreadAndShutdown() throws Exception {
        Fixture fixture = new Fixture();
        assertThrows(UiUnavailableException.class, () -> fixture.runtime.screens().open(
                UiScreen.builder("Unavailable", new TestLeaf(null, 1.0f, 1.0f)).build()));
        fixture.runtime.markAvailable("ready");

        AtomicLong executions = new AtomicLong();
        AtomicReference<CompletableFuture<Void>> future = new AtomicReference<>();
        Thread submitter = new Thread(
                () -> future.set(fixture.runtime.execute(executions::incrementAndGet)),
                "runtime-submit-test");
        submitter.start();
        submitter.join();
        assertFalse(future.get().isDone());
        fixture.dispatcher.drain();
        future.get().join();
        assertEquals(1L, executions.get());

        fixture.runtime.stop();
        assertEquals(UiRuntimeState.STOPPED, fixture.runtime.availability().state());
        assertTrue(fixture.runtime.execute(() -> { }).isCompletedExceptionally());
    }

    @Test
    void rendererLossClosesActiveScreenAndHud() {
        Fixture fixture = new Fixture();
        fixture.runtime.markAvailable("ready");
        CoreScreenSession screen = (CoreScreenSession) fixture.runtime.screens().open(
                UiScreen.builder("Screen", new TestLeaf(null, 1.0f, 1.0f)).build());
        HudRegistration hud = fixture.runtime.hud().mount(
                layer("hud", 0, new TestLeaf(null, 1.0f, 1.0f)));

        fixture.runtime.markRendererUnavailable("backend changed");

        assertEquals(UiRuntimeState.RENDERER_UNAVAILABLE, fixture.runtime.availability().state());
        assertFalse(screen.active());
        assertEquals(SessionCloseReason.FAILED, screen.closeReason().orElseThrow());
        assertFalse(hud.active());
        assertEquals(SessionCloseReason.FAILED, hud.closeReason().orElseThrow());
        assertEquals(List.of(screen), fixture.screenHost.closed);
    }

    @Test
    void closeInsideLifecycleAndEventCallbacksDefersDetachmentUntilCallbackExit() {
        Fixture lifecycleFixture = new Fixture();
        lifecycleFixture.runtime.markAvailable("ready");
        TestLeaf closeOnAttach = new TestLeaf(null, 100.0f, 80.0f) {
            @Override
            public void attached(ComponentContext context) {
                super.attached(context);
                context.session().close();
                assertEquals(0, detachedCount());
            }
        };
        CoreScreenSession closedDuringAttach = (CoreScreenSession) lifecycleFixture.runtime.screens().open(
                UiScreen.builder("Close on attach", closeOnAttach).build());
        assertFalse(closedDuringAttach.active());
        assertEquals(0, closeOnAttach.detached);
        assertTrue(lifecycleFixture.screenHost.opened.isEmpty());
        lifecycleFixture.dispatcher.drain();
        assertEquals(1, closeOnAttach.detached);

        Fixture eventFixture = new Fixture();
        eventFixture.runtime.markAvailable("ready");
        TestLeaf eventRoot = new TestLeaf(null, 100.0f, 80.0f);
        CoreScreenSession eventSession = (CoreScreenSession) eventFixture.runtime.screens().open(
                UiScreen.builder("Close on event", eventRoot).build());
        eventFixture.runtime.renderFrames(VIEWPORT, 1L);
        eventRoot.on(PointerEvent.class, EventRoute.BUBBLE, (event, context) -> {
            eventSession.close();
            assertEquals(0, eventRoot.detached);
        });
        eventSession.dispatch(pointer(PointerAction.DOWN, 1.0f, 1.0f));
        assertFalse(eventSession.active());
        assertEquals(0, eventRoot.detached);
        eventFixture.dispatcher.drain();
        assertEquals(1, eventRoot.detached);
        assertEquals(1, eventFixture.screenHost.closed.size());
    }

    private static HudLayer layer(String value, int order, UiComponent root) {
        return HudLayer.builder(UiKey.of("test", value), root).order(order).build();
    }

    private static PointerEvent pointer(PointerAction action, float x, float y) {
        Optional<PointerButton> changed = action == PointerAction.DOWN || action == PointerAction.UP
                ? Optional.of(PointerButton.PRIMARY)
                : Optional.empty();
        Set<PointerButton> buttons = action == PointerAction.DOWN || action == PointerAction.MOVE
                ? Set.of(PointerButton.PRIMARY)
                : Set.of();
        return new PointerEvent(
                action,
                new Point(x, y),
                new Point(0.0f, 0.0f),
                changed,
                buttons,
                action == PointerAction.DOWN ? 1 : 0,
                Set.of(),
                10L);
    }

    private static final class Fixture {
        private final TestDispatcher dispatcher = new TestDispatcher();
        private final TestScreenHost screenHost = new TestScreenHost();
        private final AtomicLong clock = new AtomicLong();
        private final MutableResourceService resources = new MutableResourceService();
        private final RecordingCursorHost cursorHost = new RecordingCursorHost();
        private final CoreUiRuntime runtime = new CoreUiRuntime(
                dispatcher,
                screenHost,
                resources,
                request -> CompletableFuture.failedFuture(new UnsupportedOperationException("text")),
                cn.fandmc.fandui.api.input.ClipboardService.inMemory(),
                cursorHost,
                UiCapabilities.of(false, true),
                clock::get);
    }

    private static final class RecordingCursorHost implements CursorHost {
        private final List<CursorShape> changes = new ArrayList<>();

        @Override
        public void setCursor(CursorShape shape) {
            changes.add(shape);
        }

        @Override
        public void close() {
        }
    }

    private static final class TestDispatcher implements UiThreadDispatcher {
        private final Thread owner = Thread.currentThread();
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        @Override
        public boolean isUiThread() {
            return Thread.currentThread() == owner;
        }

        @Override
        public synchronized void execute(Runnable action) {
            queued.add(action);
        }

        private void drain() {
            if (!isUiThread()) {
                throw new IllegalStateException("Test queue must be drained on its owner thread");
            }
            while (true) {
                Runnable action;
                synchronized (this) {
                    action = queued.poll();
                }
                if (action == null) {
                    return;
                }
                action.run();
            }
        }
    }

    private static final class TestScreenHost implements ScreenHost {
        private final List<CoreScreenSession> opened = new ArrayList<>();
        private final List<CoreScreenSession> closed = new ArrayList<>();

        @Override
        public void open(CoreScreenSession session) {
            opened.add(session);
        }

        @Override
        public void close(CoreScreenSession session) {
            closed.add(session);
        }
    }

    private static class TestLeaf extends UiComponent {
        private final Size desired;
        private int attached;
        private int detached;
        private int measured;
        private int painted;
        private boolean failPaint;

        private TestLeaf(UiKey key, float width, float height) {
            super(key);
            this.desired = new Size(width, height);
        }

        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            measured++;
            Size size = constraints.constrain(desired);
            return scope.layout(size.width(), size.height(), placements -> { });
        }

        @Override
        public void paint(PaintScope scope) {
            painted++;
            if (failPaint) {
                throw new IllegalStateException("paint failure");
            }
        }

        @Override
        public void attached(ComponentContext context) {
            attached++;
        }

        @Override
        public void detached(ComponentContext context) {
            detached++;
        }

        private void invalidateFromTest() {
            invalidatePaint();
        }

        final int detachedCount() {
            return detached;
        }
    }

    private static final class TestContainer extends UiContainer {
        private TestContainer(UiKey key) {
            super(key);
        }

        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            List<Placeable> measured = new ArrayList<>();
            Constraints childConstraints = Constraints.loose(
                    new Size(constraints.maxWidth(), constraints.maxHeight()));
            for (UiComponent child : children()) {
                measured.add(scope.measure(child, childConstraints));
            }
            Size size = constraints.constrain(new Size(constraints.maxWidth(), constraints.maxHeight()));
            return scope.layout(size.width(), size.height(), placements -> {
                for (int index = 0; index < measured.size(); index++) {
                    placements.place(measured.get(index), 10.0f + index * 30.0f, 10.0f);
                }
            });
        }
    }

    private static final class MutableResourceService implements ResourceService {
        private final AtomicLong generation = new AtomicLong();

        @Override
        public long generation() {
            return generation.get();
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
            return generation.incrementAndGet();
        }

        @Override
        public EventRegistration onReload(ResourceReloadListener listener) {
            throw new UnsupportedOperationException("onReload");
        }
    }
}
