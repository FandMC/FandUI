package cn.fandmc.fandui.fabric.v1201;

import cn.fandmc.fandui.api.event.KeyCode;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.ScrollEvent;
import cn.fandmc.fandui.api.event.TextInputEvent;
import cn.fandmc.fandui.api.event.UiEvent;
import cn.fandmc.fandui.api.focus.FocusDirection;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.screen.ScreenBackground;
import cn.fandmc.fandui.api.session.SessionCloseReason;
import cn.fandmc.fandui.core.input.GlfwInputMapper;
import cn.fandmc.fandui.core.input.KeyInputState;
import cn.fandmc.fandui.core.input.PointerInputState;
import cn.fandmc.fandui.core.input.Utf16InputAssembler;
import cn.fandmc.fandui.core.runtime.CoreScreenSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

final class FandUiScreen1201 extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(FandUiClient1201.MOD_ID);

    private final CoreScreenSession session;
    private final LongSupplier clock;
    private final PointerInputState pointer = new PointerInputState();
    private final KeyInputState keys = new KeyInputState();
    private final Utf16InputAssembler textInput = new Utf16InputAssembler();
    private Point lastPointer;

    FandUiScreen1201(CoreScreenSession session, LongSupplier clock) {
        super(Component.literal(Objects.requireNonNull(session, "session").screen().title()));
        this.session = session;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    CoreScreenSession session() {
        return session;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        if (session.screen().background() == ScreenBackground.DEFAULT) {
            renderBackground(graphics);
        }
        super.render(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyCode key = GlfwInputMapper.key(keyCode);
        Set<KeyModifier> normalizedModifiers = GlfwInputMapper.modifiers(modifiers);
        boolean consumed = dispatch(new KeyEvent(
                key,
                scanCode,
                keys.press(key),
                normalizedModifiers,
                now()));
        if (consumed || !session.active()) {
            return true;
        }
        if (key.equals(Keys.TAB)) {
            FocusDirection direction = normalizedModifiers.contains(KeyModifier.SHIFT)
                    ? FocusDirection.BACKWARD
                    : FocusDirection.FORWARD;
            return session.focus().move(direction);
        }
        if (key.equals(Keys.ESCAPE) && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        KeyCode key = GlfwInputMapper.key(keyCode);
        return dispatch(new KeyEvent(
                key,
                scanCode,
                keys.release(key),
                GlfwInputMapper.modifiers(modifiers),
                now()));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        List<String> committed = textInput.accept(codePoint);
        if (committed.isEmpty()) {
            return true;
        }
        boolean consumed = false;
        for (String text : committed) {
            consumed |= dispatch(new TextInputEvent(text, now()));
        }
        return consumed;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        Point position = point(mouseX, mouseY);
        Point delta = lastPointer == null
                ? new Point(0.0f, 0.0f)
                : new Point(position.x() - lastPointer.x(), position.y() - lastPointer.y());
        lastPointer = position;
        dispatch(pointer.move(position, delta, currentModifiers(), now()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Point position = point(mouseX, mouseY);
        lastPointer = position;
        return dispatch(pointer.down(
                position,
                GlfwInputMapper.button(button),
                currentModifiers(),
                now()));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Point position = point(mouseX, mouseY);
        lastPointer = position;
        return dispatch(pointer.up(
                position,
                GlfwInputMapper.button(button),
                currentModifiers(),
                now()));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        Point position = point(mouseX, mouseY);
        lastPointer = position;
        return dispatch(pointer.move(
                position,
                point(dragX, dragY),
                currentModifiers(),
                now()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        Point position = point(mouseX, mouseY);
        lastPointer = position;
        return dispatch(new ScrollEvent(
                0.0,
                verticalAmount,
                position,
                currentModifiers(),
                now()));
    }

    @Override
    public void onClose() {
        try {
            if (session.active()) {
                session.hostClosed(SessionCloseReason.ESCAPE);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("FandUI screen failed while handling Escape close", exception);
            super.onClose();
        }
    }

    @Override
    public void removed() {
        super.removed();
        keys.clear();
        textInput.flush();
        if (!session.active()) {
            return;
        }
        try {
            session.hostClosed(SessionCloseReason.HOST);
        } catch (RuntimeException exception) {
            LOGGER.error("FandUI screen failed while handling host removal", exception);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return session.screen().pausesGame();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return session.screen().closesOnEscape();
    }

    private boolean dispatch(UiEvent event) {
        if (!session.active()) {
            return true;
        }
        try {
            return session.dispatch(event);
        } catch (RuntimeException exception) {
            LOGGER.error("FandUI screen input handler failed; the screen session was closed", exception);
            return true;
        }
    }

    private long now() {
        return clock.getAsLong();
    }

    private static Point point(double x, double y) {
        return new Point((float) x, (float) y);
    }

    private static Set<KeyModifier> currentModifiers() {
        return GlfwInputMapper.modifiers(hasShiftDown(), hasControlDown(), hasAltDown());
    }
}
