package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.text.TextRaster;

import java.util.List;

interface TextTextureStore extends OpenGlTextureResolver, AutoCloseable {
    void activate(List<TextRaster> rasters);

    @Override
    void close();
}
