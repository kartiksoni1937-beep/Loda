package odinclient.module.world;

import java.util.List;

public final class ChunkFinderFlags {
    public static final int NEAR_RADIUS = 7;
    public static final int FAR_RADIUS = 32;
    public static final int FLAG_HOLES = 1;
    public static final int FLAG_CACTI = 8;
    public static final int FLAG_GEODES = 16;
    public static final int FLAG_SUGAR_CANE = 32;
    public static final int NEAR_FLAGS_MASK = 17;
    public static final int FAR_FLAGS_MASK = 40;
    public static final int OFFSET_SPAN = 64;
    public static final int OFFSET_ROW_SIZE = 65;
    public static final int OFFSET_CELL_COUNT = 4225;

    private ChunkFinderFlags() {}

    public static boolean isWithinChunks(int x, int z, int centerX, int centerZ, int radius) {
        return Math.abs((long) x - centerX) <= radius && Math.abs((long) z - centerZ) <= radius;
    }

    public static int getActiveFlags(int x, int z, int centerX, int centerZ, int enabledFlags) {
        int flags = isWithinChunks(x, z, centerX, centerZ, 7) ? enabledFlags & 17 : 0;
        if (isWithinChunks(x, z, centerX, centerZ, 32)) {
            flags |= enabledFlags & 40;
        }

        return flags;
    }

    public static int filterPendingFlags(
            int activeFlags, int scannedFlags, boolean neighborsReady) {
        int pending = activeFlags & ~scannedFlags;
        return neighborsReady ? pending : pending & 40;
    }

    public static int getScanRadius(int flags) {
        return (flags & 40) != 0 ? 32 : ((flags & 17) != 0 ? 7 : 0);
    }

    public static int unpackOffsetX(int offset) {
        return Math.floorMod(offset, 4225) % 65 - 32;
    }

    public static int unpackOffsetZ(int offset) {
        return Math.floorMod(offset, 4225) / 65 - 32;
    }

    public static <T> List<T> selectList(int flags, int mask, List<T> previous, List<T> fresh) {
        return (flags & mask) != 0 ? fresh : previous;
    }
}
