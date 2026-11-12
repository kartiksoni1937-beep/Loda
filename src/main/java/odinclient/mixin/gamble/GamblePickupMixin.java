package odinclient.mixin.gamble;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;

import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class GamblePickupMixin {
    @Inject(method = "onItemPickupAnimation", at = @At("HEAD"))
    private void odinclient$pickup(ItemPickupAnimationS2CPacket packet, CallbackInfo ci) {
        GambleRigger.onItemPickup(packet);
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void odinclient$slot(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        GambleRigger.onInventoryUpdate(packet.getSyncId(), false);
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    private void odinclient$contents(InventoryS2CPacket packet, CallbackInfo ci) {
        GambleRigger.onInventoryUpdate(packet.syncId(), true);
    }

    @Inject(method = "onSetCursorItem", at = @At("TAIL"))
    private void odinclient$cursor(SetCursorItemS2CPacket packet, CallbackInfo ci) {
        GambleRigger.syncInventory();
    }

    @Inject(method = "onSetPlayerInventory", at = @At("TAIL"))
    private void odinclient$playerSlot(SetPlayerInventoryS2CPacket packet, CallbackInfo ci) {
        GambleRigger.syncInventory();
    }

    @Inject(method = "onOpenScreen", at = @At("TAIL"))
    private void odinclient$open(OpenScreenS2CPacket packet, CallbackInfo ci) {
        GambleRigger.onOpenScreen(packet);
    }
}
