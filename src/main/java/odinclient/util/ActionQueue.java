package odinclient.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import odinclient.module.ModuleContext;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One action per client tick.
 *
 * <p>Modules never call the interaction manager on their own: they push a {@link Runnable} here and
 * the queue runs it at the start of a tick, after claiming {@link ActionLock}. That keeps every
 * interaction in order and guarantees a single world action per tick no matter how many modules
 * want to act.
 *
 * <p>A placement task also predicts its block on the client, so the placed block is visible
 * immediately instead of after the server round trip.
 */
public final class ActionQueue {

    /** Maximum number of actions waiting; a module that overflows it is simply deferred. */
    private static final int MAX_QUEUED = 32;

    private static final Deque<Task> QUEUE = new ArrayDeque<>();

    private ActionQueue() {}

    /** A single queued interaction. */
    private record Task(String label, Runnable body, BlockPos placePos, ItemStack placeStack) {}

    /** Queues a plain action (attack, use, slot change, break). */
    public static boolean push(String label, Runnable action) {
        return push(label, action, null, null);
    }

    /**
     * Queues a block placement: the action runs first, then {@code pos} is rendered on the client
     * with the block {@code stack} would place.
     */
    public static boolean pushPlace(String label, BlockPos pos, ItemStack stack, Runnable action) {
        return push(label, action, pos, stack);
    }

    private static boolean push(
            String label, Runnable action, BlockPos placePos, ItemStack placeStack) {
        if (action == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !client.isOnThread()) {
            return false;
        }

        if (QUEUE.size() >= MAX_QUEUED) {
            return false;
        }

        QUEUE.add(new Task(label == null ? "action" : label, action, placePos, placeStack));
        return true;
    }

    /**
     * Runs at most one queued action. Called as the first thing a client tick does, after
     * {@link ActionLock#clear()}.
     *
     * @return {@code true} when an action ran
     */
    public static boolean tick(MinecraftClient client) {
        if (QUEUE.isEmpty() || client == null || client.player == null || client.world == null) {
            return false;
        }

        if (!ActionLock.claim()) {
            return false;
        }

        Task task = QUEUE.poll();
        try {
            task.body().run();
            if (task.placePos() != null) {
                renderPlacement(client, task.placePos(), task.placeStack());
            }
        } catch (RuntimeException error) {
            ModuleContext.LOGGER.error("Queued action '{}' failed", task.label(), error);
        }

        return true;
    }

    /** Number of actions waiting for a tick. */
    public static int size() {
        return QUEUE.size();
    }

    /** Drops everything queued, for world changes. */
    public static void clear() {
        QUEUE.clear();
    }

    /**
     * Shows the placement on the client right away. The server still owns the real block; when it
     * disagrees its block update replaces this prediction.
     */
    private static void renderPlacement(
            MinecraftClient client, BlockPos pos, ItemStack stack) {
        if (pos == null || stack == null || stack.isEmpty()) {
            return;
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        BlockState predicted = blockItem.getBlock().getDefaultState();
        if (client.world.getBlockState(pos) == predicted) {
            return;
        }

        client.world.setBlockState(pos, predicted, Block.NOTIFY_ALL);
    }
}
