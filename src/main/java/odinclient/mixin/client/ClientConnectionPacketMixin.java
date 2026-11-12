package odinclient.mixin.client;

import io.netty.channel.ChannelFutureListener;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;

import odinclient.event.PacketEvent;
import odinclient.module.ModuleContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public abstract class ClientConnectionPacketMixin {
    @Inject(
            method =
                    "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"))
    private void odinclient$packetSend(
            Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        ModuleContext.EVENT_BUS.post(new PacketEvent.Send(packet));
    }

    @Inject(method = "handlePacket", at = @At("HEAD"))
    private static void odinclient$packetReceive(
            Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        ModuleContext.EVENT_BUS.post(new PacketEvent.Receive(packet));
    }
}
