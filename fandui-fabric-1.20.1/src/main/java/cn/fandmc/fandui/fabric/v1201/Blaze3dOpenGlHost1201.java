package cn.fandmc.fandui.fabric.v1201;

import cn.fandmc.fandui.render.opengl.OpenGlTarget;
import cn.fandmc.fandui.render.opengl.OpenGlStateHandoff;
import cn.fandmc.fandui.render.opengl.RenderHost;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import java.util.Optional;
import net.minecraft.client.Minecraft;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;

public final class Blaze3dOpenGlHost1201 implements RenderHost {
    private OpenGlTarget cachedTarget;
    private Optional<OpenGlTarget> cachedResult = Optional.empty();

    @Override
    public String name() {
        return "minecraft-1.20.1-opengl";
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

    @Override
    public boolean supportsStateHandoff() {
        return true;
    }

    @Override
    public void prepareStateForFandUi() {
        applyCanonicalHandoff(true);
    }

    @Override
    public void restoreStateAfterFandUi() {
        applyCanonicalHandoff(false);
    }

    private void applyCanonicalHandoff(boolean synchronizeCache) {
        assertRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        if (minecraft.noRender || target.frameBufferId <= 0 || target.viewWidth <= 0 || target.viewHeight <= 0) {
            throw new IllegalStateException("Minecraft OpenGL target disappeared during the FandUI pass");
        }

        int texture0 = RenderSystem.getShaderTexture(0);
        OpenGlStateHandoff.restoreCanonical(
                target.frameBufferId,
                target.viewWidth,
                target.viewHeight,
                0,
                0,
                GL_TEXTURE0,
                texture0,
                0);
        BufferUploader.invalidate();
        if (synchronizeCache) {
            synchronizeBlaze3dState(target, texture0);
        }
    }

    private static void synchronizeBlaze3dState(RenderTarget target, int texture0) {
        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._depthFunc(GL_LEQUAL);
        GlStateManager._enableCull();
        GlStateManager._disableColorLogicOp();
        GlStateManager._scissorBox(0, 0, target.viewWidth, target.viewHeight);
        GlStateManager._disableScissorTest();
        GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        GlStateManager._blendEquation(GL_FUNC_ADD);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._stencilFunc(GL_ALWAYS, 0, ~0);
        GlStateManager._stencilMask(~0);
        GlStateManager._stencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        GlStateManager._viewport(0, 0, target.viewWidth, target.viewHeight);
        synchronizeTexture(GL_TEXTURE0, texture0);
        synchronizeTexture(GL_TEXTURE1, 0);
        GlStateManager._activeTexture(GL_TEXTURE0);
    }

    private static void synchronizeTexture(int unit, int texture) {
        GlStateManager._activeTexture(unit);
        GlStateManager._bindTexture(texture);
    }

    private Optional<OpenGlTarget> noTarget() {
        cachedTarget = null;
        cachedResult = Optional.empty();
        return cachedResult;
    }
}
