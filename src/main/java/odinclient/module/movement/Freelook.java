package odinclient.module.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.NumberSetting;

@Environment(EnvType.CLIENT)
public final class Freelook extends Module {
    private static Freelook instance;
    private final Setting<Double> distance =
            this.settings
                    .getGeneral()
                    .add(
                            new NumberSetting.Builder()
                                    .name("distance")
                                    .description("Distance between the camera and the player.")
                                    .defaultValue(4.0)
                                    .range(1.0, 15.0)
                                    .sliderRange(1.0, 15.0)
                                    .build());
    private final Setting<Double> sensitivity =
            this.settings
                    .getGeneral()
                    .add(
                            new NumberSetting.Builder()
                                    .name("sensitivity")
                                    .description("Freelook mouse sensitivity.")
                                    .defaultValue(1.0)
                                    .range(0.1, 3.0)
                                    .sliderRange(0.1, 3.0)
                                    .build());
    private final Setting<Boolean> throughWalls =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("through-walls")
                                    .description("Allow the camera to pass through blocks.")
                                    .defaultValue(false)
                                    .build());
    private final Setting<Boolean> invertY =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("invert-y")
                                    .description("Invert vertical camera movement.")
                                    .defaultValue(false)
                                    .build());
    private Perspective previousPerspective;
    private boolean previousChunkCulling;
    private float cameraYaw;
    private float cameraPitch;

    public Freelook(Category category) {
        super(
                category,
                "freelook",
                "Rotates a third-person camera without changing player rotation.");
        instance = this;
    }

    public static Freelook getInstance() {
        return instance;
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null && this.mc.options != null) {
            this.previousPerspective = this.mc.options.getPerspective();
            this.previousChunkCulling = this.mc.chunkCullingEnabled;
            this.cameraYaw = this.mc.player.getYaw();
            this.cameraPitch = this.mc.player.getPitch();
            this.mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            this.mc.chunkCullingEnabled = false;
        } else {
            this.setActive(false);
        }
    }

    @Override
    public void onDeactivate() {
        if (this.mc.options != null && this.previousPerspective != null) {
            this.mc.options.setPerspective(this.previousPerspective);
            this.mc.chunkCullingEnabled = this.previousChunkCulling;
        }

        this.previousPerspective = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.world == null) {
            this.setActive(false);
        }
    }

    public boolean isCameraActive() {
        return this.isActive() && this.mc.player != null && this.mc.currentScreen == null;
    }

    public void onMouseMove(double dx, double dy) {
        if (this.isCameraActive()) {
            double scale = 0.15 * this.sensitivity.getValue();
            this.cameraYaw = MathHelper.wrapDegrees(this.cameraYaw + (float) (dx * scale));
            double invert = this.invertY.getValue() ? -1.0 : 1.0;
            this.cameraPitch =
                    MathHelper.clamp(
                            this.cameraPitch + (float) (dy * scale * invert), -90.0F, 90.0F);
        }
    }

    public float getCameraYaw() {
        return this.cameraYaw;
    }

    public float getCameraPitch() {
        return this.cameraPitch;
    }

    public float getCameraDistance() {
        return this.distance.getValue().floatValue();
    }

    public boolean isThroughWalls() {
        return this.throughWalls.getValue();
    }
}
