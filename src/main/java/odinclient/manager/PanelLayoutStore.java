package odinclient.manager;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class PanelLayoutStore {
    private static final String[] PANEL_IDS;

    private PanelLayoutStore() {}

    public static Map<String, PanelLayoutStore.PanelPosition> load() {
        Map<String, PanelLayoutStore.PanelPosition> positions = new HashMap<>();
        Path file = getLayoutFile();
        if (!Files.isRegularFile(file)) {
            return positions;
        } else {
            Properties properties = new Properties();

            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);

                for (String id : PANEL_IDS) {
                    String x = properties.getProperty(id + ".x");
                    String y = properties.getProperty(id + ".y");
                    if (x != null && y != null) {
                        try {
                            positions.put(
                                    id,
                                    new PanelLayoutStore.PanelPosition(
                                            Integer.parseInt(x), Integer.parseInt(y)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println(
                        "OdinClient: could not read Panel GUI positions: " + e.getMessage());
            }

            return positions;
        }
    }

    public static void save(Map<String, PanelLayoutStore.PanelPosition> positions) {
        Properties properties = new Properties();

        for (String id : PANEL_IDS) {
            PanelLayoutStore.PanelPosition position = positions.get(id);
            if (position != null) {
                properties.setProperty(id + ".x", Integer.toString(position.x()));
                properties.setProperty(id + ".y", Integer.toString(position.y()));
            }
        }

        Path file = getLayoutFile();
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");

        try {
            Files.createDirectories(file.getParent());

            try (OutputStream out = Files.newOutputStream(tempFile)) {
                properties.store(out, "OdinClient Panel GUI positions");
            }

            try {
                Files.move(
                        tempFile,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("OdinClient: could not save Panel GUI positions: " + e.getMessage());
        }
    }

    private static Path getLayoutFile() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("odinclient-panel-layout.properties");
    }

    static {
        PANEL_IDS = new String[] {"toolbar", "combat", "render", "misc", "donut", "client"};
    }

    public record PanelPosition(int x, int y) {}
}
