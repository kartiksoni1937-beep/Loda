package odinclient.module.shortcut;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.InputUtil;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.KeybindSetting;
import odinclient.util.ChatUtil;

@Environment(EnvType.CLIENT)
public final class KeyRtp extends Module {
    private final Setting<Integer> activationKey =
            this.settings
                    .getGeneral()
                    .add(
                            new KeybindSetting.Builder()
                                    .name("activation-key")
                                    .description(
                                            "Press this key while Key RTP is enabled to send /rtp"
                                                + " once.")
                                    .build());
    private boolean keyHeld;
    private int lastKeyCode = -2147483648;

    public KeyRtp(Category category) {
        super(category, "key-rtp", "Sends /rtp once per activation-key press.");
    }

    @Override
    public void onActivate() {
        int key = this.activationKey.getValue();
        this.lastKeyCode = key;
        this.keyHeld = this.isKeyDown(key);
    }

    @Override
    public void onDeactivate() {
        this.keyHeld = false;
        this.lastKeyCode = -2147483648;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        int key = this.activationKey.getValue();
        boolean down = this.isKeyDown(key);
        if (key != this.lastKeyCode) {
            this.lastKeyCode = key;
            this.keyHeld = down;
        } else {
            boolean pressed = down && !this.keyHeld;
            this.keyHeld = down;
            if (pressed
                    && this.mc.currentScreen == null
                    && this.mc.player != null
                    && this.mc.world != null) {
                ChatUtil.sendChat("/rtp");
            }
        }
    }

    private boolean isKeyDown(int key) {
        return key >= 0
                && this.mc.getWindow() != null
                && InputUtil.isKeyPressed(this.mc.getWindow(), key);
    }
}
