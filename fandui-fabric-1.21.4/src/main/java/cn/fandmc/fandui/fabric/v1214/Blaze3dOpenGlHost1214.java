package cn.fandmc.fandui.fabric.v1214;

import cn.fandmc.fandui.render.opengl.OpenGlTarget;
import cn.fandmc.fandui.render.opengl.RenderHost;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Optional;
import net.minecraft.client.Minecraft;

public final class Blaze3dOpenGlHost1214 implements RenderHost {
    private OpenGlTarget cachedTarget;
    private Optional<OpenGlTarget> cachedResult = Optional.empty();

    @Override
    public String name() {
        return "minecraft-1.21.4-opengl";
    }

    @Override
    public void assertRenderThread() {
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public Optional<OpenGlTarget> currentTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.noRender) {
            return noTarget();
        }

        RenderTarget target = minecraft.getMainRenderTarget();
        int texture = target.getColorTextureId();
        if (texture <= 0 || target.width <= 0 || target.height <= 0) {
            return noTarget();
        }

        long generation = System.identityHashCode(target);
        OpenGlTarget current = cachedTarget;
        if (current == null
                || current.colorTextureId() != texture
                || current.width() != target.width
                || current.height() != target.height
                || current.generationToken() != generation) {
            current = new OpenGlTarget(texture, 0, target.width, target.height, generation);
            cachedTarget = current;
            cachedResult = Optional.of(current);
        }
        return cachedResult;
    }

    private Optional<OpenGlTarget> noTarget() {
        cachedTarget = null;
        cachedResult = Optional.empty();
        return cachedResult;
    }
}
