package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.core.resource.ImageRaster;

@FunctionalInterface
interface ImageRasterResolver {
    ImageRaster resolve(ImageRef image);
}
