package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.KeybindSetting;
import odinclient.util.WorldUtils;
import odinclient.util.InventoryUtils;

import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class KeyPearl extends Module {
    private final Setting<Integer> activateKey;
    private final Setting<Integer> delay;
    private final Setting<Boolean> switchBackEnabled;
    private final Setting<Integer> switchDelay;
    private int lastKey = -2147483648;
    private boolean wasKeyDown;
    private int previousSlot = -1;
    private int pearlSlot = -1;
    private int throwTicksLeft;
    private int switchTicksLeft;
    private KeyPearl.PearlState state = KeyPearl.PearlState.IDLE;

    public KeyPearl(Category category) {
        super(
                category,
                "key-pearl",
                "Switches to an ender pearl and throws it when you press a bind.");
        SettingGroup general = this.settings.getGeneral();
        this.activateKey =
                general.add(
                        new KeybindSetting.Builder()
                                .name("activate-key")
                                .description("Throws one ender pearl when this key is pressed.")
                                .defaultValue(-1)
                                .build());
        this.delay =
                general.add(
                        new IntSetting.Builder()
                                .name("delay")
                                .description(
                                        "Ticks to wait after selecting the pearl before throwing"
                                            + " it.")
                                .defaultValue(0)
                                .range(0, 20)
                                .sliderRange(0, 20)
                                .build());
        this.switchBackEnabled =
                general.add(
                        new BooleanSetting.Builder()
                                .name("switch-back")
                                .description(
                                        "Switch back to the previous hotbar slot after throwing.")
                                .defaultValue(true)
                                .build());
        this.switchDelay =
                general.add(
                        new IntSetting.Builder()
                                .name("switch-delay")
                                .description("Delay after throwing pearl before switching back.")
                                .defaultValue(0)
                                .range(0, 20)
                                .sliderRange(0, 20)
                                .build());
    }

    @Override
    public void onActivate() {
        this.resetInput();
    }

    @Override
    public void onDeactivate() {
        this.switchBack();
        this.resetInput();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player != null
                && this.mc.world != null
                && this.mc.getNetworkHandler() != null
                && this.mc.getWindow() != null) {
            int key = this.activateKey.getValue();
            boolean down = this.isKeyDown(key);
            if (key != this.lastKey) {
                this.lastKey = key;
                this.wasKeyDown = down;
            } else {
                boolean pressed = down && !this.wasKeyDown;
                this.wasKeyDown = down;
                if (pressed
                        && this.mc.currentScreen == null
                        && this.state == KeyPearl.PearlState.IDLE) {
                    this.startThrow();
                }
            }

            if (this.mc.currentScreen == null) {
                if (this.state == KeyPearl.PearlState.WAITING_TO_THROW) {
                    if (this.throwTicksLeft > 0) {
                        this.throwTicksLeft--;
                    } else {
                        this.throwPearl();
                    }
                } else if (this.state == KeyPearl.PearlState.WAITING_TO_SWITCH_BACK) {
                    if (this.switchTicksLeft > 0) {
                        this.switchTicksLeft--;
                    } else {
                        this.switchBack();
                        this.resetState();
                    }
                }
            }
        } else {
            this.resetInput();
        }
    }

    private void startThrow() {
        int slot = this.findPearlSlot();
        if (slot >= 0) {
            this.previousSlot = this.mc.player.getInventory().getSelectedSlot();
            this.pearlSlot = slot;
            this.throwTicksLeft = this.delay.getValue();
            this.switchTicksLeft = 0;
            this.state = KeyPearl.PearlState.WAITING_TO_THROW;
            this.selectSlot(this.pearlSlot);
        }
    }

    private void throwPearl() {
        if (this.getPearlStack(this.pearlSlot) == null) {
            this.switchBack();
            this.resetState();
        } else if (this.mc.player.getInventory().getSelectedSlot() != this.pearlSlot) {
            this.switchBack();
            this.resetState();
        } else {
            // Use the held pearl through the vanilla use key; vanilla swings and sends the use.
            WorldUtils.interactItem(Hand.MAIN_HAND);

            if (this.switchBackEnabled.getValue()) {
                this.switchTicksLeft = Math.max(0, this.switchDelay.getValue() - 1);
                this.state = KeyPearl.PearlState.WAITING_TO_SWITCH_BACK;
            } else {
                this.resetState();
            }
        }
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            if (this.getPearlStack(i) != null) {
                return i;
            }
        }

        return -1;
    }

    private ItemStack getPearlStack(int slot) {
        if (slot >= 0 && slot <= 8) {
            ItemStack stack = this.mc.player.getInventory().getStack(slot);
            return stack.isOf(Items.ENDER_PEARL) ? stack : null;
        } else {
            return null;
        }
    }

    private void switchBack() {
        if (this.previousSlot >= 0 && this.previousSlot <= 8 && this.mc.player != null) {
            this.selectSlot(this.previousSlot);
        }
    }

    private void selectSlot(int slot) {
        InventoryUtils.selectHotbarSlot(slot);
    }

    private boolean isKeyDown(int key) {
        if (key >= 1000 && key <= 1007) {
            int button = key - 1000;
            return GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), button) == 1;
        } else {
            return key >= 0 && key <= 348 && InputUtil.isKeyPressed(this.mc.getWindow(), key);
        }
    }

    private void resetInput() {
        this.lastKey = -2147483648;
        this.wasKeyDown = false;
        this.resetState();
    }

    private void resetState() {
        this.previousSlot = -1;
        this.pearlSlot = -1;
        this.throwTicksLeft = 0;
        this.switchTicksLeft = 0;
        this.state = KeyPearl.PearlState.IDLE;
    }

    private static enum PearlState {
        IDLE,
        WAITING_TO_THROW,
        WAITING_TO_SWITCH_BACK;
    }
}
