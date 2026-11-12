package odinclient.module.hud.regionmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

public final class RegionMapLayout {
    public static final int GRID_SIZE = 36;
    public static final double WORLD_HALF_EXTENT = 225000.0;
    public static final List<RegionMapLayout.RegionRect> REGIONS = loadRegions();

    private RegionMapLayout() {}

    private static List<RegionMapLayout.RegionRect> loadRegions() {
        LinkedHashMap<String, List<int[]>> rectsByRegion = new LinkedHashMap<>();

        try (InputStream stream =
                RegionMapLayout.class.getResourceAsStream(
                        "/assets/odinclient/data/region_map.csv")) {
            if (stream == null) {
                throw new IllegalStateException("Missing region map layout");
            }

            String line;
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        String[] parts = line.split(",");
                        if (parts.length != 5) {
                            throw new IllegalStateException("Invalid region rectangle");
                        }

                        int[] rect = new int[4];

                        for (int i = 0; i < 4; i++) {
                            rect[i] = Integer.parseInt(parts[i + 1]);
                        }

                        rectsByRegion.computeIfAbsent(parts[0], r -> new ArrayList<>()).add(rect);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read region map layout", e);
        }

        List<RegionMapLayout.RegionRect> regions = new ArrayList<>();
        boolean[] occupied = new boolean[1296];

        for (Entry<String, List<int[]>> entry : rectsByRegion.entrySet()) {
            entry.getValue()
                    .sort(Comparator.comparingInt((int[] r) -> r[1]).thenComparingInt(r -> r[0]));
            int number = 0;

            for (int[] values : entry.getValue()) {
                int column = values[0];
                int row = values[1];
                int width = values[2];
                int height = values[3];
                if (column < 0
                        || row < 0
                        || width < 1
                        || height < 1
                        || column + width > 36
                        || row + height > 36) {
                    throw new IllegalStateException("Region rectangle outside map");
                }

                for (int cellY = row; cellY < row + height; cellY++) {
                    for (int cellX = column; cellX < column + width; cellX++) {
                        int index = cellY * 36 + cellX;
                        if (occupied[index]) {
                            throw new IllegalStateException("Overlapping region rectangles");
                        }

                        occupied[index] = true;
                    }
                }

                regions.add(
                        new RegionMapLayout.RegionRect(
                                entry.getKey(), ++number, column, row, width, height));
            }
        }

        for (boolean filled : occupied) {
            if (!filled) {
                throw new IllegalStateException("Incomplete region map");
            }
        }

        return List.copyOf(regions);
    }

    public static RegionMapLayout.RegionRect findRegion(double worldX, double worldZ) {
        double column = (worldX + 225000.0) * 36.0 / 450000.0;
        double row = (worldZ + 225000.0) * 36.0 / 450000.0;
        if (!(column < 0.0) && !(row < 0.0) && !(column >= 36.0) && !(row >= 36.0)) {
            for (RegionMapLayout.RegionRect region : REGIONS) {
                if (column >= region.column
                        && column < region.column + region.width
                        && row >= region.row
                        && row < region.row + region.height) {
                    return region;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public record RegionRect(
            String region, int number, int column, int row, int width, int height) {}
}
