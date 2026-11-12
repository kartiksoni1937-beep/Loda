package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.util.WorldUtils;

@Environment(EnvType.CLIENT)
public final class ShieldBreaker extends Module {
    private final Setting<Boolean> switchBack =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("switch-back")
                                    .description(
                                            "Return to the previous hotbar slot after the shield is"
                                                + " lowered.")
                                    .defaultValue(true)
                                    .build());
    private final Setting<Integer> switchDelay =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("switch-delay")
                                    .description("Ticks to wait before switching and attacking.")
                                    .defaultValue(0)
                                    .range(0, 10)
                                    .sliderRange(0, 10)
                                    .build());
    private final Setting<Double> minStrength;
    private int ticksSinceTarget;
    private int previousSlot;
    private int axeSlot;
    private boolean attacked;

    public ShieldBreaker(Category category) {
        super(
                category,
                "shield-breaker",
                "Breaks a blocking player's shield with the strongest hotbar axe.");
        this.minStrength =
                this.settings
                        .getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("min-strength")
                                        .description("Minimum vanilla attack cooldown strength.")
                                        .defaultValue(0.9)
                                        .range(0.1, 1.0)
                                        .sliderRange(0.1, 1.0)
                                        .build());
        this.previousSlot = -1;
        this.axeSlot = -1;
    }

    @Override
    public void onDeactivate() {
        this.restoreSlot();
        this.resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        PlayerEntity target = this.getBlockingTarget();
        if (target == null) {
            this.restoreSlot();
            this.resetState();
        } else {
            this.ticksSinceTarget++;
            if (!this.attacked
                    && this.ticksSinceTarget > this.switchDelay.getValue()
                    && !(this.mc.player.getAttackCooldownProgress(0.0F)
                            < this.minStrength.getValue())) {
                int slot = this.findBestAxeSlot();
                if (slot != -1) {
                    if (this.mc.player.getInventory().getSelectedSlot() != slot) {
                        this.previousSlot = this.mc.player.getInventory().getSelectedSlot();
                        this.axeSlot = slot;
                        WorldUtils.setHotbarSlot(slot);
                        // Swap on this tick, attack on the next one so the server sees the
                        // item change before the attack.
                        return;
                    }

                    WorldUtils.hitEntity(target);
                    this.mc.player.swingHand(Hand.MAIN_HAND);
                    this.mc.player.resetTicksSinceLastAttack();
                    this.attacked = true;
                }
            }
        }
    }

    private PlayerEntity getBlockingTarget() {
        if (this.mc.player != null
                && this.mc.world != null
                && this.mc.interactionManager != null
                && this.mc.currentScreen == null) {
            HitResult hit = this.mc.crosshairTarget;
            if (hit instanceof EntityHitResult) {
                if (((EntityHitResult) hit).getEntity() instanceof PlayerEntity player
                        && player != this.mc.player
                        && player.isAlive()
                        && player.isBlocking()) {
                    return player;
                }

                return null;
            }
        }

        return null;
    }

    private int findBestAxeSlot() {
        int bestSlot = -1;
        int bestTier = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) {
                int tier =
                        stack.isOf(Items.NETHERITE_AXE)
                                ? 6
                                : stack.isOf(Items.DIAMOND_AXE)
                                        ? 5
                                        : stack.isOf(Items.IRON_AXE)
                                                ? 4
                                                : stack.isOf(Items.GOLDEN_AXE)
                                                        ? 3
                                                        : stack.isOf(Items.STONE_AXE)
                                                                ? 2
                                                                : stack.isOf(Items.WOODEN_AXE)
                                                                        ? 1
                                                                        : 0;

                if (tier > bestTier) {
                    bestTier = tier;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private void restoreSlot() {
        if (this.mc.player != null
                && this.switchBack.getValue()
                && this.previousSlot >= 0
                && this.previousSlot <= 8
                && this.mc.player.getInventory().getSelectedSlot() == this.axeSlot) {
            this.mc.player.getInventory().setSelectedSlot(this.previousSlot);
        }

        this.previousSlot = -1;
        this.axeSlot = -1;
    }

    private void resetState() {
        this.ticksSinceTarget = 0;
        this.attacked = false;
    }
}
