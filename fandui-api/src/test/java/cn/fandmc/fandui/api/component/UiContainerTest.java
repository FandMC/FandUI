package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.internal.event.EventListeners;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void listenerRegistrationOnlyRemovesItselfAndCloseIsIdempotent() {
        TestComponent component = new TestComponent();
        KeyEvent event = new KeyEvent(Keys.ENTER, KeyAction.PRESS, Set.of(), 1L);
        var first = component.on(KeyEvent.class, EventRoute.BUBBLE, (ignored, context) -> { });
        var second = component.on(KeyEvent.class, EventRoute.BUBBLE, (ignored, context) -> { });

        assertEquals(2, EventListeners.handlers(component, event, EventRoute.BUBBLE).size());
        first.close();
        first.close();

        assertFalse(first.active());
        assertTrue(second.active());
        assertEquals(1, EventListeners.handlers(component, event, EventRoute.BUBBLE).size());
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
