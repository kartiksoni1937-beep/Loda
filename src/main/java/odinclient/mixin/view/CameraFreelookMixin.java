package odinclient.mixin.view;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import odinclient.module.movement.Freelook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraFreelookMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract float clipToSpace(float distance);

    @Shadow
    protected abstract void moveBy(float x, float y, float z);

    @Inject(method = "update", at = @At("TAIL"))
    private void odinclient$updateFreelookCamera(
            World world,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickProgress,
            CallbackInfo ci) {
        Freelook freelook = Freelook.getInstance();
        if (freelook != null && freelook.isCameraActive() && focusedEntity != null) {
            Vec3d eyePos = focusedEntity.getCameraPosVec(tickProgress);
            this.setRotation(freelook.getCameraYaw(), freelook.getCameraPitch());
            this.setPos(eyePos.x, eyePos.y, eyePos.z);
            float distance = freelook.getCameraDistance();
            if (!freelook.isThroughWalls()) {
                distance = this.clipToSpace(distance);
            }

            this.moveBy(-distance, 0.0F, 0.0F);
        }
    }

    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void odinclient$freelookIsThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        Freelook freelook = Freelook.getInstance();
        if (freelook != null && freelook.isCameraActive()) {
            cir.setReturnValue(true);
        }
    }
}
