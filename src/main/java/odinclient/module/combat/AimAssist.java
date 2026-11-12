package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;

import org.lwjgl.glfw.GLFW;

import java.util.Random;

@Environment(EnvType.CLIENT)
public final class AimAssist extends Module {
    private static final Random RANDOM = new Random();
    private static AimAssist instance;
    private final Setting<Boolean> stickyAim;
    private final Setting<Boolean> onlyWeapon;
    private final Setting<Boolean> onLeftClick;
    private final Setting<AimAssist.AimPoint> aimAt;
    private final Setting<Boolean> stopAtTargetVertical;
    private final Setting<Boolean> stopAtTargetHorizontal;
    private final Setting<Double> radius;
    private final Setting<Boolean> seeOnly;
    private final Setting<Boolean> lookAtNearest;
    private final Setting<Integer> fov;
    private final Setting<Double> verticalSpeedMin;
    private final Setting<Double> verticalSpeedMax;
    private final Setting<Double> horizontalSpeedMin;
    private final Setting<Double> horizontalSpeedMax;
    private final Setting<Integer> speedDelay;
    private final Setting<Integer> chance;
    private final Setting<Boolean> horizontal;
    private final Setting<Boolean> vertical;
    private final Setting<Integer> waitOnMove;
    private final Setting<AimAssist.LerpMode> lerp;
    private final Setting<AimAssist.PositionMode> positionMode;
    private boolean active;
    private float verticalSpeed;
    private float horizontalSpeed;
    private long lastMouseMoveTime;
    private long lastSpeedRefreshTime;

    public AimAssist(Category category) {
        super(category, "aim-assist", "Smoothly guides your crosshair toward nearby players.");
        instance = this;
        SettingGroup general = this.settings.getGeneral();
        this.stickyAim =
                general.add(
                        booleanSetting(
                                "sticky-aim",
                                "Keeps aiming at the last player that hit you.",
                                false));
        this.onlyWeapon =
                general.add(
                        booleanSetting(
                                "only-weapon", "Only works while holding a sword or axe.", true));
        this.onLeftClick =
                general.add(
                        booleanSetting(
                                "on-left-click",
                                "Only works while holding the attack button.",
                                false));
        EnumSetting.Builder<AimAssist.AimPoint> aimAtBuilder = new EnumSetting.Builder<>();
        aimAtBuilder
                .name("aim-at")
                .description("Body area used as the aim point.")
                .defaultValue(AimAssist.AimPoint.Head);
        this.aimAt = general.add(aimAtBuilder.build());
        this.stopAtTargetVertical =
                general.add(
                        booleanSetting(
                                "stop-at-target-vert",
                                "Stops vertical assistance while already on target.",
                                true));
        this.stopAtTargetHorizontal =
                general.add(
                        booleanSetting(
                                "stop-at-target-horiz",
                                "Stops horizontal assistance while already on target.",
                                false));
        this.radius =
                general.add(numberSetting("radius", "Maximum target distance.", 5.0, 0.1, 6.0));
        this.seeOnly =
                general.add(booleanSetting("see-only", "Only targets players you can see.", true));
        this.lookAtNearest =
                general.add(
                        booleanSetting(
                                "look-at-nearest",
                                "Offsets toward the nearest side of the target.",
                                false));
        this.fov =
                general.add(
                        intSetting(
                                "fov", "Maximum field of view used for targeting.", 180, 5, 360));
        this.verticalSpeedMin =
                general.add(
                        numberSetting(
                                "vertical-speed-min",
                                "Minimum randomized vertical speed.",
                                2.0,
                                0.0,
                                10.0));
        this.verticalSpeedMax =
                general.add(
                        numberSetting(
                                "vertical-speed-max",
                                "Maximum randomized vertical speed.",
                                4.0,
                                0.0,
                                10.0));
        this.horizontalSpeedMin =
                general.add(
                        numberSetting(
                                "horizontal-speed-min",
                                "Minimum randomized horizontal speed.",
                                2.0,
                                0.0,
                                10.0));
        this.horizontalSpeedMax =
                general.add(
                        numberSetting(
                                "horizontal-speed-max",
                                "Maximum randomized horizontal speed.",
                                4.0,
                                0.0,
                                10.0));
        this.speedDelay =
                general.add(
                        intSetting(
                                "speed-delay",
                                "Milliseconds before random aim speeds are refreshed.",
                                250,
                                0,
                                1000));
        this.chance =
                general.add(
                        intSetting(
                                "chance",
                                "Chance to apply assistance on each rendered frame.",
                                50,
                                0,
                                100));
        this.horizontal =
                general.add(booleanSetting("horizontal", "Enables horizontal assistance.", true));
        this.vertical =
                general.add(booleanSetting("vertical", "Enables vertical assistance.", true));
        this.waitOnMove =
                general.add(
                        intSetting(
                                "wait-on-move",
                                "Pause time after manual mouse movement, in milliseconds.",
                                0,
                                0,
                                1000));
        EnumSetting.Builder<AimAssist.LerpMode> lerpBuilder = new EnumSetting.Builder<>();
        lerpBuilder
                .name("lerp")
                .description("Interpolation curve used for rotations.")
                .defaultValue(AimAssist.LerpMode.Normal);
        this.lerp = general.add(lerpBuilder.build());
        EnumSetting.Builder<AimAssist.PositionMode> positionModeBuilder =
                new EnumSetting.Builder<>();
        positionModeBuilder
                .name("pos-mode")
                .description("Normal or interpolated target position.")
                .defaultValue(AimAssist.PositionMode.Normal);
        this.positionMode = general.add(positionModeBuilder.build());
    }

    @Override
    public void onActivate() {
        this.active = true;
        this.refreshSpeeds();
        long now = System.currentTimeMillis();
        this.lastMouseMoveTime = now;
        this.lastSpeedRefreshTime = now;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        long now = System.currentTimeMillis();
        if (!this.active && now - this.lastMouseMoveTime >= this.waitOnMove.getValue().intValue()) {
            this.active = true;
        }

        if (this.active && this.isInWorld() && this.mc.currentScreen == null) {
            if (!this.onlyWeapon.getValue() || this.isHoldingWeapon()) {
                if (!this.onLeftClick.getValue()
                        || GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), 0) == 1) {
                    PlayerEntity target = this.findTarget();
                    if (this.stickyAim.getValue()
                            && this.mc.player.getAttacking() instanceof PlayerEntity attacker
                            && attacker != this.mc.player
                            && attacker.isAlive()
                            && attacker.distanceTo(this.mc.player) < this.radius.getValue()) {
                        target = attacker;
                    }

                    if (target != null) {
                        if (now - this.lastSpeedRefreshTime
                                >= this.speedDelay.getValue().intValue()) {
                            this.refreshSpeeds();
                            this.lastSpeedRefreshTime = now;
                        }

                        Vec3d basePos =
                                this.positionMode.getValue() == AimAssist.PositionMode.Normal
                                        ? target.getEntityPos()
                                        : target.getLerpedPos(event.tickDelta);

                        Vec3d aimPos = basePos;
                        if (this.aimAt.getValue() == AimAssist.AimPoint.Chest) {
                            aimPos = basePos.add(0.0, -0.5, 0.0);
                        } else if (this.aimAt.getValue() == AimAssist.AimPoint.Legs) {
                            aimPos = basePos.add(0.0, -1.2, 0.0);
                        }

                        if (this.lookAtNearest.getValue()) {
                            double offsetX =
                                    this.mc.player.getX() - target.getX() > 0.0 ? 0.29 : -0.29;
                            double offsetZ =
                                    this.mc.player.getZ() - target.getZ() > 0.0 ? 0.29 : -0.29;

                            aimPos = aimPos.add(offsetX, 0.0, offsetZ);
                        }

                        AimAssist.Rotation rotation = getRotationTo(this.mc.player, aimPos);
                        float yaw = this.mc.player.getYaw();
                        float pitch = this.mc.player.getPitch();
                        if (!(getAngleDistance(yaw, pitch, rotation)
                                > this.fov.getValue().intValue() / 2.0)) {
                            float horizontalStep = this.horizontalSpeed / 50.0F;
                            float verticalStep = this.verticalSpeed / 50.0F;
                            float newYaw;
                            float newPitch;
                            if (this.lerp.getValue() == AimAssist.LerpMode.Smoothstep) {
                                newYaw = (float) lerpSmoothstep(horizontalStep, yaw, rotation.yaw);
                                newPitch =
                                        (float) lerpSmoothstep(verticalStep, pitch, rotation.pitch);
                            } else if (this.lerp.getValue() == AimAssist.LerpMode.EaseOut) {
                                float deltaTicks =
                                        this.mc.getRenderTickCounter().getDynamicDeltaTicks();
                                newYaw =
                                        (float)
                                                lerpEaseOut(
                                                        yaw,
                                                        rotation.yaw,
                                                        horizontalStep * deltaTicks);
                                newPitch =
                                        (float)
                                                lerpEaseOut(
                                                        pitch,
                                                        rotation.pitch,
                                                        verticalStep * deltaTicks);
                            } else {
                                newYaw = lerpLinear(horizontalStep, yaw, rotation.yaw);
                                newPitch = lerpLinear(verticalStep, pitch, rotation.pitch);
                            }

                            if (this.rollChance(this.chance.getValue())) {
                                HitResult hit = this.mc.crosshairTarget;
                                boolean onTarget =
                                        hit instanceof EntityHitResult
                                                && ((EntityHitResult) hit).getEntity() == target;

                                if (this.horizontal.getValue()
                                        && (!this.stopAtTargetHorizontal.getValue() || !onTarget)) {
                                    this.mc.player.setYaw(newYaw);
                                }

                                if (this.vertical.getValue()
                                        && (!this.stopAtTargetVertical.getValue() || !onTarget)) {
                                    this.mc.player.setPitch(newPitch);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void onManualMouseMove() {
        AimAssist aimAssist = instance;
        if (aimAssist != null && aimAssist.isActive()) {
            aimAssist.active = false;
            aimAssist.lastMouseMoveTime = System.currentTimeMillis();
        }
    }

    private boolean isInWorld() {
        return this.mc != null && this.mc.player != null && this.mc.world != null;
    }

    private boolean isHoldingWeapon() {
        Item item = this.mc.player.getMainHandStack().getItem();
        String path = Registries.ITEM.getId(item).getPath();
        return item instanceof AxeItem || path.endsWith("_sword") || path.endsWith("_axe");
    }

    private PlayerEntity findTarget() {
        PlayerEntity closest = null;
        float closestDistance = 3.4028235E38F;

        for (PlayerEntity player : this.mc.world.getPlayers()) {
            if (player != this.mc.player && player.isAlive()) {
                float distance = player.distanceTo(this.mc.player);
                if (!(distance > this.radius.getValue())
                        && !(distance >= closestDistance)
                        && (!this.seeOnly.getValue() || this.mc.player.canSee(player))) {
                    closest = player;
                    closestDistance = distance;
                }
            }
        }

        return closest;
    }

    private void refreshSpeeds() {
        this.verticalSpeed =
                randomBetween(this.verticalSpeedMin.getValue(), this.verticalSpeedMax.getValue());
        this.horizontalSpeed =
                randomBetween(
                        this.horizontalSpeedMin.getValue(), this.horizontalSpeedMax.getValue());
    }

    private static float randomBetween(double a, double b) {
        double min = Math.min(a, b);
        double range = Math.max(a, b) - min;
        return (float) (min + RANDOM.nextDouble() * range);
    }

    private boolean rollChance(int percent) {
        return RANDOM.nextInt(1, 100) <= percent;
    }

    private static AimAssist.Rotation getRotationTo(PlayerEntity player, Vec3d target) {
        double dx = target.x - player.getX();
        double dy = target.y - player.getY();
        double dz = target.z - player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch =
                (float)
                        (-MathHelper.wrapDegrees(
                                Math.toDegrees(Math.atan2(dy, horizontalDistance))));
        return new AimAssist.Rotation(yaw, pitch);
    }

    private static double getAngleDistance(float yaw, float pitch, AimAssist.Rotation target) {
        double yawDiff = MathHelper.wrapDegrees((double) (yaw - target.yaw));
        double pitchDiff = MathHelper.wrapDegrees((double) (pitch - target.pitch));
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private static float lerpLinear(float t, float from, float to) {
        return from + MathHelper.wrapDegrees(to - from) * t;
    }

    private static double lerpSmoothstep(double t, double from, double to) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double eased = clamped * clamped * (3.0 - 2.0 * clamped);
        return from + MathHelper.wrapDegrees(to - from) * eased;
    }

    private static double lerpEaseOut(double from, double to, float t) {
        double overshoot = 1.70158;
        double overshootPlusOne = 2.70158;
        double progress = 1.0 - Math.pow(1.0 - t, 3.0);
        return from
                + MathHelper.wrapDegrees(to - from)
                        * (1.0
                                + overshootPlusOne * Math.pow(progress - 1.0, 3.0)
                                + overshoot * Math.pow(progress - 1.0, 2.0));
    }

    private static Setting<Boolean> booleanSetting(
            String name, String description, boolean defaultValue) {
        return new BooleanSetting.Builder()
                .name(name)
                .description(description)
                .defaultValue(defaultValue)
                .build();
    }

    private static Setting<Integer> intSetting(
            String name, String description, int defaultValue, int min, int max) {
        return new IntSetting.Builder()
                .name(name)
                .description(description)
                .defaultValue(defaultValue)
                .range(min, max)
                .sliderRange(min, max)
                .build();
    }

    private static Setting<Double> numberSetting(
            String name, String description, double defaultValue, double min, double max) {
        return new NumberSetting.Builder()
                .name(name)
                .description(description)
                .defaultValue(defaultValue)
                .range(min, max)
                .sliderRange(min, max)
                .build();
    }

    public static enum AimPoint {
        Head,
        Chest,
        Legs;
    }

    public static enum LerpMode {
        Normal,
        Smoothstep,
        EaseOut;
    }

    public static enum PositionMode {
        Normal,
        Lerped;
    }

    private record Rotation(float yaw, float pitch) {}
}
