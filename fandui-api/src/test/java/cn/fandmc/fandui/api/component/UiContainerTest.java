package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.internal.event.EventListeners;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiContainerTest {
    @Test
    void ownsChildrenAndReturnsImmutableSnapshots() {
        TestContainer root = new TestContainer();
        TestComponent child = new TestComponent();

        root.add(child);
        List<UiComponent> snapshot = root.children();
        root.remove(child);

        assertEquals(List.of(child), snapshot);
        assertTrue(child.parent().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    @Test
    void rejectsDuplicateParentsAndCycles() {
        TestContainer root = new TestContainer();
        TestContainer nested = new TestContainer();
        TestContainer other = new TestContainer();
        root.add(nested);

        assertThrows(IllegalStateException.class, () -> other.add(nested));
        assertThrows(IllegalArgumentException.class, () -> nested.add(root));
        assertThrows(IllegalArgumentException.class, () -> root.add(root));
    }

    @Test
    void replacesChildrenAtomicallyAndKeepsExistingSnapshotsStable() {
        TestContainer root = new TestContainer();
        TestComponent previous = new TestComponent();
        TestComponent replacement = new TestComponent();
        root.add(previous);
        List<UiComponent> snapshot = root.children();

        assertSame(previous, root.replace(0, replacement));

        assertEquals(List.of(previous), snapshot);
        assertEquals(List.of(replacement), root.children());
        assertTrue(previous.parent().isEmpty());
        assertSame(root, replacement.parent().orElseThrow());
    }

    @Test
    void requiredSingleChildCannotBeRemovedAndFailedReplacementKeepsIt() {
        TestComponent original = new TestComponent();
        Box box = Box.builder(original).alignment(Alignment.CENTER).build();
        TestContainer owner = new TestContainer();
        TestComponent owned = new TestComponent();
        owner.add(owned);

        assertThrows(IllegalStateException.class, () -> box.remove(original));
        assertThrows(IllegalStateException.class, () -> box.remove(0));
        IllegalStateException clearFailure = assertThrows(IllegalStateException.class, box::clear);
        assertTrue(clearFailure.getMessage().contains("setChild"));
        assertThrows(IllegalStateException.class, () -> box.add(new TestComponent()));
        assertThrows(IllegalStateException.class, () -> box.setChild(owned));

        assertSame(original, box.child());
        assertSame(box, original.parent().orElseThrow());
        assertSame(owner, owned.parent().orElseThrow());
    }

    @Test
    void requiredSingleChildUsesAtomicSetChildForDynamicContent() {
        TestComponent original = new TestComponent();
        TestComponent replacement = new TestComponent();
        Box box = Box.of(original);

        box.setChild(replacement);

        assertSame(replacement, box.child());
        assertTrue(original.parent().isEmpty());
        assertSame(box, replacement.parent().orElseThrow());
    }

    @Test
    void serializesMutationsOnAnUnattachedTree() throws Exception {
        TestContainer root = new TestContainer();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> addChildren(root));
            var second = executor.submit(() -> addChildren(root));
            first.get();
            second.get();
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(200, root.children().size());
    }

    private static void addChildren(TestContainer root) {
        for (int index = 0; index < 100; index++) {
            root.add(new TestComponent());
        }
    }

    @Test
    void listenerRegistrationOnlyRemovesItselfAndCloseIsIdempotent() {
        TestComponent component = new TestComponent();
        KeyEvent event = new KeyEvent(Keys.ENTER, KeyAction.PRESS, Set.of(), 1L);
        var first = component.on(KeyEvent.class, EventRoute.BUBBLE, (ignored, context) -> { });
        var second = component.on(KeyEvent.class, EventRoute.BUBBLE, (ignored, context) -> { });

        List<?> cached = EventListeners.handlers(component, event, EventRoute.BUBBLE);
        assertEquals(2, cached.size());
        assertSame(cached, EventListeners.handlers(component, event, EventRoute.BUBBLE));
        first.close();
        first.close();

        assertFalse(first.active());
        assertTrue(second.active());
        List<?> rebuilt = EventListeners.handlers(component, event, EventRoute.BUBBLE);
        assertEquals(1, rebuilt.size());
        assertNotSame(cached, rebuilt);
        assertSame(rebuilt, EventListeners.handlers(component, event, EventRoute.BUBBLE));
    }

    private static final class TestContainer extends UiContainer {
        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            return scope.layout(constraints.minWidth(), constraints.minHeight(), placements -> { });
        }
    }

    private static final class TestComponent extends UiComponent {
        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            return scope.layout(constraints.minWidth(), constraints.minHeight(), placements -> { });
        }
    }
}
