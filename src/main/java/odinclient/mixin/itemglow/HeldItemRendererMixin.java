package odinclient.mixin.itemglow;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

import odinclient.module.render.visual.ToolSize;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void odinclient$replaceLocalToolSwing(
            float swingProgress, MatrixStack matrices, int light, Arm arm, CallbackInfo ci) {
        ToolSize toolSize = ToolSize.getInstance();
        if (toolSize != null && toolSize.shouldReplaceSwing(arm)) {
            toolSize.applySwing(matrices, arm, light, swingProgress);
            ci.cancel();
        }
    }

    @Inject(
            method =
                    "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"))
    private void odinclient$scaleLocalTool(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            CallbackInfo ci) {
        ToolSize toolSize = ToolSize.getInstance();
        if (toolSize != null && toolSize.shouldScaleItem(entity, stack, displayContext)) {
            toolSize.applyScale(matrices);
        }
    }
}
