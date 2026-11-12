package odinclient.ui.layout;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class GuiPanelLayoutStore {
    private static final String FILE_NAME = "odinclient-modern-panels.properties";

    private GuiPanelLayoutStore() {}

    public static GuiPanelLayoutStore.PanelLayout loadLayout(String[] panelNames) {
        Map<String, float[]> positions = new HashMap<>();
        Set<String> hidden = new HashSet<>();
        Properties properties = new Properties();
        Path path = getConfigPath();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException e) {
                System.err.println("OdinClient: could not load panel layout: " + e.getMessage());
            }
        }

        for (String name : panelNames) {
            String prefix = "panel." + name + ".";
            if (Boolean.parseBoolean(
                    properties.getProperty(
                            prefix + "hidden", Boolean.toString("scamming".equals(name))))) {
                hidden.add(name);
            }

            String xText = properties.getProperty(prefix + "x");
            String yText = properties.getProperty(prefix + "y");
            if (xText != null && yText != null) {
                try {
                    float x = Float.parseFloat(xText);
                    float y = Float.parseFloat(yText);
                    if (Float.isFinite(x) && Float.isFinite(y)) {
                        positions.put(name, new float[] {x, y});
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return new GuiPanelLayoutStore.PanelLayout(positions, hidden);
    }

    public static void saveLayout(
            String[] panelNames, Map<String, float[]> positions, Set<String> hidden) {
        Properties properties = new Properties();

        for (String name : panelNames) {
            String prefix = "panel." + name + ".";
            properties.setProperty(prefix + "hidden", Boolean.toString(hidden.contains(name)));
            float[] position = positions.get(name);
            if (position != null
                    && position.length >= 2
                    && Float.isFinite(position[0])
                    && Float.isFinite(position[1])) {
                properties.setProperty(prefix + "x", Float.toString(position[0]));
                properties.setProperty(prefix + "y", Float.toString(position[1]));
            }
        }

        Path path = getConfigPath();
        Path tempPath = path.resolveSibling("odinclient-modern-panels.properties.tmp");

        try {
            Files.createDirectories(path.getParent());

            try (OutputStream output = Files.newOutputStream(tempPath)) {
                properties.store(output, "OdinClient modern panel layout");
            }

            try {
                Files.move(
                        tempPath,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("OdinClient: could not save panel layout: " + e.getMessage());
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public record PanelLayout(Map<String, float[]> positions, Set<String> hidden) {}
}
