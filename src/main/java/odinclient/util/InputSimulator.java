package odinclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import odinclient.mixin.accessor.VanillaAttackInvoker;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simulates player input through vanilla key bindings and the vanilla attack path.
 *
 * <p>Keyboard: {@link #hold(KeyBinding, int)} / {@link #press(KeyBinding)} flip the same
 * {@code KeyBinding} the game reads, so the action is produced by vanilla logic rather than by a
 * hand-built packet. Clicks: {@link #click(Click)} runs the vanilla attack routine (crosshair
 * target, swing and interaction packet included) or holds the vanilla use key for one tick.
 *
 * <p>All simulated input is released automatically; nothing is left pressed across ticks.
 */
public final class InputSimulator {

    public enum Click {
        LEFT,
        RIGHT
    }

    private static final int MAX_HOLD_TICKS = 20;

    private static final Map<KeyBinding, Integer> HELD = new LinkedHashMap<>();

    private InputSimulator() {}

    /** Simulates a click through the vanilla input path, at the start of the tick. */
    public static boolean click(Click click) {
        if (click == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        if (!ActionScheduler.isTickStart()) {
            return ActionScheduler.runAtTickStart(() -> click(click));
        }

        if (click == Click.RIGHT) {
            return hold(client.options.useKey, 1);
        }

        return ((VanillaAttackInvoker) client).odinclient$doAttack();
    }

    /** Holds {@code key} for {@code ticks} ticks (1 = this tick only). */
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

        key.setPressed(true);
        HELD.put(key, Math.max(1, Math.min(MAX_HOLD_TICKS, ticks)));
        return true;
    }

    /** Presses and releases {@code key} once, inside the current tick. */
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

        key.setPressed(true);
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

    public static boolean isSimulating(KeyBinding key) {
        return key != null && HELD.containsKey(key);
    }

    /** Called by {@link ActionScheduler} at the start of every tick. */
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
}
