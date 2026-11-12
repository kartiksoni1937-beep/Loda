package odinclient.mixin.render;

import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;

import odinclient.module.render.visual.StrawHatFeatureRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class ChinaHeadPlayerRendererMixin
        extends LivingEntityRenderer<LivingEntity, PlayerEntityRenderState, PlayerEntityModel> {
    protected ChinaHeadPlayerRendererMixin(Context context, PlayerEntityModel model, float scale) {
        super(context, model, scale);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void odinclient$addFittedHat(Context context, boolean slim, CallbackInfo ci) {
        this.addFeature(new StrawHatFeatureRenderer(this));
    }
}
