package odinclient.mixin.render;

import net.minecraft.client.render.WorldRenderer;

import odinclient.module.render.esp.BlockOutlines;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class BlockOutlineMixin {
    @ModifyArg(
            method =
                    "renderTargetBlockOutline(Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/util/math/MatrixStack;ZLnet/minecraft/client/render/state/WorldRenderState;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;IF)V",
                            ordinal = 1),
            index = 6)
    private int odinclient$customBlockOutlineColor(int color) {
        return BlockOutlines.getOutlineColor(color);
    }

    @Inject(
            method =
                    "renderTargetBlockOutline(Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/util/math/MatrixStack;ZLnet/minecraft/client/render/state/WorldRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void odinclient$replaceWithFullBlockOutline(CallbackInfo ci) {
        if (BlockOutlines.isFullOutline()) {
            ci.cancel();
        }
    }
}
