package odinclient.mixin.hud;

import net.minecraft.client.gui.DrawContext;

import odinclient.module.hud.ThemeModule;
import odinclient.ui.draw.DashboardRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "odinclient.module.spotify.SpotifyCardRenderer")
public abstract class PanelSpotifyHudStyleMixin {
    @Inject(method = "drawCardBackground", at = @At("HEAD"), cancellable = true)
    private void odinclient$drawBorderlessSpotifyCard(DrawContext context, int y, CallbackInfo ci) {
        if (ThemeModule.isPanelStyle()) {
            DashboardRenderer.drawRoundedHighlight(context, 0, 0, y, 62, 9, -301330923);
            ci.cancel();
        }
    }
}
