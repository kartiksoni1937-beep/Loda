package odinclient.mixin.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import odinclient.module.movement.Freecam;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public class FreecamEntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 1)
    private void odinclient$onChangeLookDirection(double dx, double dy, CallbackInfo ci) {
        Freecam freecam = Freecam.instance;
        if ((Entity) (Object) this == MinecraftClient.getInstance().player
                && freecam != null
                && freecam.isActive()) {
            freecam.changeLookDirection(
                    dx * 0.15 * freecam.getLookSensitivity(),
                    dy * 0.15 * freecam.getLookSensitivity());
            ci.cancel();
        }
    }

    @Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true, require = 1)
    private void odinclient$onIsSneaking(CallbackInfoReturnable<Boolean> cir) {
        Freecam freecam = Freecam.instance;
        if (freecam != null
                && freecam.isActive()
                && (Object) this == MinecraftClient.getInstance().player) {
            cir.setReturnValue(freecam.getSavedPlayerInput().sneak());
        }
    }

    @Inject(method = "shouldRender(D)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void odinclient$onShouldRender(double distance, CallbackInfoReturnable<Boolean> cir) {
        Freecam freecam = Freecam.instance;
        if (freecam != null && freecam.isActive() && distance < 25600.0) {
            cir.setReturnValue(true);
        }
    }
}
