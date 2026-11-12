package odinclient.mixin.gamble;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;

import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class GambleItemRenderMixin {
    @Shadow @Final private ItemModelManager itemModelManager;

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void odinclient$replaceDroppedItemVisual(
            ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ItemStack stack = GambleRigger.getEntityDisplayStack(entity);
        if (stack != null) {
            state.update(entity, stack, this.itemModelManager);
            state.displayName = null;
            state.nameLabelPos = null;
        }
    }
}
