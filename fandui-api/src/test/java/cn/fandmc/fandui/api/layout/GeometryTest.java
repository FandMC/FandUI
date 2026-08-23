package cn.fandmc.fandui.api.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeometryTest {
    @Test
    void constraintsClampBothDimensions() {
        Constraints constraints = new Constraints(10.0f, 20.0f, 30.0f, 40.0f);

        assertEquals(new Size(10.0f, 40.0f), constraints.constrain(new Size(5.0f, 50.0f)));
        assertEquals(new Size(15.0f, 35.0f), constraints.constrain(new Size(15.0f, 35.0f)));
    }

    @Test
    void acceptsUnboundedMaximumButRejectsInvalidGeometry() {
        Constraints unbounded = new Constraints(0.0f, Float.POSITIVE_INFINITY, 0.0f, Float.POSITIVE_INFINITY);

        assertEquals(new Size(125.0f, 250.0f), unbounded.constrain(new Size(125.0f, 250.0f)));
        assertThrows(IllegalArgumentException.class, () -> new Size(-1.0f, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> new Point(Float.NaN, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> new Rect(0.0f, 0.0f, Float.POSITIVE_INFINITY, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new Constraints(2.0f, 1.0f, 0.0f, 1.0f));
    }
}
