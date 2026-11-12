package odinclient.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import odinclient.module.render.esp.PlayerEsp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientOutlineMixin {
    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void odinclient$enablePlayerShaderOutline(
            Entity entity, CallbackInfoReturnable<Boolean> cir) {
        PlayerEsp playerEsp = PlayerEsp.getInstance();
        if (playerEsp != null && playerEsp.shouldApplyShader(entity)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
