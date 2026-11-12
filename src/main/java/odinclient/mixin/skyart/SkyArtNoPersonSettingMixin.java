package odinclient.mixin.skyart;

import odinclient.module.Category;
import odinclient.module.render.sky.NoPersonSetting;
import odinclient.module.render.sky.SkyArts;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyArts.class)
public abstract class SkyArtNoPersonSettingMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void odinclient$addNoPersonChoice(Category category, CallbackInfo ci) {
        NoPersonSetting.initSetting((SkyArts) (Object) this);
    }
}
