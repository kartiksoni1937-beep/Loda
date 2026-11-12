package odinclient.mixin.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;

import odinclient.module.movement.Freecam;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public class FreecamPlayerTickMixin {
    @Inject(method = "tickMovement", at = @At("HEAD"), require = 1)
    private void odinclient$onTickMovement(CallbackInfo ci) {
        Freecam freecam = Freecam.instance;
        if (freecam != null && freecam.isActive()) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            player.setSneaking(freecam.getSavedPlayerInput().sneak());
            player.setSprinting(freecam.keepWalking.getValue() && freecam.wasSprinting());
        }
    }
}
