package cn.fandmc.fandui.fabric.v262.mixin;

import cn.fandmc.fandui.fabric.v262.FandUiClient262;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V",
                    shift = At.Shift.AFTER),
            require = 1)
    private void fandui$renderAfterGui(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        FandUiClient262.renderAfterGui(renderer.mainRenderTarget());
    }
}

