package odinclient.module.render.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.NumberSetting;

@Environment(EnvType.CLIENT)
public final class ToolSize extends Module {
    private static ToolSize instance;
    private final Setting<Double> size;

    public ToolSize(Category category) {
        super(category, "tool-size", "Changes the first-person size of held tools and weapons.");
        this.size =
                this.settings
                        .getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("size")
                                        .description(
                                                "Changes the first-person size of held tools and"
                                                    + " weapons.")
                                        .defaultValue(1.0)
                                        .range(0.25, 1.0)
                                        .sliderRange(0.25, 1.0)
                                        .build());
        instance = this;
    }

    public static ToolSize getInstance() {
        return instance;
    }

    public boolean shouldReplaceSwing(Arm arm) {
        return false;
    }

    public boolean shouldScaleItem(
            LivingEntity entity, ItemStack stack, ItemDisplayContext context) {
        if (this.isFirstPersonActive()
                && entity == this.mc.player
                && isFirstPersonContext(context)
                && isToolOrWeapon(stack)) {
            if (Math.abs(this.size.getValue() - 1.0) > 1.0E-4) {
                return true;
            }
        }

        return false;
    }

    public void applyScale(MatrixStack matrices) {
        float scale = this.size.getValue().floatValue();
        if (scale < 1.0F) {
            matrices.translate(0.0F, (1.0F - scale) * 0.36F, 0.0F);
        }

        matrices.scale(scale, scale, scale);
    }

    public void applySwing(MatrixStack matrices, Arm arm, int swingTicks, float progress) {}

    private boolean isFirstPersonActive() {
        return this.isActive()
                && this.mc.player != null
                && this.mc.options != null
                && this.mc.options.getPerspective().isFirstPerson();
    }

    private static boolean isFirstPersonContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    private static boolean isToolOrWeapon(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.get(DataComponentTypes.TOOL) != null
                        || stack.get(DataComponentTypes.WEAPON) != null);
    }
}
