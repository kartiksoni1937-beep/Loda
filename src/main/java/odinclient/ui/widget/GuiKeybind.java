package odinclient.ui.widget;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import odinclient.setting.type.KeybindSetting;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public final class GuiKeybind {
    public static final int DEFAULT_KEY = 48;
    private static final Path KEY_FILE;
    private static boolean loading;
    private static final KeybindSetting SETTING =
            new KeybindSetting.Builder()
                    .name("gui-keybind")
                    .description("Keyboard or mouse button used to open the OdinClient menu.")
                    .defaultValue(48)
                    .onChange(GuiKeybind::saveKeyToFile)
                    .build();

    private GuiKeybind() {}

    public static void init() {}

    public static KeybindSetting setting() {
        return SETTING;
    }

    public static int getKeyCode() {
        int key = SETTING.getValue();
        if ((key < 0 || key > 348) && (key < 1000 || key > 1007)) {
            return 48;
        }

        return key;
    }

    private static void loadInitialKey() {
        int key = readKeyFromFile();
        boolean fromConfig = false;
        if (!isValidKey(key)) {
            key = readKeyFromConfig();
            fromConfig = isValidKey(key);
        }

        if (!isValidKey(key)) {
            key = 48;
        }

        loading = true;

        try {
            SETTING.setValue(key);
        } finally {
            loading = false;
        }

        if (fromConfig || !Files.isRegularFile(KEY_FILE)) {
            saveKeyToFile(key);
        }
    }

    private static int readKeyFromFile() {
        if (!Files.isRegularFile(KEY_FILE)) {
            return -2147483648;
        } else {
            try {
                return Integer.parseInt(Files.readString(KEY_FILE, StandardCharsets.UTF_8).trim());
            } catch (Exception e) {
                return -2147483648;
            }
        }
    }

    private static int readKeyFromConfig() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        String[] fileNames = {"odinclient.json", "odinclient.json"};

        for (int i = 0; i < fileNames.length; i++) {
            Path path = configDir.resolve(fileNames[i]);
            if (Files.isRegularFile(path)) {
                try (BufferedReader reader =
                        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject modules =
                            JsonParser.parseReader(reader)
                                    .getAsJsonObject()
                                    .getAsJsonObject("modules");
                    JsonObject module =
                            modules == null ? null : modules.getAsJsonObject("client-keybind");
                    JsonObject settings =
                            module == null ? null : module.getAsJsonObject("settings");

                    if (settings != null && settings.has("keybind")) {
                        int key = Integer.parseInt(settings.get("keybind").getAsString());
                        if (isValidKey(key)) {
                            return key;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }

        return -2147483648;
    }

    private static void saveKeyToFile(Integer key) {
        if (!loading && key != null && isValidKey(key)) {
            try {
                Files.createDirectories(KEY_FILE.getParent());
                Files.writeString(KEY_FILE, Integer.toString(key), StandardCharsets.UTF_8);
            } catch (IOException e) {
            }
        }
    }

    private static boolean isValidKey(int key) {
        return key == -1 || key >= 0 && key <= 348 || key >= 1000 && key <= 1007;
    }

    static {
        KEY_FILE = FabricLoader.getInstance().getConfigDir().resolve("odinclient-gui-keybind.txt");
        loadInitialKey();
    }
}
