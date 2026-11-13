package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.util.EntityUtils;
import odinclient.util.InventoryUtils;
import odinclient.util.WorldUtils;

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
    private net.minecraft.entity.Entity pendingTarget;
    private int pendingSwapTicks;

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

    /**
     * Handles the vanilla attack click. Swaps to the mace and cancels the hit; the hit itself is
     * issued on the following tick so the server processes the slot change first.
     *
     * @return {@code true} when the vanilla hit must be cancelled
     */
    public static boolean onAttack() {
        MaceSwap module = instance;
        return module != null && module.isActive() && module.beginSwapOnClick();
    }

    private boolean beginSwapOnClick() {
        if (this.mc.player == null
                || this.mc.world == null
                || this.mc.currentScreen != null
                || !this.isCritReady()) {
            return false;
        }

        if (!(this.mc.crosshairTarget
                instanceof net.minecraft.util.hit.EntityHitResult entityHit)) {
            return false;
        }

        net.minecraft.entity.Entity target = entityHit.getEntity();
        if (target == null || target == this.mc.player || target.isRemoved()) {
            return false;
        }

        String itemPath = getItemPath(this.mc.player.getMainHandStack());
        if (this.onlySword.getValue() && !itemPath.endsWith("_sword")) {
            return false;
        }

        if (this.onlyAxe.getValue() && !itemPath.endsWith("_axe")) {
            return false;
        }

        int maceSlot = this.findMaceSlot();
        if (maceSlot < 0 || maceSlot == this.mc.player.getInventory().getSelectedSlot()) {
            return false;
        }

        this.previousSlot = this.mc.player.getInventory().getSelectedSlot();
        if (!WorldUtils.setHotbarSlot(maceSlot)) {
            this.previousSlot = -1;
            return false;
        }

        this.pendingTarget = target;
        this.pendingSwapTicks = 1;
        this.switchBackTicks = this.switchDelay.getValue() + 1;
        return true;
    }

    /** Mace crits only land while falling, off the ground and with a charged attack. */
    private boolean isCritReady() {
        return this.mc.player != null
                && this.mc.player.fallDistance > 0.0F
                && !this.mc.player.isOnGround()
                && this.mc.player.getAttackCooldownProgress(0.5F) > 0.9F;
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
    private void onTickStart(TickEvent.Pre event) {
        if (this.pendingTarget != null) {
            if (this.pendingSwapTicks-- > 0) {
                // Slot change happened on the previous tick; attack on this one.
                return;
            }

            net.minecraft.entity.Entity target = this.pendingTarget;
            this.pendingTarget = null;
            if (target.isRemoved()
                    || !this.isCritReady()
                    || !this.mc.player.getMainHandStack().isOf(Items.MACE)) {
                return;
            }

            WorldUtils.hitEntity(target);
            return;
        }

        if (this.switchBackTicks >= 0 && this.switchBackEnabled.getValue()) {
            if (this.switchBackTicks-- == 0) {
                this.switchBack();
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
        if (this.previousSlot >= 0 && this.previousSlot <= 8 && this.mc.player != null) {
            this.selectSlot(this.previousSlot);
        }

        this.previousSlot = -1;
        this.switchBackTicks = -1;
    }

    private void selectSlot(int slot) {
        InventoryUtils.selectHotbarSlot(slot);
    }

    private static String getItemPath(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }
}
