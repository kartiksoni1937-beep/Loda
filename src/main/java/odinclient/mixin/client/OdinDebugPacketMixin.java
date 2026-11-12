package odinclient.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import odinclient.module.antivanish.AntiVanishActivityMonitor;
import odinclient.module.automation.AutoLog;
import odinclient.module.movement.AutoFirework;
import odinclient.module.playerdebug.PlayerFinderV2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class OdinDebugPacketMixin {
    @Inject(
            method =
                    "onCommandSuggestions(Lnet/minecraft/network/packet/s2c/play/CommandSuggestionsS2CPacket;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void odinclient$ownCompletionProbe(
            CommandSuggestionsS2CPacket packet, CallbackInfo ci) {
        if (AntiVanishActivityMonitor.interceptCommandSuggestions(packet)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "onChunkData(Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;)V",
            at = @At("HEAD"))
    private void odinclient$beforeChunkUpdate(ChunkDataS2CPacket packet, CallbackInfo ci) {
        PlayerFinderV2.onChunkDataPacket(packet.getChunkX(), packet.getChunkZ());
    }

    @Inject(
            method =
                    "onEntityStatus(Lnet/minecraft/network/packet/s2c/play/EntityStatusS2CPacket;)V",
            at = @At("HEAD"))
    private void odinclient$ownTotemPop(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread() && client.world != null && client.player != null) {
            if (packet.getStatus() == 35 && packet.getEntity(client.world) == client.player) {
                AutoLog.onTotemPop();
                AutoFirework.onTotemPop();
            }
        }
    }
}
