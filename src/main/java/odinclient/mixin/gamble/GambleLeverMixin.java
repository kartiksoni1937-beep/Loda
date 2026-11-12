package odinclient.mixin.gamble;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class GambleLeverMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void odinclient$selectSetup(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir) {
        if (GambleRigger.onSelectionClick(hitResult)) {
            cir.setReturnValue(ActionResult.SUCCESS);
        } else {
            GambleRigger.onDropperUse(hitResult);
        }
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void odinclient$observeLever(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir) {
        GambleRigger.onLeverUse(hitResult);
    }
}
