package odinclient.core;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import odinclient.module.Category;

public final class ModuleCategories {
    public static final Category MAIN =
            new Category("scamming", new ItemStack(Items.COMMAND_BLOCK));

    private ModuleCategories() {}
}
