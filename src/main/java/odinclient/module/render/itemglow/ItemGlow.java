package odinclient.module.render.itemglow;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemDisplayContext;

import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;

@Environment(EnvType.CLIENT)
public final class ItemGlow extends Module {
    private static ItemGlow instance;
    private final Setting<EntityColor> color;
    private final Setting<Double> range;
    private final Setting<Double> heldWidth;
    private final Setting<Integer> heldStrength;
    private final Setting<Boolean> itemShaders;
    private final Setting<Double> shaderSoftness;
    private final Setting<Double> shaderBrightness;

    public ItemGlow(Category category) {
        super(
                category,
                "item-glow",
                "Highlights dropped and held items with a smooth colored shader glow.");
        this.title = "Item Glow";
        instance = this;
        SettingGroup general = this.settings.getGeneral();
        this.color =
                general.add(
                        new ColorSetting.Builder()
                                .name("glow-color")
                                .description("Color of the glow around dropped and held items.")
                                .defaultValue(new EntityColor(190, 72, 255, 255))
                                .build());
        this.range =
                general.add(
                        new NumberSetting.Builder()
                                .name("range")
                                .description("Maximum distance at which dropped items glow.")
                                .defaultValue(100.0)
                                .range(8.0, 128.0)
                                .sliderRange(8.0, 128.0)
                                .build());
        this.heldWidth =
                general.add(
                        new NumberSetting.Builder()
                                .name("held-width")
                                .description("Width of the glow around held items.")
                                .defaultValue(2.0)
                                .range(0.5, 4.0)
                                .sliderRange(0.5, 4.0)
                                .build());
        this.heldStrength =
                general.add(
                        new IntSetting.Builder()
                                .name("held-strength")
                                .description("Brightness of the glow around held items.")
                                .defaultValue(82)
                                .range(10, 100)
                                .sliderRange(10, 100)
                                .build());
        this.itemShaders =
                general.add(
                        new BooleanSetting.Builder()
                                .name("item-shaders")
                                .description(
                                        "Add a smooth GPU-blurred halo using the selected glow"
                                            + " color.")
                                .defaultValue(true)
                                .build());
        this.shaderSoftness =
                general.add(
                        new NumberSetting.Builder()
                                .name("shader-softness")
                                .description("Softness of the colored item-shader halo.")
                                .defaultValue(2.0)
                                .range(0.5, 4.0)
                                .sliderRange(0.5, 4.0)
                                .visibleWhen(() -> this.itemShaders.getValue())
                                .build());
        this.shaderBrightness =
                general.add(
                        new NumberSetting.Builder()
                                .name("shader-brightness")
                                .description(
                                        "Brightness of the shader halo; the item texture stays"
                                            + " unchanged.")
                                .defaultValue(1.6)
                                .range(0.5, 3.0)
                                .sliderRange(0.5, 3.0)
                                .visibleWhen(() -> this.itemShaders.getValue())
                                .build());
    }

    @Override
    public void onDeactivate() {
        ItemGlowPhaseRenderer.reset();
        ItemGlowRenderer.cleanup();
    }

    public static boolean isShaderEnabled() {
        return instance != null && instance.isActive() && instance.itemShaders.getValue();
    }

    public static float getShaderSoftness() {
        return instance == null
                ? 2.0F
                : Math.clamp(instance.shaderSoftness.getValue().floatValue(), 0.5F, 4.0F);
    }

    public static float getShaderBrightness() {
        return instance == null
                ? 1.6F
                : Math.clamp(instance.shaderBrightness.getValue().floatValue(), 0.5F, 3.0F);
    }

    public static ItemGlow.GlowStyle getStyleForColor(int argb) {
        if (isShaderEnabled() && instance.mc.player != null && instance.mc.world != null) {
            EntityColor glowColor = instance.color.getValue();
            return glowColor != null && glowColor.a > 0 && argb == glowColor.toArgb()
                    ? instance.createStyle(glowColor)
                    : null;
        } else {
            return null;
        }
    }

    public static boolean shouldGlowEntity(Entity entity) {
        ItemGlow glow = instance;
        if (glow != null
                && glow.isActive()
                && glow.mc.player != null
                && glow.mc.world != null
                && entity instanceof ItemEntity) {
            double range = Math.max(8.0, Math.min(128.0, glow.range.getValue()));
            EntityColor glowColor = glow.color.getValue();
            return glowColor != null
                    && glowColor.a > 0
                    && glow.mc.player.squaredDistanceTo(entity) <= range * range;
        } else {
            return false;
        }
    }

    public static int getEntityGlowColor(Entity entity) {
        ItemGlow glow = instance;
        if (shouldGlowEntity(entity) && glow != null) {
            EntityColor glowColor = glow.color.getValue();
            return glowColor != null && glowColor.a > 0 ? glowColor.toArgb() : 0;
        } else {
            return 0;
        }
    }

    public static ItemGlow.GlowStyle getHeldStyle(ItemDisplayContext context) {
        ItemGlow glow = instance;
        if (glow != null
                && glow.isActive()
                && glow.mc.player != null
                && glow.mc.world != null
                && (isFirstPerson(context) || isThirdPerson(context))) {
            EntityColor glowColor = glow.color.getValue();
            return glowColor != null && glowColor.a > 0 ? glow.createStyle(glowColor) : null;
        } else {
            return null;
        }
    }

    private ItemGlow.GlowStyle createStyle(EntityColor glowColor) {
        float width = Math.max(0.5F, Math.min(4.0F, this.heldWidth.getValue().floatValue()));
        float strength =
                Math.max(0.1F, Math.min(1.0F, this.heldStrength.getValue().intValue() / 100.0F));
        return new ItemGlow.GlowStyle(
                glowColor.r, glowColor.g, glowColor.b, glowColor.a, width, strength);
    }

    public static boolean isFirstPerson(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    public static boolean isThirdPerson(ItemDisplayContext context) {
        return context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    public record GlowStyle(int red, int green, int blue, int alpha, float width, float strength) {
        public float redFloat() {
            return this.red / 255.0F;
        }

        public float greenFloat() {
            return this.green / 255.0F;
        }

        public float blueFloat() {
            return this.blue / 255.0F;
        }

        public float alphaFloat(float multiplier) {
            return Math.max(0.0F, Math.min(1.0F, this.alpha / 255.0F * this.strength * multiplier));
        }
    }
}
