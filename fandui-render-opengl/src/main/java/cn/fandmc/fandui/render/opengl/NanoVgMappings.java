package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.canvas.ArcDirection;
import cn.fandmc.fandui.api.canvas.CompositeOperation;
import cn.fandmc.fandui.api.canvas.LineCap;
import cn.fandmc.fandui.api.canvas.LineJoin;
import cn.fandmc.fandui.api.canvas.PathWinding;

import static org.lwjgl.nanovg.NanoVG.*;

final class NanoVgMappings {
    private NanoVgMappings() {
    }

    static int composite(CompositeOperation operation) {
        return switch (operation) {
            case SOURCE_OVER -> NVG_SOURCE_OVER;
            case SOURCE_IN -> NVG_SOURCE_IN;
            case SOURCE_OUT -> NVG_SOURCE_OUT;
            case SOURCE_ATOP -> NVG_ATOP;
            case DESTINATION_OVER -> NVG_DESTINATION_OVER;
            case DESTINATION_IN -> NVG_DESTINATION_IN;
            case DESTINATION_OUT -> NVG_DESTINATION_OUT;
            case DESTINATION_ATOP -> NVG_DESTINATION_ATOP;
            case LIGHTER -> NVG_LIGHTER;
            case COPY -> NVG_COPY;
            case XOR -> NVG_XOR;
        };
    }

    static int lineCap(LineCap cap) {
        return switch (cap) {
            case BUTT -> NVG_BUTT;
            case ROUND -> NVG_ROUND;
            case SQUARE -> NVG_SQUARE;
        };
    }

    static int lineJoin(LineJoin join) {
        return switch (join) {
            case MITER -> NVG_MITER;
            case ROUND -> NVG_ROUND;
            case BEVEL -> NVG_BEVEL;
        };
    }

    static int winding(PathWinding winding) {
        return winding == PathWinding.SOLID ? NVG_SOLID : NVG_HOLE;
    }

    static int arcDirection(ArcDirection direction) {
        return direction == ArcDirection.CLOCKWISE ? NVG_CW : NVG_CCW;
    }
}
