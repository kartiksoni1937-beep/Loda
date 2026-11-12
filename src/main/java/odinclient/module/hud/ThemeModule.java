package odinclient.module.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.manager.ModuleManager;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.EnumSetting;

@Environment(EnvType.CLIENT)
public final class ThemeModule extends Module {
    public static final int DEFAULT_GRADIENT_START = 5744895;
    public static final int DEFAULT_GRADIENT_END = 16014289;
    private static ThemeModule instance;
    public final Setting<ThemeModule.GuiStyle> guiStyle;
    public final Setting<EntityColor> color;
    public final Setting<Boolean> rainbow;
    public final Setting<Boolean> animations;
    public final Setting<Boolean> msaa;
    public static final int ACCENT_BLUE = -8248324;
    private static final int[] RAINBOW_COLORS;
    private static final float RAINBOW_PERIOD_SECONDS = 9.0F;

    public ThemeModule(Category category) {
        super(category, "theme", "Changes OdinClient colors, animation and menu layout.");
        instance = this;
        this.guiStyle =
                this.settings
                        .getGeneral()
                        .add(
                                new EnumSetting.Builder<ThemeModule.GuiStyle>()
                                        .name("gui-style")
                                        .description(
                                                "Switches between the Dashboard and the compact"
                                                    + " Panel GUI.")
                                        .defaultValue(ThemeModule.GuiStyle.DASHBOARD)
                                        .build());
        this.color =
                this.settings
                        .getGeneral()
                        .add(
                                new ColorSetting.Builder()
                                        .name("color")
                                        .description("Accent color used by the OdinClient menu.")
                                        .defaultValue(new EntityColor(35, 139, 255))
                                        .build());
        this.rainbow =
                this.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("rainbow")
                                        .description(
                                                "Uses OdinClient's blue-to-pink rainbow accent.")
                                        .defaultValue(false)
                                        .build());
        this.animations =
                this.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("animations")
                                        .description(
                                                "Smoothly animates all OdinClient GUI surfaces.")
                                        .defaultValue(true)
                                        .build());
        this.msaa =
                this.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("msaa")
                                        .description(
                                                "Anti-aliases ESP tracers. This can affect"
                                                    + " performance.")
                                        .defaultValue(false)
                                        .build());
        this.setActive(true);
    }

    private static ThemeModule getInstance() {
        if (instance == null) {
            Module module = ModuleManager.get().getByName("theme");
            if (module instanceof ThemeModule) {
                instance = (ThemeModule) module;
            }
        }

        return instance;
    }

    public static ThemeModule.GuiStyle getGuiStyle() {
        ThemeModule module = getInstance();
        if (module != null && module.guiStyle.getValue() != null) {
            return module.guiStyle.getValue();
        }

        return ThemeModule.GuiStyle.DASHBOARD;
    }

    public static boolean isPanelStyle() {
        return getGuiStyle() == ThemeModule.GuiStyle.PANELS;
    }

    public static boolean areAnimationsEnabled() {
        ThemeModule module = getInstance();
        return module == null || Boolean.TRUE.equals(module.animations.getValue());
    }

    public static boolean isLegacyRenderingEnabled() {
        return false;
    }

    public static boolean isMsaaEnabled() {
        ThemeModule module = getInstance();
        return module != null && Boolean.TRUE.equals(module.msaa.getValue());
    }

    public static int getAnimationSteps() {
        return 3;
    }

    public static int getAccentColor() {
        return getAccentColorAt(0.0F);
    }

    public static int getAccentColorAt(float offset) {
        float scaled =
                ((float) (System.currentTimeMillis() % 9000L) / 9000.0F
                                + Math.max(0.0F, Math.min(1.0F, offset)) * 0.35F)
                        * RAINBOW_COLORS.length;
        int index = (int) Math.floor(scaled) % RAINBOW_COLORS.length;
        float fraction = scaled - (float) Math.floor(scaled);
        float eased = fraction * fraction * (3.0F - 2.0F * fraction);
        return lerpColor(
                RAINBOW_COLORS[index], RAINBOW_COLORS[(index + 1) % RAINBOW_COLORS.length], eased);
    }

    private static int lerpColor(int from, int to, float t) {
        return 0xFF000000
                | Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t)
                        << 16
                | Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t) << 8
                | Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
    }

    static {
        RAINBOW_COLORS = new int[] {-10808106, -8248324, -6005505, -9491470};
    }

    public static enum GuiStyle {
        DASHBOARD,
        PANELS;
    }
}
