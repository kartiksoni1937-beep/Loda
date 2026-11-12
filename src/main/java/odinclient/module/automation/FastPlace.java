package odinclient.module.automation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;

import odinclient.mixin.accessor.MinecraftClientAccessor;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;

@Environment(EnvType.CLIENT)
public final class FastPlace extends Module {
    private static FastPlace instance;
    private final SettingGroup generalSettings = this.settings.getGeneral();
    private final Setting<Boolean> onlyXp =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("only-xp")
                            .description("Only removes the placement delay for experience bottles.")
                            .defaultValue(false)
                            .build());
    private final Setting<Boolean> blocks =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("blocks")
                            .description("Apply Fast Place to block items.")
                            .defaultValue(true)
                            .visibleWhen(() -> !Boolean.TRUE.equals(this.onlyXp.getValue()))
                            .build());
    private final Setting<Boolean> items =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("items")
                            .description("Apply Fast Place to supported non-block items.")
                            .defaultValue(true)
                            .visibleWhen(() -> !Boolean.TRUE.equals(this.onlyXp.getValue()))
                            .build());
    private final Setting<Integer> delay =
            this.generalSettings.add(
                    new IntSetting.Builder()
                            .name("delay")
                            .description("Cooldown after each use action. Lower is faster.")
                            .defaultValue(0)
                            .range(0, 10)
                            .sliderRange(0, 10)
                            .build());

    public FastPlace(Category category) {
        super(
                category,
                "fast-place",
                "Reduces the client-side delay between supported item placements.");
        instance = this;
    }

    public static void applyFastPlace(MinecraftClient client) {
        if (instance != null && instance.isActive() && instance.shouldApply(client)) {
            ((MinecraftClientAccessor) client)
                    .odinclient$setItemUseCooldown(instance.delay.getValue());
        }
    }

    private boolean shouldApply(MinecraftClient client) {
        if (client != null && client.player != null) {
            ItemStack mainHand = client.player.getMainHandStack();
            ItemStack offHand = client.player.getOffHandStack();
            boolean mainXp = mainHand.isOf(Items.EXPERIENCE_BOTTLE);
            boolean offXp = offHand.isOf(Items.EXPERIENCE_BOTTLE);
            if (Boolean.TRUE.equals(this.onlyXp.getValue())) {
                if (!mainXp && !offXp) {
                    return false;
                }
            } else {
                Item mainItem = mainHand.getItem();
                Item offItem = offHand.getItem();
                if (mainItem instanceof BlockItem || offItem instanceof BlockItem) {
                    if (!Boolean.TRUE.equals(this.blocks.getValue())) {
                        return false;
                    }
                } else if (!Boolean.TRUE.equals(this.items.getValue())) {
                    return false;
                }
            }

            if (this.isFood(mainHand) || this.isFood(offHand)) {
                return false;
            }

            if (mainHand.isOf(Items.RESPAWN_ANCHOR)
                    || mainHand.isOf(Items.GLOWSTONE)
                    || offHand.isOf(Items.RESPAWN_ANCHOR)
                    || offHand.isOf(Items.GLOWSTONE)) {
                return false;
            }

            return !(mainHand.getItem() instanceof RangedWeaponItem)
                    && !(offHand.getItem() instanceof RangedWeaponItem);
        } else {
            return false;
        }
    }

    private boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponents().contains(DataComponentTypes.FOOD);
    }
}
