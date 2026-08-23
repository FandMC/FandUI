package cn.fandmc.fandui.fabric.v262;

import cn.fandmc.fandui.api.event.KeyCode;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.ScrollEvent;
import cn.fandmc.fandui.api.event.TextCompositionEvent;
import cn.fandmc.fandui.api.event.TextInputEvent;
import cn.fandmc.fandui.api.event.UiEvent;
import cn.fandmc.fandui.api.focus.FocusDirection;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.screen.ScreenBackground;
import cn.fandmc.fandui.api.session.SessionCloseReason;
import cn.fandmc.fandui.core.input.GlfwInputMapper;
import cn.fandmc.fandui.core.input.KeyInputState;
import cn.fandmc.fandui.core.input.PointerInputState;
import cn.fandmc.fandui.core.runtime.CoreScreenSession;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.LongSupplier;

final class FandUiScreen262 extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(FandUiClient262.MOD_ID);

    private final CoreScreenSession session;
    private final LongSupplier clock;
    private final PointerInputState pointer = new PointerInputState();
    private final KeyInputState keys = new KeyInputState();
    private Point lastPointer;

    FandUiScreen262(CoreScreenSession session, LongSupplier clock) {
        super(Component.literal(Objects.requireNonNull(session, "session").screen().title()));
        this.session = session;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    CoreScreenSession session() {
        return session;
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float tickDelta
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float tickDelta
    ) {
        if (session.screen().background() == ScreenBackground.DEFAULT) {
            super.extractBackground(graphics, mouseX, mouseY, tickDelta);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        KeyCode key = GlfwInputMapper.key(event.key());
        Set<KeyModifier> normalizedModifiers = GlfwInputMapper.modifiers(event.modifiers());
        boolean consumed = dispatch(new KeyEvent(
                key,
                event.scancode(),
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
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        KeyCode key = GlfwInputMapper.key(event.key());
        return dispatch(new KeyEvent(
                key,
                event.scancode(),
                keys.release(key),
                GlfwInputMapper.modifiers(event.modifiers()),
                now()));
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return dispatch(new TextInputEvent(event.codepointAsString(), now()));
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        TextCompositionEvent composition = event == null
                ? new TextCompositionEvent(
                        false,
                        "",
                        0,
                        List.of(),
                        OptionalInt.empty(),
                        now())
                : new TextCompositionEvent(
                        true,
                        event.fullText(),
                        event.caretPosition(),
                        event.blocks(),
                        OptionalInt.of(event.focusedBlock()),
                        now());
        return dispatch(composition);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Point position = point(event.x(), event.y());
        lastPointer = position;
        return dispatch(pointer.down(
                position,
                GlfwInputMapper.button(event.button()),
                GlfwInputMapper.modifiers(event.modifiers()),
                now()));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        Point position = point(event.x(), event.y());
        lastPointer = position;
        return dispatch(pointer.up(
                position,
                GlfwInputMapper.button(event.button()),
                GlfwInputMapper.modifiers(event.modifiers()),
                now()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        Point position = point(event.x(), event.y());
        lastPointer = position;
        return dispatch(pointer.move(
                position,
                point(dragX, dragY),
                GlfwInputMapper.modifiers(event.modifiers()),
                now()));
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        Point position = point(mouseX, mouseY);
        lastPointer = position;
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
        keys.clear();
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

    private Set<KeyModifier> currentModifiers() {
        return GlfwInputMapper.modifiers(
                minecraft.hasShiftDown(),
                minecraft.hasControlDown(),
                minecraft.hasAltDown());
    }
}
