package odinclient.mixin.shader;

import net.minecraft.util.math.Vec3d;

import odinclient.render.Color;
import odinclient.render.world.GizmoRenderer;
import odinclient.render.world.TracerLineBatch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GizmoRenderer.class)
public abstract class TracerOriginMixin {
    @Inject(method = "stableTracer", at = @At("HEAD"), cancellable = true)
    private void odinclient$queueSmoothTracer(
            Vec3d origin, Color color, float width, CallbackInfo ci) {
        if (origin != null && color != null) {
            TracerLineBatch.queueTracer(origin, color.toArgb(), width);
        }

        ci.cancel();
    }
}
