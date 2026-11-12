package odinclient.mixin.view;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;

import odinclient.module.movement.Freelook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public abstract class FreelookMouseMixin {
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void odinclient$consumeFreelookMouse(double timeDelta, CallbackInfo ci) {
        Freelook freelook = Freelook.getInstance();
        if (freelook != null && freelook.isCameraActive()) {
            freelook.onMouseMove(this.cursorDeltaX, this.cursorDeltaY);
            this.cursorDeltaX = 0.0;
            this.cursorDeltaY = 0.0;
            ci.cancel();
        }
    }
}
