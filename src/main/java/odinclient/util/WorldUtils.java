package odinclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.BooleanSupplier;

/**
 * Single choke point for world interactions.
 *
 * <p>No interaction method and no packet is built here either: every action is a press on the
 * vanilla attack or use key. This class only decides <em>when</em> a press is allowed, by checking
 * state, vanilla-scale reach ({@link #ENTITY_REACH} / {@link #BLOCK_REACH}), aiming first, and
 * claiming {@link ActionLock} so at most one action happens per tick. The packets themselves come
 * from the vanilla input handling that reads those keys.
 */
public final class WorldUtils {

    /** Maximum entity interaction distance. */
    public static final double ENTITY_REACH = 3.0;

    /** Maximum block interaction distance. */
    public static final double BLOCK_REACH = 4.5;

    private WorldUtils() {}

    /** Attacks {@code target} through the vanilla attack key. */
    public static boolean hitEntity(Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = ready(client);
        if (player == null || target == null || target == player || target.isRemoved()) {
            return false;
        }

        if (player.distanceTo(target) > ENTITY_REACH) {
            return false;
        }

        if (!ActionLock.claim()) {
            return false;
        }

        if (!RotationUtils.sendRotationBeforeInteract(target.getBoundingBox().getCenter())
                || !aimsAtEntity(client, target)) {
            ActionLock.release();
            return false;
        }

        if (!InputSimulator.attack()) {
            ActionLock.release();
            return false;
        }

        return true;
    }

    /**
     * Interacts with the block described by {@code hit} through the vanilla use key.
     *
     * <p>The vanilla client applies the held item to whatever the crosshair is on, so the
     * interaction is only allowed when the refreshed crosshair resolves to the same block.
     */
    public static boolean interactBlock(BlockHitResult hit, Hand hand) {
        if (hit == null || hand == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = ready(client);
        if (player == null) {
            return false;
        }

        Vec3d hitPos = hit.getPos();
        if (!RotationUtils.isFinite(hitPos) || player.getEyePos().distanceTo(hitPos) > BLOCK_REACH) {
            return false;
        }

        if (!ActionLock.claim()) {
            return false;
        }

        if (!RotationUtils.sendRotationBeforeInteract(hitPos)
                || !aimsAtBlock(client, hit.getBlockPos())) {
            ActionLock.release();
            return false;
        }

        if (!InputSimulator.use()) {
            ActionLock.release();
            return false;
        }

        return true;
    }

    /**
     * Switches the selected hotbar slot. Only the inventory field changes; the vanilla client sends
     * the slot packet itself, exactly as it does for the 1-9 keys.
     */
    public static boolean setHotbarSlot(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = ready(client);
        if (player == null || slot < 0 || slot > 8) {
            return false;
        }

        if (player.getInventory().getSelectedSlot() == slot) {
            return false;
        }

        if (!ActionLock.claim()) {
            return false;
        }

        return perform(() -> InventoryUtils.selectHotbarSlot(slot));
    }

    /** Places a block at {@code pos} through the vanilla use key. */
    public static boolean placeBlock(BlockPos pos, Hand hand) {
        if (pos == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = ready(client);
        if (player == null || !InventoryActions.canPlaceAt(client, pos)) {
            return false;
        }

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        return interactBlock(hit, hand);
    }

    /**
     * Keeps the vanilla attack key held for {@code ticks} so the vanilla client mines the block at
     * {@code pos}. Mining is the one action that needs the key down across ticks rather than a
     * single press.
     */
    public static boolean breakBlock(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = ready(client);
        if (player == null || client.world.getBlockState(pos).isAir()) {
            return false;
        }

        Vec3d center = Vec3d.ofCenter(pos);
        if (player.getEyePos().distanceTo(center) > BLOCK_REACH) {
            return false;
        }

        if (!ActionLock.claim()) {
            return false;
        }

        if (!RotationUtils.sendRotationBeforeInteract(center)
                || !aimsAtBlock(client, pos)) {
            ActionLock.release();
            return false;
        }

        return InputSimulator.hold(client.options.attackKey, 2);
    }

    /** Uses the held item through the vanilla use key. */
    public static boolean interactItem(Hand hand) {
        if (hand == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = ready(client);
        if (player == null || player.getStackInHand(hand).isEmpty()) {
            return false;
        }

        if (!ActionLock.claim()) {
            return false;
        }

        if (!InputSimulator.use()) {
            ActionLock.release();
            return false;
        }

        return true;
    }

    /**
     * Recomputes the vanilla crosshair for the rotation applied above. Without this the crosshair
     * still describes the previous rotation, and a key press would act on the wrong target.
     */
    private static boolean aimsAtEntity(MinecraftClient client, Entity target) {
        if (!refreshCrosshair(client)) {
            return false;
        }

        HitResult hit = client.crosshairTarget;
        return hit instanceof EntityHitResult entityHit
                && (entityHit.getEntity() == target || entityHit.getEntity().getId() == target.getId());
    }

    private static boolean aimsAtBlock(MinecraftClient client, BlockPos pos) {
        if (!refreshCrosshair(client)) {
            return false;
        }

        HitResult hit = client.crosshairTarget;
        return hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos);
    }

    private static boolean refreshCrosshair(MinecraftClient client) {
        if (client.gameRenderer == null) {
            return false;
        }

        client.gameRenderer.updateCrosshairTarget(1.0F);
        return client.crosshairTarget != null;
    }

    /**
     * Runs the action at the start of the tick. Actions requested after the start-of-tick window
     * are queued for the next tick and reported as accepted.
     */
    private static boolean perform(BooleanSupplier action) {
        if (ActionScheduler.isTickStart()) {
            return action.getAsBoolean();
        }

        return ActionScheduler.runAtTickStart(() -> action.getAsBoolean());
    }

    private static ClientPlayerEntity ready(MinecraftClient client) {
        if (client == null
                || client.player == null
                || client.world == null
                || client.interactionManager == null) {
            return null;
        }

        return client.player;
    }

}
