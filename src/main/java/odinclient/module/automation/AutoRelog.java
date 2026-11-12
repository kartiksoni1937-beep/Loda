package odinclient.module.automation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Module;
import odinclient.module.movement.AutoFirework;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.KeybindSetting;
import odinclient.setting.type.NumberSetting;

import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class AutoRelog extends Module {
    private static final long RELOG_DELAY_MS = 300L;
    private static final long RELOG_COOLDOWN_MS = 10000L;
    private final Setting<Boolean> onlyOnActivationKey;
    private final Setting<Integer> activationKey;
    private final Setting<Double> triggerY;
    private int lastKey = -2147483648;
    private boolean wasKeyDown;
    private boolean belowTriggerY;
    private boolean reconnectPending;
    private long reconnectAtMs;
    private long cooldownUntilMs;
    private ServerInfo targetServer;
    private Screen previousScreen;

    public AutoRelog() {
        super(
                Categories.MAIN,
                "auto-relog",
                "Relogs below the selected Y level or only on an activation-key press.");
        this.title = "Auto Relog";
        SettingGroup general = this.settings.getGeneral();
        this.onlyOnActivationKey =
                general.add(
                        new BooleanSetting.Builder()
                                .name("only-on-activationkey")
                                .description(
                                        "Relog only when the activation key is pressed; ignore the"
                                            + " Y trigger.")
                                .defaultValue(false)
                                .build());
        this.activationKey =
                general.add(
                        new KeybindSetting.Builder()
                                .name("activation-key")
                                .description("Relog once per key press.")
                                .defaultValue(-1)
                                .visibleWhen(
                                        () ->
                                                Boolean.TRUE.equals(
                                                        this.onlyOnActivationKey.getValue()))
                                .build());
        this.triggerY =
                general.add(
                        new NumberSetting.Builder()
                                .name("trigger-y")
                                .description("Relog when descending below this Y level.")
                                .defaultValue(-2.0)
                                .min(-64.0)
                                .sliderRange(-64.0, 320.0)
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
    private void onTick(TickEvent.Post event) {
        if (!AutoLog.isLoggedOut() && !AutoFirework.isDisconnecting()) {
            long now = System.currentTimeMillis();
            if (this.reconnectPending) {
                this.tryReconnect(now);
            } else if (this.mc.player != null && this.mc.world != null) {
                int key = this.activationKey.getValue();
                boolean down = this.isKeyDown(key);
                boolean pressed = key == this.lastKey && down && !this.wasKeyDown;
                this.lastKey = key;
                this.wasKeyDown = down;
                if (Boolean.TRUE.equals(this.onlyOnActivationKey.getValue())) {
                    this.belowTriggerY = false;
                    if (pressed && this.mc.currentScreen == null) {
                        this.startRelog(now);
                    }
                } else {
                    boolean below = this.mc.player.getY() < this.triggerY.getValue();
                    if (!below) {
                        this.belowTriggerY = false;
                    } else if (!this.belowTriggerY && now >= this.cooldownUntilMs) {
                        this.belowTriggerY = true;
                        this.startRelog(now);
                    }
                }
            }
        } else {
            this.reset();
        }
    }

    private void startRelog(long now) {
        if (this.mc.getNetworkHandler() != null) {
            ServerInfo server = this.mc.getCurrentServerEntry();
            if (server == null) {
                server = this.mc.getNetworkHandler().getServerInfo();
            }

            if (server != null && server.address != null && !server.address.isBlank()) {
                this.targetServer = server;
                this.previousScreen = this.mc.currentScreen;
                this.cooldownUntilMs = now + 10000L;
                this.reconnectAtMs = now + 300L;
                this.reconnectPending = true;
                this.mc.getNetworkHandler().getConnection().disconnect(Text.of("Auto Relog"));
            }
        }
    }

    private void tryReconnect(long now) {
        if (now >= this.reconnectAtMs
                && this.mc.world == null
                && this.mc.getNetworkHandler() == null) {
            ServerInfo server = this.targetServer;
            this.reconnectPending = false;
            this.targetServer = null;
            Screen screen =
                    this.mc.currentScreen != null ? this.mc.currentScreen : this.previousScreen;

            this.previousScreen = null;
            if (server != null && server.address != null && !server.address.isBlank()) {
                ConnectScreen.connect(
                        screen, this.mc, ServerAddress.parse(server.address), server, false, null);
            }
        }
    }

    private void reset() {
        this.lastKey = -2147483648;
        this.wasKeyDown = false;
        this.belowTriggerY = false;
        this.reconnectPending = false;
        this.reconnectAtMs = 0L;
        this.cooldownUntilMs = 0L;
        this.targetServer = null;
        this.previousScreen = null;
    }

    private boolean isKeyDown(int key) {
        if (this.mc.getWindow() == null) {
            return false;
        } else if (key >= 1000 && key <= 1007) {
            int button = key - 1000;
            return GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), button) == 1;
        } else {
            return key >= 0 && key <= 348 && InputUtil.isKeyPressed(this.mc.getWindow(), key);
        }
    }
}
