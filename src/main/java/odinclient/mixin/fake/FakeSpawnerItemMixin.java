package odinclient.mixin.fake;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import odinclient.module.fake.FakeSpawner;
import odinclient.module.hud.WorthTooltip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class FakeSpawnerItemMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void odinclient$spawnerItemName(CallbackInfoReturnable<Text> cir) {
        if (this.isFakeSpawner()) {
            cir.setReturnValue(Text.literal("Spawner").formatted(Formatting.LIGHT_PURPLE));
        }
    }

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void odinclient$spawnerTooltip(
            TooltipContext context,
            PlayerEntity player,
            TooltipType type,
            CallbackInfoReturnable<List<Text>> cir) {
        if (this.isFakeSpawner()) {
            int count = ((ItemStack) (Object) this).getCount();
            List<Text> lines =
                    WorthTooltip.insertWorthLineMultiplied(
                            cir.getReturnValue(), FakeSpawner.getPriceText(), count);
            lines.add(
                    Math.min(1, lines.size()),
                    Text.literal(FakeSpawner.getMobType().getDisplayName())
                            .formatted(Formatting.YELLOW));
            lines.add(Text.empty());
            lines.add(Text.literal("Interact with Spawn Egg:").formatted(Formatting.GRAY));
            lines.add(Text.literal("  Sets Mob Type").formatted(Formatting.BLUE));
            cir.setReturnValue(lines);
        }
    }

    private boolean isFakeSpawner() {
        return FakeSpawner.isEnabled()
                && ((ItemStack) (Object) this).isOf(Items.YELLOW_STAINED_GLASS);
    }
}
