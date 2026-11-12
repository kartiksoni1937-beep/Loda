package odinclient.module.movement;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.mixin.accessor.SelectedSlotSyncInvoker;
import odinclient.module.Module;
import odinclient.module.playerdebug.AutoFireworkTiming;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;

public final class AutoFirework extends Module {
    private static AutoFirework instance;
    private final Setting<Double> minDelay;
    private final Setting<Double> maxDelay;
    private final Setting<Boolean> hotbarSwitch;
    private final Setting<Boolean> logoutOnTotemPop;
    private long nextUseNanos = Long.MAX_VALUE;
    private Key pressedKey;
    private boolean disconnecting;

    public AutoFirework() {
        super(
                Categories.MAIN,
                "auto-firework",
                "Uses fireworks with a fresh random delay for each use.");
        this.title = "Auto Firework";
        SettingGroup general = this.settings.getGeneral();
        this.minDelay =
                general.add(
                        new NumberSetting.Builder()
                                .name("min-delay")
                                .description("Minimum delay between firework uses, in seconds.")
                                .defaultValue(5.0)
                                .range(0.05, 3600.0)
                                .sliderRange(0.05, 60.0)
                                .build());
        this.maxDelay =
                general.add(
                        new NumberSetting.Builder()
                                .name("max-delay")
                                .description("Maximum delay between firework uses, in seconds.")
                                .defaultValue(10.0)
                                .range(0.05, 3600.0)
                                .sliderRange(0.05, 60.0)
                                .build());
        this.hotbarSwitch =
                general.add(
                        new BooleanSetting.Builder()
                                .name("hotbar-switch")
                                .description(
                                        "Switch to the nearest firework stack in the hotbar when"
                                            + " neither hand holds one.")
                                .defaultValue(true)
                                .build());
        this.logoutOnTotemPop =
                general.add(
                        new BooleanSetting.Builder()
                                .name("logout-on-totem-pop")
                                .description("Disconnect when your own player pops a totem.")
                                .defaultValue(false)
                                .build());
        instance = this;
    }

    @Override
    public void onActivate() {
        this.resetTimer(true);
    }

    @Override
    public void onDeactivate() {
        this.resetTimer(false);
    }

    @EventHandler
    private void onWorldJoin(WorldJoinEvent event) {
        this.resetTimer(true);
    }

    @EventHandler
    private void onWorldLeave(WorldLeaveEvent event) {
        this.releaseUseKey();
        this.nextUseNanos = Long.MAX_VALUE;
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (this.isInWorld()
                && !this.disconnecting
                && this.mc.currentScreen == null
                && this.nextUseNanos != Long.MAX_VALUE) {
            long now = System.nanoTime();
            if (now >= this.nextUseNanos) {
                KeyBinding useKey = this.mc.options.useKey;
                if (!useKey.isPressed() && this.ensureFireworkHeld()) {
                    this.pressedKey = useKey.getDefaultKey();
                    KeyBinding.setKeyPressed(this.pressedKey, true);
                    KeyBinding.onKeyPressed(this.pressedKey);
                }

                this.nextUseNanos = this.nextUseTime(now);
            }
        }
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        this.releaseUseKey();
    }

    private boolean ensureFireworkHeld() {
        if (isFirework(this.mc.player.getMainHandStack())
                || isFirework(this.mc.player.getOffHandStack())) {
            return true;
        } else if (this.hotbarSwitch.getValue() && this.mc.interactionManager != null) {
            PlayerInventory inventory = this.mc.player.getInventory();
            IntPredicate isFireworkSlot = slot -> isFirework(inventory.getStack(slot));
            int targetSlot =
                    AutoFireworkTiming.findSlot(inventory.getSelectedSlot(), isFireworkSlot);
            if (targetSlot < 0) {
                return false;
            } else {
                inventory.setSelectedSlot(targetSlot);
                ((SelectedSlotSyncInvoker) this.mc.interactionManager)
                        .odinclient$syncSelectedSlot();
                return true;
            }
        } else {
            return false;
        }
    }

    private static boolean isFirework(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(Items.FIREWORK_ROCKET);
    }

    private long nextUseTime(long now) {
        long delay =
                AutoFireworkTiming.randomDelayNanos(
                        this.minDelay.getValue(),
                        this.maxDelay.getValue(),
                        ThreadLocalRandom.current().nextDouble());
        return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE - 1 : now + delay;
    }

    private void releaseUseKey() {
        if (this.pressedKey != null) {
            KeyBinding.setKeyPressed(this.pressedKey, false);
            this.pressedKey = null;
        }
    }

    private boolean isInWorld() {
        return this.mc.player != null && this.mc.world != null;
    }

    private void resetTimer(boolean schedule) {
        this.releaseUseKey();
        this.disconnecting = false;
        this.nextUseNanos =
                schedule && this.isInWorld() ? this.nextUseTime(System.nanoTime()) : Long.MAX_VALUE;
    }

    public static void onTotemPop() {
        AutoFirework module = instance;
        if (module != null && module.isActive() && module.logoutOnTotemPop.getValue()) {
            module.disconnect("Totem popped");
        }
    }

    public static boolean isDisconnecting() {
        return instance != null && instance.isActive() && instance.disconnecting;
    }

    private void disconnect(String reason) {
        if (!this.disconnecting && this.isInWorld() && this.mc.getNetworkHandler() != null) {
            this.disconnecting = true;
            this.releaseUseKey();
            this.mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
        }
    }
}
