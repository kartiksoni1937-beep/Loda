package odinclient.mixin.skyart;

import odinclient.module.render.sky.NoPersonSetting;
import odinclient.module.render.sky.SkyArtRenderer;
import odinclient.module.render.sky.SkyArts;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyArtRenderer.class)
public abstract class SkyArtNoPersonRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private static void odinclient$hidePerson(SkyArts.Artwork artwork, CallbackInfo ci) {
        if (NoPersonSetting.isEnabled()) {
            ci.cancel();
        }
    }
}
