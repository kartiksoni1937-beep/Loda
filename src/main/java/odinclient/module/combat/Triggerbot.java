package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.EnumSetting;
import odinclient.util.InputSimulator;
import odinclient.setting.type.IntSetting;

import java.util.Random;

@Environment(EnvType.CLIENT)
public final class Triggerbot extends Module {
    private final Setting<Integer> minDelay;
    private final Setting<Integer> maxDelay;
    private final Setting<Boolean> onlyItem;
    private final Setting<Triggerbot.ItemFilter> itemFilter;
    private final Setting<Boolean> onlyCrit;
    private final Random random = new Random();
    private int tickCounter;
    private int requiredTicks = 10;

    public Triggerbot(Category category) {
        super(category, "triggerbot", "Automatically attacks living entities under the crosshair.");
        SettingGroup general = this.settings.getGeneral();
        this.minDelay =
                general.add(
                        new IntSetting.Builder()
                                .name("min-delay")
                                .description("Minimum delay between attacks, in milliseconds.")
                                .defaultValue(450)
                                .range(0, 5000)
                                .sliderRange(0, 5000)
                                .build());
        this.maxDelay =
                general.add(
                        new IntSetting.Builder()
                                .name("max-delay")
                                .description("Maximum delay between attacks, in milliseconds.")
                                .defaultValue(550)
                                .range(0, 5000)
                                .sliderRange(0, 5000)
                                .build());
        this.onlyItem =
                general.add(
                        new BooleanSetting.Builder()
                                .name("only-item")
                                .description("Only attacks while the selected item type is held.")
                                .defaultValue(false)
                                .build());
        this.itemFilter =
                general.add(
                        new EnumSetting.Builder<Triggerbot.ItemFilter>()
                                .name("item-filter")
                                .description("Item type required when Only Item is enabled.")
                                .defaultValue(Triggerbot.ItemFilter.Sword)
                                .build());
        this.onlyCrit =
                general.add(
                        new BooleanSetting.Builder()
                                .name("only-crit")
                                .description("Only attacks while falling for a critical hit.")
                                .defaultValue(false)
                                .build());
    }

    @Override
    public void onActivate() {
        this.tickCounter = 0;
        this.rollAttackDelay();
    }

    @Override
    public void onDeactivate() {
        this.tickCounter = 0;
    }

    @EventHandler
    private void onTickStart(TickEvent.Pre event) {
        if (this.mc.player != null
                && this.mc.interactionManager != null
                && this.mc.currentScreen == null) {
            if (this.mc.crosshairTarget != null
                    && this.mc.crosshairTarget.getType() == Type.ENTITY) {
                Entity target = ((EntityHitResult) this.mc.crosshairTarget).getEntity();
                if (target instanceof LivingEntity && target != this.mc.player) {
                    if (this.onlyItem.getValue()) {
                        Item heldItem = this.mc.player.getMainHandStack().getItem();
                        if (this.itemFilter.getValue() == Triggerbot.ItemFilter.Sword
                                && !this.isSword(heldItem)) {
                            return;
                        }

                        if (this.itemFilter.getValue() == Triggerbot.ItemFilter.Axe
                                && !(heldItem instanceof AxeItem)) {
                            return;
                        }

                        if (this.itemFilter.getValue() == Triggerbot.ItemFilter.Hand
                                && !this.mc.player.getMainHandStack().isEmpty()) {
                            return;
                        }
                    }

                    if ((!this.onlyCrit.getValue()
                                    || !this.mc.player.isOnGround()
                                            && !(this.mc.player.fallDistance <= 0.0))
                            && !(this.mc.player.getAttackCooldownProgress(0.5F) < 1.0F)) {
                        this.tickCounter++;
                        if (this.tickCounter >= this.requiredTicks) {
                            InputSimulator.click(InputSimulator.Click.LEFT);
                            this.tickCounter = 0;
                            this.rollAttackDelay();
                        }
                    }
                }
            } else {
                this.tickCounter = 0;
            }
        }
    }

    private boolean isSword(Item item) {
        return item == Items.WOODEN_SWORD
                || item == Items.STONE_SWORD
                || item == Items.IRON_SWORD
                || item == Items.GOLDEN_SWORD
                || item == Items.DIAMOND_SWORD
                || item == Items.NETHERITE_SWORD;
    }

    private void rollAttackDelay() {
        int minMs = this.minDelay.getValue();
        int maxMs = this.maxDelay.getValue();
        if (maxMs < minMs) {
            maxMs = minMs;
        }

        int extraMs = maxMs > minMs ? this.random.nextInt(maxMs - minMs + 1) : 0;
        this.requiredTicks = (minMs + extraMs + 49) / 50;
    }

    private static enum ItemFilter {
        Sword,
        Axe,
        Hand;
    }
}
