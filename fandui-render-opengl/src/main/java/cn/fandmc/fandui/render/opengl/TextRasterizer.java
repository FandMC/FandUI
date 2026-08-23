package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.text.TextRaster;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface TextRasterizer {
    CompletableFuture<TextRaster> raster(TextLayout layout, float deviceScale);
}
