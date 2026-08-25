package cn.fandmc.fandui.internal.control;

import cn.fandmc.fandui.api.event.EventRegistration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChangeListenersTest {
    @Test
    void runnablePublisherUsesStableSnapshotsWithoutPerNotificationCopies() {
        ChangeListeners listeners = new ChangeListeners();
        List<String> calls = new ArrayList<>();
        EventRegistration[] second = new EventRegistration[1];
        listeners.add(() -> {
            calls.add("first");
            second[0].close();
            listeners.add(() -> calls.add("late"));
        });
        second[0] = listeners.add(() -> calls.add("second"));

        listeners.notifyListeners();
        assertEquals(List.of("first"), calls);
        assertFalse(second[0].active());

        listeners.notifyListeners();
        assertEquals(List.of("first", "first", "late"), calls);
    }

    @Test
    void valuePublisherUsesTheLatestRegistrationSnapshot() {
        ValueChangeListeners<Integer> listeners = new ValueChangeListeners<>();
        List<Integer> calls = new ArrayList<>();
        EventRegistration first = listeners.add(calls::add);

        listeners.notifyListeners(1);
        first.close();
        listeners.add(value -> calls.add(value * 10));
        listeners.notifyListeners(2);

        assertEquals(List.of(1, 20), calls);
    }
}
