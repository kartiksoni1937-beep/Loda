package odinclient.mixin.scanner;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;

import odinclient.module.playerchunk.LightChunkTracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class PlayerChunkLightMixin {
    @Inject(
            method = "onChunkData(Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;)V",
            at = @At("HEAD"))
    private void odinclient$captureChunkLight(ChunkDataS2CPacket packet, CallbackInfo ci) {
        LightChunkTracker.onLightData(
                packet.getChunkX(), packet.getChunkZ(), packet.getLightData());
    }

    @Inject(
            method = "onLightUpdate(Lnet/minecraft/network/packet/s2c/play/LightUpdateS2CPacket;)V",
            at = @At("HEAD"))
    private void odinclient$captureLightUpdate(LightUpdateS2CPacket packet, CallbackInfo ci) {
        LightChunkTracker.onLightData(packet.getChunkX(), packet.getChunkZ(), packet.getData());
    }
}
