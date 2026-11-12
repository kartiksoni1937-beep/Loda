package odinclient.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

import org.joml.Vector3d;

@Environment(EnvType.CLIENT)
public final class EntityUtils {
    private EntityUtils() {}

    public static Vector3d getInterpolatedPos(Vector3d out, Entity entity, double tickDelta) {
        return out.set(
                entity.lastX + (entity.getX() - entity.lastX) * tickDelta,
                entity.lastY + (entity.getY() - entity.lastY) * tickDelta,
                entity.lastZ + (entity.getZ() - entity.lastZ) * tickDelta);
    }

    public static int getEnchantmentLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && stack != null && key != null) {
            RegistryEntry<Enchantment> entry =
                    client.world
                            .getRegistryManager()
                            .getOrThrow(RegistryKeys.ENCHANTMENT)
                            .getEntry(key.getValue())
                            .orElse(null);
            return entry == null ? 0 : EnchantmentHelper.getLevel(entry, stack);
        } else {
            return 0;
        }
    }
}
