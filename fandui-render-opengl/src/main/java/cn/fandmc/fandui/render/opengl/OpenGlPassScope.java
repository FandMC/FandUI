package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.render.opengl.internal.GlStateSnapshot;

/** Owns host OpenGL state across texture activation and display-list replay. */
final class OpenGlPassScope implements AutoCloseable {
    private static final boolean ASSERT_STATE = Boolean.parseBoolean(
            System.getProperty(NanoVgGl3Renderer.ASSERT_STATE_PROPERTY, "false"));
    private static final OpenGlPassScope INSTANCE = new OpenGlPassScope();
    private static final GlStateSnapshot BEFORE = GlStateSnapshot.reusable();
    private static final GlStateSnapshot AFTER = GlStateSnapshot.reusable();

    private static Thread owner;
    private static boolean active;

    private RenderHost host;
    private boolean hostHandoff;

    private OpenGlPassScope() {
    }

    static OpenGlPassScope open(RenderHost host) {
        host.assertRenderThread();
        if (active) {
            throw new IllegalStateException("FandUI OpenGL passes must not overlap");
        }
        Thread current = Thread.currentThread();
        boolean hostHandoff = host.supportsStateHandoff();
        if (hostHandoff) {
            host.prepareStateForFandUi();
        }
        if (ASSERT_STATE || !hostHandoff) {
            BEFORE.recapture();
        }
        owner = current;
        INSTANCE.host = host;
        INSTANCE.hostHandoff = hostHandoff;
        active = true;
        return INSTANCE;
    }

    static boolean isActive() {
        return active && owner == Thread.currentThread();
    }

    static void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("OpenGL work must run inside a FandUI pass scope");
        }
    }

    @Override
    public void close() {
        if (!isActive()) {
            throw new IllegalStateException("FandUI OpenGL pass scope closed by the wrong thread");
        }
        RenderHost currentHost = host;
        boolean currentHostHandoff = hostHandoff;
        active = false;
        owner = null;
        host = null;
        hostHandoff = false;

        if (currentHostHandoff) {
            currentHost.restoreStateAfterFandUi();
        } else {
            BEFORE.restore();
        }
        if (ASSERT_STATE) {
            BEFORE.assertRestored(AFTER.recapture());
        }
    }
}
