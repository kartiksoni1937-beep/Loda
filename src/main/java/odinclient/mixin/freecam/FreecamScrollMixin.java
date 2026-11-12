package odinclient.mixin.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;

import odinclient.module.movement.Freecam;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public class FreecamScrollMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 1)
    private void odinclient$useScrollForFreecamSpeed(
            long window, double horizontal, double vertical, CallbackInfo ci) {
        Freecam freecam = Freecam.instance;
        if (freecam != null
                && freecam.isActive()
                && MinecraftClient.getInstance().currentScreen == null) {
            double amount = vertical != 0.0 ? vertical : horizontal;

            if (amount != 0.0 && freecam.onScroll(amount)) {
                ci.cancel();
            }
        }
    }
}
