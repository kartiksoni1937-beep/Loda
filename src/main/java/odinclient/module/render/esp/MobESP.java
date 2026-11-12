package odinclient.module.render.esp;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.math.Box;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.EntityTypeListSetting;
import odinclient.setting.type.IntSetting;

import java.util.List;

public final class MobESP extends Module {
    private final SettingGroup general = this.settings.getGeneral();
    private final EntityTypeListSetting mobs =
            (EntityTypeListSetting)
                    this.general.add(
                            new EntityTypeListSetting.Builder()
                                    .name("mobs")
                                    .description("Choose the mobs to highlight.")
                                    .defaultValue()
                                    .filter(
                                            type ->
                                                    type != null
                                                            && SpawnEggItem.forEntity(type) != null)
                                    .build());
    private final Setting<Boolean> tracers =
            this.general.add(
                    new BooleanSetting.Builder()
                            .name("tracers")
                            .description("Draw tracers to selected mobs.")
                            .defaultValue(false)
                            .build());
    private final Setting<Integer> alpha =
            this.general.add(
                    new IntSetting.Builder()
                            .name("alpha")
                            .description("Opacity of the filled mob boxes.")
                            .defaultValue(100)
                            .range(0, 255)
                            .sliderRange(0, 255)
                            .build());

    public MobESP() {
        super(
                Categories.MAIN,
                "mob-esp",
                "Highlights selected mobs with configurable colors and tracers.");
        this.title = "Mob ESP";
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.mc.player != null) {
            List<EntityType<?>> types = this.mobs.getValue();
            if (types != null && !types.isEmpty()) {
                for (Entity entity : this.mc.world.getEntities()) {
                    if (entity != null && entity != this.mc.player && entity.isAlive()) {
                        EntityType<?> type = entity.getType();
                        if (types.contains(type)) {
                            EntityColor entityColor = this.mobs.getColor(type);
                            if (entityColor != null) {
                                Color fill =
                                        new Color(
                                                entityColor.r,
                                                entityColor.g,
                                                entityColor.b,
                                                this.alpha.getValue());
                                Color outline =
                                        new Color(entityColor.r, entityColor.g, entityColor.b, 255);
                                Box box = entity.getBoundingBox().expand(0.04);
                                event.renderer.stableBox(
                                        box.minX,
                                        box.minY,
                                        box.minZ,
                                        box.maxX,
                                        box.maxY,
                                        box.maxZ,
                                        fill,
                                        outline,
                                        BoxRenderMode.Sides);
                                if (Boolean.TRUE.equals(this.tracers.getValue())) {
                                    event.renderer.blockEspHybridTracer(
                                            box.getCenter(), outline, 0.9F);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
