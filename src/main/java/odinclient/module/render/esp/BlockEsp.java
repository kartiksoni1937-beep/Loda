package odinclient.module.render.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
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
import odinclient.setting.type.BlockColorMapSetting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Environment(EnvType.CLIENT)
public final class BlockEsp extends Module {
    private static final int RESCAN_INTERVAL_TICKS = 10;
    private final BlockColorMapSetting blocks;
    private final Setting<Integer> alpha;
    private final Setting<Boolean> tracers;
    private final Map<BlockPos, Block> foundBlocks = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pendingScans = new AtomicInteger();
    private volatile ExecutorService scanner;
    private volatile Object scannedWorld;
    private volatile int scannedRevision = -1;
    private volatile long scannerGeneration;
    private int rescanTickCounter;

    public BlockEsp(Category category) {
        super(category, "block-esp", "Highlights specific blocks through walls");
        BlockColorMapSetting.Builder blocksBuilder = new BlockColorMapSetting.Builder();
        blocksBuilder.name("blocks");
        blocksBuilder.description("Blocks to highlight and the color to use for each block.");
        this.blocks = (BlockColorMapSetting) this.settings.getGeneral().add(blocksBuilder.build());
        IntSetting.Builder alphaBuilder = new IntSetting.Builder();
        alphaBuilder.name("alpha");
        alphaBuilder.description("Opacity of the highlighted blocks.");
        alphaBuilder.defaultValue(125);
        alphaBuilder.range(1, 255);
        alphaBuilder.sliderRange(1, 255);
        this.alpha = this.settings.getGeneral().add(alphaBuilder.build());
        BooleanSetting.Builder tracersBuilder = new BooleanSetting.Builder();
        tracersBuilder.name("tracers");
        tracersBuilder.description("Draws a line from your crosshair to the block.");
        tracersBuilder.defaultValue(false);
        this.tracers = this.settings.getGeneral().add(tracersBuilder.build());
    }

    @Override
    public void onActivate() {
        this.restartScanner();
    }

    @Override
    public void onDeactivate() {
        this.stopScanner();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world != null && this.mc.player != null) {
            int revision = this.blocks.getBlockSetRevision();
            if (this.scannedWorld != this.mc.world || this.scannedRevision != revision) {
                this.restartScanner();
            } else if (!this.blocks.getBlocks().isEmpty()) {
                if (this.rescanTickCounter < 10) {
                    this.rescanTickCounter++;
                }

                if (this.rescanTickCounter >= 10 && this.pendingScans.get() == 0) {
                    this.rescanTickCounter = 0;
                    this.queueLoadedChunks(this.scannerGeneration);
                }
            }
        } else if (this.scannedWorld != null || !this.foundBlocks.isEmpty()) {
            this.stopScanner();
        }
    }

    @EventHandler
    private void onChunkLoaded(ChunkLoadEvent event) {
        this.queueChunk(event.getChunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        Set<Block> trackedBlocks = this.blocks.getBlocks();
        if (!trackedBlocks.isEmpty()) {
            Block oldBlock = event.oldState.getBlock();
            Block newBlock = event.newState.getBlock();
            boolean added;
            if (trackedBlocks.contains(newBlock) && !trackedBlocks.contains(oldBlock)) {
                added = true;
            } else {
                if (trackedBlocks.contains(newBlock) || !trackedBlocks.contains(oldBlock)) {
                    return;
                }

                added = false;
            }

            BlockPos pos = event.pos.toImmutable();
            this.submit(
                    () ->
                            this.applyBlockUpdate(
                                    pos.getX(), pos.getY(), pos.getZ(), added, newBlock));
        }
    }

    @EventHandler
    private void onRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.mc.player != null && !this.foundBlocks.isEmpty()) {
            int viewDistance = this.mc.options.getClampedViewDistance();
            ChunkPos playerChunk = new ChunkPos(this.mc.player.getBlockPos());
            int alphaValue = this.alpha.getValue();
            boolean drawTracers = this.tracers.getValue();

            for (Entry<BlockPos, Block> entry : this.foundBlocks.entrySet()) {
                BlockPos pos = entry.getKey();
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (Math.abs(chunkX - playerChunk.x) <= viewDistance
                        && Math.abs(chunkZ - playerChunk.z) <= viewDistance) {
                    Block block = entry.getValue();
                    Color color = this.colorFor(block, alphaValue);
                    event.renderer.stableBox(
                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            pos.getX() + 1,
                            pos.getY() + 1,
                            pos.getZ() + 1,
                            color,
                            color,
                            BoxRenderMode.Sides);
                    if (drawTracers) {
                        event.renderer.blockEspHybridTracer(
                                new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                                this.colorFor(block, 255),
                                1.0F);
                    }
                }
            }
        }
    }

    private Color colorFor(Block block, int alphaValue) {
        EntityColor entityColor = this.blocks.getColor(block);
        if (entityColor != null) {
            return new Color(entityColor.r, entityColor.g, entityColor.b, alphaValue);
        } else {
            int seed = Registries.BLOCK.getId(block).hashCode();
            Random random = new Random(seed);
            return new Color(
                    100 + random.nextInt(156),
                    100 + random.nextInt(156),
                    100 + random.nextInt(156),
                    alphaValue);
        }
    }

    private synchronized void restartScanner() {
        long generation = ++this.scannerGeneration;
        ExecutorService oldScanner = this.scanner;
        if (oldScanner != null && !oldScanner.isShutdown()) {
            oldScanner.shutdownNow();
        }

        this.foundBlocks.clear();
        this.scannedChunks.clear();
        this.queuedChunks.clear();
        this.pendingScans.set(0);
        this.rescanTickCounter = 0;
        this.scannedWorld = this.mc.world;
        this.scannedRevision = this.blocks.getBlockSetRevision();
        ThreadFactory threadFactory =
                runnable -> {
                    Thread thread = new Thread(runnable, "OdinClient-BlockESP-Scanner");
                    thread.setDaemon(true);
                    return thread;
                };
        this.scanner = Executors.newSingleThreadExecutor(threadFactory);
        if (this.mc.world != null && this.mc.player != null && !this.blocks.getBlocks().isEmpty()) {
            this.queueLoadedChunks(generation);
        }
    }

    private void queueLoadedChunks(long generation) {
        if (generation == this.scannerGeneration
                && this.mc.world != null
                && this.mc.player != null
                && !this.blocks.getBlocks().isEmpty()) {
            ChunkPos playerChunk = new ChunkPos(this.mc.player.getBlockPos());
            int radius = Math.max(2, this.mc.options.getClampedViewDistance() + 1);

            for (int ring = 0; ring <= radius; ring++) {
                for (int x = playerChunk.x - ring; x <= playerChunk.x + ring; x++) {
                    this.queueLoadedChunkAt(x, playerChunk.z - ring, generation);
                    if (ring > 0) {
                        this.queueLoadedChunkAt(x, playerChunk.z + ring, generation);
                    }
                }

                for (int z = playerChunk.z - ring + 1; z < playerChunk.z + ring; z++) {
                    this.queueLoadedChunkAt(playerChunk.x - ring, z, generation);
                    if (ring > 0) {
                        this.queueLoadedChunkAt(playerChunk.x + ring, z, generation);
                    }
                }
            }
        }
    }

    private void queueLoadedChunkAt(int chunkX, int chunkZ, long generation) {
        if (generation == this.scannerGeneration && this.mc.world != null) {
            WorldChunk chunk =
                    this.mc
                            .world
                            .getChunkManager()
                            .getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk != null) {
                this.queueChunk(chunk, generation);
            }
        }
    }

    private synchronized void stopScanner() {
        this.scannerGeneration++;
        this.foundBlocks.clear();
        this.scannedChunks.clear();
        this.queuedChunks.clear();
        this.pendingScans.set(0);
        this.rescanTickCounter = 0;
        this.scannedWorld = null;
        this.scannedRevision = -1;
        ExecutorService oldScanner = this.scanner;
        this.scanner = null;
        if (oldScanner != null && !oldScanner.isShutdown()) {
            oldScanner.shutdownNow();
        }
    }

    private void queueChunk(WorldChunk chunk) {
        this.queueChunk(chunk, this.scannerGeneration);
    }

    private void queueChunk(WorldChunk chunk, long generation) {
        if (chunk != null && generation == this.scannerGeneration) {
            long chunkKey = chunk.getPos().toLong();
            if (this.queuedChunks.add(chunkKey)) {
                this.pendingScans.incrementAndGet();
                boolean submitted =
                        this.submit(
                                () -> {
                                    try {
                                        this.scanChunk(chunk, generation);
                                    } finally {
                                        if (generation == this.scannerGeneration) {
                                            this.queuedChunks.remove(chunkKey);
                                            this.pendingScans.decrementAndGet();
                                        }
                                    }
                                });
                if (!submitted && generation == this.scannerGeneration) {
                    this.queuedChunks.remove(chunkKey);
                    this.pendingScans.decrementAndGet();
                }
            }
        }
    }

    private boolean submit(Runnable task) {
        ExecutorService executor = this.scanner;
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.submit(task);
                return true;
            } catch (RejectedExecutionException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    private void scanChunk(WorldChunk chunk, long generation) {
        if (this.isActive() && generation == this.scannerGeneration) {
            Set<Block> trackedBlocks = this.blocks.getBlocks();
            if (!trackedBlocks.isEmpty()) {
                ChunkPos chunkPos = chunk.getPos();
                Map<BlockPos, Block> found = new HashMap<>();
                ChunkSection[] sections = chunk.getSectionArray();
                int startX = chunkPos.getStartX();
                int startZ = chunkPos.getStartZ();
                int bottomY = chunk.getBottomY();

                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    if (Thread.currentThread().isInterrupted()
                            || generation != this.scannerGeneration) {
                        return;
                    }

                    ChunkSection section = sections[sectionIndex];
                    if (section != null
                            && !section.isEmpty()
                            && section.hasAny(state -> trackedBlocks.contains(state.getBlock()))) {
                        int sectionY = bottomY + (sectionIndex << 4);

                        for (int dx = 0; dx < 16; dx++) {
                            for (int dy = 0; dy < 16; dy++) {
                                for (int dz = 0; dz < 16; dz++) {
                                    Block block = section.getBlockState(dx, dy, dz).getBlock();
                                    if (trackedBlocks.contains(block)) {
                                        found.put(
                                                new BlockPos(
                                                        startX + dx, sectionY + dy, startZ + dz),
                                                block);
                                    }
                                }
                            }
                        }
                    }
                }

                if (!Thread.currentThread().isInterrupted()
                        && this.isActive()
                        && generation == this.scannerGeneration) {
                    for (Entry<BlockPos, Block> entry : this.foundBlocks.entrySet()) {
                        BlockPos pos = entry.getKey();
                        if (pos.getX() >> 4 == chunkPos.x && pos.getZ() >> 4 == chunkPos.z) {
                            this.foundBlocks.remove(pos, entry.getValue());
                        }
                    }

                    this.foundBlocks.putAll(found);
                    this.scannedChunks.add(chunkPos);
                    this.refreshScannedChunk(chunkPos.x - 1, chunkPos.z);
                    this.refreshScannedChunk(chunkPos.x + 1, chunkPos.z);
                    this.refreshScannedChunk(chunkPos.x, chunkPos.z - 1);
                    this.refreshScannedChunk(chunkPos.x, chunkPos.z + 1);
                }
            }
        }
    }

    private void refreshScannedChunk(int chunkX, int chunkZ) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        if (this.scannedChunks.contains(chunkPos)) {
            for (Entry<BlockPos, Block> entry : this.foundBlocks.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.getX() >> 4 == chunkX && pos.getZ() >> 4 == chunkZ) {
                    this.foundBlocks.put(pos, entry.getValue());
                }
            }
        }
    }

    private void applyBlockUpdate(int x, int y, int z, boolean added, Block block) {
        BlockPos pos = new BlockPos(x, y, z);
        if (added) {
            this.foundBlocks.put(pos, block);
        } else {
            this.foundBlocks.remove(pos);
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        BlockPos neighbor = new BlockPos(x + dx, y + dy, z + dz);
                        Block neighborBlock = this.foundBlocks.get(neighbor);
                        if (neighborBlock != null) {
                            this.foundBlocks.put(neighbor, neighborBlock);
                        }
                    }
                }
            }
        }
    }
}
