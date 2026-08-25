package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.animation.AnimationEndReason;
import cn.fandmc.fandui.api.animation.AnimationFrameListener;
import cn.fandmc.fandui.api.animation.AnimationHandle;
import cn.fandmc.fandui.api.animation.AnimationManager;
import cn.fandmc.fandui.api.animation.AnimationSpec;
import cn.fandmc.fandui.api.component.ComponentContext;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.focus.FocusManager;
import cn.fandmc.fandui.api.input.ClipboardService;
import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.session.SessionCloseReason;
import cn.fandmc.fandui.api.session.UiSession;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.text.TextService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetargetableScalarTransitionTest {
    private static final Duration RESPONSE = Duration.ofMillis(100);

    @Test
    void continuousRetargetingUsesOneDriverAndFollowsTheLatestTarget() {
        TestContext context = new TestContext();
        RetargetableScalarTransition transition = new RetargetableScalarTransition(
                0.0, RESPONSE, ignored -> { });
        transition.attach(context);

        double previous = transition.value();
        for (int frame = 1; frame <= 12; frame++) {
            double target = frame / 12.0;
            transition.setTarget(target);
            assertEquals(target, frame / 12.0);
            context.animations.frame(frame / 16.0);
            double sampled = transition.sample(frame * 16_000_000L);
            assertTrue(sampled > previous, "visual value must advance on every drag frame");
            assertTrue(sampled < target, "visual value must keep smoothing instead of snapping");
            previous = sampled;
        }
        assertEquals(1, context.animations.starts);
        assertEquals(1, context.animations.activeCount());

        settle(transition, context.animations, 12);
        assertEquals(1.0, transition.value());
        assertEquals(0, context.animations.activeCount());
    }

    @Test
    void aSettledTransitionCanStartAgainAndDetachCancelsIt() {
        TestContext context = new TestContext();
        RetargetableScalarTransition transition = new RetargetableScalarTransition(
                0.0, RESPONSE, ignored -> { });
        transition.attach(context);
        transition.setTarget(100.0);
        settle(transition, context.animations, 0);
        assertEquals(100.0, transition.value());
        assertEquals(0, context.animations.activeCount());

        transition.setTarget(20.0);
        assertEquals(2, context.animations.starts);
        assertEquals(1, context.animations.activeCount());
        transition.setTarget(30.0);
        transition.setTarget(40.0);
        assertEquals(2, context.animations.starts);

        transition.detach(context);
        assertEquals(0, context.animations.activeCount());
        assertEquals(40.0, transition.value());
    }

    @Test
    void snapPublishesImmediatelyAndCancelsTheActiveSegment() {
        TestContext context = new TestContext();
        RetargetableScalarTransition transition = new RetargetableScalarTransition(
                0.0, RESPONSE, ignored -> { });
        transition.attach(context);
        transition.setTarget(100.0);
        context.animations.frame(0.25);
        transition.sample(16_000_000L);

        transition.snapTo(60.0);
        assertEquals(60.0, transition.value());
        assertEquals(0, context.animations.activeCount());
        assertEquals(1, context.animations.starts);
    }

    @Test
    void aFreshTargetCannotSettleOnTheSameFrameAndRestartDuringDrag() {
        TestContext context = new TestContext();
        RetargetableScalarTransition transition = new RetargetableScalarTransition(
                0.0, Duration.ofMillis(10), ignored -> { });
        transition.attach(context);

        for (int frame = 1; frame <= 20; frame++) {
            transition.setTarget(frame / 20.0);
            context.animations.frame((frame % 15) / 16.0);
            transition.sample(frame * 16_000_000L);
            assertEquals(1, context.animations.starts,
                    "continuous drag must not create a new animation segment");
            assertEquals(1, context.animations.activeCount());
        }

        context.animations.frame(0.5);
        transition.sample(21L * 16_000_000L);
        assertEquals(1.0, transition.value());
        assertEquals(0, context.animations.activeCount());
    }

    private static void settle(
            RetargetableScalarTransition transition,
            TestAnimationManager animations,
            int completedFrames) {
        int frame = completedFrames;
        while (animations.activeCount() > 0 && frame < completedFrames + 32) {
            frame++;
            animations.frame((frame % 15) / 16.0);
            transition.sample(frame * 16_000_000L);
        }
        assertFalse(animations.activeCount() > 0, "transition did not settle");
    }

    private static final class TestContext implements ComponentContext {
        private final TestAnimationManager animations = new TestAnimationManager();
        private final UiSession session = new TestSession(animations);

        @Override
        public UiSession session() {
            return session;
        }

        @Override
        public Theme theme() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceService resources() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextService text() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClipboardService clipboard() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> execute(Runnable action) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestSession implements UiSession {
        private final AnimationManager animations;

        private TestSession(AnimationManager animations) {
            this.animations = animations;
        }

        @Override
        public UiComponent root() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public UiViewport viewport() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FocusManager focus() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Theme theme() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnimationManager animations() {
            return animations;
        }

        @Override
        public Optional<UiComponent> find(UiKey key) {
            return Optional.empty();
        }

        @Override
        public void invalidate() {
        }

        @Override
        public Optional<SessionCloseReason> closeReason() {
            return Optional.empty();
        }

        @Override
        public EventRegistration onClose(cn.fandmc.fandui.api.session.SessionCloseListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }

    private static final class TestAnimationManager implements AnimationManager {
        private FakeHandle current;
        private int starts;

        @Override
        public AnimationHandle start(AnimationSpec spec, AnimationFrameListener listener) {
            assertTrue(spec.infinite(), "retargetable transition requires a persistent driver");
            starts++;
            current = new FakeHandle(listener);
            listener.frame(0.0);
            return current;
        }

        private void frame(double progress) {
            FakeHandle active = current;
            if (active == null || !active.active()) {
                throw new IllegalStateException("No active animation");
            }
            active.listener.frame(progress);
        }

        private int activeCount() {
            return current != null && current.active() ? 1 : 0;
        }
    }

    private static final class FakeHandle implements AnimationHandle {
        private final AnimationFrameListener listener;
        private final CompletableFuture<AnimationEndReason> completion = new CompletableFuture<>();
        private boolean active = true;

        private FakeHandle(AnimationFrameListener listener) {
            this.listener = listener;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public CompletableFuture<AnimationEndReason> completion() {
            return completion.copy();
        }

        @Override
        public void close() {
            finish(AnimationEndReason.CANCELLED);
        }

        private void finish(AnimationEndReason reason) {
            if (active) {
                active = false;
                completion.complete(reason);
            }
        }
    }
}
