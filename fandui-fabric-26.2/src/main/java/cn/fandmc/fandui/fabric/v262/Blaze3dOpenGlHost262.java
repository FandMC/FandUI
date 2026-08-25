package cn.fandmc.fandui.fabric.v262;

import cn.fandmc.fandui.render.opengl.OpenGlTarget;
import cn.fandmc.fandui.render.opengl.OpenGlStateHandoff;
import cn.fandmc.fandui.render.opengl.RenderHost;
import cn.fandmc.fandui.fabric.v262.mixin.CommandEncoderAccessor;
import cn.fandmc.fandui.fabric.v262.mixin.GlCommandEncoderAccessor;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Optional;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;

public final class Blaze3dOpenGlHost262 implements RenderHost {
    private RenderTarget target;
    private OpenGlTarget cachedTarget;
    private Optional<OpenGlTarget> cachedResult = Optional.empty();
    private GpuDevice encoderDevice;
    private GlCommandEncoderAccessor encoderState;

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

    @Override
    public boolean supportsStateHandoff() {
        return isOpenGlBackend();
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
        RenderTarget current = target;
        if (current == null || current.width <= 0 || current.height <= 0 || !isOpenGlBackend()) {
            throw new IllegalStateException("Minecraft OpenGL target disappeared during the FandUI pass");
        }

        OpenGlStateHandoff.restoreCanonical(
                0,
                current.width,
                current.height,
                0,
                0,
                GL_TEXTURE0,
                0,
                0);
        invalidateEncoderState();
        if (synchronizeCache) {
            synchronizeBlaze3dState(current);
        }
    }

    private static void synchronizeBlaze3dState(RenderTarget target) {
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, 0);
        for (int index = 0; index < 8; index++) {
            GlStateManager._disableBlend(index);
        }
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._depthFunc(GL_LEQUAL);
        GlStateManager._enableCull();
        GlStateManager._disableColorLogicOp();
        GlStateManager._scissorBox(0, 0, target.width, target.height);
        GlStateManager._disableScissorTest();
        GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        GlStateManager._blendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        GlStateManager._colorMask(0xf);
        GlStateManager._viewport(0, 0, target.width, target.height);
        synchronizeTexture(GL_TEXTURE0, 0);
        synchronizeTexture(GL_TEXTURE1, 0);
        GlStateManager._activeTexture(GL_TEXTURE0);
    }

    private static void synchronizeTexture(int unit, int texture) {
        GlStateManager._activeTexture(unit);
        GlStateManager._bindTexture(texture);
    }

    private void invalidateEncoderState() {
        GlCommandEncoderAccessor state = encoderState();
        state.fandui$lastPipeline(null);
        state.fandui$lastProgram(null);
        state.fandui$lastVertexArray(null);
    }

    private GlCommandEncoderAccessor encoderState() {
        GpuDevice device = RenderSystem.getDevice();
        GlCommandEncoderAccessor current = encoderState;
        if (current != null && encoderDevice == device) {
            return current;
        }

        CommandEncoder encoder = device.createCommandEncoder();
        CommandEncoderBackend backend = ((CommandEncoderAccessor) (Object) encoder).fandui$backend();
        if (!(backend instanceof GlCommandEncoderAccessor access)) {
            throw new IllegalStateException("Minecraft OpenGL command encoder does not expose its state cache");
        }
        encoderDevice = device;
        encoderState = access;
        return access;
    }

    private Optional<OpenGlTarget> noTarget() {
        cachedTarget = null;
        cachedResult = Optional.empty();
        return cachedResult;
    }
}
