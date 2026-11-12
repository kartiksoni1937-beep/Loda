package odinclient.module.fake;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.StringSetting;

@Environment(EnvType.CLIENT)
public final class FakeSpawner extends Module {
    private static FakeSpawner instance;
    private final Setting<FakeSpawner.MobType> mobType;
    private final Setting<String> priceEach;
    private final Setting<Integer> distance;

    public FakeSpawner(Category category) {
        super(
                category,
                "fake-spawner",
                "Locally shows yellow stained glass as a selectable mob spawner with a fake menu.");
        SettingGroup general = this.settings.getGeneral();
        this.mobType =
                general.add(
                        new EnumSetting.Builder<FakeSpawner.MobType>()
                                .name("mob-type")
                                .description("Mob shown in the spawner, item text and menu.")
                                .defaultValue(FakeSpawner.MobType.Skeleton)
                                .build());
        this.priceEach =
                general.add(
                        new StringSetting.Builder()
                                .name("price-each")
                                .description("Visual price per spawner, e.g. 7.4K or 50000.")
                                .defaultValue("7.4K")
                                .build());
        this.distance =
                general.add(
                        new IntSetting.Builder()
                                .name("distance")
                                .description(
                                        "Maximum distance in blocks for fake spawner rendering.")
                                .defaultValue(16)
                                .range(4, 32)
                                .sliderRange(4, 32)
                                .build());
        instance = this;
    }

    public static boolean isEnabled() {
        return instance != null && instance.isActive();
    }

    public static FakeSpawner.MobType getMobType() {
        return instance == null ? FakeSpawner.MobType.Skeleton : instance.mobType.getValue();
    }

    public static String getPriceText() {
        if (instance == null) {
            return "7.4K";
        } else {
            String text = instance.priceEach.getValue().strip();
            if (text.startsWith("$")) {
                text = text.substring(1).strip();
            }

            if (text.isEmpty()) {
                return "0";
            } else {
                return text.length() > 24 ? text.substring(0, 24) : text;
            }
        }
    }

    public static int getRenderDistance() {
        return instance == null ? 16 : Math.max(4, Math.min(32, instance.distance.getValue()));
    }

    @Override
    public void onActivate() {
        this.reloadWorldRenderer();
    }

    @Override
    public void onDeactivate() {
        this.reloadWorldRenderer();
    }

    private void reloadWorldRenderer() {
        if (this.mc.world != null) {
            this.mc.worldRenderer.reload();
        }
    }

    public static enum MobType {
        Skeleton("Skeleton", "skeleton", 0.31F),
        Pig("Pig", "pig", 0.38F),
        IronGolem("Iron Golem", "iron_golem", 0.22F),
        Zombie("Zombie", "zombie", 0.31F),
        ZombiePiglin("Zombie Piglin", "zombified_piglin", 0.31F),
        Creeper("Creeper", "creeper", 0.31F),
        Blaze("Blaze", "blaze", 0.31F),
        Spider("Spider", "spider", 0.35F),
        Cow("Cow", "cow", 0.31F);

        private final String display;
        private final String id;
        private final float scale;

        private MobType(String display, String id, float scale) {
            this.display = display;
            this.id = id;
            this.scale = scale;
        }

        public String getDisplayName() {
            return this.display;
        }

        public String id() {
            return this.id;
        }

        public float scale() {
            return this.scale;
        }

        public EntityType<?> getEntityType() {
            return Registries.ENTITY_TYPE.get(Identifier.of("minecraft", this.id));
        }

        public Item getHeadItem() {
            String headName =
                    switch (this) {
                        case Skeleton -> "skeleton_skull";
                        case Zombie -> "zombie_head";
                        case Creeper -> "creeper_head";
                        default -> null;
                    };
            return headName == null
                    ? null
                    : Registries.ITEM.get(Identifier.of("minecraft", headName));
        }

        @Override
        public String toString() {
            return this.display;
        }
    }
}
