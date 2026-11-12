package odinclient.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import odinclient.module.Module;
import odinclient.module.ModuleContext;
import odinclient.render.Color;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BlockColorMapSetting;
import odinclient.setting.type.EntityTypeListSetting;
import odinclient.setting.type.MapSetting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path configPath =
            FabricLoader.getInstance().getConfigDir().resolve("odinclient.json");
    private static final Path legacyConfigPath =
            FabricLoader.getInstance().getConfigDir().resolve("odinclient.json");
    private static boolean loading;
    private static boolean dirty;
    private static int tickCounter;

    private ConfigStore() {}

    public static void load() {
        Path path = Files.isRegularFile(configPath) ? configPath : legacyConfigPath;
        if (!Files.isRegularFile(path)) {
            return;
        }

        loading = true;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonObject modules = root == null ? null : root.getAsJsonObject("modules");
            if (modules != null) {
                for (Module module : ModuleManager.get().getAll()) {
                    JsonObject moduleJson = modules.getAsJsonObject(module.name);
                    if (moduleJson == null) {
                        moduleJson = modules.getAsJsonObject(getLegacyModuleName(module.name));
                    }

                    if (moduleJson != null) {
                        JsonObject settings = moduleJson.getAsJsonObject("settings");
                        if (settings != null) {
                            readModuleSettings(module, settings);
                        }

                        if (moduleJson.has("active") && moduleJson.get("active").getAsBoolean()) {
                            module.setActive(true);
                        }
                    }
                }
            }
        } catch (Exception e) {
            ModuleContext.LOGGER.error("Could not read OdinClient config {}", path, e);
        } finally {
            loading = false;
            dirty = false;
        }
    }

    public static void markDirty() {
        if (!loading) {
            dirty = true;
        }
    }

    public static void onTick() {
        if (dirty && ++tickCounter >= 20) {
            save();
        }
    }

    public static void save() {
        tickCounter = 0;
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        root.add("modules", modules);

        for (Module module : ModuleManager.get().getAll()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("active", module.isActive());
            JsonObject settings = new JsonObject();
            moduleJson.add("settings", settings);
            writeModuleSettings(module, settings);
            modules.add(module.name, moduleJson);
        }

        try {
            Files.createDirectories(configPath.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson((JsonElement) root, writer);
            }

            dirty = false;
        } catch (IOException e) {
            ModuleContext.LOGGER.error("Could not save OdinClient config {}", configPath, e);
        }
    }

    private static void readModuleSettings(Module module, JsonObject json) {
        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                JsonElement element = json.get(setting.name);
                if (element != null && element.isJsonPrimitive()) {
                    setting.setValueFromString(element.getAsString());
                } else if (module.name.equals("block-esp") && setting.name.equals("alpha")) {
                    migrateBlockEspAlpha(json, setting);
                } else if (setting instanceof MapSetting mapSetting) {
                    migrateProtectedPlayers(json, mapSetting);
                }
            }
        }
    }

    private static void writeModuleSettings(Module module, JsonObject json) {
        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                Object value = setting.getValue();
                if (setting instanceof BlockColorMapSetting blockColors) {
                    json.addProperty(setting.name, blockColors.serialize());
                } else if (setting instanceof MapSetting mapSetting) {
                    json.addProperty(setting.name, mapSetting.serialize());
                } else if (setting instanceof EntityTypeListSetting entityTypes) {
                    json.addProperty(setting.name, entityTypes.serialize());
                } else if (!(value instanceof List)) {
                    if (value instanceof Color color) {
                        json.addProperty(
                                setting.name,
                                String.format(
                                        "%02x%02x%02x%02x", color.r, color.g, color.b, color.a));
                    } else {
                        json.addProperty(setting.name, String.valueOf(value));
                    }
                }
            }
        }
    }

    private static void migrateProtectedPlayers(JsonObject json, MapSetting setting) {
        JsonElement enabled = json.get("protect-others");
        JsonElement player = json.get("player-to-protect");
        JsonElement alias = json.get("protected-as");
        if (enabled != null
                && enabled.isJsonPrimitive()
                && enabled.getAsBoolean()
                && player != null
                && player.isJsonPrimitive()
                && alias != null
                && alias.isJsonPrimitive()) {
            setting.setValue(Map.of(player.getAsString(), alias.getAsString()));
        }
    }

    private static void migrateBlockEspAlpha(JsonObject json, Setting<?> setting) {
        JsonElement fill = json.get("fill");
        if (fill != null && fill.isJsonPrimitive()) {
            setting.setValueFromString(fill.getAsBoolean() ? "38" : "0");
        }
    }

    private static String getLegacyModuleName(String name) {
        return switch (name) {
            case "freecam" -> "odinclient-freecam";
            case "spawner-protect" -> "odinclient-spawner-protect";
            case "block-notifier" -> "odinclient-block-notifier";
            default -> name;
        };
    }
}
