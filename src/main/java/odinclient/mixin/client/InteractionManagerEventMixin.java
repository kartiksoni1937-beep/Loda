package odinclient.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import odinclient.event.AttackBlockEvent;
import odinclient.event.AttackEntityEvent;
import odinclient.event.InteractItemEvent;
import odinclient.module.ModuleContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class InteractionManagerEventMixin {
    @Inject(method = "interactItem", at = @At("HEAD"))
    private void odinclient$interactItem(
            PlayerEntity player, Hand hand, CallbackInfoReturnable<?> cir) {
        ModuleContext.EVENT_BUS.post(new InteractItemEvent(hand));
    }

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void odinclient$attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        ModuleContext.EVENT_BUS.post(new AttackEntityEvent());
    }

    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void odinclient$attackBlock(
            BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        ModuleContext.EVENT_BUS.post(new AttackBlockEvent());
    }
}
