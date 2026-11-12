package odinclient.module.render.sky;

import net.minecraft.util.Identifier;

import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.EnumSetting;

public final class SkyArts extends Module {
    private static SkyArts instance;
    private final SettingGroup general = this.settings.getGeneral();
    public final Setting<SkyArts.Artwork> artwork =
            this.general.add(
                    new EnumSetting.Builder<SkyArts.Artwork>()
                            .name("artwork")
                            .description("Open the artwork gallery and select exactly one figure.")
                            .defaultValue(SkyArts.Artwork.BLONDE)
                            .build());
    public final Setting<SkyArts.Sky> sky =
            this.general.add(
                    new EnumSetting.Builder<SkyArts.Sky>()
                            .name("sky")
                            .description(
                                    "Choose a sky background. The selected figure always renders on"
                                        + " top.")
                            .defaultValue(SkyArts.Sky.OFF)
                            .build());
    public final Setting<Boolean> doubleSided =
            this.general.add(
                    new BooleanSetting.Builder()
                            .name("double-sided")
                            .description(
                                    "Renders the selected figure on opposite sides of the sky.")
                            .defaultValue(false)
                            .build());
    public final Setting<Boolean> skyAnimation =
            this.general.add(
                    new BooleanSetting.Builder()
                            .name("sky-animation")
                            .description(
                                    "Continuous themed sky motion: orbiting planets, meteors,"
                                        + " birds, lanterns and fireflies. Visual only.")
                            .defaultValue(true)
                            .build());

    public SkyArts(Category category) {
        super(
                category,
                "sky-arts",
                "Renders a selectable sky background with artwork layered on top.");
        instance = this;
    }

    public static boolean shouldRender() {
        return instance != null && instance.isActive() && instance.mc.world != null;
    }

    public static SkyArts.Artwork selectedArtwork() {
        return instance != null && instance.artwork.getValue() != null
                ? instance.artwork.getValue()
                : SkyArts.Artwork.BLONDE;
    }

    public static SkyArts.Sky selectedSky() {
        return instance != null && instance.sky.getValue() != null
                ? instance.sky.getValue()
                : SkyArts.Sky.OFF;
    }

    public static boolean isDoubleSided() {
        return instance != null && Boolean.TRUE.equals(instance.doubleSided.getValue());
    }

    public static boolean isSkyAnimated() {
        return instance != null && Boolean.TRUE.equals(instance.skyAnimation.getValue());
    }

    public static enum Artwork {
        BLONDE("Blonde", "textures/sky/sky_art_blonde.png", 1122, 1402),
        CAT_EARS("Cat Ears", "textures/sky/sky_art_cat_ears.png", 1022, 1539),
        MIRROR("Mirror", "textures/sky/sky_art_mirror.png", 1083, 1452),
        WHITE_TEE("White Tee", "textures/sky/sky_art_white_tee.png", 1086, 1448),
        BLUE_TOP("Blue Top", "textures/sky/sky_art_blue_top.png", 946, 1662),
        BLACK_DRESS("Black Dress", "textures/sky/sky_art_black_dress.png", 798, 1429);

        private final String displayName;
        private final Identifier texture;
        private final int textureWidth;
        private final int textureHeight;

        private Artwork(
                String displayName, String texturePath, int textureWidth, int textureHeight) {
            this.displayName = displayName;
            this.texture = Identifier.of("odinclient", texturePath);
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Identifier getTexture() {
            return this.texture;
        }

        public int getTextureWidth() {
            return this.textureWidth;
        }

        public int getTextureHeight() {
            return this.textureHeight;
        }

        public float getAspectRatio() {
            return (float) this.textureWidth / this.textureHeight;
        }
    }

    public static enum Sky {
        OFF("Vanilla / Off", null, false),
        LIISTA_BLUE("Blue Thunder", "textures/sky/backgrounds/liista_blue.png", false),
        CUSTOM_SUNSET("Custom Sunset", "textures/sky/backgrounds/custom_sunset.png", false),
        DRAMATIC_DAY("Dramatic Day", "textures/sky/backgrounds/dramatic_day.png", false),
        DRAMATIC_NIGHT("Dramatic Night", "textures/sky/backgrounds/dramatic_night.png", false),
        TOKYO_ALLEY("Tokyo Alley", "textures/sky/backgrounds/tokyo_alley.png", true),
        DEEP_SPACE("Deep Space", "textures/sky/backgrounds/deep_space.png", true),
        AURORA_MOUNTAINS("Aurora Mountains", "textures/sky/backgrounds/aurora_mountains.png", true),
        SUNSET_COAST("Sunset Coast", "textures/sky/backgrounds/sunset_coast.png", true),
        ENCHANTED_FOREST("Enchanted Forest", "textures/sky/backgrounds/enchanted_forest.png", true),
        DESERT_RUINS("Desert Ruins", "textures/sky/backgrounds/desert_ruins.png", true),
        COSMIC_FLOW("Cosmic Flow", "textures/sky/backgrounds/cosmic_flow.png", true);

        private final String displayName;
        private final Identifier texture;
        private final Identifier panorama;

        private Sky(String displayName, String texturePath, boolean animated) {
            this.displayName = displayName;
            this.texture = texturePath == null ? null : Identifier.of("odinclient", texturePath);
            this.panorama =
                    texturePath == null
                            ? null
                            : Identifier.of(
                                    "odinclient",
                                    texturePath.replace("/backgrounds/", "/panoramas/"));
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Identifier getTexture() {
            return this.texture;
        }

        public Identifier getPanorama() {
            return this.panorama;
        }

        public boolean hasTexture() {
            return this.texture != null;
        }

        public int getThemeIndex() {
            return switch (this) {
                case LIISTA_BLUE -> 1;
                case CUSTOM_SUNSET, SUNSET_COAST -> 5;
                case DRAMATIC_DAY -> 6;
                case DRAMATIC_NIGHT -> 7;
                case TOKYO_ALLEY -> 8;
                case DEEP_SPACE -> 2;
                case AURORA_MOUNTAINS -> 3;
                case ENCHANTED_FOREST -> 9;
                case DESERT_RUINS -> 10;
                case COSMIC_FLOW -> 4;
                default -> 0;
            };
        }

        public int atlasWidth() {
            return 1024;
        }

        public int atlasHeight() {
            return 384;
        }

        public float getAtlasU() {
            return 0.0F;
        }

        public float getAtlasV(int width, int height) {
            return (384 - this.getRegionHeight(width, height)) / 2.0F;
        }

        public int getRegionHeight(int width, int height) {
            return Math.min(384, Math.max(1, Math.round(1024.0F * height / Math.max(1, width))));
        }
    }
}
