package odinclient.mixin.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

import odinclient.ui.hud.HudLayoutEditor;
import odinclient.ui.hud.ModuleNotifications;
import odinclient.ui.scale.ScreenScaleCompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ModuleNotificationsHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void odinclient$beginHudFrame(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HudLayoutEditor.clearElements();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void odinclient$renderModuleNotifications(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try (ScreenScaleCompat.ScaleScope scope = ScreenScaleCompat.enter(context)) {
            ModuleNotifications.renderHud(context);
            HudLayoutEditor.renderOverlay(context, tickCounter.getTickProgress(true));
        }
    }
}
