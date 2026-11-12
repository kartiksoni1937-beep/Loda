package odinclient.mixin.itemglow;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;

import odinclient.module.render.itemglow.ItemGlow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class ItemGlowColorMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void odinclient$colorDroppedItemGlow(
            Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        int color = ItemGlow.getEntityGlowColor(entity);
        if (color != 0) {
            state.outlineColor = color;
        }
    }
}
