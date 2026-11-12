package odinclient.module.playerdebug;

import java.util.function.IntPredicate;

public final class AutoFireworkTiming {
    private AutoFireworkTiming() {}

    public static int findSlot(int currentSlot, IntPredicate predicate) {
        for (int distance = 1; distance <= 4; distance++) {
            int next = Math.floorMod(currentSlot + distance, 9);
            int previous = Math.floorMod(currentSlot - distance, 9);
            if (predicate.test(next)) {
                return next;
            }

            if (predicate.test(previous)) {
                return previous;
            }
        }

        return -1;
    }

    public static long randomDelayNanos(double minSeconds, double maxSeconds, double fraction) {
        double first = clampSeconds(minSeconds, 5.0);
        double second = clampSeconds(maxSeconds, 10.0);
        double low = Math.min(first, second);
        double high = Math.max(first, second);
        double t = Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 0.5;

        return Math.max(1L, Math.round((low + t * (high - low)) * 1.0E9));
    }

    private static double clampSeconds(double seconds, double fallback) {
        return Double.isFinite(seconds) ? Math.max(0.05, Math.min(3600.0, seconds)) : fallback;
    }
}
