package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.canvas.ArcDirection;
import cn.fandmc.fandui.api.canvas.CompositeOperation;
import cn.fandmc.fandui.api.canvas.LineCap;
import cn.fandmc.fandui.api.canvas.LineJoin;
import cn.fandmc.fandui.api.canvas.PathWinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.nanovg.NanoVG.*;

class NanoVgMappingsTest {
    @Test
    void mapsEveryPublicEnumToTheStockNanoVgConstant() {
        assertAll(
                () -> assertEquals(NVG_SOURCE_OVER, NanoVgMappings.composite(CompositeOperation.SOURCE_OVER)),
                () -> assertEquals(NVG_SOURCE_IN, NanoVgMappings.composite(CompositeOperation.SOURCE_IN)),
                () -> assertEquals(NVG_SOURCE_OUT, NanoVgMappings.composite(CompositeOperation.SOURCE_OUT)),
                () -> assertEquals(NVG_ATOP, NanoVgMappings.composite(CompositeOperation.SOURCE_ATOP)),
                () -> assertEquals(NVG_DESTINATION_OVER,
                        NanoVgMappings.composite(CompositeOperation.DESTINATION_OVER)),
                () -> assertEquals(NVG_DESTINATION_IN,
                        NanoVgMappings.composite(CompositeOperation.DESTINATION_IN)),
                () -> assertEquals(NVG_DESTINATION_OUT,
                        NanoVgMappings.composite(CompositeOperation.DESTINATION_OUT)),
                () -> assertEquals(NVG_DESTINATION_ATOP,
                        NanoVgMappings.composite(CompositeOperation.DESTINATION_ATOP)),
                () -> assertEquals(NVG_LIGHTER, NanoVgMappings.composite(CompositeOperation.LIGHTER)),
                () -> assertEquals(NVG_COPY, NanoVgMappings.composite(CompositeOperation.COPY)),
                () -> assertEquals(NVG_XOR, NanoVgMappings.composite(CompositeOperation.XOR)),
                () -> assertEquals(NVG_BUTT, NanoVgMappings.lineCap(LineCap.BUTT)),
                () -> assertEquals(NVG_ROUND, NanoVgMappings.lineCap(LineCap.ROUND)),
                () -> assertEquals(NVG_SQUARE, NanoVgMappings.lineCap(LineCap.SQUARE)),
                () -> assertEquals(NVG_MITER, NanoVgMappings.lineJoin(LineJoin.MITER)),
                () -> assertEquals(NVG_ROUND, NanoVgMappings.lineJoin(LineJoin.ROUND)),
                () -> assertEquals(NVG_BEVEL, NanoVgMappings.lineJoin(LineJoin.BEVEL)),
                () -> assertEquals(NVG_SOLID, NanoVgMappings.winding(PathWinding.SOLID)),
                () -> assertEquals(NVG_HOLE, NanoVgMappings.winding(PathWinding.HOLE)),
                () -> assertEquals(NVG_CW, NanoVgMappings.arcDirection(ArcDirection.CLOCKWISE)),
                () -> assertEquals(NVG_CCW, NanoVgMappings.arcDirection(ArcDirection.COUNTER_CLOCKWISE)));
    }
}
