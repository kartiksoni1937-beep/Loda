package odinclient.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;

import odinclient.module.privacy.SkinProtect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class SkinProtectMixin {
    @Inject(
            method = "getSkin()Lnet/minecraft/entity/player/SkinTextures;",
            at = @At("HEAD"),
            cancellable = true)
    private void odinclientUseProtectedSkin(CallbackInfoReturnable<SkinTextures> cir) {
        SkinProtect skinProtect = SkinProtect.getActive();
        if (skinProtect != null) {
            SkinTextures textures = skinProtect.getSkinTextures();
            AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
            if (textures != null && skinProtect.shouldReplace(player.getUuid())) {
                cir.setReturnValue(textures);
            }
        }
    }
}
