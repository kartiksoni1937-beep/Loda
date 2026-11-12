package odinclient.module.render.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.WorldRenderEvent;
import odinclient.manager.ModuleManager;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.render.world.WorldShapeRenderer;
import odinclient.setting.Setting;
import odinclient.setting.type.ColorSetting;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class PearlTrajectory extends Module {
    private static final int MAX_STEPS = 180;
    private static final double INITIAL_SPEED = 1.5;
    private static final double DRAG = 0.99;
    private static final double GRAVITY = 0.03;
    private final Setting<EntityColor> color;
    private List<Vec3d> pathPoints = List.of();
    private boolean hitBlock;

    public PearlTrajectory() {
        super(
                Categories.MAIN,
                "pearl-trajectory",
                "Predicts the vanilla flight path and impact point of an ender pearl.");
        this.title = "Pearl Trajectory";
        this.color =
                this.settings
                        .getGeneral()
                        .add(
                                new ColorSetting.Builder()
                                        .name("trajectory-color")
                                        .description(
                                                "Color of the predicted pearl path and impact"
                                                    + " marker.")
                                        .defaultValue(new EntityColor(196, 88, 255, 255))
                                        .build());
    }

    @Override
    public void onActivate() {
        this.clearTrajectory();
    }

    @Override
    public void onDeactivate() {
        this.clearTrajectory();
    }

    @EventHandler
    private void onRender(WorldRenderEvent event) {
        if (this.mc.world != null
                && this.mc.player != null
                && !this.isFreecamActive()
                && this.isHoldingPearl()) {
            Camera camera = this.mc.gameRenderer == null ? null : this.mc.gameRenderer.getCamera();

            if (camera != null) {
                Vec3d forward = WorldShapeRenderer.getCameraForward(camera).normalize();
                Vec3d cameraPos = camera.getCameraPos();
                this.simulateTrajectory(cameraPos.add(forward.multiply(0.12)), cameraPos, forward);
                if (this.pathPoints.size() >= 2) {
                    EntityColor entityColor = this.color.getValue();
                    Color lineColor =
                            new Color(entityColor.r, entityColor.g, entityColor.b, entityColor.a);
                    Vec3d previous = this.pathPoints.get(0);

                    for (int i = 1; i < this.pathPoints.size(); i++) {
                        Vec3d point = this.pathPoints.get(i);
                        event.renderer.stableLine(previous, point, lineColor, 1.5F);
                        previous = point;
                    }

                    if (this.hitBlock) {
                        Vec3d impact = this.pathPoints.get(this.pathPoints.size() - 1);
                        double half = 0.22;
                        Color fillColor =
                                new Color(
                                        entityColor.r,
                                        entityColor.g,
                                        entityColor.b,
                                        Math.min(entityColor.a, 80));
                        event.renderer.stableBox(
                                impact.x - half,
                                impact.y - half,
                                impact.z - half,
                                impact.x + half,
                                impact.y + half,
                                impact.z + half,
                                fillColor,
                                lineColor,
                                BoxRenderMode.Both);
                    }
                }
            }
        } else {
            this.clearTrajectory();
        }
    }

    private boolean isHoldingPearl() {
        return this.mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)
                || this.mc.player.getOffHandStack().isOf(Items.ENDER_PEARL);
    }

    private boolean isFreecamActive() {
        Module freecam = ModuleManager.get().getByName("freecam");
        return freecam != null && freecam.isActive();
    }

    private void simulateTrajectory(Vec3d start, Vec3d origin, Vec3d direction) {
        Vec3d position = origin.add(0.0, -0.1, 0.0);
        Vec3d velocity = direction.multiply(INITIAL_SPEED);
        ArrayList<Vec3d> points = new ArrayList<>(MAX_STEPS + 1);
        points.add(start);
        boolean hit = false;

        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3d dragged = velocity.multiply(DRAG);
            Vec3d next = position.add(dragged);
            PearlTrajectory.RaycastStep step = this.traceStep(position, next);
            points.add(step.position());
            if (step.hit()) {
                hit = true;
                break;
            }

            position = next;
            velocity = dragged.add(0.0, -GRAVITY, 0.0);
            if (next.y < -128.0 || next.y > 512.0) {
                break;
            }
        }

        this.pathPoints = List.copyOf(points);
        this.hitBlock = hit;
    }

    private PearlTrajectory.RaycastStep traceStep(Vec3d from, Vec3d to) {
        BlockHitResult result =
                this.mc.world.raycast(
                        new RaycastContext(
                                from, to, ShapeType.COLLIDER, FluidHandling.NONE, this.mc.player));
        boolean hit = result.getType() != Type.MISS;
        return new PearlTrajectory.RaycastStep(hit ? result.getPos() : to, hit);
    }

    private void clearTrajectory() {
        this.pathPoints = List.of();
        this.hitBlock = false;
    }

    private record RaycastStep(Vec3d position, boolean hit) {}
}
