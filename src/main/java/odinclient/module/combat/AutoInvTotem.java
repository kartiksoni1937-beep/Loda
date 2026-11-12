package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.util.InventoryActions;

@Environment(EnvType.CLIENT)
public final class AutoInvTotem extends Module {
    private final Setting<Integer> delay =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("delay")
                                    .description("Ticks between inventory operations.")
                                    .defaultValue(0)
                                    .range(0, 20)
                                    .sliderRange(0, 20)
                                    .build());
    private final Setting<Boolean> hotbar =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("hotbar")
                                    .description("Also keep a totem in the preferred hotbar slot.")
                                    .defaultValue(true)
                                    .build());
    private final Setting<Integer> totemSlot =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("totem-slot")
                                    .description("Preferred hotbar slot.")
                                    .defaultValue(1)
                                    .range(1, 9)
                                    .sliderRange(1, 9)
                                    .build());
    private final Setting<Boolean> autoSelectSlot =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("auto-select-slot")
                                    .description(
                                            "Select the preferred hotbar slot while filling it.")
                                    .defaultValue(false)
                                    .build());
    private final Setting<Boolean> autoOpenInventory =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("auto-open-inventory")
                                    .description("Open the inventory when a totem needs moving.")
                                    .defaultValue(false)
                                    .build());
    private final Setting<Integer> closeDelay =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("close-delay")
                                    .description(
                                            "Ticks before an automatically opened inventory"
                                                + " closes.")
                                    .defaultValue(0)
                                    .range(0, 20)
                                    .sliderRange(0, 20)
                                    .build());
    private int delayTicks;
    private int closeTicks;
    private boolean openedByModule;

    public AutoInvTotem(Category category) {
        super(
                category,
                "auto-inv-totem",
                "Equips inventory totems into the offhand and preferred hotbar slot.");
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!InventoryActions.isReady(this.mc)) {
            this.reset();
        } else {
            boolean inInventory = this.mc.currentScreen instanceof InventoryScreen;
            boolean needsOffhand = !this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
            int hotbarIndex = this.totemSlot.getValue() - 1;
            boolean hotbarHasTotem =
                    this.mc
                            .player
                            .getInventory()
                            .getStack(hotbarIndex)
                            .isOf(Items.TOTEM_OF_UNDYING);
            boolean needsHotbar = this.hotbar.getValue() && !hotbarHasTotem;

            int foundSlot = InventoryActions.findInventorySlot(this.mc, Items.TOTEM_OF_UNDYING);
            if ((needsOffhand || needsHotbar)
                    && foundSlot >= 0
                    && !inInventory
                    && this.autoOpenInventory.getValue()
                    && this.mc.currentScreen == null) {
                this.mc.setScreen(new InventoryScreen(this.mc.player));
                this.openedByModule = true;
                this.delayTicks = this.delay.getValue();
            } else if (!inInventory) {
                if (!this.openedByModule) {
                    this.closeTicks = 0;
                }
            } else if (this.delayTicks > 0) {
                this.delayTicks--;
            } else {
                if (this.autoSelectSlot.getValue()) {
                    this.mc.player.getInventory().setSelectedSlot(hotbarIndex);
                }

                if (needsOffhand && foundSlot >= 0) {
                    InventoryActions.swapToOffhand(this.mc, foundSlot);
                    this.delayTicks = this.delay.getValue();
                } else {
                    int inventorySlot =
                            InventoryActions.findInventorySlot(this.mc, Items.TOTEM_OF_UNDYING);
                    if (needsHotbar && inventorySlot >= 0 && inventorySlot != hotbarIndex) {
                        InventoryActions.swapToHotbar(this.mc, inventorySlot, hotbarIndex);
                        this.delayTicks = this.delay.getValue();
                    } else if (this.openedByModule
                            && !needsOffhand
                            && (!this.hotbar.getValue() || hotbarHasTotem)) {
                        int elapsedTicks = this.closeTicks++;
                        if (elapsedTicks >= this.closeDelay.getValue()) {
                            this.mc.setScreen(null);
                            this.openedByModule = false;
                            this.closeTicks = 0;
                        }
                    }
                }
            }
        }
    }

    private void reset() {
        this.delayTicks = 0;
        this.closeTicks = 0;
        this.openedByModule = false;
    }
}
