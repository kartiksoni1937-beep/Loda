package odinclient.module.playerdebug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChunkBlockSnapshotStore {
    public static final int MIN_SECTION_INDEX = 6;
    public static final int MAX_SECTION_INDEX = 23;
    private final Map<Long, int[][]> snapshots = new HashMap<>();
    private final int airState;

    public ChunkBlockSnapshotStore(int airState) {
        this.airState = airState;
    }

    public static long packChunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 4294967295L;
    }

    public boolean hasSnapshot(int chunkX, int chunkZ) {
        return this.snapshots.containsKey(packChunkKey(chunkX, chunkZ));
    }

    public void clear() {
        this.snapshots.clear();
    }

    public void remove(int chunkX, int chunkZ) {
        this.snapshots.remove(packChunkKey(chunkX, chunkZ));
    }

    public void removeOutside(int centerX, int centerZ, int radius) {
        this.snapshots
                .keySet()
                .removeIf(
                        key ->
                                Math.abs((long) ((int) (key >> 32)) - centerX) > radius
                                        || Math.abs((long) ((int) key.longValue()) - centerZ)
                                                > radius);
    }

    public void store(int chunkX, int chunkZ, int[][] sections) {
        int[][] copy = new int[24][];

        for (int i = 6; i <= 23 && i < sections.length; i++) {
            if (sections[i] != null) {
                copy[i] = sections[i].clone();
            }
        }

        this.snapshots.put(packChunkKey(chunkX, chunkZ), copy);
    }

    public List<ChunkBlockSnapshotStore.BlockChange> diff(
            int chunkX, int chunkZ, int[][] sections) {
        List<ChunkBlockSnapshotStore.BlockChange> changes = new ArrayList<>();
        int[][] previous = this.snapshots.get(packChunkKey(chunkX, chunkZ));
        if (this.isEmptySnapshot(previous)) {
            return changes;
        } else {
            for (int section = 6; section <= 23; section++) {
                int[] oldBlocks = previous[section];
                int[] newBlocks = section < sections.length ? sections[section] : null;
                if (!Arrays.equals(oldBlocks, newBlocks)) {
                    int length =
                            oldBlocks != null
                                    ? oldBlocks.length
                                    : (newBlocks == null ? 0 : newBlocks.length);

                    for (int i = 0; i < length; i++) {
                        int oldState = oldBlocks == null ? this.airState : oldBlocks[i];
                        int newState = newBlocks == null ? this.airState : newBlocks[i];
                        if (oldState != newState) {
                            changes.add(
                                    new ChunkBlockSnapshotStore.BlockChange(
                                            chunkX * 16 + (i & 15),
                                            (section - 4) * 16 + (i >> 8 & 15),
                                            chunkZ * 16 + (i >> 4 & 15),
                                            newState,
                                            oldState));
                        }
                    }
                }
            }

            return changes;
        }
    }

    private boolean isEmptySnapshot(int[][] sections) {
        if (sections == null) {
            return true;
        } else {
            for (int i = 6; i <= 23; i++) {
                if (sections[i] != null) {
                    for (int state : sections[i]) {
                        if (state != this.airState) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }
    }

    public record BlockChange(int worldX, int worldY, int worldZ, int newState, int oldState) {}
}
