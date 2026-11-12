package odinclient.mixin.fake;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import odinclient.module.fake.FakePay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class FakePayCommandMixin {
    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void odinclient$localFakePay(String command, CallbackInfo ci) {
        if (FakePay.interceptPayCommand(command)) {
            ci.cancel();
        }
    }

    @Inject(method = "runClickEventCommand", at = @At("HEAD"), cancellable = true)
    private void odinclient$localConfirmedFakePay(String command, Screen screen, CallbackInfo ci) {
        if (FakePay.interceptPayCommand(command)) {
            ci.cancel();
        }
    }
}
