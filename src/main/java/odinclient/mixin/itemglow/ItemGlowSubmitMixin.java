package odinclient.mixin.itemglow;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;

import odinclient.module.render.itemglow.ItemGlowPhaseRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BatchingRenderCommandQueue.class)
public abstract class ItemGlowSubmitMixin {
    @Inject(method = "submitItem", at = @At("HEAD"))
    private void odinclient$captureHeldItemGeometry(
            MatrixStack matrices,
            ItemDisplayContext displayContext,
            int light,
            int overlay,
            int color,
            int[] tints,
            List<BakedQuad> quads,
            RenderLayer layer,
            Glint glint,
            CallbackInfo ci) {
        ItemGlowPhaseRenderer.submitItem(
                matrices, displayContext, light, overlay, color, tints, quads, layer, glint);
    }
}
