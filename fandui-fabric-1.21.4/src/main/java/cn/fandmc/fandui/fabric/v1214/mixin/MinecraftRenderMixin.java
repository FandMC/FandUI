package cn.fandmc.fandui.fabric.v1214.mixin;

import cn.fandmc.fandui.fabric.v1214.FandUiClient1214;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftRenderMixin {
    @Inject(
            method = "runTick(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;unbindWrite()V",
                    shift = At.Shift.BEFORE),
            require = 1)
    private void fandui$renderAfterGui(boolean renderLevel, CallbackInfo callbackInfo) {
        FandUiClient1214.renderAfterGui();
    }
}

