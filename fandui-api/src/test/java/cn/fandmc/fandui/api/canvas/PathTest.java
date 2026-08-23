package cn.fandmc.fandui.api.canvas;

import cn.fandmc.fandui.api.layout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathTest {
    @Test
    void buildCreatesAnImmutableSnapshotAndBuilderCanContinue() {
        PathBuilder builder = Path.builder().moveTo(2.0f, 3.0f).lineTo(8.0f, 11.0f);
        Path first = builder.build();
        builder.rect(new Rect(-5.0f, -7.0f, 2.0f, 4.0f));
        Path second = builder.build();

        assertEquals(new Rect(2.0f, 3.0f, 6.0f, 8.0f), first.bounds());
        assertEquals(new Rect(-5.0f, -7.0f, 13.0f, 18.0f), second.bounds());
    }

    @Test
    void rejectsNonFinitePathCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> Path.builder().moveTo(Float.NaN, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> Path.builder().arc(
                0.0f, 0.0f, -1.0f, 0.0f, 1.0f, ArcDirection.CLOCKWISE));
    }
}
