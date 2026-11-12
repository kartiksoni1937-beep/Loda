package odinclient.mixin.fake;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

import odinclient.module.fake.FakeElytra;
import odinclient.module.hud.WorthTooltip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class FakeElytraItemMixin {
    @Shadow
    public abstract boolean isOf(Item item);

    @Inject(method = "getItemName", at = @At("HEAD"), cancellable = true)
    private void odinclient$fakeElytraName(CallbackInfoReturnable<Text> cir) {
        if (this.fakeChestplate()) {
            cir.setReturnValue(this.visualCopy().getItemName());
        }
    }

    @Inject(method = "getRarity", at = @At("HEAD"), cancellable = true)
    private void odinclient$fakeElytraRarity(CallbackInfoReturnable<Rarity> cir) {
        if (this.fakeChestplate()) {
            cir.setReturnValue(this.visualCopy().getRarity());
        }
    }

    @Inject(method = "getTooltip", at = @At("HEAD"), cancellable = true)
    private void odinclient$fakeElytraTooltip(
            TooltipContext context,
            PlayerEntity player,
            TooltipType type,
            CallbackInfoReturnable<List<Text>> cir) {
        if (this.fakeChestplate()) {
            List<Text> lines =
                    WorthTooltip.insertWorthLine(
                            this.visualCopy().getTooltip(context, player, type),
                            FakeElytra.getPriceText());
            cir.setReturnValue(lines);
        }
    }

    @Inject(method = "isItemBarVisible", at = @At("HEAD"), cancellable = true)
    private void odinclient$fakeElytraItemBar(CallbackInfoReturnable<Boolean> cir) {
        if (this.fakeChestplate()) {
            cir.setReturnValue(this.visualCopy().isItemBarVisible());
        }
    }

    @Inject(method = "getItemBarStep", at = @At("HEAD"), cancellable = true)
    private void odinclient$fakeElytraItemBarStep(CallbackInfoReturnable<Integer> cir) {
        if (this.fakeChestplate()) {
            cir.setReturnValue(this.visualCopy().getItemBarStep());
        }
    }

    @Inject(method = "getItemBarColor", at = @At("HEAD"), cancellable = true)
    private void odinclient$fakeElytraItemBarColor(CallbackInfoReturnable<Integer> cir) {
        if (this.fakeChestplate()) {
            cir.setReturnValue(this.visualCopy().getItemBarColor());
        }
    }

    @Unique
    private boolean fakeChestplate() {
        return FakeElytra.isEnabled() && this.isOf(Items.GOLDEN_CHESTPLATE);
    }

    @Unique
    private ItemStack visualCopy() {
        return ((ItemStack) (Object) this).withItem(Items.ELYTRA);
    }
}
