package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.mixin.accessor.HandledScreenAccessor;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.util.InventoryActions;

@Environment(EnvType.CLIENT)
public final class HoverTotem extends Module {
    private final Setting<Integer> tickDelay =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("tick-delay")
                                    .description("Ticks to wait between hover operations.")
                                    .defaultValue(0)
                                    .range(0, 20)
                                    .sliderRange(0, 20)
                                    .build());
    private final Setting<Boolean> hotbarTotem =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("hotbar-totem")
                                    .description("Also place a totem in the preferred hotbar slot.")
                                    .defaultValue(true)
                                    .build());
    private final Setting<Integer> hotbarSlot =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("hotbar-slot")
                                    .description("Preferred hotbar slot.")
                                    .defaultValue(1)
                                    .range(1, 9)
                                    .sliderRange(1, 9)
                                    .build());
    private final Setting<Boolean> autoSwitch =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("auto-switch-to-totem")
                                    .description(
                                            "Automatically switches to the preferred totem slot"
                                                + " while the inventory is open.")
                                    .defaultValue(false)
                                    .build());
    private int delayTicksLeft;

    public HoverTotem(Category category) {
        super(
                category,
                "hover-totem",
                "Equips a hovered inventory totem into the offhand and optional hotbar slot.");
    }

    @Override
    public void onActivate() {
        this.resetDelay();
    }

    @Override
    public void onDeactivate() {
        this.resetDelay();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (InventoryActions.isReady(this.mc)
                && this.mc.currentScreen instanceof InventoryScreen screen) {
            if (this.autoSwitch.getValue()) {
                this.mc.player.getInventory().setSelectedSlot(this.hotbarSlot.getValue() - 1);
            }

            Slot focused = ((HandledScreenAccessor) screen).odinclient$getFocusedSlot();
            if (focused != null
                    && focused.inventory == this.mc.player.getInventory()
                    && focused.getIndex() <= 35
                    && !focused.getStack().isEmpty()
                    && focused.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                if (this.delayTicksLeft > 0) {
                    this.delayTicksLeft--;
                } else {
                    int syncId = screen.getScreenHandler().syncId;
                    int slotId = focused.id;
                    if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                        this.swapToOffhand(syncId, slotId);
                    } else if (this.hotbarTotem.getValue()) {
                        int hotbarIndex = this.hotbarSlot.getValue() - 1;
                        if (!this.mc
                                .player
                                .getInventory()
                                .getStack(hotbarIndex)
                                .isOf(Items.TOTEM_OF_UNDYING)) {
                            this.swapToHotbar(syncId, slotId, hotbarIndex);
                        }
                    }
                }
            }
        } else {
            this.resetDelay();
        }
    }

    private void swapToOffhand(int syncId, int slotId) {
        InventoryActions.clickSlot(this.mc, syncId, slotId, 40, SlotActionType.SWAP);
        this.resetDelay();
    }

    private void swapToHotbar(int syncId, int slotId, int hotbarIndex) {
        InventoryActions.clickSlot(this.mc, syncId, slotId, hotbarIndex, SlotActionType.SWAP);
        this.resetDelay();
    }

    private void resetDelay() {
        this.delayTicksLeft = this.tickDelay.getValue();
    }
}
