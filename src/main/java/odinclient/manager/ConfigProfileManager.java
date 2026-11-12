package odinclient.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import odinclient.module.Module;
import odinclient.module.hud.ThemeModule;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class ConfigProfileManager {
    public static final int MAX_PROFILES = 3;
    private static final int MAX_CODE_LENGTH = 1000000;
    private static final int MAX_DECODED_SIZE = 4000000;
    private static final int MAX_SHARE_LENGTH = 1999;
    private static final String LEGACY_CODE_PREFIX = "ZY1-";
    private static final String CODE_PREFIX = "OC2-";
    private static final String BUNDLED_CONFIG_PATH = "/assets/odinclient/configs/odin_config.oc2";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<ConfigProfileManager.BundledConfig> BUNDLED_CONFIGS =
            List.of(
                    new ConfigProfileManager.BundledConfig(
                            "Odin Config",
                            "Built-in OdinClient setup, available offline.",
                            loadBundledConfig()));
    private static final List<ConfigProfileManager.Profile> PROFILES = new ArrayList<>();
    private static boolean loaded;
    private static String activeProfileName = "";

    private ConfigProfileManager() {}

    public static synchronized List<ConfigProfileManager.Profile> profiles() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(PROFILES));
    }

    public static List<ConfigProfileManager.BundledConfig> getBundledConfigs() {
        return BUNDLED_CONFIGS;
    }

    private static String loadBundledConfig() {
        try (InputStream in = ConfigProfileManager.class.getResourceAsStream(BUNDLED_CONFIG_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Bundled Odin Config is missing");
            }

            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!text.startsWith(CODE_PREFIX)) {
                throw new IllegalStateException("Bundled Odin Config is invalid");
            }

            return text;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load bundled Odin Config", e);
        }
    }

    public static synchronized String getActiveProfileName() {
        ensureLoaded();
        return activeProfileName;
    }

    public static synchronized ConfigProfileManager.Result saveCurrent(String rawName) {
        ensureLoaded();
        if (PROFILES.size() >= 3) {
            return ConfigProfileManager.Result.failure("Maximum of 3 configs reached");
        } else {
            String name = makeUniqueName(sanitizeName(rawName));
            if (name.isBlank()) {
                return ConfigProfileManager.Result.failure("Enter a config name");
            } else {
                try {
                    String configJson = readCurrentConfigJson();
                    PROFILES.add(
                            new ConfigProfileManager.Profile(
                                    name, configJson, System.currentTimeMillis()));
                    activeProfileName = name;
                    saveProfiles();
                    return ConfigProfileManager.Result.success("Saved " + name);
                } catch (Exception e) {
                    return ConfigProfileManager.Result.failure(
                            "Could not save config: " + describeError(e));
                }
            }
        }
    }

    public static synchronized ConfigProfileManager.Result updateProfile(int index) {
        ensureLoaded();
        if (!isValidIndex(index)) {
            return ConfigProfileManager.Result.failure("Select a config first");
        } else {
            try {
                ConfigProfileManager.Profile profile = PROFILES.get(index);
                PROFILES.set(
                        index,
                        new ConfigProfileManager.Profile(
                                profile.name, readCurrentConfigJson(), System.currentTimeMillis()));
                activeProfileName = profile.name;
                saveProfiles();
                return ConfigProfileManager.Result.success("Updated " + profile.name);
            } catch (Exception e) {
                return ConfigProfileManager.Result.failure(
                        "Could not update config: " + describeError(e));
            }
        }
    }

    public static synchronized ConfigProfileManager.Result loadProfile(int index) {
        ensureLoaded();
        if (!isValidIndex(index)) {
            return ConfigProfileManager.Result.failure("Config no longer exists");
        } else {
            ConfigProfileManager.Profile profile = PROFILES.get(index);

            try {
                applyConfigJson(profile.configJson);
                activeProfileName = profile.name;
                saveProfiles();
                return ConfigProfileManager.Result.success("Loaded " + profile.name);
            } catch (Exception e) {
                return ConfigProfileManager.Result.failure(
                        "Could not load config: " + describeError(e));
            }
        }
    }

    public static synchronized ConfigProfileManager.Result deleteProfile(int index) {
        ensureLoaded();
        if (!isValidIndex(index)) {
            return ConfigProfileManager.Result.failure("Config no longer exists");
        } else {
            String name = PROFILES.remove(index).name;
            if (activeProfileName.equalsIgnoreCase(name)) {
                activeProfileName = "";
            }

            try {
                saveProfiles();
                return ConfigProfileManager.Result.success("Deleted " + name);
            } catch (Exception e) {
                return ConfigProfileManager.Result.failure(
                        "Deleted, but storage failed: " + describeError(e));
            }
        }
    }

    public static synchronized ConfigProfileManager.Result resetToDefaults() {
        ensureLoaded();

        try {
            Field defaultField = Setting.class.getDeclaredField("defaultValue");
            defaultField.setAccessible(true);

            for (Module module : ModuleManager.get().getAll()) {
                if (module.isActive()) {
                    module.setActive(false);
                }

                for (SettingGroup group : module.settings) {
                    for (Setting setting : group) {
                        Object value = defaultField.get(setting);
                        if (value instanceof List list) {
                            value = new ArrayList<>(list);
                        } else if (value instanceof Map map) {
                            value = new HashMap<>(map);
                        }

                        setting.setValue(value);
                    }
                }
            }

            activeProfileName = "";
            ConfigStore.save();
            saveProfiles();
            return ConfigProfileManager.Result.success(
                    "All modules disabled and defaults restored");
        } catch (Exception e) {
            return ConfigProfileManager.Result.failure("Reset failed: " + describeError(e));
        }
    }

    public static synchronized ConfigProfileManager.Result exportProfile(int index) {
        ensureLoaded();
        if (!isValidIndex(index)) {
            return ConfigProfileManager.Result.failure("Select a config first");
        } else {
            try {
                ConfigProfileManager.Profile profile = PROFILES.get(index);
                JsonObject config = JsonParser.parseString(profile.configJson).getAsJsonObject();
                validateConfig(config);
                byte[] encoded = encodeConfig(profile.name, config);
                String code =
                        CODE_PREFIX
                                + Base64.getUrlEncoder()
                                        .withoutPadding()
                                        .encodeToString(compress(encoded));
                return code.length() > 1999
                        ? ConfigProfileManager.Result.failure(
                                "Config is too large for one Discord message ("
                                        + code.length()
                                        + " characters)")
                        : ConfigProfileManager.Result.successWithCode(
                                "Config code copied (" + code.length() + " characters)", code);
            } catch (Exception e) {
                return ConfigProfileManager.Result.failure(
                        "Could not export config: " + describeError(e));
            }
        }
    }

    public static synchronized ConfigProfileManager.Result importCode(String input) {
        ensureLoaded();
        if (PROFILES.size() >= 3) {
            return ConfigProfileManager.Result.failure("Delete a config first (maximum is 3)");
        } else if (input == null) {
            return ConfigProfileManager.Result.failure("Clipboard is empty");
        } else {
            String code = input.trim().replace("\r", "").replace("\n", "");
            if (code.length() > 1000000) {
                return ConfigProfileManager.Result.failure("Config code is too large");
            } else {
                try {
                    if (code.startsWith(CODE_PREFIX)) {
                        return importOdinCode(code);
                    } else {
                        return code.startsWith(LEGACY_CODE_PREFIX)
                                ? importLegacyCode(code)
                                : ConfigProfileManager.Result.failure(
                                        "Clipboard has no OdinClient config code");
                    }
                } catch (IllegalArgumentException e) {
                    return ConfigProfileManager.Result.failure("Invalid config code");
                } catch (Exception ex) {
                    return ConfigProfileManager.Result.failure(
                            "Import failed: " + describeError(ex));
                }
            }
        }
    }

    private static ConfigProfileManager.Result importOdinCode(String code) throws Exception {
        byte[] encoded =
                decompress(Base64.getUrlDecoder().decode(code.substring(CODE_PREFIX.length())));
        JsonObject current = JsonParser.parseString(readCurrentConfigJson()).getAsJsonObject();
        validateConfig(current);
        ConfigProfileManager.DecodedConfig decoded = decodeConfig(encoded, current);
        String name = sanitizeImportedName(decoded.name);
        String configJson = GSON.toJson(decoded.config);
        applyConfigJson(configJson);
        PROFILES.add(
                new ConfigProfileManager.Profile(name, configJson, System.currentTimeMillis()));
        activeProfileName = name;
        saveProfiles();
        return ConfigProfileManager.Result.success("Imported and loaded " + name);
    }

    private static ConfigProfileManager.Result importLegacyCode(String code) throws Exception {
        byte[] compressed =
                Base64.getUrlDecoder().decode(code.substring(LEGACY_CODE_PREFIX.length()));
        JsonObject root =
                JsonParser.parseString(new String(gunzip(compressed), StandardCharsets.UTF_8))
                        .getAsJsonObject();
        if (root.has("version") && root.get("version").getAsInt() == 1) {
            JsonObject config = root.getAsJsonObject("config");
            validateConfig(config);
            String rawName = root.has("name") ? root.get("name").getAsString() : "Imported Config";
            String name = sanitizeImportedName(rawName);
            String configJson = GSON.toJson(config);
            applyConfigJson(configJson);
            PROFILES.add(
                    new ConfigProfileManager.Profile(name, configJson, System.currentTimeMillis()));
            activeProfileName = name;
            saveProfiles();
            return ConfigProfileManager.Result.success("Imported and loaded " + name);
        } else {
            return ConfigProfileManager.Result.failure("Unsupported config version");
        }
    }

    private static String sanitizeImportedName(String rawName) {
        String name = makeUniqueName(sanitizeName(rawName));
        return name.isBlank() ? makeUniqueName("Imported Config") : name;
    }

    private static byte[] encodeConfig(String name, JsonObject config) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeString(out, sanitizeName(name));
            out.writeInt(computeLayoutHash(config));
            JsonObject modules = config.getAsJsonObject("modules");
            writeVarInt(out, modules.size());
            for (Entry<String, JsonElement> moduleEntry : modules.entrySet()) {
                JsonObject module = moduleEntry.getValue().getAsJsonObject();
                out.writeByte(module.has("active") && module.get("active").getAsBoolean() ? 1 : 0);
                JsonObject settings = module.getAsJsonObject("settings");
                if (settings != null) {
                    for (Entry<String, JsonElement> settingEntry : settings.entrySet()) {
                        JsonElement value = settingEntry.getValue();
                        writeString(
                                out,
                                value != null && !value.isJsonNull() ? value.getAsString() : "");
                    }
                }
            }
        }

        return bytes.toByteArray();
    }

    private static ConfigProfileManager.DecodedConfig decodeConfig(byte[] data, JsonObject current)
            throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String name = readString(in);
            JsonObject layout = findCompatibleLayout(in.readInt(), current);
            if (layout == null) {
                throw new IOException("Config is from a different OdinClient version");
            }

            JsonObject modules = layout.getAsJsonObject("modules");
            if (readVarInt(in) != modules.size()) {
                throw new IOException(
                        "Config module layout does not match this OdinClient version");
            }

            for (Entry<String, JsonElement> moduleEntry : modules.entrySet()) {
                JsonObject module = moduleEntry.getValue().getAsJsonObject();
                int state = in.readUnsignedByte();
                if (state > 1) {
                    throw new IOException("Invalid module state");
                }

                module.addProperty("active", state == 1);
                JsonObject settings = module.getAsJsonObject("settings");
                if (settings != null) {
                    List<String> keys = new ArrayList<>();
                    for (Entry<String, JsonElement> settingEntry : settings.entrySet()) {
                        keys.add(settingEntry.getKey());
                    }

                    for (String key : keys) {
                        settings.addProperty(key, readString(in));
                    }
                }
            }

            if (in.read() != -1) {
                throw new IOException("Config code has trailing data");
            }

            return new ConfigProfileManager.DecodedConfig(
                    name, mergeIntoCurrentLayout(layout, current));
        }
    }

    private static JsonObject findCompatibleLayout(int hash, JsonObject config) {
        if (computeLayoutHash(config) == hash) {
            return config;
        } else {
            JsonObject withoutSpotify = config.deepCopy();
            removeModule(withoutSpotify, "spotify-hud");
            if (computeLayoutHash(withoutSpotify) == hash) {
                return withoutSpotify;
            } else {
                for (int mask = 0; mask < 1024; mask++) {
                    JsonObject candidate = config.deepCopy();
                    if ((mask & 1) != 0) {
                        removeSetting(candidate, "jump-circles", "only-while-jumping");
                    }

                    if ((mask & 2) != 0) {
                        removeModule(candidate, "player-chunk");
                    }

                    if ((mask & 4) != 0) {
                        removeModule(candidate, "block-outlines");
                    }

                    if ((mask & 8) != 0) {
                        removeSetting(candidate, "theme", "gui-style");
                    }

                    if ((mask & 16) != 0) {
                        removeModule(candidate, "player-debug-v2");
                        removeModule(candidate, "auto-firework");
                        removeModule(candidate, "antivanish-detection");
                        removeSetting(candidate, "autolog", "logout-on-player");
                        removeSetting(candidate, "autolog", "logout-on-totem-pop");
                        removeSetting(candidate, "autolog", "logout-on-antivanish-detection");
                    }

                    if ((mask & 32) != 0) {
                        removeSetting(candidate, "item-glow", "item-shaders");
                        removeSetting(candidate, "item-glow", "shader-softness");
                        removeSetting(candidate, "item-glow", "shader-brightness");
                    }

                    if ((mask & 64) != 0) {
                        ConfigMigrations.addLogoutOnRenderFlag(candidate);
                    }

                    if ((mask & 128) != 0) {
                        ConfigMigrations.migratePlayerDebug(candidate);
                    }

                    if ((mask & 256) != 0) {
                        removeSetting(candidate, "hud", "coords");
                        ConfigMigrations.addNetheriteFinder(candidate);
                    }

                    if ((mask & 512) != 0) {
                        removeSetting(candidate, "chunkfinder", "smart-adjustment");
                        removeSetting(candidate, "chunkfinder", "fully-grown-cactus");
                        removeSetting(candidate, "chunkfinder", "fully-grown-sugar-cane");
                        ConfigMigrations.migrateSkyEffects(candidate);
                        ConfigMigrations.addArmadillos(candidate);
                    }

                    JsonObject match = matchLayoutOrMigrate(candidate, hash);
                    if (match != null) {
                        return match;
                    }

                    ConfigMigrations.migrateSpawnerFlagger(candidate);
                    JsonObject spawnerMatch = matchLayoutOrMigrate(candidate, hash);
                    if (spawnerMatch != null) {
                        return spawnerMatch;
                    }

                    ConfigMigrations.addHoleEnclosure(candidate);
                    JsonObject enclosureMatch = matchLayoutOrMigrate(candidate, hash);
                    if (enclosureMatch != null) {
                        return enclosureMatch;
                    }

                    removeSetting(candidate, "spawner-flagger", "chunk-range");
                    removeSetting(candidate, "spawner-flagger", "scan-interval");
                    JsonObject finalMatch = matchLayoutOrMigrate(candidate, hash);
                    if (finalMatch != null) {
                        return finalMatch;
                    }
                }

                return null;
            }
        }
    }

    private static JsonObject matchLayoutOrMigrate(JsonObject config, int hash) {
        if (computeLayoutHash(config) == hash) {
            return config;
        } else {
            JsonObject stripped = ConfigMigrations.stripChatMessages(config);
            return computeLayoutHash(stripped) == hash ? stripped : null;
        }
    }

    private static void removeModule(JsonObject config, String moduleName) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null) {
            modules.remove(moduleName);
        }
    }

    private static void removeSetting(JsonObject config, String moduleName, String settingName) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null && modules.has(moduleName) && modules.get(moduleName).isJsonObject()) {
            JsonObject settings = modules.getAsJsonObject(moduleName).getAsJsonObject("settings");
            if (settings != null) {
                settings.remove(settingName);
            }
        }
    }

    private static JsonObject mergeIntoCurrentLayout(JsonObject decoded, JsonObject current) {
        if (decoded == current) {
            return decoded;
        } else {
            JsonObject result = current.deepCopy();
            JsonObject decodedModules = decoded.getAsJsonObject("modules");
            JsonObject resultModules = result.getAsJsonObject("modules");

            for (Entry<String, JsonElement> moduleEntry : decodedModules.entrySet()) {
                if (resultModules.has(moduleEntry.getKey())
                        && resultModules.get(moduleEntry.getKey()).isJsonObject()) {
                    JsonObject decodedModule = moduleEntry.getValue().getAsJsonObject();
                    JsonObject resultModule = resultModules.getAsJsonObject(moduleEntry.getKey());
                    if (decodedModule.has("active")) {
                        resultModule.add("active", decodedModule.get("active").deepCopy());
                    }

                    JsonObject decodedSettings = decodedModule.getAsJsonObject("settings");
                    JsonObject resultSettings = resultModule.getAsJsonObject("settings");
                    if (decodedSettings != null && resultSettings != null) {
                        for (Entry<String, JsonElement> settingEntry : decodedSettings.entrySet()) {
                            if (resultSettings.has(settingEntry.getKey())) {
                                resultSettings.add(
                                        settingEntry.getKey(), settingEntry.getValue().deepCopy());
                            }
                        }
                    }
                }
            }

            return result;
        }
    }

    private static int computeLayoutHash(JsonObject config) {
        CRC32 crc = new CRC32();

        for (Entry<String, JsonElement> moduleEntry :
                config.getAsJsonObject("modules").entrySet()) {
            updateChecksum(crc, moduleEntry.getKey());
            crc.update(0);
            JsonObject settings =
                    moduleEntry.getValue().getAsJsonObject().getAsJsonObject("settings");
            if (settings != null) {
                for (Entry<String, JsonElement> settingEntry : settings.entrySet()) {
                    updateChecksum(crc, settingEntry.getKey());
                    crc.update(0);
                }
            }

            crc.update(1);
        }

        return (int) crc.getValue();
    }

    private static void updateChecksum(CRC32 crc, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        crc.update(bytes, 0, bytes.length);
    }

    private static void writeString(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length >= 0 && length <= 4000000) {
            byte[] bytes = in.readNBytes(length);
            if (bytes.length != length) {
                throw new EOFException("Incomplete config string");
            } else {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } else {
            throw new IOException("Config string is too large");
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & -128) != 0) {
            out.writeByte(value & 127 | 128);
            value >>>= 7;
        }

        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;

        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            result |= (b & 127) << shift;
            if ((b & 128) == 0) {
                return result;
            }
        }

        throw new IOException("Invalid config integer");
    }

    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(9);

        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes, deflater, 8192)) {
            out.write(data);
        } catch (Throwable t) {
            deflater.end();
            throw t;
        }

        deflater.end();
        return bytes.toByteArray();
    }

    private static byte[] decompress(byte[] data) throws IOException {
        Inflater inflater = new Inflater();

        byte[] result;
        try (InflaterInputStream in =
                new InflaterInputStream(new ByteArrayInputStream(data), inflater)) {
            result = readLimited(in);
        } catch (Throwable t) {
            inflater.end();
            throw t;
        }

        inflater.end();
        return result;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            Path path = getProfilesPath();
            if (Files.isRegularFile(path)) {
                try {
                    JsonObject root =
                            JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                                    .getAsJsonObject();
                    activeProfileName =
                            root.has("active")
                                    ? sanitizeName(root.get("active").getAsString())
                                    : "";
                    JsonArray profiles = root.getAsJsonArray("profiles");
                    if (profiles == null) {
                        return;
                    }

                    for (JsonElement element : profiles) {
                        if (PROFILES.size() >= 3 || !element.isJsonObject()) {
                            break;
                        }

                        JsonObject entry = element.getAsJsonObject();
                        if (entry.has("name") && entry.has("config")) {
                            String name = sanitizeName(entry.get("name").getAsString());
                            JsonObject config = entry.getAsJsonObject("config");
                            validateConfig(config);
                            long updated =
                                    entry.has("updated") ? entry.get("updated").getAsLong() : 0L;

                            if (!name.isBlank()) {
                                PROFILES.add(
                                        new ConfigProfileManager.Profile(
                                                makeUniqueName(name),
                                                GSON.toJson(config),
                                                updated));
                            }
                        }
                    }
                } catch (Exception e) {
                    PROFILES.clear();
                    activeProfileName = "";
                }
            }
        }
    }

    private static String readCurrentConfigJson() throws Exception {
        ConfigStore.save();
        String json = Files.readString(getConfigPath(), StandardCharsets.UTF_8);
        validateConfig(JsonParser.parseString(json).getAsJsonObject());
        return json;
    }

    private static void applyConfigJson(String json) throws Exception {
        JsonObject config = JsonParser.parseString(json).getAsJsonObject();
        validateConfig(config);
        stampGuiStyle(config);
        Path path = getConfigPath();
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".profile.tmp");
        Files.writeString(tmp, GSON.toJson(config), StandardCharsets.UTF_8);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        for (Module module : ModuleManager.get().getAll()) {
            if (module.isActive()) {
                module.setActive(false);
            }
        }

        ConfigStore.load();
        ConfigStore.save();
    }

    private static void stampGuiStyle(JsonObject config) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules != null && modules.has("theme") && modules.get("theme").isJsonObject()) {
            JsonObject theme = modules.getAsJsonObject("theme");
            if (theme.has("settings") && theme.get("settings").isJsonObject()) {
                theme.getAsJsonObject("settings")
                        .addProperty("gui-style", ThemeModule.getGuiStyle().name());
            }
        }
    }

    private static void validateConfig(JsonObject config) {
        if (config == null || !config.has("modules") || !config.get("modules").isJsonObject()) {
            throw new IllegalArgumentException("Missing modules");
        }
    }

    private static Path getConfigPath() {
        try {
            Field field = ConfigStore.class.getDeclaredField("configPath");
            field.setAccessible(true);
            return (Path) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("OdinClient config path is unavailable", e);
        }
    }

    private static Path getProfilesPath() {
        return getConfigPath().resolveSibling("odinclient-config-profiles.json");
    }

    private static void saveProfiles() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("active", activeProfileName);
        JsonArray profiles = new JsonArray();

        for (ConfigProfileManager.Profile profile : PROFILES) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", profile.name);
            entry.addProperty("updated", profile.updated);
            entry.add("config", JsonParser.parseString(profile.configJson));
            profiles.add(entry);
        }

        root.add("profiles", profiles);
        Path path = getProfilesPath();
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return readLimited(in);
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;

        while (true) {
            int read = in.read(buffer);
            if (read < 0) {
                return out.toByteArray();
            }

            total += read;
            if (total > 4000000) {
                throw new IOException("Expanded config is too large");
            }

            out.write(buffer, 0, read);
        }
    }

    private static boolean isValidIndex(int index) {
        return index >= 0 && index < PROFILES.size();
    }

    private static String makeUniqueName(String name) {
        if (name.isBlank()) {
            return name;
        } else {
            String candidate = name;
            int counter = 2;

            while (isNameTaken(candidate)) {
                String suffix = " " + counter++;
                candidate =
                        name.substring(0, Math.min(name.length(), 24 - suffix.length())) + suffix;
            }

            return candidate;
        }
    }

    private static boolean isNameTaken(String name) {
        for (ConfigProfileManager.Profile profile : PROFILES) {
            if (profile.name.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        } else {
            String cleaned =
                    name.replaceAll("[^A-Za-z0-9 _\\-]", "").trim().replaceAll("\\s+", " ");
            return cleaned.substring(0, Math.min(cleaned.length(), 24));
        }
    }

    private static String describeError(Exception e) {
        String message = e.getMessage();
        if (message != null && !message.isBlank()) {
            String singleLine = message.replace('\n', ' ').replace('\r', ' ');
            return singleLine.substring(0, Math.min(singleLine.length(), 70));
        } else {
            return e.getClass().getSimpleName();
        }
    }

    public static final class BundledConfig {
        public final String name;
        public final String description;
        public final String configJson;

        public BundledConfig(String name, String description, String configJson) {
            this.name = name;
            this.description = description;
            this.configJson = configJson;
        }
    }

    private static final class DecodedConfig {
        private final String name;
        private final JsonObject config;

        private DecodedConfig(String name, JsonObject config) {
            this.name = name;
            this.config = config;
        }
    }

    public static final class Profile {
        public final String name;
        public final String configJson;
        public final long updated;

        private Profile(String name, String configJson, long updated) {
            this.name = name;
            this.configJson = configJson;
            this.updated = updated;
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;
        public final String code;

        private Result(boolean success, String message, String code) {
            this.success = success;
            this.message = message;
            this.code = code;
        }

        public static ConfigProfileManager.Result success(String message) {
            return new ConfigProfileManager.Result(true, message, null);
        }

        public static ConfigProfileManager.Result successWithCode(String message, String code) {
            return new ConfigProfileManager.Result(true, message, code);
        }

        public static ConfigProfileManager.Result failure(String message) {
            return new ConfigProfileManager.Result(false, message, null);
        }
    }
}
