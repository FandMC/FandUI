package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.core.resource.ImageRaster;

import java.util.List;

interface ImageTextureStore extends OpenGlTextureResolver, AutoCloseable {
    void activate(List<ImageRaster> rasters);

    @Override
    void close();
}
