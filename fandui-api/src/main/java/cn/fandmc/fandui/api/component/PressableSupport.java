package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.event.EventContext;
import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Size;
import org.jspecify.annotations.Nullable;

/** Package-private pointer/keyboard activation state shared by pressable controls. */
final class PressableSupport {
    private boolean armed;

    boolean handlePointer(
            UiComponent owner,
            PointerEvent event,
            EventContext context,
            Size measuredSize,
            Runnable activation) {
        if (event.action() == PointerAction.DOWN
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()) {
            if (!owner.enabled()) {
                return false;
            }
            armed = true;
            context.requestFocus();
            context.capturePointer();
            context.consume();
            return true;
        }
        if (event.action() == PointerAction.MOVE && armed) {
            context.consume();
            return true;
        }
        if (event.action() == PointerAction.UP
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()) {
            boolean activate = armed
                    && owner.enabled()
                    && inside(context.sceneToLocal(event.scenePosition()).orElse(null), measuredSize);
            armed = false;
            context.releasePointer();
            context.consume();
            if (activate) {
                activation.run();
            }
            return true;
        }
        if (event.action() == PointerAction.CANCEL) {
            armed = false;
        }
        return false;
    }

    boolean handleKey(
            UiComponent owner,
            KeyEvent event,
            EventContext context,
            Runnable activation) {
        if (!owner.enabled() || event.action() != KeyAction.PRESS) {
            return false;
        }
        if (event.key().equals(Keys.ENTER) || event.key().equals(Keys.SPACE)) {
            context.consume();
            activation.run();
            return true;
        }
        return false;
    }

    void cancel() {
        armed = false;
    }

    private static boolean inside(@Nullable Point point, Size size) {
        return point != null
                && point.x() >= 0.0f
                && point.y() >= 0.0f
                && point.x() < size.width()
                && point.y() < size.height();
    }
}
