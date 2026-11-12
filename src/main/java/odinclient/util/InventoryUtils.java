package odinclient.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

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

    public static boolean selectHotbarSlot(int slot, boolean sendPacket) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && slot >= 0 && slot <= 8) {
            client.player.getInventory().setSelectedSlot(slot);
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            return true;
        } else {
            return false;
        }
    }

    public static InventoryUtils.SlotSwapAction swapSlot() {
        return new InventoryUtils.SlotSwapAction();
    }

    private static int toContainerSlot(int slot) {
        return slot >= 0 && slot < 9 ? 36 + slot : slot;
    }

    @Environment(EnvType.CLIENT)
    public static final class SlotSwapAction {
        private int slot = -1;

        public InventoryUtils.SlotSwapAction slot(int containerSlot) {
            this.slot = containerSlot;
            return this;
        }

        public void swapWithHotbar(int hotbarSlot) {
            this.sendSwap(hotbarSlot);
        }

        public void swapWithOffhand() {
            this.sendSwap(40);
        }

        private void sendSwap(int button) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.interactionManager != null && this.slot >= 0) {
                client.interactionManager.clickSlot(
                        client.player.currentScreenHandler.syncId,
                        InventoryUtils.toContainerSlot(this.slot),
                        button,
                        SlotActionType.SWAP,
                        client.player);
            }
        }
    }
}
