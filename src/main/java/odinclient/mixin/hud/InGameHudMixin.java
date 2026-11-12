package odinclient.mixin.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

import odinclient.event.Render2DEvent;
import odinclient.manager.ModuleManager;
import odinclient.module.ModuleContext;
import odinclient.module.hud.HudModule;
import odinclient.render.world.TracerLineBatch;
import odinclient.ui.draw.DrawContextHolder;
import odinclient.ui.scale.ScreenScaleCompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void odinclient$renderRadarBackground(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        TracerLineBatch.render(context);
        if (ModuleManager.get().getByName("hud") instanceof HudModule hud && hud.isActive()) {
            try (ScreenScaleCompat.ScaleScope scope = ScreenScaleCompat.enter(context)) {
                hud.renderRadarBackground(context);
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void odinclient$renderHud(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try (ScreenScaleCompat.ScaleScope scope = ScreenScaleCompat.enter(context)) {
            DrawContextHolder.set(context);

            try {
                ModuleContext.EVENT_BUS.post(
                        new Render2DEvent(context, tickCounter.getTickProgress(false)));
            } finally {
                DrawContextHolder.clear();
            }
        }
    }
}
