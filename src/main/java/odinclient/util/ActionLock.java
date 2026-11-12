package odinclient.util;

/**
 * One world interaction per client tick.
 *
 * <p>Duplicate and out-of-order interactions (place + place, place + break, interact + attack all
 * landing in the same tick) are what a server sees as packet flooding. {@link #clear()} runs as the
 * very first statement of the client tick, before any module handler, and every world interaction in
 * {@link WorldUtils} has to claim this lock first. Exactly one claim is granted per tick.
 */
public final class ActionLock {

    private static boolean claimed;

    private ActionLock() {}

    /** Opens a new tick. Must be the first thing the client tick does. */
    public static void clear() {
        claimed = false;
    }

    /**
     * Claims this tick's single world interaction.
     *
     * @return {@code true} for the first claim of the tick, {@code false} for every further one
     */
    public static boolean claim() {
        if (claimed) {
            return false;
        }

        claimed = true;
        return true;
    }

    /** @return {@code true} when a world interaction already happened this tick */
    public static boolean isClaimed() {
        return claimed;
    }

    /** Gives back an unused claim when an action was aborted before it reached the server. */
    public static void release() {
        claimed = false;
    }
}
