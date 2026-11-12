package odinclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Single choke point for client-side rotation changes.
 *
 * <p>Modules must never write {@code player.setYaw}/{@code setPitch} directly: every rotation goes
 * through this class so that per-update steps stay inside {@link #YAW_CAP}/{@link #PITCH_CAP},
 * non-finite input is dropped, and sub-perceptual deltas are not re-applied.
 */
public final class RotationUtils {

    /** Maximum yaw change applied or sent per update. */
    public static final float YAW_CAP = 26.0f;

    /** Maximum pitch change applied or sent per update. */
    public static final float PITCH_CAP = 10.0f;

    private static final float DEDUPE_DELTA = 0.02f;
    private static final double MIN_TARGET_DISTANCE_SQ = 1.0E-6;

    private RotationUtils() {}

    /**
     * Turns the player toward {@code target} before an interaction. The rotation is applied
     * locally and is carried to the server by the vanilla movement packet for this tick.
     *
     * @return {@code true} when a rotation was applied
     */
    public static boolean sendRotationBeforeInteract(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || target == null || !isFinite(target)) {
            return false;
        }

        ClientPlayerEntity player = client.player;
        Vec3d delta = target.subtract(player.getEyePos());
        if (!isFinite(delta) || delta.lengthSquared() < MIN_TARGET_DISTANCE_SQ) {
            return false;
        }

        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
        if (!isFinite(yaw) || !isFinite(pitch)) {
            return false;
        }

        float yawStep = clamp(wrapDegrees(yaw - player.getYaw()), -YAW_CAP, YAW_CAP);
        float pitchStep = clamp(pitch - player.getPitch(), -PITCH_CAP, PITCH_CAP);
        if (Math.abs(yawStep) < DEDUPE_DELTA && Math.abs(pitchStep) < DEDUPE_DELTA) {
            // Already aimed: nothing to apply, but the caller may proceed with its action.
            return true;
        }

        float finalYaw = player.getYaw() + yawStep;
        float finalPitch = clamp(player.getPitch() + pitchStep, -90.0F, 90.0F);
        player.setYaw(finalYaw);
        player.setPitch(finalPitch);
        // The rotation rides along with the vanilla movement packet for this tick, so no extra
        // look packet is built here.
        return true;
    }

    /** Applies a yaw step, capped and de-duplicated. */
    public static boolean applyYaw(float yaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || !isFinite(yaw)) {
            return false;
        }

        ClientPlayerEntity player = client.player;
        float step = clamp(wrapDegrees(yaw - player.getYaw()), -YAW_CAP, YAW_CAP);
        if (Math.abs(step) < DEDUPE_DELTA) {
            return false;
        }

        player.setYaw(player.getYaw() + step);
        return true;
    }

    /** Applies a pitch step, capped and de-duplicated. */
    public static boolean applyPitch(float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || !isFinite(pitch)) {
            return false;
        }

        ClientPlayerEntity player = client.player;
        float step = clamp(pitch - player.getPitch(), -PITCH_CAP, PITCH_CAP);
        if (Math.abs(step) < DEDUPE_DELTA) {
            return false;
        }

        player.setPitch(clamp(player.getPitch() + step, -90.0F, 90.0F));
        return true;
    }

    /** Applies a yaw and pitch step together. */
    public static boolean applyRotation(float yaw, float pitch) {
        boolean yawApplied = applyYaw(yaw);
        boolean pitchApplied = applyPitch(pitch);
        return yawApplied || pitchApplied;
    }

    /**
     * Re-asserts an exact rotation without stepping it, for modules that hold the player's own
     * rotation in place (freecam restoring the camera). Values are still finite-checked and
     * identical rotations are skipped.
     */
    public static boolean restoreRotation(float yaw, float pitch, boolean syncHeadAndBody) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || !isFinite(yaw) || !isFinite(pitch)) {
            return false;
        }

        ClientPlayerEntity player = client.player;
        boolean changed =
                Math.abs(wrapDegrees(yaw - player.getYaw())) >= DEDUPE_DELTA
                        || Math.abs(pitch - player.getPitch()) >= DEDUPE_DELTA;
        player.setYaw(yaw);
        player.setPitch(pitch);
        if (syncHeadAndBody) {
            player.setHeadYaw(yaw);
            player.setBodyYaw(yaw);
        }

        return changed;
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    public static boolean isFinite(Vec3d vector) {
        return isFinite((float) vector.x) && isFinite((float) vector.y) && isFinite((float) vector.z);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
