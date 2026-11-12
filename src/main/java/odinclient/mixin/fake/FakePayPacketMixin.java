package odinclient.mixin.fake;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;

import odinclient.module.fake.FakePay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class FakePayPacketMixin {
    @Inject(
            method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void odinclient$cancelFakePay(Packet<?> packet, CallbackInfo ci) {
        String command =
                packet instanceof CommandExecutionC2SPacket unsigned
                        ? unsigned.command()
                        : (packet instanceof ChatCommandSignedC2SPacket signed
                                ? signed.command()
                                : null);
        if (FakePay.interceptPayCommand(command)) {
            ci.cancel();
        }
    }
}
