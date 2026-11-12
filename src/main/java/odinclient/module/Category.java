package odinclient.module;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class Category {
    public final String name;
    public final ItemStack icon;

    public Category(String name, ItemStack icon) {
        this.name = Objects.requireNonNull(name, "name");
        this.icon = icon;
    }
}
