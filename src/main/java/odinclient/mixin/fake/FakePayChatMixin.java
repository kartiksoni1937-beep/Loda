package odinclient.mixin.fake;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;

import odinclient.module.fake.FakePay;
import odinclient.module.fake.FakePayTextRewriter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class FakePayChatMixin {
    @ModifyVariable(
            method =
                    "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Text odinclient$fakePayChat(Text message) {
        FakePay.onChatMessage(message);
        return FakePayTextRewriter.rewriteBalanceMessage(message);
    }
}
