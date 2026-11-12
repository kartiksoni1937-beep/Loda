package odinclient.mixin.view;

import net.minecraft.client.Mouse;

import odinclient.module.render.visual.Zoom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Mouse.class)
public abstract class ZoomMouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void odinclient$consumeZoomScroll(
            long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Zoom.onScroll(vertical)) {
            ci.cancel();
        }
    }

    @ModifyArgs(
            method = "updateMouse",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void odinclient$applyZoomLookSensitivity(Args args) {
        args.set(0, Zoom.scaleMouseDelta(args.<Double>get(0)));
        args.set(1, Zoom.scaleMouseDelta(args.<Double>get(1)));
    }
}
