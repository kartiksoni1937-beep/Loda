package odinclient.module.render.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import odinclient.event.EventHandler;
import odinclient.event.WorldRenderEvent;
import odinclient.manager.ModuleManager;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.IntSetting;

@Environment(EnvType.CLIENT)
public final class PlayerEsp extends Module {
    private final Setting<PlayerEsp.EspMode> mode =
            this.settings
                    .getGeneral()
                    .add(
                            new EnumSetting.Builder<PlayerEsp.EspMode>()
                                    .name("esp-mode")
                                    .description(
                                            "Choose one player ESP style. Boxed and Shader cannot"
                                                + " run together.")
                                    .defaultValue(PlayerEsp.EspMode.Boxed)
                                    .build());
    private final Setting<Boolean> tracers =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("tracers")
                                    .description(
                                            "Draw a line from the camera to every visible ESP"
                                                + " target.")
                                    .defaultValue(false)
                                    .build());
    private final Setting<EntityColor> color =
            this.settings
                    .getGeneral()
                    .add(
                            new ColorSetting.Builder()
                                    .name("color")
                                    .description("Color used by Boxed, Shader and tracers.")
                                    .defaultValue(new EntityColor(35, 180, 255))
                                    .build());
    private final Setting<Integer> alpha =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("alpha")
                                    .description("Fill opacity used by Boxed mode.")
                                    .defaultValue(42)
                                    .range(0, 255)
                                    .sliderRange(0, 255)
                                    .build());
    private final Setting<Integer> range =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("range")
                                    .description("Maximum player ESP distance in blocks.")
                                    .defaultValue(192)
                                    .range(16, 512)
                                    .sliderRange(16, 512)
                                    .build());

    public PlayerEsp(Category category) {
        super(
                category,
                "player-esp",
                "Highlights players with either boxes or a model shader outline.");
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.mc.player != null && this.mc.gameRenderer != null) {
            EntityColor espColor = this.color.getValue();
            Color lineColor = new Color(espColor.r, espColor.g, espColor.b, 235);
            Color fillColor = new Color(espColor.r, espColor.g, espColor.b, this.alpha.getValue());
            boolean boxed = this.mode.getValue() == PlayerEsp.EspMode.Boxed;

            for (PlayerEntity player : this.mc.world.getPlayers()) {
                if (this.isValidTarget(player)) {
                    Box box = player.getBoundingBox().expand(0.04);
                    if (boxed) {
                        event.renderer.stableBox(
                                box.minX,
                                box.minY,
                                box.minZ,
                                box.maxX,
                                box.maxY,
                                box.maxZ,
                                fillColor,
                                lineColor,
                                BoxRenderMode.Sides);
                    }

                    if (this.tracers.getValue()) {
                        event.renderer.blockEspHybridTracer(box.getCenter(), lineColor, 0.9F);
                    }
                }
            }
        }
    }

    private boolean isValidTarget(PlayerEntity target) {
        return target != null
                && target != this.mc.player
                && target.isAlive()
                && this.mc.player != null
                && this.mc.player.distanceTo(target) <= this.range.getValue().intValue();
    }

    public boolean shouldApplyShader(Entity entity) {
        return this.isActive()
                && this.mode.getValue() == PlayerEsp.EspMode.Shader
                && entity instanceof PlayerEntity
                && this.isValidTarget((PlayerEntity) entity);
    }

    public int getColorRgb() {
        EntityColor espColor = this.color.getValue();
        return espColor.r << 16 | espColor.g << 8 | espColor.b;
    }

    public static PlayerEsp getInstance() {
        return ModuleManager.get().getByName("player-esp") instanceof PlayerEsp module
                ? module
                : null;
    }

    public static enum EspMode {
        Boxed,
        Shader;
    }
}
