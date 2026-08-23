package cn.fandmc.fandui.core.input;

import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.layout.Point;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Tracks the pressed pointer buttons owned by one Minecraft Screen instance. */
public final class PointerInputState {
    private static final long MULTI_CLICK_INTERVAL_NANOS = 500_000_000L;
    private static final float MULTI_CLICK_DISTANCE_SQUARED = 16.0f;

    private final Set<PointerButton> buttons = new LinkedHashSet<>();
    private PointerButton lastClickButton;
    private Point lastClickPosition;
    private long lastClickNanos = Long.MIN_VALUE;
    private int clickCount;

    public PointerEvent move(
            Point position,
            Point delta,
            Set<KeyModifier> modifiers,
            long timestampNanos
    ) {
        return event(
                PointerAction.MOVE,
                position,
                delta,
                Optional.empty(),
                0,
                modifiers,
                timestampNanos);
    }

    public PointerEvent down(
            Point position,
            PointerButton button,
            Set<KeyModifier> modifiers,
            long timestampNanos
    ) {
        buttons.add(button);
        int currentClickCount = nextClickCount(position, button, timestampNanos);
        return event(
                PointerAction.DOWN,
                position,
                new Point(0.0f, 0.0f),
                Optional.of(button),
                currentClickCount,
                modifiers,
                timestampNanos);
    }

    private int nextClickCount(Point position, PointerButton button, long timestampNanos) {
        boolean closeInTime = lastClickNanos != Long.MIN_VALUE
                && timestampNanos >= lastClickNanos
                && timestampNanos - lastClickNanos <= MULTI_CLICK_INTERVAL_NANOS;
        boolean closeInSpace = lastClickPosition != null
                && distanceSquared(lastClickPosition, position) <= MULTI_CLICK_DISTANCE_SQUARED;
        clickCount = button.equals(lastClickButton) && closeInTime && closeInSpace
                ? clickCount + 1
                : 1;
        lastClickButton = button;
        lastClickPosition = position;
        lastClickNanos = timestampNanos;
        return clickCount;
    }

    private static float distanceSquared(Point first, Point second) {
        float x = second.x() - first.x();
        float y = second.y() - first.y();
        return x * x + y * y;
    }

    public PointerEvent up(
            Point position,
            PointerButton button,
            Set<KeyModifier> modifiers,
            long timestampNanos
    ) {
        buttons.remove(button);
        return event(
                PointerAction.UP,
                position,
                new Point(0.0f, 0.0f),
                Optional.of(button),
                0,
                modifiers,
                timestampNanos);
    }

    private PointerEvent event(
            PointerAction action,
            Point position,
            Point delta,
            Optional<PointerButton> changedButton,
            int clickCount,
            Set<KeyModifier> modifiers,
            long timestampNanos
    ) {
        return new PointerEvent(
                action,
                position,
                delta,
                changedButton,
                Set.copyOf(buttons),
                clickCount,
                modifiers,
                timestampNanos);
    }
}
