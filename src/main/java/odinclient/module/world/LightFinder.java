package odinclient.module.world;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.render.world.WorldShapeRenderer;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LightFinder extends Module {
    private static final int RADIUS_CHUNKS = 8;
    private static final int DISCOVERY_INTERVAL_TICKS = 40;
    private static final long TOGGLE_CACHE_NANOS = 30000000000L;
    private final Map<Long, LightFinder.ChunkLightData> cellsByChunk = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<LightFinder.ScanTask> scanQueue =
            new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<LightFinder.MeshBuildTask> meshBuildQueue =
            new ConcurrentLinkedQueue<>();
    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> queuedMeshes = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scannerRunning = new AtomicBoolean(false);
    private final ExecutorService scanner =
            Executors.newSingleThreadExecutor(
                    task -> {
                        Thread thread = new Thread(task, "OdinClient-LightFinder-Scanner");
                        thread.setDaemon(true);
                        thread.setPriority(1);
                        return thread;
                    });
    private volatile Object scannedWorld;
    private volatile long scannerGeneration;
    private long cacheExpiresAtNanos;
    private int tickCounter;
    private int lastCenterX = -2147483648;
    private int lastCenterZ = -2147483648;
    private int lastRadius = -1;

    public LightFinder() {
        super(
                Categories.MAIN,
                "light-finder",
                "Renders all block-light sections below Y=0 for base finding.");
        this.title = "Light Finder";
    }

    @Override
    public void onActivate() {
        ClientWorld world = this.mc.world;
        long now = System.nanoTime();
        if (world == this.scannedWorld && now <= this.cacheExpiresAtNanos) {
            this.scannerGeneration++;
            this.scanQueue.clear();
            this.meshBuildQueue.clear();
            this.queuedChunks.clear();
            this.queuedMeshes.clear();
        } else {
            this.resetForWorld(world);
        }

        this.cacheExpiresAtNanos = 0L;
        if (world != null && this.mc.player != null) {
            int radius = RADIUS_CHUNKS;
            ChunkPos center = this.mc.player.getChunkPos();
            this.pruneOutsideRenderDistance(center, radius);
            this.enqueueVisibleChunks(world, center, radius);
            this.rememberCenter(center, radius);
            this.startScanner();
        }
    }

    @Override
    public void onDeactivate() {
        this.scannerGeneration++;
        this.scanQueue.clear();
        this.meshBuildQueue.clear();
        this.queuedChunks.clear();
        this.queuedMeshes.clear();
        if (this.mc.player != null) {
            this.pruneOutsideRenderDistance(this.mc.player.getChunkPos(), RADIUS_CHUNKS);
        }

        this.cacheExpiresAtNanos = System.nanoTime() + TOGGLE_CACHE_NANOS;
        LightFinderRenderer.clearQueued();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientWorld world = this.mc.world;
        if (world != null && this.mc.player != null) {
            if (this.scannedWorld != world) {
                this.resetForWorld(world);
            }

            ChunkPos center = this.mc.player.getChunkPos();
            int radius = RADIUS_CHUNKS;
            boolean centerChanged = center.x != this.lastCenterX || center.z != this.lastCenterZ;
            if (centerChanged) {
                this.tickCounter = 0;
                this.scannerGeneration++;
                this.scanQueue.clear();
                this.meshBuildQueue.clear();
                this.queuedChunks.clear();
                this.queuedMeshes.clear();
                this.releaseMeshesOutsideRenderDistance(center, radius);
                this.enqueueVisibleChunks(world, center, radius);
                this.rememberCenter(center, radius);
            } else if (++this.tickCounter >= DISCOVERY_INTERVAL_TICKS) {
                this.tickCounter = 0;
                this.enqueueVisibleChunks(world, center, radius);
            }

            this.startScanner();
        } else if (this.scannedWorld != null) {
            this.resetForWorld(null);
        }
    }

    @EventHandler
    private void onRender(WorldRenderEvent event) {
        LightFinderRenderer.clearQueued();
        if (this.mc.world != null && this.mc.player != null && !this.cellsByChunk.isEmpty()) {
            ChunkPos center = this.mc.player.getChunkPos();
            int radius = RADIUS_CHUNKS;

            for (int chunkX = center.x - radius; chunkX <= center.x + radius; chunkX++) {
                for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; chunkZ++) {
                    if (withinRenderDistance(chunkX, chunkZ, center, radius)) {
                        LightFinder.ChunkLightData data =
                                this.cellsByChunk.get(ChunkPos.toLong(chunkX, chunkZ));
                        if (data != null && !data.isEmpty() && this.isChunkVisible(data)) {
                            LightFinderRenderer.ChunkLightMesh mesh = data.getMesh();
                            if (mesh == null) {
                                this.enqueueMeshBuild(ChunkPos.toLong(chunkX, chunkZ), data);
                            } else {
                                LightFinderRenderer.queueMesh(mesh);
                            }
                        }
                    }
                }
            }

            this.startScanner();
        }
    }

    private boolean isChunkVisible(LightFinder.ChunkLightData data) {
        return WorldShapeRenderer.isBoxInFrustum(
                data.blockX,
                data.bottomY,
                data.blockZ,
                data.blockX + 16.0,
                0.0,
                data.blockZ + 16.0);
    }

    private static boolean withinRenderDistance(
            int chunkX, int chunkZ, ChunkPos center, int radius) {
        long dx = (long) chunkX - center.x;
        long dz = (long) chunkZ - center.z;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private void rememberCenter(ChunkPos center, int radius) {
        this.lastCenterX = center.x;
        this.lastCenterZ = center.z;
        this.lastRadius = radius;
    }

    private synchronized void resetForWorld(Object world) {
        this.scannerGeneration++;
        this.scannedWorld = world;
        this.cacheExpiresAtNanos = 0L;
        this.tickCounter = 0;
        this.lastCenterX = -2147483648;
        this.lastCenterZ = -2147483648;
        this.lastRadius = -1;

        for (LightFinder.ChunkLightData data : this.cellsByChunk.values()) {
            data.releaseMesh();
        }

        this.cellsByChunk.clear();
        this.scanQueue.clear();
        this.meshBuildQueue.clear();
        this.queuedChunks.clear();
        this.queuedMeshes.clear();
    }

    private void enqueueVisibleChunks(ClientWorld world, ChunkPos center, int radius) {
        if (world == this.scannedWorld) {
            long generation = this.scannerGeneration;
            this.enqueueChunk(world, center.x, center.z, generation);

            for (int ring = 1; ring <= radius; ring++) {
                int minX = center.x - ring;
                int maxX = center.x + ring;
                int minZ = center.z - ring;
                int maxZ = center.z + ring;

                for (int x = minX; x <= maxX; x++) {
                    if (withinRenderDistance(x, minZ, center, radius)) {
                        this.enqueueChunk(world, x, minZ, generation);
                    }

                    if (withinRenderDistance(x, maxZ, center, radius)) {
                        this.enqueueChunk(world, x, maxZ, generation);
                    }
                }

                for (int z = minZ + 1; z < maxZ; z++) {
                    if (withinRenderDistance(minX, z, center, radius)) {
                        this.enqueueChunk(world, minX, z, generation);
                    }

                    if (withinRenderDistance(maxX, z, center, radius)) {
                        this.enqueueChunk(world, maxX, z, generation);
                    }
                }
            }
        }
    }

    private void enqueueChunk(ClientWorld world, int chunkX, int chunkZ, long generation) {
        long key = ChunkPos.toLong(chunkX, chunkZ);
        if (!this.cellsByChunk.containsKey(key) && this.queuedChunks.add(key)) {
            this.scanQueue.offer(new LightFinder.ScanTask(world, chunkX, chunkZ, key, generation));
        }
    }

    private void startScanner() {
        if ((!this.scanQueue.isEmpty() || !this.meshBuildQueue.isEmpty())
                && this.scannerRunning.compareAndSet(false, true)) {
            this.scanner.execute(this::drainScanQueue);
        }
    }

    private void drainScanQueue() {
        try {
            while (this.isActive()) {
                LightFinder.ScanTask scanTask = this.scanQueue.poll();
                if (scanTask != null) {
                    try {
                        if (scanTask.generation == this.scannerGeneration
                                && scanTask.world == this.scannedWorld) {
                            this.scanChunk(scanTask);
                        }
                    } catch (RuntimeException e) {
                    } finally {
                        if (scanTask.generation == this.scannerGeneration) {
                            this.queuedChunks.remove(scanTask.key);
                        }
                    }
                } else {
                    LightFinder.MeshBuildTask meshTask = this.meshBuildQueue.poll();
                    if (meshTask != null) {
                        try {
                            try {
                                this.buildCachedMesh(meshTask);
                            } catch (RuntimeException e) {
                            }
                            continue;
                        } finally {
                            if (meshTask.generation == this.scannerGeneration) {
                                this.queuedMeshes.remove(meshTask.key);
                            }
                        }
                    }
                    break;
                }
            }
        } finally {
            this.scannerRunning.set(false);
            if (this.isActive() && (!this.scanQueue.isEmpty() || !this.meshBuildQueue.isEmpty())) {
                this.startScanner();
            }
        }
    }

    private void scanChunk(LightFinder.ScanTask task) {
        WorldChunk chunk =
                task.world.getChunkManager().getWorldChunk(task.chunkX, task.chunkZ, false);
        if (chunk != null && !chunk.isEmpty()) {
            int bottomY = task.world.getBottomY();
            int height = -bottomY;
            if (height > 0 && height <= 4095) {
                int originX = task.chunkX << 4;
                int originZ = task.chunkZ << 4;
                LightFinder.IntList cells = new LightFinder.IntList(1024);
                Mutable pos = new Mutable();

                for (int yIndex = 0; yIndex < height; yIndex++) {
                    if (task.generation != this.scannerGeneration || !this.isActive()) {
                        return;
                    }

                    int y = bottomY + yIndex;

                    for (int dz = 0; dz < 16; dz++) {
                        for (int dx = 0; dx < 16; dx++) {
                            pos.set(originX + dx, y, originZ + dz);
                            int light =
                                    Math.min(15, task.world.getLightLevel(LightType.BLOCK, pos));
                            if (light > 0) {
                                cells.add(pack(dx, yIndex, dz, light));
                            }
                        }
                    }
                }

                LightFinder.ChunkLightData data =
                        new LightFinder.ChunkLightData(
                                task.chunkX, task.chunkZ, bottomY, cells.toArray());
                if (!data.isEmpty()
                        && task.generation == this.scannerGeneration
                        && this.isActive()) {
                    data.setMesh(
                            LightFinderRenderer.buildMesh(
                                    data.blockX, data.bottomY, data.blockZ, data.packedCells));
                }

                this.publish(task, data);
            } else {
                this.publish(
                        task,
                        new LightFinder.ChunkLightData(
                                task.chunkX, task.chunkZ, bottomY, new int[0]));
            }
        }
    }

    private void publish(LightFinder.ScanTask task, LightFinder.ChunkLightData data) {
        if (task.generation == this.scannerGeneration
                && task.world == this.scannedWorld
                && this.isActive()) {
            this.cellsByChunk.put(task.key, data);
        } else {
            data.releaseMesh();
        }
    }

    private void enqueueMeshBuild(long key, LightFinder.ChunkLightData data) {
        if (!data.isEmpty() && data.getMesh() == null && this.queuedMeshes.add(key)) {
            this.meshBuildQueue.offer(
                    new LightFinder.MeshBuildTask(key, data, this.scannerGeneration));
        }
    }

    private void buildCachedMesh(LightFinder.MeshBuildTask task) {
        if (task.generation == this.scannerGeneration
                && task.data == this.cellsByChunk.get(task.key)
                && task.data.getMesh() == null
                && this.isActive()) {
            LightFinderRenderer.ChunkLightMesh mesh =
                    LightFinderRenderer.buildMesh(
                            task.data.blockX,
                            task.data.bottomY,
                            task.data.blockZ,
                            task.data.packedCells);
            if (task.generation == this.scannerGeneration
                    && task.data == this.cellsByChunk.get(task.key)
                    && this.isActive()) {
                task.data.setMesh(mesh);
            } else {
                LightFinderRenderer.scheduleRelease(mesh);
            }
        }
    }

    private static int pack(int x, int y, int z, int light) {
        return y << 12 | z << 8 | x << 4 | light;
    }

    private void pruneOutsideRenderDistance(ChunkPos center, int radius) {
        for (Long key : this.cellsByChunk.keySet()) {
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);
            if (!withinRenderDistance(chunkX, chunkZ, center, radius)) {
                LightFinder.ChunkLightData data = this.cellsByChunk.remove(key);
                if (data != null) {
                    data.releaseMesh();
                }
            }
        }
    }

    private void releaseMeshesOutsideRenderDistance(ChunkPos center, int radius) {
        for (Entry<Long, LightFinder.ChunkLightData> entry : this.cellsByChunk.entrySet()) {
            long key = entry.getKey();
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);
            if (!withinRenderDistance(chunkX, chunkZ, center, radius)) {
                entry.getValue().releaseMesh();
            }
        }
    }

    public static String[] appendClassName(String[] names) {
        for (String name : names) {
            if (name.equals("LightFinder")) {
                return names;
            }
        }

        String[] result = Arrays.copyOf(names, names.length + 1);
        result[result.length - 1] = "LightFinder";
        return result;
    }

    private static final class ChunkLightData {
        private final int blockX;
        private final int blockZ;
        private final int bottomY;
        private final int[] packedCells;
        private volatile LightFinderRenderer.ChunkLightMesh mesh;

        private ChunkLightData(int chunkX, int chunkZ, int bottomY, int[] packedCells) {
            this.blockX = chunkX << 4;
            this.blockZ = chunkZ << 4;
            this.bottomY = bottomY;
            this.packedCells = packedCells;
        }

        private boolean isEmpty() {
            return this.packedCells.length == 0;
        }

        private LightFinderRenderer.ChunkLightMesh getMesh() {
            return this.mesh;
        }

        private synchronized void setMesh(LightFinderRenderer.ChunkLightMesh newMesh) {
            if (newMesh != null) {
                if (this.mesh == null) {
                    this.mesh = newMesh;
                } else {
                    LightFinderRenderer.scheduleRelease(newMesh);
                }
            }
        }

        private synchronized void releaseMesh() {
            LightFinderRenderer.ChunkLightMesh old = this.mesh;
            this.mesh = null;
            LightFinderRenderer.scheduleRelease(old);
        }
    }

    private static final class IntList {
        private int[] values;
        private int size;

        private IntList(int capacity) {
            this.values = new int[Math.max(1, capacity)];
        }

        private void add(int value) {
            if (this.size == this.values.length) {
                this.values = Arrays.copyOf(this.values, this.values.length << 1);
            }

            this.values[this.size++] = value;
        }

        private int[] toArray() {
            return Arrays.copyOf(this.values, this.size);
        }
    }

    private record MeshBuildTask(long key, LightFinder.ChunkLightData data, long generation) {}

    private record ScanTask(ClientWorld world, int chunkX, int chunkZ, long key, long generation) {}

    public record LightCell(int x, int y, int z, int level) {}
}
