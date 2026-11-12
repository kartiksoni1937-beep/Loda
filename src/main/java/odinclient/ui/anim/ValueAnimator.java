package odinclient.ui.anim;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ValueAnimator {
    private static final float DEFAULT_DELTA_SECONDS = 0.016666668F;
    private static final float MAX_DELTA_SECONDS = 0.05F;
    private static final float SMOOTHING_SPEED = 20.0F;
    private static final float SNAP_THRESHOLD = 0.002F;
    private static final int ACCENT_FROM_COLOR = -13291714;
    private static final int NEUTRAL_FROM_COLOR = -3947063;
    private static final int NEUTRAL_TO_COLOR = -526086;
    private static final Map<Map<?, ?>, Map<String, Long>> lastUpdateNanos =
            new IdentityHashMap<>();

    private ValueAnimator() {}

    public static float smooth(
            Object owner, Map<String, Float> values, String key, float target, boolean animate) {
        if (!animate) {
            values.put(key, target);
            clearTimestamp(values, key);
            return target;
        } else {
            long now = System.nanoTime();
            long previous = swapLastTimestamp(values, key, now);
            float delta;
            if (previous == 0L) {
                delta = DEFAULT_DELTA_SECONDS;
            } else {
                delta =
                        Math.min(
                                MAX_DELTA_SECONDS,
                                Math.max(0.0F, (float) (now - previous) / 1.0E9F));
            }

            float current = values.getOrDefault(key, target);
            float next =
                    current
                            + (target - current)
                                    * (1.0F - (float) Math.exp(-SMOOTHING_SPEED * delta));
            if (Math.abs(target - next) < SNAP_THRESHOLD) {
                next = target;
            }

            values.put(key, next);
            return next;
        }
    }

    public static int accentColor(float progress, int color) {
        return blendColors(ACCENT_FROM_COLOR, 0xFF000000 | color & 0xFFFFFF, progress);
    }

    public static int neutralColor(float progress) {
        return blendColors(NEUTRAL_FROM_COLOR, NEUTRAL_TO_COLOR, progress);
    }

    private static synchronized long swapLastTimestamp(Map<?, ?> values, String key, long nanos) {
        Long previous =
                lastUpdateNanos.computeIfAbsent(values, k -> new HashMap<>()).put(key, nanos);
        return previous == null ? 0L : previous;
    }

    private static synchronized void clearTimestamp(Map<?, ?> values, String key) {
        Map<String, Long> timestamps = lastUpdateNanos.get(values);
        if (timestamps != null) {
            timestamps.remove(key);
        }
    }

    private static int blendColors(int from, int to, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        return lerp(from >>> 24 & 0xFF, to >>> 24 & 0xFF, t) << 24
                | lerp(from >>> 16 & 0xFF, to >>> 16 & 0xFF, t) << 16
                | lerp(from >>> 8 & 0xFF, to >>> 8 & 0xFF, t) << 8
                | lerp(from & 0xFF, to & 0xFF, t);
    }

    private static int lerp(int from, int to, float progress) {
        return Math.round(from + (to - from) * progress);
    }
}
