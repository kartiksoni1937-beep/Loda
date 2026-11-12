package odinclient.module;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import odinclient.manager.ConfigStore;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroupList;
import odinclient.setting.type.KeybindSetting;
import odinclient.ui.hud.ModuleNotifications;

@Environment(EnvType.CLIENT)
public class Module {
    public final Category category;
    public String name;
    public String title;
    public final String description;
    public final SettingGroupList settings = new SettingGroupList();
    public final Setting<Integer> keybind;
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean active;

    public Module(Category category, String name, String description) {
        this.category = category;
        this.name = name;
        this.title = title(name);
        this.description = description;
        this.keybind =
                this.settings
                        .getGeneral()
                        .add(
                                new KeybindSetting.Builder()
                                        .name("keybind")
                                        .description(
                                                "Keyboard key used to toggle this module. Click and"
                                                    + " press a key; Escape clears it.")
                                        .build());
    }

    public final boolean isActive() {
        return this.active;
    }

    public final void rename(String newName) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
            this.title = title(newName);
        }
    }

    public void toggle() {
        this.setActive(!this.active);
    }

    public void setActive(boolean active) {
        ModuleNotifications.beginToggle(this, active);
        if (this.active == active) {
            ModuleNotifications.endToggle(this);
        } else {
            this.active = active;
            ConfigStore.markDirty();
            if (this.active) {
                ModuleContext.EVENT_BUS.register(this);

                try {
                    this.onActivate();
                } catch (Throwable error) {
                    this.active = false;
                    ModuleContext.EVENT_BUS.unregister(this);
                    ModuleContext.LOGGER.error("Could not activate module {}", this.name, error);
                }
            } else {
                try {
                    this.onDeactivate();
                } catch (Throwable failure) {
                    ModuleContext.EVENT_BUS.unregister(this);
                    ModuleNotifications.endToggle(this);
                    throw failure;
                }

                ModuleContext.EVENT_BUS.unregister(this);
            }

            ModuleNotifications.endToggle(this);
        }
    }

    public void onActivate() {}

    public void onDeactivate() {}

    public void info(String message, Object... args) {
        this.sendMessage("\u00a79[OdinClient] \u00a7f" + format(message, args));
    }

    public void warning(String message, Object... args) {
        this.sendMessage("\u00a76[OdinClient] \u00a7f" + format(message, args));
    }

    public void error(String message, Object... args) {
        this.sendMessage("\u00a7c[OdinClient] \u00a7f" + format(message, args));
    }

    private void sendMessage(String message) {
        if (this.mc.player != null) {
            this.mc.player.sendMessage(Text.literal(message), false);
        } else {
            ModuleContext.LOGGER.info(message.replaceAll("\u00a7.", ""));
        }
    }

    private static String format(String message, Object[] args) {
        try {
            return args != null && args.length != 0 ? String.format(message, args) : message;
        } catch (RuntimeException e) {
            return message;
        }
    }

    private static String title(String name) {
        StringBuilder builder = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c != '-' && c != '_') {
                builder.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else {
                builder.append(' ');
                capitalizeNext = true;
            }
        }

        return builder.toString();
    }
}
