package cn.fandmc.fandui.api.component.control;

import cn.fandmc.fandui.internal.control.ScrollControllers;
import cn.fandmc.fandui.internal.control.TextControllers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerTest {
    @Test
    void textControllerProtectsUtf16BoundariesAndReplacesDirectedSelection() {
        TextController controller = TextController.create("A\ud83d\ude00B");

        assertThrows(IllegalArgumentException.class, () -> controller.setSelection(new TextSelection(2, 2)));
        controller.setSelection(new TextSelection(4, 1));
        controller.replaceSelection("Z");

        assertEquals("AZ", controller.text());
        assertEquals(new TextSelection(2, 2), controller.selection());
    }

    @Test
    void textControllerExposesArbitraryRangeReplacement() {
        TextController controller = TextController.create("prefix value suffix");

        controller.replace(new TextSelection(7, 12), "result");

        assertEquals("prefix result suffix", controller.text());
        assertEquals(new TextSelection(13, 13), controller.selection());
    }

    @Test
    void nestedTextMutationsRunAfterTheCurrentNotificationBatch() {
        TextController controller = TextController.create();
        List<String> calls = new ArrayList<>();
        controller.onChange(() -> {
            calls.add("first:" + controller.text());
            if (controller.text().equals("a")) {
                controller.setText("b");
            }
        });
        var second = controller.onChange(() -> calls.add("second:" + controller.text()));

        controller.setText("a");
        second.close();
        second.close();

        assertEquals(List.of("first:a", "second:a", "first:b", "second:b"), calls);
        assertFalse(second.active());
    }

    @Test
    void controllersEnforceSingleBindingAndClampScrollExtent() {
        ScrollController controller = ScrollController.create(100.0);
        Object owner = new Object();
        var state = ScrollControllers.state(controller);

        state.bind(owner, 50.0);
        assertEquals(50.0, controller.offset());
        assertEquals(50.0, controller.maximumOffset().orElseThrow());
        assertThrows(IllegalStateException.class, () -> state.bind(new Object(), 20.0));

        state.updateMaximum(owner, 25.0);
        controller.scrollBy(-100.0);
        assertEquals(0.0, controller.offset());
        state.unbind(owner);

        assertTrue(controller.maximumOffset().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> controller.scrollTo(-1.0));
    }

    @Test
    void textControllerBindingUsesExactOwner() {
        TextController controller = TextController.create("text");
        Object owner = new Object();
        var state = TextControllers.state(controller);

        state.bind(owner);
        assertThrows(IllegalStateException.class, () -> state.bind(new Object()));
        assertThrows(IllegalStateException.class, () -> state.unbind(new Object()));
        state.unbind(owner);
    }
}
