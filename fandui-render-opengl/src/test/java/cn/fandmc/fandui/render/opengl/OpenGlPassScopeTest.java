package cn.fandmc.fandui.render.opengl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlPassScopeTest {
    @Test
    void preparesBeforeRenderingAndRestoresAfterwards() {
        List<String> events = new ArrayList<>();
        RenderHost host = handoffHost(events, false);

        OpenGlPassScope scope = OpenGlPassScope.open(host);
        try {
            assertTrue(OpenGlPassScope.isActive());
            events.add("render");
        } finally {
            scope.close();
        }

        assertFalse(OpenGlPassScope.isActive());
        assertEquals(List.of("prepare", "render", "restore"), events);
    }

    @Test
    void prepareFailureDoesNotActivateOrRetainTheScope() {
        assertThrows(
                IllegalStateException.class,
                () -> OpenGlPassScope.open(handoffHost(new ArrayList<>(), true)));
        assertFalse(OpenGlPassScope.isActive());

        List<String> events = new ArrayList<>();
        OpenGlPassScope scope = OpenGlPassScope.open(handoffHost(events, false));
        try {
            assertTrue(OpenGlPassScope.isActive());
        } finally {
            scope.close();
        }
        assertEquals(List.of("prepare", "restore"), events);
    }

    private static RenderHost handoffHost(List<String> events, boolean failPrepare) {
        return new RenderHost() {
            private final Thread renderThread = Thread.currentThread();

            @Override
            public String name() {
                return "test";
            }

            @Override
            public void assertRenderThread() {
                if (Thread.currentThread() != renderThread) {
                    throw new IllegalStateException("wrong thread");
                }
            }

            @Override
            public Optional<OpenGlTarget> currentTarget() {
                return Optional.empty();
            }

            @Override
            public boolean supportsStateHandoff() {
                return true;
            }

            @Override
            public void prepareStateForFandUi() {
                events.add("prepare");
                if (failPrepare) {
                    throw new IllegalStateException("prepare failed");
                }
            }

            @Override
            public void restoreStateAfterFandUi() {
                events.add("restore");
            }
        };
    }
}
