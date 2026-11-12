package odinclient.module.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.mixin.freecam.InputAccessor;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.util.RotationUtils;
import odinclient.util.input.MouseKeyBindState;

import org.joml.Vector3d;

@Environment(EnvType.CLIENT)
public final class Freecam extends Module {
    private static final float MIN_SPEED = 0.01F;
    private static final float MAX_SPEED = 50.0F;
    private static final float SCROLL_STEP = 0.25F;
    private static final float MAX_SCROLL_SPEED = 100.0F;
    public static Freecam instance;
    private final SettingGroup generalSettings = this.settings.getGeneral();
    public final Setting<Double> speed =
            this.generalSettings.add(
                    new NumberSetting.Builder()
                            .name("speed")
                            .description("Your speed while in freecam.")
                            .defaultValue(1.0)
                            .min(0.009999999776482582)
                            .sliderMax(50.0)
                            .build());
    public final Setting<Double> scrollSensitivity =
            this.generalSettings.add(
                    new NumberSetting.Builder()
                            .name("speed-scroll-sensitivity")
                            .description(
                                    "How strongly the scroll wheel changes freecam speed. 0"
                                        + " disables it.")
                            .defaultValue(1.0)
                            .min(0.0)
                            .sliderMax(5.0)
                            .build());
    public final Setting<Boolean> keepWalking =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("keep-walking")
                            .description(
                                    "Locks the real player's walking input from the moment freecam"
                                        + " is activated.")
                            .defaultValue(false)
                            .build());
    public final Setting<Boolean> keepJumping =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("keep-jumping")
                            .description(
                                    "Locks the real player's jump input from the moment freecam is"
                                        + " activated.")
                            .defaultValue(false)
                            .build());
    public final Vector3d pos = new Vector3d();
    public final Vector3d prevPos = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    public float yaw;
    public float pitch;
    public float prevYaw;
    public float prevPitch;
    private final boolean smoothMovement = true;
    private final float lookSensitivity = 0.5F;
    private float baseSpeed;
    private float speedBoost;
    private Perspective savedPerspective;
    private boolean savedChunkCulling;
    private long lastUpdateTime;
    private float playerYaw;
    private float playerPitch;
    private PlayerInput savedPlayerInput = PlayerInput.DEFAULT;
    private Vec2f savedMovementVector = Vec2f.ZERO;
    private boolean savedSprinting;

    public Freecam() {
        super(
                Categories.MAIN,
                "odinclient-freecam",
                "Moves the camera independently from the real player.");
        instance = this;
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null && this.mc.world != null) {
            InputAccessor input = (InputAccessor) this.mc.player.input;
            this.savedPlayerInput = input.odinclient$getPlayerInput();
            this.savedMovementVector = input.odinclient$getMovementVector();
            this.savedSprinting = this.mc.player.isSprinting();
            this.savedPerspective = this.mc.options.getPerspective();
            this.savedChunkCulling = this.mc.chunkCullingEnabled;
            this.mc.chunkCullingEnabled = false;
            this.playerYaw = this.mc.player.getYaw();
            this.playerPitch = this.mc.player.getPitch();
            this.yaw = this.playerYaw;
            this.pitch = this.playerPitch;
            Vec3d cameraPos = this.mc.player.getCameraPosVec(1.0F);
            this.pos.set(cameraPos.x, cameraPos.y, cameraPos.z);
            this.prevPos.set(this.pos);
            this.prevYaw = this.yaw;
            this.prevPitch = this.pitch;
            this.lastUpdateTime = System.currentTimeMillis();
            this.velocity.set(0.0, 0.0, 0.0);
            this.baseSpeed = this.getClampedSpeed();
            this.speedBoost = 0.0F;
        } else {
            this.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (this.mc.player != null) {
            RotationUtils.restoreRotation(this.playerYaw, this.playerPitch, true);
        }

        this.mc.options.setPerspective(
                this.savedPerspective != null ? this.savedPerspective : Perspective.FIRST_PERSON);

        this.mc.chunkCullingEnabled = this.savedChunkCulling;
        this.velocity.set(0.0, 0.0, 0.0);
        this.baseSpeed = this.getClampedSpeed();
        this.speedBoost = 0.0F;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (this.mc.player != null) {
            RotationUtils.restoreRotation(this.playerYaw, this.playerPitch, true);
        }
    }

    @EventHandler
    private void onWorldLeave(WorldLeaveEvent event) {
        if (this.isActive()) {
            this.toggle();
        }
    }

    public void updateCamera() {
        if (this.mc.player != null) {
            this.prevPos.set(this.pos);
            this.prevYaw = this.yaw;
            this.prevPitch = this.pitch;
            long now = System.currentTimeMillis();
            float elapsed = (float) (now - this.lastUpdateTime) / 1000.0F;
            this.lastUpdateTime = now;
            float delta = Math.min(elapsed, 0.1F);
            if (delta < 0.001F) {
                delta = 0.016F;
            }

            float yawRad = (float) Math.toRadians(this.yaw);
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double rightX = -Math.cos(yawRad);
            double rightZ = -Math.sin(yawRad);
            double moveX = 0.0;
            double moveY = 0.0;
            double moveZ = 0.0;
            double moveSpeed = (this.baseSpeed + this.speedBoost) * 2.0;
            if (this.mc.options != null && this.mc.options.sprintKey.isPressed()) {
                moveSpeed *= 2.0;
            }

            boolean moving = false;
            if (this.mc.options.forwardKey.isPressed()) {
                moveX += forwardX * moveSpeed;
                moveZ += forwardZ * moveSpeed;
                moving = true;
            }

            if (this.mc.options.backKey.isPressed()) {
                moveX -= forwardX * moveSpeed;
                moveZ -= forwardZ * moveSpeed;
                moving = true;
            }

            if (this.mc.options.rightKey.isPressed()) {
                moveX += rightX * moveSpeed;
                moveZ += rightZ * moveSpeed;
                moving = true;
            }

            if (this.mc.options.leftKey.isPressed()) {
                moveX -= rightX * moveSpeed;
                moveZ -= rightZ * moveSpeed;
                moving = true;
            }

            if (this.mc.options.jumpKey.isPressed()) {
                moveY += moveSpeed;
                moving = true;
            }

            if (MouseKeyBindState.isKeyPressed(this.mc.options.sneakKey)) {
                moveY -= moveSpeed;
                moving = true;
            }

            if (!moving) {
                this.speedBoost = 0.0F;
                this.velocity.set(0.0, 0.0, 0.0);
            } else {
                double speedFactor = 5.0;
                double blend = 1.0 - Math.pow(0.001, delta);
                this.velocity.x = MathHelper.lerp(blend, this.velocity.x, moveX * speedFactor);
                this.velocity.y = MathHelper.lerp(blend, this.velocity.y, moveY * speedFactor);
                this.velocity.z = MathHelper.lerp(blend, this.velocity.z, moveZ * speedFactor);
                this.pos.x = this.pos.x + this.velocity.x * delta;
                this.pos.y = this.pos.y + this.velocity.y * delta;
                this.pos.z = this.pos.z + this.velocity.z * delta;
            }
        }
    }

    public boolean onScroll(double amount) {
        double sensitivity = this.scrollSensitivity.getValue();
        if (!(sensitivity <= 0.0) && amount != 0.0) {
            this.baseSpeed =
                    (float)
                            Math.min(
                                    Math.max(this.baseSpeed + amount * 0.25 * sensitivity, 0.01),
                                    100.0);
            this.speedBoost = 0.0F;
            return true;
        } else {
            return false;
        }
    }

    public void changeLookDirection(double deltaYaw, double deltaPitch) {
        this.yaw += (float) deltaYaw;
        this.pitch += (float) deltaPitch;
        this.yaw = MathHelper.wrapDegrees(this.yaw);
        this.pitch = MathHelper.clamp(this.pitch, -90.0F, 90.0F);
    }

    public double getLerpedX(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.x, this.pos.x);
    }

    public double getLerpedY(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.y, this.pos.y);
    }

    public double getLerpedZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.z, this.pos.z);
    }

    public float getLerpedYaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevYaw, this.yaw);
    }

    public float getLerpedPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPitch, this.pitch);
    }

    public float getLookSensitivity() {
        return 0.5F;
    }

    public PlayerInput getSavedPlayerInput() {
        return this.savedPlayerInput;
    }

    public Vec2f getSavedMovementVector() {
        return this.savedMovementVector;
    }

    public boolean wasSprinting() {
        return this.savedSprinting;
    }

    public void adjustSpeed(double amount) {
        this.onScroll(amount);
    }

    private float getClampedSpeed() {
        return MathHelper.clamp(this.speed.getValue().floatValue(), MIN_SPEED, MAX_SPEED);
    }
}
