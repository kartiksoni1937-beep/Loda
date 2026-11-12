package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;

@Environment(EnvType.CLIENT)
public final class AutoTotem extends Module {
    private final Setting<Double> health;
    private final Setting<Integer> delay;
    private int tickCounter;

    public AutoTotem(Category category) {
        super(category, "auto-totem", "Equips a totem in the offhand when health is low.");
        this.health =
                this.settings
                        .getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("health")
                                        .description("Equip a totem at or below this health value.")
                                        .defaultValue(20.0)
                                        .range(1.0, 20.0)
                                        .sliderRange(1.0, 20.0)
                                        .build());
        this.delay =
                this.settings
                        .getGeneral()
                        .add(
                                new IntSetting.Builder()
                                        .name("delay")
                                        .description("Ticks between inventory checks.")
                                        .defaultValue(1)
                                        .range(0, 20)
                                        .sliderRange(0, 20)
                                        .build());
    }

    @Override
    public void onActivate() {
        this.tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player != null
                && this.mc.interactionManager != null
                && this.mc.currentScreen == null) {
            if (this.tickCounter++ >= this.delay.getValue()) {
                this.tickCounter = 0;
                if (!(this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount()
                        > this.health.getValue())) {
                    if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                        if (this.mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
                            int totemSlot = this.findTotemSlot();
                            if (totemSlot != -1) {
                                int slot = totemSlot < 9 ? 36 + totemSlot : totemSlot;
                                int syncId = this.mc.player.playerScreenHandler.syncId;
                                this.mc.interactionManager.clickSlot(
                                        syncId, slot, 0, SlotActionType.PICKUP, this.mc.player);
                                this.mc.interactionManager.clickSlot(
                                        syncId, 45, 0, SlotActionType.PICKUP, this.mc.player);
                                this.mc.interactionManager.clickSlot(
                                        syncId, slot, 0, SlotActionType.PICKUP, this.mc.player);
                            }
                        }
                    }
                }
            }
        }
    }

    private int findTotemSlot() {
        for (int i = 0; i < 36; i++) {
            if (this.mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }

        return -1;
    }
}
