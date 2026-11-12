package odinclient.mixin.fake;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import odinclient.module.fake.FakePayTextRewriter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class FakePayHudMixin {
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"))
    private void odinclient$beginFakePaySidebar(
            DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        FakePayTextRewriter.resetCounters();
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
                            ordinal = 0),
            index = 1)
    private Text odinclient$fakePaySidebarTitle(Text text) {
        return FakePayTextRewriter.rewriteMoney(text);
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
                            ordinal = 1),
            index = 1)
    private Text odinclient$fakePaySidebarName(Text text) {
        return FakePayTextRewriter.rewriteSidebarName(text);
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
                            ordinal = 2),
            index = 1)
    private Text odinclient$fakePaySidebarScore(Text text) {
        return FakePayTextRewriter.rewriteSidebarScore(text);
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text odinclient$fakePayBalanceActionbar(Text text) {
        return FakePayTextRewriter.rewriteBalanceMessage(text);
    }
}
