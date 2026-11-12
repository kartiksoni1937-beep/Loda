package odinclient.module.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ChunkAreaMerger {
    public static final int PADDING_CHUNKS = 4;
    private static final Comparator<ChunkAreaMerger.LongChunkPos> POSITION_ORDER =
            Comparator.comparingLong(ChunkAreaMerger.LongChunkPos::z)
                    .thenComparingLong(ChunkAreaMerger.LongChunkPos::x);

    private ChunkAreaMerger() {}

    public static List<ChunkAreaMerger.ChunkArea> mergeChunks(
            Collection<ChunkAreaMerger.ChunkCoord> chunks, boolean merge) {
        HashSet<ChunkAreaMerger.LongChunkPos> positions = new HashSet<>();

        for (ChunkAreaMerger.ChunkCoord chunk : chunks) {
            positions.add(new ChunkAreaMerger.LongChunkPos(chunk.x, chunk.z));
        }

        if (positions.isEmpty()) {
            return List.of();
        } else if (!merge) {
            return toRectangles(positions);
        } else {
            ArrayList<ChunkAreaMerger.LongChunkPos> positionList = new ArrayList<>(positions);
            int[] parents = new int[positionList.size()];
            for (int i = 0; i < parents.length; i++) {
                parents[i] = i;
            }

            HashMap<ChunkAreaMerger.LongChunkPos, ChunkAreaMerger.ClusterCell> cells =
                    new HashMap<>();

            for (int index = 0; index < positionList.size(); index++) {
                ChunkAreaMerger.LongChunkPos center = positionList.get(index);

                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        ChunkAreaMerger.LongChunkPos neighbor =
                                new ChunkAreaMerger.LongChunkPos(center.x + dx, center.z + dz);
                        ChunkAreaMerger.ClusterCell cell = cells.get(neighbor);
                        if (cell == null) {
                            cells.put(neighbor, new ChunkAreaMerger.ClusterCell(index));
                        } else {
                            cell.overlapCount++;
                            parents[findRoot(parents, index)] = findRoot(parents, cell.ownerIndex);
                        }
                    }
                }
            }

            HashMap<Integer, Set<ChunkAreaMerger.LongChunkPos>> groups = new HashMap<>();

            for (int n = 0; n < positionList.size(); n++) {
                ChunkAreaMerger.LongChunkPos pos = positionList.get(n);
                Set<ChunkAreaMerger.LongChunkPos> group =
                        groups.computeIfAbsent(findRoot(parents, n), k -> new HashSet<>());
                int maxOverlap = 0;

                for (int gx = -4; gx <= 4; gx++) {
                    for (int gz = -4; gz <= 4; gz++) {
                        maxOverlap =
                                Math.max(
                                        maxOverlap,
                                        cells.get(
                                                        new ChunkAreaMerger.LongChunkPos(
                                                                pos.x + gx, pos.z + gz))
                                                .overlapCount);
                    }
                }

                for (int ox = -4; ox <= 4; ox++) {
                    for (int oz = -4; oz <= 4; oz++) {
                        ChunkAreaMerger.LongChunkPos cellPos =
                                new ChunkAreaMerger.LongChunkPos(pos.x + ox, pos.z + oz);
                        if (cells.get(cellPos).overlapCount == maxOverlap) {
                            group.add(cellPos);
                        }
                    }
                }
            }

            ArrayList<ChunkAreaMerger.ChunkArea> areas = new ArrayList<>();

            for (Set<ChunkAreaMerger.LongChunkPos> cluster : groups.values()) {
                areas.addAll(toRectangles(cluster));
            }

            areas.sort(
                    Comparator.comparingLong(ChunkAreaMerger.ChunkArea::minimumZ)
                            .thenComparingLong(ChunkAreaMerger.ChunkArea::minimumX));
            return List.copyOf(areas);
        }
    }

    private static int findRoot(int[] parents, int index) {
        while (parents[index] != index) {
            parents[index] = parents[parents[index]];
            index = parents[index];
        }

        return index;
    }

    private static List<ChunkAreaMerger.ChunkArea> toRectangles(
            Set<ChunkAreaMerger.LongChunkPos> positions) {
        TreeSet<ChunkAreaMerger.LongChunkPos> remaining = new TreeSet<>(POSITION_ORDER);
        remaining.addAll(positions);
        ArrayList<ChunkAreaMerger.ChunkArea> areas = new ArrayList<>();

        while (!remaining.isEmpty()) {
            ChunkAreaMerger.LongChunkPos first = remaining.first();
            long endX = first.x + 1L;

            while (remaining.contains(new ChunkAreaMerger.LongChunkPos(endX, first.z))) {
                endX++;
            }

            long endZ = first.z + 1L;

            while (isRowFilled(remaining, first.x, endX, endZ)) {
                endZ++;
            }

            for (long z = first.z; z < endZ; z++) {
                for (long x = first.x; x < endX; x++) {
                    remaining.remove(new ChunkAreaMerger.LongChunkPos(x, z));
                }
            }

            areas.add(new ChunkAreaMerger.ChunkArea(first.x, first.z, endX, endZ));
        }

        return List.copyOf(areas);
    }

    private static boolean isRowFilled(
            Set<ChunkAreaMerger.LongChunkPos> positions, long startX, long endX, long z) {
        for (long x = startX; x < endX; x++) {
            if (!positions.contains(new ChunkAreaMerger.LongChunkPos(x, z))) {
                return false;
            }
        }

        return true;
    }

    public record ChunkArea(
            long minimumX, long minimumZ, long maximumXExclusive, long maximumZExclusive) {
        public double minBlockX() {
            return this.minimumX * 16.0;
        }

        public double minBlockZ() {
            return this.minimumZ * 16.0;
        }

        public double maxBlockX() {
            return this.maximumXExclusive * 16.0;
        }

        public double maxBlockZ() {
            return this.maximumZExclusive * 16.0;
        }

        public double centerBlockX() {
            return (this.minimumX + this.maximumXExclusive) * 8.0;
        }

        public double centerBlockZ() {
            return (this.minimumZ + this.maximumZExclusive) * 8.0;
        }

        public ChunkAreaMerger.ChunkArea clip(int chunkX, int chunkZ, int radius) {
            long minX = Math.max(this.minimumX, (long) chunkX - radius);
            long minZ = Math.max(this.minimumZ, (long) chunkZ - radius);
            long maxX = Math.min(this.maximumXExclusive, (long) chunkX + radius + 1L);
            long maxZ = Math.min(this.maximumZExclusive, (long) chunkZ + radius + 1L);
            return minX < maxX && minZ < maxZ
                    ? new ChunkAreaMerger.ChunkArea(minX, minZ, maxX, maxZ)
                    : null;
        }
    }

    public record ChunkCoord(int x, int z) {}

    private static final class ClusterCell {
        private int overlapCount = 1;
        private final int ownerIndex;

        private ClusterCell(int ownerIndex) {
            this.ownerIndex = ownerIndex;
        }
    }

    private record LongChunkPos(long x, long z) {}
}
