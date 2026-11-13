package odinclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import odinclient.mixin.accessor.KeyBindingAccessor;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only way the client acts: vanilla key state.
 *
 * <p>Nothing here builds a packet or calls an interaction method directly. A simulated action is a
 * press on the very {@link KeyBinding} the game reads, so the vanilla client runs its own input
 * handling ({@code MinecraftClient#handleInputEvents}) and produces the single packet a human hand
 * would produce. The press is released at the start of the next tick, before any module runs and
 * before vanilla reads input again, so exactly one action is emitted per simulated press.
 *
 * <p>{@link #attack()}, {@link #use()}, {@link #jump()} and {@link #sprint()} are the complete
 * action vocabulary; {@link #hold(KeyBinding, int)} covers cases that need the key down longer
 * (mining).
 */
public final class InputSimulator {

    public enum Click {
        LEFT,
        RIGHT
    }

    private static final int MAX_HOLD_TICKS = 20;

    private static final Map<KeyBinding, Integer> HELD = new LinkedHashMap<>();

    private InputSimulator() {}

    /** Presses the vanilla attack key for this tick; vanilla swings and attacks the crosshair. */
    public static boolean attack() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && hold(client.options.attackKey, 1);
    }

    /** Presses the vanilla use key for this tick; vanilla places/uses on the crosshair. */
    public static boolean use() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && hold(client.options.useKey, 1);
    }

    /** Presses the vanilla jump key for this tick. */
    public static boolean jump() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && hold(client.options.jumpKey, 1);
    }

    /** Presses the vanilla sprint key for this tick. */
    public static boolean sprint() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && hold(client.options.sprintKey, 1);
    }

    /** Backwards-compatible click entry point: both clicks are plain key presses. */
    public static boolean click(Click click) {
        if (click == null) {
            return false;
        }

        return click == Click.RIGHT ? use() : attack();
    }

    /**
     * Holds {@code key} for {@code ticks} ticks (1 = this tick only). The key is released by
     * {@link #beginTick()} at the start of the following tick.
     */
    public static boolean hold(KeyBinding key, int ticks) {
        if (key == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        if (!ActionScheduler.isTickStart()) {
            return ActionScheduler.runAtTickStart(() -> hold(key, ticks));
        }

        if (!key.isPressed()) {
            key.setPressed(true);
            bumpPressedCount(key);
        }

        HELD.put(key, Math.max(1, Math.min(MAX_HOLD_TICKS, ticks)));
        return true;
    }

    /** Presses and releases {@code key} once, so vanilla sees a single press this tick. */
    public static boolean press(KeyBinding key) {
        if (key == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        if (!ActionScheduler.isTickStart()) {
            return ActionScheduler.runAtTickStart(() -> press(key));
        }

        bumpPressedCount(key);
        key.setPressed(false);
        return true;
    }

    /** Releases a simulated key immediately. */
    public static void release(KeyBinding key) {
        if (key != null) {
            key.setPressed(false);
            HELD.remove(key);
        }
    }

    /** Releases every key this class pressed. */
    public static void releaseAll() {
        Iterator<Map.Entry<KeyBinding, Integer>> iterator = HELD.entrySet().iterator();
        while (iterator.hasNext()) {
            iterator.next().getKey().setPressed(false);
            iterator.remove();
        }
    }

    /** {@code true} while this class is holding {@code key} down. */
    public static boolean isSimulating(KeyBinding key) {
        return key != null && HELD.containsKey(key);
    }

    /** Called by {@link ActionScheduler} at the start of every tick, before any module. */
    static void beginTick() {
        Iterator<Map.Entry<KeyBinding, Integer>> iterator = HELD.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<KeyBinding, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                entry.getKey().setPressed(false);
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    /**
     * Vanilla reads {@code wasPressed()} for attack and use, which is driven by the press counter
     * rather than the pressed flag. Bumping it once per simulated press makes the vanilla input
     * handling consume exactly one action.
     */
    private static void bumpPressedCount(KeyBinding key) {
        KeyBindingAccessor accessor = (KeyBindingAccessor) key;
        if (accessor.odinclient$getTimesPressed() <= 0) {
            accessor.odinclient$setTimesPressed(1);
        }
    }
}
