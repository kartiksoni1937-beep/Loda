package odinclient.mixin.gamble;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;

import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class GambleKeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void odinclient$activation(long window, int action, KeyInput input, CallbackInfo ci) {
        GambleRigger.onKeyEvent(window, input.getKeycode(), action);
    }
}
