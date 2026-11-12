package odinclient.mixin.gamble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = ItemStack.class, priority = 1100)
public abstract class GambleInventoryItemMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void odinclient$gambleName(CallbackInfoReturnable<Text> cir) {
        ItemStack override = GambleRigger.getStackDisplayOverride((ItemStack) (Object) this);
        if (override != null) {
            cir.setReturnValue(override.getName());
        }
    }

    @Inject(method = "getTooltip", at = @At("HEAD"), cancellable = true)
    private void odinclient$gambleTooltip(
            TooltipContext context,
            PlayerEntity player,
            TooltipType type,
            CallbackInfoReturnable<List<Text>> cir) {
        ItemStack override = GambleRigger.getStackDisplayOverride((ItemStack) (Object) this);
        if (override != null) {
            cir.setReturnValue(override.getTooltip(context, player, type));
        }
    }
}
