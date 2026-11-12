package odinclient.module.hud.regionmap;

import odinclient.module.world.ChunkAreaMerger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RegionFillBuilder {
    private RegionFillBuilder() {}

    public static List<RegionFillBuilder.ColoredArea> buildColoredAreas(
            List<ChunkAreaMerger.ChunkArea> firstAreas,
            int firstColor,
            List<ChunkAreaMerger.ChunkArea> secondAreas,
            int secondColor) {
        HashMap<ChunkAreaMerger.ChunkCoord, Integer> cellColors = new HashMap<>();
        paintCells(cellColors, firstAreas, firstColor);
        paintCells(cellColors, secondAreas, secondColor);
        TreeMap<Integer, List<ChunkAreaMerger.ChunkCoord>> cellsByColor = new TreeMap<>();
        cellColors.forEach(
                (coord, color) ->
                        cellsByColor.computeIfAbsent(color, c -> new ArrayList<>()).add(coord));
        ArrayList<RegionFillBuilder.ColoredArea> result = new ArrayList<>();
        cellsByColor.forEach(
                (color, cells) -> {
                    for (ChunkAreaMerger.ChunkArea area :
                            ChunkAreaMerger.mergeChunks(cells, false)) {
                        result.add(new RegionFillBuilder.ColoredArea(area, color));
                    }
                });
        return List.copyOf(result);
    }

    private static void paintCells(
            Map<ChunkAreaMerger.ChunkCoord, Integer> cellColors,
            List<ChunkAreaMerger.ChunkArea> areas,
            int color) {
        if (color >>> 24 != 0) {
            for (ChunkAreaMerger.ChunkArea area : areas) {
                for (long x = area.minimumX(); x < area.maximumXExclusive(); x++) {
                    for (long z = area.minimumZ(); z < area.maximumZExclusive(); z++) {
                        if (x >= -2147483648L
                                && x <= 2147483647L
                                && z >= -2147483648L
                                && z <= 2147483647L) {
                            cellColors.put(new ChunkAreaMerger.ChunkCoord((int) x, (int) z), color);
                        }
                    }
                }
            }
        }
    }

    public record ColoredArea(ChunkAreaMerger.ChunkArea area, int color) {}
}
