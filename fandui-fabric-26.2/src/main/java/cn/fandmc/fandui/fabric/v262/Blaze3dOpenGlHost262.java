package cn.fandmc.fandui.fabric.v262;

import cn.fandmc.fandui.render.opengl.OpenGlTarget;
import cn.fandmc.fandui.render.opengl.RenderHost;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Optional;

public final class Blaze3dOpenGlHost262 implements RenderHost {
    private RenderTarget target;
    private OpenGlTarget cachedTarget;
    private Optional<OpenGlTarget> cachedResult = Optional.empty();

    public void target(RenderTarget target) {
        this.target = target;
    }

    @Override
    public String name() {
        return "minecraft-26.2-opengl";
    }

    @Override
    public void assertRenderThread() {
        RenderSystem.assertOnRenderThread();
    }

    public String backendName() {
        return RenderSystem.getDevice().getDeviceInfo().backendName();
    }

    public boolean isOpenGlBackend() {
        return "OpenGL".equalsIgnoreCase(backendName());
    }

    @Override
    public Optional<OpenGlTarget> currentTarget() {
        RenderTarget current = target;
        if (current == null || !isOpenGlBackend()) {
            return noTarget();
        }

        GpuTextureView colorView = current.getColorTextureView();
        if (!(colorView instanceof GlTextureView glView)) {
            return noTarget();
        }
        if (glView.glId() <= 0 || current.width <= 0 || current.height <= 0) {
            return noTarget();
        }

        int texture = glView.glId();
        int mipLevel = glView.fboMipLevel();
        long generation = System.identityHashCode(colorView);
        OpenGlTarget cached = cachedTarget;
        if (cached == null
                || cached.colorTextureId() != texture
                || cached.mipLevel() != mipLevel
                || cached.width() != current.width
                || cached.height() != current.height
                || cached.generationToken() != generation) {
            cached = new OpenGlTarget(texture, mipLevel, current.width, current.height, generation);
            cachedTarget = cached;
            cachedResult = Optional.of(cached);
        }
        return cachedResult;
    }

    private Optional<OpenGlTarget> noTarget() {
        cachedTarget = null;
        cachedResult = Optional.empty();
        return cachedResult;
    }
}
