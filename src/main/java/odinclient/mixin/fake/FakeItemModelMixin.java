package odinclient.mixin.fake;

import net.minecraft.block.Blocks;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import odinclient.module.fake.FakeElytra;
import odinclient.module.fake.FakeSpawner;
import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelManager.class)
public abstract class FakeItemModelMixin {
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack odinclient$fakeItemModel(ItemStack stack) {
        ItemStack override = GambleRigger.getStackDisplayOverride(stack);
        if (override != null) {
            return override;
        } else if (FakeElytra.isEnabled() && stack.isOf(Items.GOLDEN_CHESTPLATE)) {
            return stack.withItem(Items.ELYTRA);
        } else {
            return FakeSpawner.isEnabled() && stack.isOf(Items.YELLOW_STAINED_GLASS)
                    ? stack.withItem(Blocks.SPAWNER)
                    : stack;
        }
    }
}
