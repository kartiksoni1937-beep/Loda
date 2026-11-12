package odinclient.module.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public final class AmethystScanner {
    private AmethystScanner() {}

    public static void scanChunk(AmethystESP esp, World world, WorldChunk chunk) {
        Set<BlockPos> clusters = findAmethystClusters(chunk);
        if (!clusters.isEmpty()) {
            ChunkPos chunkPos = chunk.getPos();
            esp.foundPositions.compute(
                    chunkPos,
                    (key, existing) -> {
                        Set<BlockPos> merged = new HashSet<>();
                        if (existing != null) {
                            merged.addAll(existing);
                        }

                        merged.addAll(clusters);
                        return merged;
                    });
            esp.markedChunks.add(chunkPos);
        }
    }

    public static Set<BlockPos> findAmethystClusters(WorldChunk chunk) {
        Set<BlockPos> positions = new HashSet<>();
        ChunkSection[] sections = chunk.getSectionArray();
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int bottomY = chunk.getBottomY();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section != null
                    && !section.isEmpty()
                    && section.hasAny(AmethystScanner::isAmethystCluster)) {
                int baseY = bottomY + sectionIndex * 16;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (isAmethystCluster(section.getBlockState(x, y, z))) {
                                positions.add(new BlockPos(startX + x, baseY + y, startZ + z));
                            }
                        }
                    }
                }
            }
        }

        return positions;
    }

    private static boolean isAmethystCluster(BlockState state) {
        return state.getBlock() == Blocks.AMETHYST_CLUSTER;
    }
}
