package odinclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import odinclient.mixin.accessor.SelectedSlotSyncInvoker;

import java.util.function.BooleanSupplier;

/**
 * Single choke point for world interactions (attacks, block/item interactions).
 *
 * <p>Every entry point validates the client state, enforces vanilla-scale reach
 * ({@link #ENTITY_REACH} for entities, {@link #BLOCK_REACH} for blocks), rotates the player
 * toward the target before acting, and runs through an action lock so the same target cannot be
 * acted on twice within one tick.
 */
public final class WorldUtils {

    /** Maximum entity interaction distance. */
    public static final double ENTITY_REACH = 3.0;

    /** Maximum block interaction distance. */
    public static final double BLOCK_REACH = 4.5;

    private WorldUtils() {}

    /** Attacks {@code target}. Returns {@code true} when the attack was performed. */
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

        if (!RotationUtils.sendRotationBeforeInteract(target.getBoundingBox().getCenter())) {
            ActionLock.release();
            return false;
        }
        return perform(
                () -> {
                    client.interactionManager.attackEntity(player, target);
                    return true;
                });
    }

    /** Interacts with the block described by {@code hit}. */
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

        if (!RotationUtils.sendRotationBeforeInteract(hitPos)) {
            ActionLock.release();
            return false;
        }

        return perform(() -> client.interactionManager.interactBlock(player, hand, hit).isAccepted());
    }

    /**
     * Switches the selected hotbar slot. Only the vanilla selected-slot sync happens here; no
     * movement packet is produced, the vanilla client already sends its own each tick.
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

        return perform(
                () -> {
                    player.getInventory().setSelectedSlot(slot);
                    ((SelectedSlotSyncInvoker) client.interactionManager)
                            .odinclient$syncSelectedSlot();
                    return true;
                });
    }

    /** Places a block at {@code pos} if the position can take one. */
    public static boolean placeBlock(BlockPos pos, Hand hand) {
        if (pos == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = ready(client);
        if (player == null || !InventoryActions.canPlaceAt(client, pos)) {
            return false;
        }

        BlockHitResult hit =
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        return interactBlock(hit, hand);
    }

    /** Breaks the block at {@code pos}. */
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

        if (!RotationUtils.sendRotationBeforeInteract(center)) {
            ActionLock.release();
            return false;
        }

        return perform(() -> client.interactionManager.breakBlock(pos));
    }

    /** Uses the held item ({@code hand}) against the world. */
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

        return perform(() -> client.interactionManager.interactItem(player, hand).isAccepted());
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
