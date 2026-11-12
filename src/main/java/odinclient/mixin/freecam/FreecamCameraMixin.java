package odinclient.mixin.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import odinclient.module.movement.Freecam;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class FreecamCameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"), require = 1)
    private void odinclient$onUpdate(
            World world,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci) {
        Freecam freecam = Freecam.instance;
        if (freecam != null && freecam.isActive()) {
            freecam.updateCamera();
            this.setPos(
                    freecam.getLerpedX(tickDelta),
                    freecam.getLerpedY(tickDelta),
                    freecam.getLerpedZ(tickDelta));
            this.setRotation(freecam.getLerpedYaw(tickDelta), freecam.getLerpedPitch(tickDelta));
        }
    }

    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true, require = 1)
    private void odinclient$onIsThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        Freecam freecam = Freecam.instance;
        if (freecam != null && freecam.isActive()) {
            cir.setReturnValue(true);
        }
    }
}
