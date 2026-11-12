package odinclient.mixin.itemglow;

import net.minecraft.client.render.WorldRenderer;

import odinclient.module.render.itemglow.ItemGlowPhaseRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class, priority = 500)
public abstract class ItemGlowWorldPhaseMixin {
    @Inject(method = "method_62214", at = @At("HEAD"))
    private void odinclient$beginWorldItemGlow(CallbackInfo ci) {
        ItemGlowPhaseRenderer.onWorldPhaseStart();
    }

    @Inject(
            method = "method_62214",
            at =
                    @At(
                            value = "INVOKE:LAST",
                            target =
                                    "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V"))
    private void odinclient$drawWorldHeldItemGlow(CallbackInfo ci) {
        ItemGlowPhaseRenderer.flushWorldPhase();
    }
}
