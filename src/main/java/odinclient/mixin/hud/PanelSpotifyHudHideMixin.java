package odinclient.mixin.hud;

import net.minecraft.client.MinecraftClient;

import odinclient.event.Render2DEvent;
import odinclient.module.hud.ThemeModule;
import odinclient.module.spotify.SpotifyHud;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpotifyHud.class)
public abstract class PanelSpotifyHudHideMixin {
    @Inject(method = "onRender2D", at = @At("HEAD"), cancellable = true, remap = false)
    private void odinclient$hideSpotifyBehindPanelScreens(Render2DEvent event, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ThemeModule.isPanelStyle() && client != null && client.currentScreen != null) {
            ci.cancel();
        }
    }
}
