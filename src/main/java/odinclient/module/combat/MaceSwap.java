package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.mixin.accessor.SelectedSlotSyncInvoker;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.util.EntityUtils;

@Environment(EnvType.CLIENT)
public final class MaceSwap extends Module {
    private static MaceSwap instance;
    private final Setting<Boolean> windBurst;
    private final Setting<Boolean> breach;
    private final Setting<Boolean> onlySword;
    private final Setting<Boolean> onlyAxe;
    private final Setting<Boolean> switchBackEnabled;
    private final Setting<Integer> switchDelay;
    private int previousSlot = -1;
    private int switchBackTicks = -1;

    public MaceSwap(Category category) {
        super(category, "mace-swap", "Switches to a mace when attacking.");
        instance = this;
        this.windBurst =
                this.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("wind-burst")
                                        .description("Allow maces enchanted with Wind Burst.")
                                        .defaultValue(true)
                                        .build());
        this.breach =
                this.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("breach")
                                        .description("Allow maces enchanted with Breach.")
                                        .defaultValue(true)
                                        .build());
        this.onlySword =
                this.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("only-sword")
                                        .description("Only swap while a sword is selected.")
                                        .defaultValue(false)
                                        .build());
        this.onlyAxe =
                this.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("only-axe")
                                        .description("Only swap while an axe is selected.")
                                        .defaultValue(false)
                                        .build());
        this.switchBackEnabled =
                this.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("switch-back")
                                        .description(
                                                "Switch back to the previous hotbar slot after"
                                                    + " attacking.")
                                        .defaultValue(true)
                                        .build());
        this.switchDelay =
                this.settings
                        .getGeneral()
                        .add(
                                new IntSetting.Builder()
                                        .name("switch-delay")
                                        .description("Ticks to wait before switching back.")
                                        .defaultValue(0)
                                        .range(0, 20)
                                        .sliderRange(0, 20)
                                        .build());
    }

    public static void onAttack() {
        MaceSwap module = instance;
        if (module != null && module.isActive()) {
            module.swapToMace();
        }
    }

    @Override
    public void onActivate() {
        this.previousSlot = -1;
        this.switchBackTicks = -1;
    }

    @Override
    public void onDeactivate() {
        this.switchBack();
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (this.switchBackTicks >= 0 && this.switchBackEnabled.getValue()) {
            if (this.switchBackTicks-- == 0) {
                this.switchBack();
            }
        }
    }

    private void swapToMace() {
        if (this.mc.player != null
                && this.mc.world != null
                && this.mc.interactionManager != null
                && this.mc.currentScreen == null) {
            String itemPath = getItemPath(this.mc.player.getMainHandStack());
            if (!this.onlySword.getValue() || itemPath.endsWith("_sword")) {
                if (!this.onlyAxe.getValue() || itemPath.endsWith("_axe")) {
                    int maceSlot = this.findMaceSlot();
                    if (maceSlot >= 0) {
                        this.previousSlot = this.mc.player.getInventory().getSelectedSlot();
                        this.selectSlot(maceSlot);
                        this.switchBackTicks = this.switchDelay.getValue();
                    }
                }
            }
        }
    }

    private int findMaceSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.MACE)) {
                if (!this.windBurst.getValue() && !this.breach.getValue()) {
                    return i;
                }

                boolean hasWindBurst =
                        this.windBurst.getValue()
                                && EntityUtils.getEnchantmentLevel(stack, Enchantments.WIND_BURST)
                                        > 0;
                boolean hasBreach =
                        this.breach.getValue()
                                && EntityUtils.getEnchantmentLevel(stack, Enchantments.BREACH) > 0;
                if (hasWindBurst || hasBreach) {
                    return i;
                }
            }
        }

        return -1;
    }

    private void switchBack() {
        if (this.previousSlot >= 0
                && this.previousSlot <= 8
                && this.mc.player != null
                && this.mc.interactionManager != null) {
            this.selectSlot(this.previousSlot);
        }

        this.previousSlot = -1;
        this.switchBackTicks = -1;
    }

    private void selectSlot(int slot) {
        this.mc.player.getInventory().setSelectedSlot(slot);
        ((SelectedSlotSyncInvoker) this.mc.interactionManager).odinclient$syncSelectedSlot();
    }

    private static String getItemPath(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }
}
