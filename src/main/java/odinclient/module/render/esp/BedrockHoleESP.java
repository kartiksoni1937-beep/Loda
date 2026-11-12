package odinclient.module.render.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.event.BlockUpdateEvent;
import odinclient.event.ChunkLoadEvent;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.IntSetting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class BedrockHoleESP extends Module {
    private static final int SCAN_RADIUS = 32;
    private static final int CHUNKS_PER_TICK = 6;
    private static final int MAX_HOLE_SIZE = 200;
    private static final int MIN_HOLE_SIZE = 2;
    private static final int[] OVERWORLD_LAYERS = new int[] {-64, -63, -62, -61, -60};
    private static final int[] NETHER_LAYERS = new int[] {0, 1, 2, 3, 4, 123, 124, 125, 126, 127};
    private static final int[] NO_LAYERS = new int[0];
    private final Setting<EntityColor> color =
            this.settings
                    .getGeneral()
                    .add(
                            new ColorSetting.Builder()
                                    .name("color")
                                    .description(
                                            "Color used to highlight enclosed holes in bedrock"
                                                + " layers.")
                                    .defaultValue(new EntityColor(240, 85, 80))
                                    .build());
    private final Setting<Integer> alpha =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("alpha")
                                    .description(
                                            "Opacity of the Bedrock Hole ESP fill. Zero keeps only"
                                                + " the outline.")
                                    .defaultValue(96)
                                    .range(0, 255)
                                    .sliderRange(0, 255)
                                    .build());
    private final Set<BlockPos> holeBlocks = new HashSet<>();
    private final ArrayDeque<ChunkPos> chunkQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunks = new HashSet<>();
    private Object lastWorld;
    private int centerChunkX = -2147483648;
    private int centerChunkZ = -2147483648;
    private int ticksSinceRescan;

    public BedrockHoleESP(Category category) {
        super(
                category,
                "bedrock-hole-esp",
                "Highlights enclosed openings in overworld and nether bedrock layers.");
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world != null && this.mc.player != null && this.getScanLayers().length != 0) {
            if (this.lastWorld != this.mc.world) {
                this.reset();
                this.lastWorld = this.mc.world;
            }

            ChunkPos playerChunk = this.mc.player.getChunkPos();
            if (this.centerChunkX != playerChunk.x
                    || this.centerChunkZ != playerChunk.z
                    || ++this.ticksSinceRescan >= 80
                    || this.chunkQueue.isEmpty()) {
                this.centerChunkX = playerChunk.x;
                this.centerChunkZ = playerChunk.z;
                this.ticksSinceRescan = 0;
                this.rebuildScanQueue();
            }

            this.processScanQueue();
            if ((this.ticksSinceRescan & 31) == 0) {
                this.holeBlocks.removeIf(pos -> !this.isChunkLoadedInRange(new ChunkPos(pos)));
            }
        } else {
            this.reset();
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        if (this.mc.world != null && this.mc.player != null) {
            ChunkPos chunkPos = event.getChunk().getPos();
            if (this.isChunkInRange(chunkPos.x, chunkPos.z)) {
                this.scanChunk(event.getChunk());
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (this.mc.world != null && this.mc.player != null && this.isScanLayer(event.pos.getY())) {
            ChunkPos chunkPos = new ChunkPos(event.pos);
            this.enqueueChunk(chunkPos.x, chunkPos.z);
            int localX = event.pos.getX() & 15;
            int localZ = event.pos.getZ() & 15;
            if (localX == 0) {
                this.enqueueChunk(chunkPos.x - 1, chunkPos.z);
            }

            if (localX == 15) {
                this.enqueueChunk(chunkPos.x + 1, chunkPos.z);
            }

            if (localZ == 0) {
                this.enqueueChunk(chunkPos.x, chunkPos.z - 1);
            }

            if (localZ == 15) {
                this.enqueueChunk(chunkPos.x, chunkPos.z + 1);
            }
        }
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.lastWorld == this.mc.world) {
            EntityColor entityColor = this.color.getValue();
            Color outlineColor = new Color(entityColor.r, entityColor.g, entityColor.b, 235);
            Color fillColor =
                    new Color(entityColor.r, entityColor.g, entityColor.b, this.alpha.getValue());
            BoxRenderMode mode =
                    this.alpha.getValue() > 0 ? BoxRenderMode.Both : BoxRenderMode.Lines;

            for (BlockPos pos : this.holeBlocks) {
                event.renderer.stableBox(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        pos.getX() + 1,
                        pos.getY() + 1,
                        pos.getZ() + 1,
                        fillColor,
                        outlineColor,
                        mode);
            }
        }
    }

    private void rebuildScanQueue() {
        this.holeBlocks.removeIf(
                pos -> !this.isChunkInRange((new ChunkPos(pos)).x, (new ChunkPos(pos)).z));
        this.chunkQueue.clear();
        this.queuedChunks.clear();

        for (int radius = 0; radius <= 32; radius++) {
            for (int x = this.centerChunkX - radius; x <= this.centerChunkX + radius; x++) {
                this.enqueueChunk(x, this.centerChunkZ - radius);
                if (radius > 0) {
                    this.enqueueChunk(x, this.centerChunkZ + radius);
                }
            }

            for (int z = this.centerChunkZ - radius + 1; z < this.centerChunkZ + radius; z++) {
                this.enqueueChunk(this.centerChunkX - radius, z);
                if (radius > 0) {
                    this.enqueueChunk(this.centerChunkX + radius, z);
                }
            }
        }
    }

    private void processScanQueue() {
        for (int i = 0; i < 6 && !this.chunkQueue.isEmpty(); i++) {
            ChunkPos chunkPos = this.chunkQueue.removeFirst();
            this.queuedChunks.remove(chunkPos.toLong());
            WorldChunk chunk =
                    this.mc
                            .world
                            .getChunkManager()
                            .getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
            if (chunk != null && !chunk.isEmpty()) {
                this.scanChunk(chunk);
            }
        }
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        this.holeBlocks.removeIf(p -> new ChunkPos(p).equals(chunkPos));
        int[] layers = this.getScanLayers();
        if (layers.length != 0) {
            int startX = chunkPos.getStartX();
            int startZ = chunkPos.getStartZ();
            Set<BlockPos> visited = new HashSet<>();

            for (int y : layers) {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        BlockPos pos = new BlockPos(startX + dx, y, startZ + dz);
                        if (!visited.contains(pos) && !this.isBedrock(this.getBlockStateAt(pos))) {
                            List<BlockPos> hole = this.floodFillHole(pos, layers, visited);
                            if (hole.size() >= 2 && hole.size() < 200 && this.isEnclosed(hole)) {
                                this.holeBlocks.addAll(hole);
                            }
                        }
                    }
                }
            }
        }
    }

    private List<BlockPos> floodFillHole(BlockPos start, int[] layers, Set<BlockPos> visited) {
        List<BlockPos> hole = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> queued = new HashSet<>();
        queue.add(start);
        queued.add(start);

        while (!queue.isEmpty() && hole.size() < 200) {
            BlockPos current = queue.removeFirst();
            if (!visited.contains(current)
                    && !this.isBedrock(this.getBlockStateAt(current))
                    && containsLayer(layers, current.getY())) {
                visited.add(current);
                hole.add(current);

                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = current.offset(direction);
                    if (!visited.contains(neighbor) && queued.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
        }

        return hole;
    }

    private boolean isEnclosed(List<BlockPos> hole) {
        Set<BlockPos> holeSet = new HashSet<>(hole);

        for (BlockPos pos : hole) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.offset(direction);
                if (!holeSet.contains(neighbor)
                        && !this.isBedrock(this.getBlockStateAt(neighbor))) {
                    return false;
                }
            }
        }

        return true;
    }

    private BlockState getBlockStateAt(BlockPos pos) {
        return this.mc.world == null
                ? Blocks.BEDROCK.getDefaultState()
                : this.mc.world.getBlockState(pos);
    }

    private boolean isBedrock(BlockState state) {
        return state.getBlock() == Blocks.BEDROCK;
    }

    private int[] getScanLayers() {
        if (this.mc.world == null) {
            return NO_LAYERS;
        } else {
            String dimension = this.mc.world.getRegistryKey().getValue().toString();
            if ("minecraft:overworld".equals(dimension)) {
                return OVERWORLD_LAYERS;
            } else {
                return "minecraft:the_nether".equals(dimension) ? NETHER_LAYERS : NO_LAYERS;
            }
        }
    }

    private boolean isScanLayer(int y) {
        return containsLayer(this.getScanLayers(), y);
    }

    private static boolean containsLayer(int[] layers, int y) {
        for (int layer : layers) {
            if (layer == y) {
                return true;
            }
        }

        return false;
    }

    private void enqueueChunk(int chunkX, int chunkZ) {
        if (this.isChunkInRange(chunkX, chunkZ) && this.mc.world != null) {
            WorldChunk chunk =
                    this.mc
                            .world
                            .getChunkManager()
                            .getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            long key = ChunkPos.toLong(chunkX, chunkZ);
            if (chunk != null && !chunk.isEmpty() && this.queuedChunks.add(key)) {
                this.chunkQueue.addLast(new ChunkPos(chunkX, chunkZ));
            }
        }
    }

    private boolean isChunkLoadedInRange(ChunkPos chunkPos) {
        return this.isChunkInRange(chunkPos.x, chunkPos.z)
                && this.mc.world != null
                && this.mc
                                .world
                                .getChunkManager()
                                .getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false)
                        != null;
    }

    private boolean isChunkInRange(int chunkX, int chunkZ) {
        return Math.abs(chunkX - this.centerChunkX) <= 32
                && Math.abs(chunkZ - this.centerChunkZ) <= 32;
    }

    private void reset() {
        this.holeBlocks.clear();
        this.chunkQueue.clear();
        this.queuedChunks.clear();
        this.lastWorld = null;
        this.centerChunkX = -2147483648;
        this.centerChunkZ = -2147483648;
        this.ticksSinceRescan = 0;
    }
}
