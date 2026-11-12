package odinclient.core;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import odinclient.module.Category;

public final class Categories {
    public static final Category MAIN =
            new Category("OdinClient", new ItemStack(Items.COMMAND_BLOCK));

    private Categories() {}
}
