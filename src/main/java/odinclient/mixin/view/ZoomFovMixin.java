package odinclient.mixin.view;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;

import odinclient.module.render.visual.Zoom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ZoomFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void odinclient$applyZoomFov(
            Camera camera,
            float tickProgress,
            boolean changingFov,
            CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(Zoom.applyZoomFov(cir.getReturnValueF()));
    }
}
