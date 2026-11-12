package odinclient.setting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import odinclient.module.Module;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.IntSetting;
import odinclient.ui.screen.HudPositionEditorScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class HudPositionSettings {
    private static final Map<Module, HudPositionSettings.PositionEditEntry> ENTRIES =
            new WeakHashMap<>();

    private HudPositionSettings() {}

    public static List<Setting<?>> injectPositionEditor(List<Setting<?>> settings, Module module) {
        if (settings != null && module != null) {
            IntSetting xSetting = null;
            IntSetting ySetting = null;

            for (Setting<?> setting : settings) {
                if (setting instanceof IntSetting intSetting) {
                    if ("x-position".equals(setting.name)) {
                        xSetting = intSetting;
                    }

                    if ("y-position".equals(setting.name)) {
                        ySetting = intSetting;
                    }
                }
            }

            if (xSetting != null && ySetting != null) {
                HudPositionSettings.PositionEditEntry entry;
                synchronized (ENTRIES) {
                    entry = ENTRIES.get(module);
                    if (entry == null || entry.xSetting != xSetting || entry.ySetting != ySetting) {
                        entry =
                                new HudPositionSettings.PositionEditEntry(
                                        module, xSetting, ySetting);
                        ENTRIES.put(module, entry);
                    }
                }

                List<Setting<?>> result = new ArrayList<>(Math.max(1, settings.size() - 1));

                for (Setting<?> candidate : settings) {
                    if (candidate == xSetting) {
                        result.add(entry.editSetting);
                    } else if (candidate != ySetting
                            && (!"spotify-hud".equals(module.name)
                                    || !"margin".equals(candidate.name))) {
                        result.add(candidate);
                    }
                }

                return result;
            } else {
                return settings;
            }
        } else {
            return settings;
        }
    }

    private static void openPositionEditor(
            Module module, IntSetting xSetting, IntSetting ySetting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            Screen parent = client.currentScreen;
            client.setScreen(new HudPositionEditorScreen(parent, module, xSetting, ySetting));
        }
    }

    private static enum EditPositionAction {
        OPEN;
    }

    private static final class PositionEditEntry {
        private final IntSetting xSetting;
        private final IntSetting ySetting;
        private final Setting<HudPositionSettings.EditPositionAction> editSetting;

        private PositionEditEntry(Module module, IntSetting xSetting, IntSetting ySetting) {
            this.xSetting = xSetting;
            this.ySetting = ySetting;
            Consumer<HudPositionSettings.EditPositionAction> onChange =
                    action -> HudPositionSettings.openPositionEditor(module, xSetting, ySetting);
            this.editSetting =
                    new EnumSetting.Builder<HudPositionSettings.EditPositionAction>()
                            .name("edit-position")
                            .description("Open the visual HUD position editor.")
                            .defaultValue(HudPositionSettings.EditPositionAction.OPEN)
                            .onChange(onChange)
                            .build();
        }
    }
}
