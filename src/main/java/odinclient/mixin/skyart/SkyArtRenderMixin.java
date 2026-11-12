package odinclient.mixin.skyart;

import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;

import odinclient.module.render.sky.SkyArtRenderer;
import odinclient.module.render.sky.SkyArts;
import odinclient.module.render.sky.SkyboxRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class SkyArtRenderMixin {
    @Inject(method = "renderCelestialBodies", at = @At("TAIL"))
    private void odinclient$renderSkyArt(
            MatrixStack matrices,
            float sunAngle,
            float moonAngle,
            float starBrightness,
            MoonPhase moonPhase,
            float rainGradient,
            float thunderGradient,
            CallbackInfo ci) {
        if (SkyArts.shouldRender()) {
            SkyboxRenderer.render(SkyArts.selectedSky());
            SkyArtRenderer.render(SkyArts.selectedArtwork());
        }
    }
}
