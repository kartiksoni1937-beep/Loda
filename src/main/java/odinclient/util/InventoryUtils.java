package odinclient.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

import odinclient.mixin.accessor.SelectedSlotSyncInvoker;

import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public final class InventoryUtils {
    private InventoryUtils() {}

    public static SlotResult findInHotbarRange(Predicate<ItemStack> predicate, int from, int to) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && predicate != null) {
            int last = Math.min(to, client.player.getInventory().size() - 1);

            for (int i = Math.max(0, from); i <= last; i++) {
                if (predicate.test(client.player.getInventory().getStack(i))) {
                    return new SlotResult(i);
                }
            }
        }

        return new SlotResult(-1);
    }

    /**
     * Selects a hotbar slot the way the 1-9 keys do: the inventory field changes and the vanilla
     * client reports the new slot from its own interaction path. No packet is built here.
     */
    public static boolean selectHotbarSlot(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || slot < 0 || slot > 8) {
            return false;
        }

        client.player.getInventory().setSelectedSlot(slot);
        if (client.interactionManager != null) {
            ((SelectedSlotSyncInvoker) client.interactionManager).odinclient$syncSelectedSlot();
        }

        return true;
    }
}
