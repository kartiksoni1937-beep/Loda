package odinclient.module.shortcut;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.InputUtil;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.KeybindSetting;
import odinclient.util.ChatUtil;

import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class HomeReset extends Module {
    private static final long SETHOME_DELAY_NANOS = 500000000L;
    private final Setting<Integer> home;
    private final Setting<Integer> activationKeybind;
    private int lastKeybind = -2147483648;
    private boolean wasKeyDown;
    private long sethomeAtNanos;
    private int pendingHome;

    public HomeReset(Category category) {
        super(
                category,
                "home-reset",
                "Deletes and recreates the selected DonutSMP home with one key press.");
        SettingGroup general = this.settings.getGeneral();
        this.home =
                general.add(
                        new IntSetting.Builder()
                                .name("home")
                                .description("Home number used by the reset macro.")
                                .defaultValue(1)
                                .range(1, 7)
                                .sliderRange(1, 7)
                                .build());
        this.activationKeybind =
                general.add(
                        new KeybindSetting.Builder()
                                .name("activation-keybind")
                                .description(
                                        "Runs the home reset macro once when this key is pressed.")
                                .defaultValue(-1)
                                .build());
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player != null && this.mc.world != null && this.mc.getWindow() != null) {
            if (this.sethomeAtNanos != 0L && System.nanoTime() >= this.sethomeAtNanos) {
                ChatUtil.sendChat("/sethome " + this.pendingHome);
                this.clearPendingSethome();
            }

            int key = this.activationKeybind.getValue();
            boolean down = this.isKeyDown(key);
            if (key != this.lastKeybind) {
                this.lastKeybind = key;
                this.wasKeyDown = down;
            } else {
                boolean pressed = down && !this.wasKeyDown;
                this.wasKeyDown = down;
                if (pressed && this.mc.currentScreen == null && this.sethomeAtNanos == 0L) {
                    this.pendingHome = Math.max(1, Math.min(7, this.home.getValue()));
                    ChatUtil.sendChat("/delhome " + this.pendingHome);
                    this.sethomeAtNanos = System.nanoTime() + SETHOME_DELAY_NANOS;
                }
            }
        } else {
            this.clearPendingSethome();
        }
    }

    private boolean isKeyDown(int key) {
        if (key >= 1000 && key <= 1007) {
            return GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), key - 1000) == 1;
        } else {
            return key >= 0 && key <= 348 && InputUtil.isKeyPressed(this.mc.getWindow(), key);
        }
    }

    private void reset() {
        this.lastKeybind = -2147483648;
        this.wasKeyDown = false;
        this.clearPendingSethome();
    }

    private void clearPendingSethome() {
        this.sethomeAtNanos = 0L;
        this.pendingHome = 0;
    }
}
