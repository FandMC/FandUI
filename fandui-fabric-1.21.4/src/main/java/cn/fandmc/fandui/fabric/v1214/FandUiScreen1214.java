package cn.fandmc.fandui.fabric.v1214;

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
import cn.fandmc.fandui.core.input.PointerMoveCoalescer;
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

final class FandUiScreen1214 extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(FandUiClient1214.MOD_ID);

    private final CoreScreenSession session;
    private final LongSupplier clock;
    private final PointerInputState pointer = new PointerInputState();
    private final PointerMoveCoalescer pointerMoves = new PointerMoveCoalescer();
    private final KeyInputState keys = new KeyInputState();
    private final Utf16InputAssembler textInput = new Utf16InputAssembler();

    FandUiScreen1214(CoreScreenSession session, LongSupplier clock) {
        super(Component.literal(Objects.requireNonNull(session, "session").screen().title()));
        this.session = session;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    CoreScreenSession session() {
        return session;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        flushPointerMove();
        if (session.screen().background() == ScreenBackground.DEFAULT) {
            renderBackground(graphics, mouseX, mouseY, tickDelta);
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
        pointerMoves.offer(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        flushPointerMove();
        Point position = point(mouseX, mouseY);
        pointerMoves.synchronize(position);
        return dispatch(pointer.down(
                position,
                GlfwInputMapper.button(button),
                currentModifiers(),
                now()));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        flushPointerMove();
        Point position = point(mouseX, mouseY);
        pointerMoves.synchronize(position);
        return dispatch(pointer.up(
                position,
                GlfwInputMapper.button(button),
                currentModifiers(),
                now()));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        pointerMoves.offer(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        flushPointerMove();
        Point position = point(mouseX, mouseY);
        pointerMoves.synchronize(position);
        return dispatch(new ScrollEvent(
                horizontalAmount,
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
        pointerMoves.clear();
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

    private void flushPointerMove() {
        var event = pointerMoves.drain(pointer, currentModifiers(), now());
        if (event != null) {
            dispatch(event);
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
