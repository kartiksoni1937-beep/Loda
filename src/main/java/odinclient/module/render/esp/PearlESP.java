package odinclient.module.render.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
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
import odinclient.setting.type.ColorSetting;

@Environment(EnvType.CLIENT)
public final class PearlESP extends Module {
    private final Setting<Boolean> tracers;
    private final Setting<EntityColor> color;

    public PearlESP() {
        super(
                Categories.MAIN,
                "pearl-esp",
                "Draws a box around loaded ender pearls with an optional tracer.");
        this.title = "Pearl ESP";
        SettingGroup general = this.settings.getGeneral();
        this.tracers =
                general.add(
                        new BooleanSetting.Builder()
                                .name("tracers")
                                .description(
                                        "Draw a tracer from the crosshair to each ender pearl.")
                                .defaultValue(true)
                                .build());
        this.color =
                general.add(
                        new ColorSetting.Builder()
                                .name("box-color")
                                .description("Color and fill opacity of pearl boxes and tracers.")
                                .defaultValue(new EntityColor(180, 75, 255, 70))
                                .build());
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.mc.player != null) {
            EntityColor entityColor = this.color.getValue();
            Color fill = new Color(entityColor.r, entityColor.g, entityColor.b, entityColor.a);
            Color outline = new Color(entityColor.r, entityColor.g, entityColor.b, 255);
            boolean drawTracers = Boolean.TRUE.equals(this.tracers.getValue());

            for (Entity entity : this.mc.world.getEntities()) {
                if (entity instanceof EnderPearlEntity && entity.isAlive()) {
                    Box box = entity.getBoundingBox().expand(0.035);
                    event.renderer.stableBox(
                            box.minX,
                            box.minY,
                            box.minZ,
                            box.maxX,
                            box.maxY,
                            box.maxZ,
                            fill,
                            outline,
                            BoxRenderMode.Both);
                    if (drawTracers) {
                        event.renderer.blockEspHybridTracer(box.getCenter(), outline, 0.9F);
                    }
                }
            }
        }
    }
}
