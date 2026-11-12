package odinclient.mixin.fake;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import odinclient.module.fake.FakePayTextRewriter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public abstract class FakePayTabMixin {
    @Shadow private Text footer;
    @Unique private Text odinclient$originalFooter;

    @Inject(method = "render", at = @At("HEAD"))
    private void odinclient$fakePayTabFooter(
            DrawContext context,
            int width,
            Scoreboard scoreboard,
            ScoreboardObjective objective,
            CallbackInfo ci) {
        this.odinclient$originalFooter = this.footer;
        this.footer = FakePayTextRewriter.rewriteMoney(this.footer);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void odinclient$restoreTabFooter(
            DrawContext context,
            int width,
            Scoreboard scoreboard,
            ScoreboardObjective objective,
            CallbackInfo ci) {
        this.footer = this.odinclient$originalFooter;
        this.odinclient$originalFooter = null;
    }
}
