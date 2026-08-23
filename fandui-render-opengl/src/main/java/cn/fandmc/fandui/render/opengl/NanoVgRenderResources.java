package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.text.TextRaster;

import java.util.Objects;
import java.util.Optional;

interface NanoVgRenderResources {
    Image resolveImage(ImageRef image, ImageSampling sampling);

    Text resolveText(TextLayout layout);

    record Image(long textureKey, OpenGlTexture texture, int width, int height) {
        public Image {
            if (textureKey == 0L) {
                throw new IllegalArgumentException("textureKey 0 is reserved");
            }
            Objects.requireNonNull(texture, "texture");
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("Image dimensions must be positive");
            }
        }
    }

    record Text(TextRaster raster, Optional<OpenGlTexture> texture) {
        public Text {
            Objects.requireNonNull(raster, "raster");
            texture = Objects.requireNonNull(texture, "texture");
            if (raster.byteSize() == 0 && texture.isPresent()) {
                throw new IllegalArgumentException("Empty text raster must not have a texture");
            }
            if (raster.byteSize() != 0 && texture.isEmpty()) {
                throw new IllegalArgumentException("Non-empty text raster requires a texture");
            }
        }
    }
}
