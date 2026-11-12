package odinclient.mixin.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

import odinclient.module.movement.Freecam;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public class FreecamKeyboardInputMixin {
    @Inject(method = "tick()V", at = @At("RETURN"), require = 1)
    private void odinclient$onTickReturn(CallbackInfo ci) {
        Freecam freecam = Freecam.instance;
        if (freecam != null && freecam.isActive()) {
            InputAccessor accessor = (InputAccessor) this;
            PlayerInput saved = freecam.getSavedPlayerInput();
            boolean keepWalking = freecam.keepWalking.getValue();
            boolean keepJumping = freecam.keepJumping.getValue();
            PlayerInput input =
                    new PlayerInput(
                            keepWalking && saved.forward(),
                            keepWalking && saved.backward(),
                            keepWalking && saved.left(),
                            keepWalking && saved.right(),
                            keepJumping && saved.jump(),
                            saved.sneak(),
                            keepWalking && freecam.wasSprinting());
            accessor.odinclient$setPlayerInput(input);
            accessor.odinclient$setMovementVector(
                    keepWalking ? freecam.getSavedMovementVector() : Vec2f.ZERO);
        }
    }
}
