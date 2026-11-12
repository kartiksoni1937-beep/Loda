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
import odinclient.setting.type.KeybindSetting;
import odinclient.setting.type.StringSetting;
import odinclient.util.ChatUtil;

import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashSet;

@Environment(EnvType.CLIENT)
public final class ChatMacros extends Module {
    public static final int MACRO_COUNT = 10;
    private final Setting<String>[] textSettings = new Setting[10];
    private final Setting<Integer>[] keySettings = new Setting[10];
    private final int[] lastKeys = new int[10];
    private final boolean[] wasPressed = new boolean[10];
    private boolean updatingSettings;
    private int lastToggleKey = -2147483648;

    public ChatMacros(Category category) {
        super(
                category,
                "chat-macros",
                "Runs saved chat messages or commands once per activation-key press.");
        this.title = "Chat Macros";
        Arrays.fill(this.lastKeys, -2147483648);
        SettingGroup general = this.settings.getGeneral();
        general.add(
                new StringSetting.Builder()
                        .name("macro-manager")
                        .description("Open the custom screen to add, edit and remove chat macros.")
                        .defaultValue("OPEN MANAGER")
                        .build());

        for (int i = 0; i < 10; i++) {
            int number = i + 1;
            int index = i;
            this.textSettings[i] =
                    general.add(
                            new StringSetting.Builder()
                                    .name("macro-" + number + "-text")
                                    .description(
                                            "Chat text or /command sent by Macro "
                                                    + number
                                                    + ". Leave blank to disable it.")
                                    .hint("/rtp eu")
                                    .defaultValue("")
                                    .visibleWhen(() -> false)
                                    .build());
            this.keySettings[i] =
                    general.add(
                            new KeybindSetting.Builder()
                                    .name("macro-" + number + "-activation-key")
                                    .description(
                                            "Sends Macro "
                                                    + number
                                                    + " once when this key is pressed.")
                                    .defaultValue(-1)
                                    .visibleWhen(() -> false)
                                    .onChange(key -> this.onActivationKeyChanged(index, key))
                                    .build());
        }
    }

    public String getMacroText(int index) {
        return isValidSlot(index) ? this.textSettings[index].getValue() : "";
    }

    public int getMacroKey(int index) {
        return isValidSlot(index) ? this.keySettings[index].getValue() : -1;
    }

    public boolean isMacroActive(int index) {
        if (!isValidSlot(index)) {
            return false;
        } else {
            String text = this.textSettings[index].getValue();
            return (text != null && !text.isBlank()) || this.keySettings[index].getValue() >= 0;
        }
    }

    public String applyMacros(String[] texts, int[] keys) {
        if (texts != null && keys != null && texts.length == 10 && keys.length == 10) {
            HashSet<Integer> usedKeys = new HashSet<>();

            for (int i = 0; i < 10; i++) {
                String text = texts[i] == null ? "" : texts[i].trim();
                int key = keys[i];
                if (!text.isEmpty() || key >= 0) {
                    if (text.isEmpty()) {
                        return "Macro " + (i + 1) + " needs a message or command.";
                    }

                    if (key < 0) {
                        return "Macro " + (i + 1) + " needs an activation key.";
                    }

                    if (text.length() > 256) {
                        return "Macro " + (i + 1) + " is too long.";
                    }

                    if (key == this.keybind.getValue()) {
                        return "A macro cannot use the Chat Macros toggle key.";
                    }

                    if (!usedKeys.add(key)) {
                        return "Every macro needs a unique activation key.";
                    }
                }
            }

            this.updatingSettings = true;

            try {
                for (int j = 0; j < 10; j++) {
                    String trimmed = texts[j] == null ? "" : texts[j].trim();
                    int keyCode = keys[j];
                    boolean active = !trimmed.isEmpty() || keyCode >= 0;
                    this.textSettings[j].setValue(active ? trimmed : "");
                    this.keySettings[j].setValue(active ? keyCode : -1);
                }
            } catch (Throwable t) {
                this.updatingSettings = false;
                throw t;
            }

            this.updatingSettings = false;
            this.resetKeyState();
            return null;
        } else {
            return "Invalid macro list.";
        }
    }

    @Override
    public void onActivate() {
        this.resetKeyState();
        this.sanitizeKeybinds(true);
    }

    @Override
    public void onDeactivate() {
        this.resetKeyState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player != null && this.mc.world != null && this.mc.getWindow() != null) {
            if (this.keybind.getValue() != this.lastToggleKey) {
                this.lastToggleKey = this.keybind.getValue();
                this.sanitizeKeybinds(true);
            }

            boolean noScreen = this.mc.currentScreen == null;

            for (int i = 0; i < 10; i++) {
                int key = this.keySettings[i].getValue();
                boolean pressed = this.isInputPressed(key);
                if (key != this.lastKeys[i]) {
                    this.lastKeys[i] = key;
                    this.wasPressed[i] = pressed;
                } else {
                    boolean justPressed = pressed && !this.wasPressed[i];

                    this.wasPressed[i] = pressed;
                    if (justPressed && noScreen) {
                        String text = this.textSettings[i].getValue();
                        if (text != null) {
                            String message = text.trim();
                            if (!message.isEmpty()) {
                                ChatUtil.sendChat(message);
                            }
                        }
                    }
                }
            }
        } else {
            this.resetKeyState();
        }
    }

    private void onActivationKeyChanged(int index, Integer key) {
        if (!this.updatingSettings && key != null && key >= 0) {
            int duplicate = this.findDuplicateKey(index, key);
            if (key == this.keybind.getValue() || duplicate >= 0) {
                this.updatingSettings = true;

                try {
                    this.keySettings[index].setValue(-1);
                } catch (Throwable t) {
                    this.updatingSettings = false;
                    throw t;
                }

                this.updatingSettings = false;
                if (duplicate >= 0) {
                    this.warning(
                            "Macro %d cannot use the same activation key as Macro %d.",
                            index + 1, duplicate + 1);
                } else {
                    this.warning(
                            "Macro %d cannot use the Chat Macros module toggle key.", index + 1);
                }
            }

            this.lastKeys[index] = -2147483648;
            this.wasPressed[index] = false;
        }
    }

    private void sanitizeKeybinds(boolean notify) {
        if (!this.updatingSettings) {
            this.updatingSettings = true;

            try {
                for (int i = 0; i < 10; i++) {
                    int key = this.keySettings[i].getValue();
                    if (key >= 0) {
                        int duplicate = this.findDuplicateKey(i, key);
                        if (key == this.keybind.getValue() || duplicate >= 0) {
                            this.keySettings[i].setValue(-1);
                            if (notify) {
                                if (duplicate >= 0) {
                                    this.warning(
                                            "Macro %d duplicated Macro %d; its activation key was"
                                                + " cleared.",
                                            i + 1, duplicate + 1);
                                } else {
                                    this.warning(
                                            "Macro %d used the module toggle key; its activation"
                                                + " key was cleared.",
                                            i + 1);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                this.updatingSettings = false;
                throw t;
            }

            this.updatingSettings = false;
            this.resetKeyState();
        }
    }

    private int findDuplicateKey(int index, int key) {
        for (int i = 0; i < 10; i++) {
            if (i != index
                    && this.keySettings[i] != null
                    && this.keySettings[i].getValue() == key) {
                return i;
            }
        }

        return -1;
    }

    private boolean isInputPressed(int key) {
        if (key >= 1000 && key <= 1007) {
            int button = key - 1000;
            return GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), button) == 1;
        } else {
            return key >= 0 && key <= 348 && InputUtil.isKeyPressed(this.mc.getWindow(), key);
        }
    }

    private void resetKeyState() {
        Arrays.fill(this.lastKeys, -2147483648);
        Arrays.fill(this.wasPressed, false);
        this.lastToggleKey = this.keybind.getValue();
    }

    private static boolean isValidSlot(int index) {
        return index >= 0 && index < 10;
    }
}
