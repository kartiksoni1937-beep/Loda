package odinclient.mixin.view;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;

import odinclient.module.combat.AimAssist;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public abstract class AimAssistMouseMixin {
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void odinclient$notifyAimAssistOfMouseMovement(double timeDelta, CallbackInfo ci) {
        if (Math.abs(this.cursorDeltaX) > 1.0E-6 || Math.abs(this.cursorDeltaY) > 1.0E-6) {
            AimAssist.onManualMouseMove();
        }
    }
}
