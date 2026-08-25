package cn.fandmc.fandui.fabric.v262.mixin;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.VertexArrayCache;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public interface GlCommandEncoderAccessor {
    @Accessor("lastPipeline")
    void fandui$lastPipeline(RenderPipeline pipeline);

    @Accessor("lastProgram")
    void fandui$lastProgram(GlProgram program);

    @Accessor("lastVertexArray")
    void fandui$lastVertexArray(VertexArrayCache.VertexArray vertexArray);
}
