package odinclient.module.shortcut;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.InputUtil;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.KeybindSetting;
import odinclient.util.ChatUtil;

@Environment(EnvType.CLIENT)
public final class KeyHomeSetter extends Module {
    private final Setting<KeyHomeSetter.HomeSlot> homeSelector =
            this.settings
                    .getGeneral()
                    .add(
                            new EnumSetting.Builder<KeyHomeSetter.HomeSlot>()
                                    .name("home-selector")
                                    .description(
                                            "Selects which DonutSMP home slot is set when the"
                                                + " activation key is pressed.")
                                    .defaultValue(KeyHomeSetter.HomeSlot.HOME_1)
                                    .build());
    private final Setting<Integer> activationKey =
            this.settings
                    .getGeneral()
                    .add(
                            new KeybindSetting.Builder()
                                    .name("activation-key")
                                    .description(
                                            "Press this key while Key Homesetter is enabled to set"
                                                + " the selected home once.")
                                    .build());
    private boolean wasKeyDown;
    private int lastKey = -2147483648;

    public KeyHomeSetter(Category category) {
        super(
                category,
                "key-homesetter",
                "Sets the selected DonutSMP home once per activation-key press.");
    }

    @Override
    public void onActivate() {
        int key = this.activationKey.getValue();
        this.lastKey = key;
        this.wasKeyDown = this.isKeyDown(key);
    }

    @Override
    public void onDeactivate() {
        this.wasKeyDown = false;
        this.lastKey = -2147483648;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        int key = this.activationKey.getValue();
        boolean down = this.isKeyDown(key);
        if (key != this.lastKey) {
            this.lastKey = key;
            this.wasKeyDown = down;
        } else {
            boolean pressed = down && !this.wasKeyDown;
            this.wasKeyDown = down;
            if (pressed
                    && this.mc.currentScreen == null
                    && this.mc.player != null
                    && this.mc.world != null) {
                ChatUtil.sendChat("/sethome " + this.homeSelector.getValue().slot);
            }
        }
    }

    private boolean isKeyDown(int key) {
        return key >= 0
                && this.mc.getWindow() != null
                && InputUtil.isKeyPressed(this.mc.getWindow(), key);
    }

    public static enum HomeSlot {
        HOME_1(1),
        HOME_2(2),
        HOME_3(3),
        HOME_4(4),
        HOME_5(5),
        HOME_6(6),
        HOME_7(7);

        private final int slot;

        private HomeSlot(int slot) {
            this.slot = slot;
        }
    }
}
