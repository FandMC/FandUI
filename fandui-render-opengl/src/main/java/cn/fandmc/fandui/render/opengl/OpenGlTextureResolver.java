package cn.fandmc.fandui.render.opengl;

import java.util.Optional;

@FunctionalInterface
public interface OpenGlTextureResolver {
    Optional<OpenGlTexture> resolve(long textureKey, OpenGlSampling sampling);
}
