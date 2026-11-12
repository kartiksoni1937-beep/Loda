package odinclient.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;

@Environment(EnvType.CLIENT)
public final class ItemNames {
    private ItemNames() {}

    public static String getStackName(ItemStack stack) {
        return stack == null ? "" : stack.getName().getString();
    }
}
