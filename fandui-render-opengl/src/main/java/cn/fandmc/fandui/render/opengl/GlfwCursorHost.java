package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.input.CursorShape;
import cn.fandmc.fandui.core.runtime.CursorHost;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

import static org.lwjgl.glfw.GLFW.GLFW_ARROW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CROSSHAIR_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_HAND_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_HRESIZE_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_IBEAM_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_VRESIZE_CURSOR;
import static org.lwjgl.glfw.GLFW.glfwCreateStandardCursor;
import static org.lwjgl.glfw.GLFW.glfwDestroyCursor;
import static org.lwjgl.glfw.GLFW.glfwSetCursor;

/** Owns lazily created GLFW standard cursors for one Minecraft window. */
public final class GlfwCursorHost implements CursorHost {
    private final LongSupplier window;
    private final Map<CursorShape, Long> cursors = new EnumMap<>(CursorShape.class);
    private boolean closed;

    public GlfwCursorHost(LongSupplier window) {
        this.window = Objects.requireNonNull(window, "window");
    }

    @Override
    public void setCursor(CursorShape shape) {
        if (closed) {
            return;
        }
        long handle = shape == CursorShape.DEFAULT
                ? 0L
                : cursors.computeIfAbsent(shape, value -> glfwCreateStandardCursor(glfwShape(value)));
        glfwSetCursor(window.getAsLong(), handle);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        glfwSetCursor(window.getAsLong(), 0L);
        cursors.values().stream().filter(value -> value != 0L).forEach(value -> glfwDestroyCursor(value));
        cursors.clear();
    }

    private static int glfwShape(CursorShape shape) {
        return switch (shape) {
            case DEFAULT -> GLFW_ARROW_CURSOR;
            case POINTER -> GLFW_HAND_CURSOR;
            case TEXT -> GLFW_IBEAM_CURSOR;
            case CROSSHAIR, MOVE -> GLFW_CROSSHAIR_CURSOR;
            case RESIZE_HORIZONTAL -> GLFW_HRESIZE_CURSOR;
            case RESIZE_VERTICAL -> GLFW_VRESIZE_CURSOR;
        };
    }
}
