package odinclient.mixin.gamble;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;

import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class GambleMouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void odinclient$activation(long window, MouseInput input, int action, CallbackInfo ci) {
        GambleRigger.onKeyEvent(window, 1000 + input.getKeycode(), action);
    }
}
