package odinclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Single choke point for world interactions.
 *
 * <p>Nothing here acts immediately: every entry point validates the client state and the vanilla
 * reach ({@link #ENTITY_REACH} / {@link #BLOCK_REACH}), aims first, and then pushes the interaction
 * onto {@link ActionQueue}. The queue runs one action per tick with {@link ActionLock} claimed, and
 * re-applies the rotation immediately before the interaction so the look comes before the action
 * packet.
 *
 * <p>When the rotation is not yet on target the entry point returns {@code false} and nothing is
 * queued, so a calling module stays in its wait phase and tries again next tick.
 */
public final class WorldUtils {

    /** Maximum entity interaction distance. */
    public static final double ENTITY_REACH = 3.0;

    /** Maximum block interaction distance. */
    public static final double BLOCK_REACH = 4.5;

    private WorldUtils() {}

    /** Attacks {@code target}. */
    public static boolean hitEntity(Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = ready(client);
        if (player == null || target == null || target == player || target.isRemoved()) {
            return false;
        }

        if (player.distanceTo(target) > ENTITY_REACH) {
            return false;
        }

        if (!aimsAtEntity(client, target)) {
            return false;
        }

        Vec3d center = target.getBoundingBox().getCenter();
        return ActionQueue.push(
                "attack:" + target.getId(),
                () -> {
                    if (client.player == null
                            || target.isRemoved()
                            || client.player.distanceTo(target) > ENTITY_REACH) {
                        return;
                    }

                    RotationUtils.sendRotationBeforeInteract(target.getBoundingBox().getCenter());
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
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

        if (!aimsAtBlock(client, hit.getBlockPos())) {
            return false;
        }

        ItemStack predicted = player.getStackInHand(hand);
        return ActionQueue.pushPlace(
                "interact:" + hit.getBlockPos().toShortString(),
                hit.getBlockPos(),
                predicted,
                () -> {
                    if (client.player == null
                            || client.player.getEyePos().distanceTo(hitPos) > BLOCK_REACH) {
                        return;
                    }

                    RotationUtils.sendRotationBeforeInteract(hitPos);
                    client.interactionManager.interactBlock(client.player, hand, hit);
                });
    }

    /**
     * Switches the selected hotbar slot as one queued action, so it never shares a tick with a
     * placement or attack.
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

        return ActionQueue.push("select-slot:" + slot, () -> InventoryUtils.selectHotbarSlot(slot));
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

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
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

        if (!RotationUtils.sendRotationBeforeInteract(center)) {
            return false;
        }

        return ActionQueue.push(
                "break:" + pos.toShortString(),
                () -> {
                    if (client.player == null || client.world.getBlockState(pos).isAir()) {
                        return;
                    }

                    RotationUtils.sendRotationBeforeInteract(center);
                    client.interactionManager.breakBlock(pos);
                });
    }

    /**
     * Keeps breaking the block at {@code pos} for this tick, the way holding attack does. Mining
     * needs one call per tick, so the module calls this each tick and each tick queues one step.
     */
    public static boolean startBreaking(BlockPos pos, Direction side) {
        if (pos == null || side == null) {
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

        if (!RotationUtils.sendRotationBeforeInteract(center)) {
            return false;
        }

        return ActionQueue.push(
                "mine:" + pos.toShortString(),
                () -> {
                    if (client.player == null || client.world.getBlockState(pos).isAir()) {
                        return;
                    }

                    RotationUtils.sendRotationBeforeInteract(center);
                    client.interactionManager.updateBlockBreakingProgress(pos, side);
                });
    }

    /** Stops the vanilla break progress. This changes no world state, so it is not queued. */
    public static void cancelBreaking() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
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

        return ActionQueue.push(
                "use-item:" + hand.name(),
                () -> {
                    if (client.player == null) {
                        return;
                    }

                    client.interactionManager.interactItem(client.player, hand);
                    client.player.swingHand(hand);
                });
    }

    /**
     * Applies one capped rotation step toward {@code target}, refreshes the vanilla crosshair and
     * reports whether it now resolves to the requested entity.
     */
    private static boolean aimsAtEntity(MinecraftClient client, Entity target) {
        if (!RotationUtils.sendRotationBeforeInteract(target.getBoundingBox().getCenter())) {
            return false;
        }

        if (!refreshCrosshair(client)) {
            return false;
        }

        HitResult hit = client.crosshairTarget;
        return hit instanceof EntityHitResult entityHit
                && (entityHit.getEntity() == target
                        || entityHit.getEntity().getId() == target.getId());
    }

    private static boolean aimsAtBlock(MinecraftClient client, BlockPos pos) {
        if (!RotationUtils.sendRotationBeforeInteract(Vec3d.ofCenter(pos))) {
            return false;
        }

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
