package odinclient.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map.Entry;

public final class ConfigMigrations {
    private ConfigMigrations() {}

    public static JsonObject stripChatMessages(JsonObject config) {
        JsonObject copy = config.deepCopy();
        JsonObject modules = copy.getAsJsonObject("modules");
        if (modules != null
                && modules.has("gamble-rigger")
                && modules.get("gamble-rigger").isJsonObject()) {
            JsonObject settings =
                    modules.getAsJsonObject("gamble-rigger").getAsJsonObject("settings");
            if (settings != null) {
                settings.remove("chat-messages");
            }
        }

        return copy;
    }

    public static void migrateSpawnerFlagger(JsonObject config) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null
                && modules.has("spawner-flagger")
                && modules.get("spawner-flagger").isJsonObject()) {
            JsonObject module = modules.getAsJsonObject("spawner-flagger");
            JsonObject settings = module.getAsJsonObject("settings");
            if (settings != null
                    && settings.has("allowed-spawners")
                    && (!settings.has("chunk-range") || !settings.has("scan-interval"))) {
                JsonObject migrated = new JsonObject();

                for (Entry<String, JsonElement> entry : settings.entrySet()) {
                    if ("allowed-spawners".equals(entry.getKey())) {
                        for (String key : List.of("chunk-range", "scan-interval")) {
                            if (settings.has(key)) {
                                migrated.add(key, settings.get(key).deepCopy());
                            } else {
                                migrated.addProperty(key, "chunk-range".equals(key) ? "8" : "20");
                            }
                        }
                    }

                    if (!"chunk-range".equals(entry.getKey())
                            && !"scan-interval".equals(entry.getKey())) {
                        migrated.add(entry.getKey(), entry.getValue().deepCopy());
                    }
                }

                module.add("settings", migrated);
            }
        }
    }

    public static void addHoleEnclosure(JsonObject config) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null
                && modules.has("chunkfinder")
                && modules.get("chunkfinder").isJsonObject()) {
            JsonObject module = modules.getAsJsonObject("chunkfinder");
            JsonObject settings = module.getAsJsonObject("settings");
            if (settings != null && !settings.has("hole-enclosure") && settings.has("fish")) {
                JsonObject migrated = new JsonObject();

                for (Entry<String, JsonElement> entry : settings.entrySet()) {
                    if ("fish".equals(entry.getKey())) {
                        migrated.addProperty("hole-enclosure", "3");
                    }

                    migrated.add(entry.getKey(), entry.getValue().deepCopy());
                }

                module.add("settings", migrated);
            }
        }
    }

    public static void addArmadillos(JsonObject config) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null
                && modules.has("chunkfinder")
                && modules.get("chunkfinder").isJsonObject()) {
            JsonObject module = modules.getAsJsonObject("chunkfinder");
            JsonObject settings = module.getAsJsonObject("settings");
            if (settings != null && !settings.has("armadillos") && settings.has("mined-geodes")) {
                JsonObject migrated = new JsonObject();

                for (Entry<String, JsonElement> entry : settings.entrySet()) {
                    if ("mined-geodes".equals(entry.getKey())) {
                        migrated.addProperty("armadillos", "true");
                    }

                    migrated.add(entry.getKey(), entry.getValue().deepCopy());
                }

                module.add("settings", migrated);
            }
        }
    }

    public static void migrateSkyEffects(JsonObject config) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null && modules.has("sky-arts") && modules.get("sky-arts").isJsonObject()) {
            JsonObject module = modules.getAsJsonObject("sky-arts");
            JsonObject settings = module.getAsJsonObject("settings");
            if (settings != null && settings.has("no-person")) {
                List<String> effectKeys =
                        List.of(
                                "sky-effects",
                                "sky-effect-shooting-stars",
                                "sky-effect-red-lanterns",
                                "sky-effect-snow",
                                "sky-effect-rain");
                if (!effectKeys.stream().allMatch(settings::has)) {
                    JsonObject migrated = new JsonObject();

                    for (Entry<String, JsonElement> entry : settings.entrySet()) {
                        if (!effectKeys.contains(entry.getKey())) {
                            migrated.add(entry.getKey(), entry.getValue().deepCopy());
                        }

                        if ("no-person".equals(entry.getKey())) {
                            for (String key : effectKeys) {
                                if (settings.has(key)) {
                                    migrated.add(key, settings.get(key).deepCopy());
                                } else {
                                    migrated.addProperty(
                                            key, "sky-effects".equals(key) ? "OPEN MENU" : "false");
                                }
                            }
                        }
                    }

                    module.add("settings", migrated);
                }
            }
        }
    }

    public static void addNetheriteFinder(JsonObject config) {
        JsonObject modules = config.getAsJsonObject("modules");
        String name = "netherite-finder";
        if (modules != null && !modules.has(name)) {
            JsonObject module = new JsonObject();
            JsonObject settings = new JsonObject();
            module.addProperty("active", false);
            module.add("settings", settings);
            String[] settingNames = {
                "keybind",
                "scan-distance-chunks",
                "chunks-per-tick",
                "shape-mode",
                "side-color",
                "line-color"
            };

            for (String settingName : settingNames) {
                settings.addProperty(settingName, "");
            }

            JsonObject sorted = new JsonObject();
            boolean inserted = false;

            for (Entry<String, JsonElement> entry : modules.entrySet()) {
                if (!inserted && entry.getKey().compareTo(name) > 0) {
                    sorted.add(name, module);
                    inserted = true;
                }

                sorted.add(entry.getKey(), entry.getValue().deepCopy());
            }

            if (!inserted) {
                sorted.add(name, module);
            }

            config.add("modules", sorted);
        }
    }

    public static void migratePlayerDebug(JsonObject config) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null
                && modules.has("player-debug-v2")
                && modules.get("player-debug-v2").isJsonObject()) {
            JsonObject module = modules.getAsJsonObject("player-debug-v2");
            JsonObject settings = module.getAsJsonObject("settings");
            if (settings != null && settings.has("side-color")) {
                if (!settings.has("clear-chunks-on-toggle")
                        || !settings.has("render-flagged-chunk")) {
                    JsonObject migrated = new JsonObject();

                    for (Entry<String, JsonElement> entry : settings.entrySet()) {
                        if ("side-color".equals(entry.getKey())) {
                            String[] newKeys = {"clear-chunks-on-toggle", "render-flagged-chunk"};

                            for (String key : newKeys) {
                                if (settings.has(key)) {
                                    migrated.add(key, settings.get(key).deepCopy());
                                } else {
                                    migrated.addProperty(key, "true");
                                }
                            }
                        }

                        if (!"clear-chunks-on-toggle".equals(entry.getKey())
                                && !"render-flagged-chunk".equals(entry.getKey())) {
                            migrated.add(entry.getKey(), entry.getValue().deepCopy());
                        }
                    }

                    module.add("settings", migrated);
                }
            }
        }
    }

    public static void addLogoutOnRenderFlag(JsonObject config) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null
                && modules.has("auto-firework")
                && modules.get("auto-firework").isJsonObject()) {
            JsonObject module = modules.getAsJsonObject("auto-firework");
            JsonObject settings = module.getAsJsonObject("settings");
            if (settings != null
                    && !settings.has("logout-on-render-flag")
                    && settings.has("logout-on-totem-pop")) {
                JsonObject migrated = new JsonObject();

                for (Entry<String, JsonElement> entry : settings.entrySet()) {
                    if ("logout-on-totem-pop".equals(entry.getKey())) {
                        migrated.addProperty("logout-on-render-flag", "false");
                    }

                    migrated.add(entry.getKey(), entry.getValue().deepCopy());
                }

                module.add("settings", migrated);
            }
        }
    }
}
