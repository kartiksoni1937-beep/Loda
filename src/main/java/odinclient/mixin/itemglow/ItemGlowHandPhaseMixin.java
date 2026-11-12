package odinclient.mixin.itemglow;

import net.minecraft.client.render.item.HeldItemRenderer;

import odinclient.module.render.itemglow.ItemGlowPhaseRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HeldItemRenderer.class, priority = 500)
public abstract class ItemGlowHandPhaseMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
    private void odinclient$beginHeldItemGlow(CallbackInfo ci) {
        ItemGlowPhaseRenderer.onHandPhaseStart();
    }

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("RETURN"))
    private void odinclient$drawHeldItemGlow(CallbackInfo ci) {
        ItemGlowPhaseRenderer.flushHandPhase();
    }
}
