package odinclient.ui.anim;

public final class AnimationUtil {
    private AnimationUtil() {}

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static float progress(float value, float start, float end) {
        return end <= start
                ? (value >= end ? 1.0F : 0.0F)
                : clamp01((value - start) / (end - start));
    }

    public static float easeOutCubic(float t) {
        float inverse = 1.0F - clamp01(t);
        return 1.0F - inverse * inverse * inverse;
    }

    public static float easeInCubic(float t) {
        float clamped = clamp01(t);
        return clamped * clamped * clamped;
    }

    public static float easeInOutCubic(float t) {
        float clamped = clamp01(t);
        return clamped < 0.5F
                ? 4.0F * clamped * clamped * clamped
                : 1.0F - (float) Math.pow(-2.0F * clamped + 2.0F, 3.0) / 2.0F;
    }

    public static float easeOutExpo(float t) {
        float clamped = clamp01(t);
        return clamped >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * clamped);
    }

    public static float easeOutBack(float t) {
        float shifted = clamp01(t) - 1.0F;
        float overshoot = 1.70158F;
        return 1.0F
                + (overshoot + 1.0F) * shifted * shifted * shifted
                + overshoot * shifted * shifted;
    }

    public static float smoothApproach(float current, float target, float speed, float delta) {
        return Math.abs(target - current) < 5.0E-4F
                ? target
                : current + (target - current) * (1.0F - (float) Math.exp(-speed * delta));
    }

    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    public static int multiplyAlpha(int color, float factor) {
        int alpha = Math.round((color >>> 24) * clamp01(factor));
        return alpha << 24 | color & 16777215;
    }

    public static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
    }

    public static int lerpColor(int from, int to, float progress) {
        float t = clamp01(progress);
        int alpha = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
