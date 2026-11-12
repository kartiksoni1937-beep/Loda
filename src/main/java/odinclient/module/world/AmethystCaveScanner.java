package odinclient.module.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AmethystCaveScanner {
    public static final int SEARCH_DIAMETER = 12;
    private static final int MIN_Y = -58;
    private static final int MAX_Y = 30;
    private static final int SEARCH_RADIUS = 8;
    private static final int MAX_RESULTS = 9000;
    private static final int REQUIRED_DARK_LIGHT = 0;
    private static final int NEIGHBOR_LIGHT_THRESHOLD = 4;
    private static final int[][] NEIGHBOR_OFFSETS;

    private AmethystCaveScanner() {}

    public static Set<BlockPos> findDarkSpots(World world, WorldChunk chunk) {
        if (world != null && chunk != null) {
            BlockPos centroid = findAmethystCentroid(world, chunk);
            if (centroid == null) {
                return Set.of();
            } else {
                HashSet<BlockPos> spots = new HashSet<>();
                Mutable pos = new Mutable();
                Mutable neighbor = new Mutable();

                search:
                for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                    for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                        for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                            pos.set(
                                    centroid.getX() + dx,
                                    centroid.getY() + dy,
                                    centroid.getZ() + dz);
                            BlockState state = world.getBlockState(pos);
                            if ((state.isOf(Blocks.AIR) || state.isOf(Blocks.AMETHYST_CLUSTER))
                                    && world.getLightLevel(LightType.BLOCK, pos)
                                            == REQUIRED_DARK_LIGHT) {
                                boolean brightNeighbor = false;

                                for (int[] offset : NEIGHBOR_OFFSETS) {
                                    neighbor.set(
                                            pos.getX() + offset[0],
                                            pos.getY() + offset[1],
                                            pos.getZ() + offset[2]);
                                    if (world.getLightLevel(LightType.BLOCK, neighbor)
                                                    > NEIGHBOR_LIGHT_THRESHOLD
                                            && world.getBlockState(neighbor).isOf(Blocks.AIR)) {
                                        brightNeighbor = true;
                                        break;
                                    }
                                }

                                if (brightNeighbor) {
                                    spots.add(pos.toImmutable());
                                    if (spots.size() >= MAX_RESULTS) {
                                        break search;
                                    }
                                }
                            }
                        }
                    }
                }

                return spots.isEmpty() ? Set.of() : Collections.unmodifiableSet(spots);
            }
        } else {
            return Set.of();
        }
    }

    public static void load() {}

    private static BlockPos findAmethystCentroid(World world, WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long sumX = 0L;
        long sumY = 0L;
        long sumZ = 0L;
        int count = 0;
        Mutable pos = new Mutable();

        for (int y = MIN_Y; y <= MAX_Y; y++) {
            for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
                for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(Blocks.AMETHYST_BLOCK) || state.isOf(Blocks.BUDDING_AMETHYST)) {
                        sumX += x;
                        sumY += y;
                        sumZ += z;
                        count++;
                    }
                }
            }
        }

        return count == 0
                ? null
                : new BlockPos((int) (sumX / count), (int) (sumY / count), (int) (sumZ / count));
    }

    static {
        NEIGHBOR_OFFSETS =
                new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    }
}
