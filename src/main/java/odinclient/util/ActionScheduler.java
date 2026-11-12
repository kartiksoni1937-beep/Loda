package odinclient.util;

import net.minecraft.client.MinecraftClient;

import odinclient.module.ModuleContext;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Runs every client action at the start of a tick.
 *
 * <p>The client tick opens a start-of-tick window (see {@code MinecraftClientMixin}) before any
 * module handler runs. Actions requested inside that window execute immediately, in request order;
 * actions requested later in the tick (end-of-tick handlers, render callbacks, packet callbacks)
 * are queued and run at the start of the next tick. Nothing is ever executed off the client thread.
 */
public final class ActionScheduler {

    private static final Deque<Runnable> PENDING = new ArrayDeque<>();
    private static final int MAX_PENDING = 256;
    private static final int MAX_ACTIONS_PER_TICK = 64;

    private static boolean tickStart;

    private ActionScheduler() {}

    /** Opens the start-of-tick window and drains everything queued for it. */
    public static void beginTickStart(MinecraftClient client) {
        tickStart = true;
        try {
            InputSimulator.beginTick();

            int budget = MAX_ACTIONS_PER_TICK;
            while (!PENDING.isEmpty() && budget-- > 0) {
                runSafely(PENDING.poll());
            }

            if (!PENDING.isEmpty()) {
                ModuleContext.LOGGER.warn(
                        "ActionScheduler dropped {} queued action(s) that exceeded the tick budget",
                        PENDING.size());
                PENDING.clear();
            }
        } finally {
            tickStart = false;
        }
    }

    public static void endTickStart() {
        tickStart = false;
    }

    public static boolean isTickStart() {
        return tickStart;
    }

    /**
     * Runs {@code action} now when the start-of-tick window is open, otherwise queues it for the
     * next one.
     *
     * @return {@code true} when the action ran or was accepted for the next tick start
     */
    public static boolean runAtTickStart(Runnable action) {
        if (action == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !client.isOnThread()) {
            return false;
        }

        if (tickStart) {
            runSafely(action);
            return true;
        }

        if (PENDING.size() >= MAX_PENDING) {
            return false;
        }

        PENDING.add(action);
        return true;
    }

    public static int pendingCount() {
        return PENDING.size();
    }

    public static void clear() {
        PENDING.clear();
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            ModuleContext.LOGGER.error("Queued client action failed", error);
        }
    }
}
