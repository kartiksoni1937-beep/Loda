package odinclient.module.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.manager.ModuleManager;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.render.world.GizmoRenderer;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AmethystESP extends Module {
    private static final int PRUNE_EXTRA_CHUNKS = 6;
    private static final int MAX_RENDER_TRACERS = 80;
    private static final int MAX_RENDER_BOXES_PER_CHUNK = 400;
    private static final double FIXED_CHUNK_Y = 55.0;
    private static final double CHUNK_THICKNESS = 0.05;
    private static final double TRACER_BEHIND_SPREAD = 2.75;
    private static final double TRACER_END_DISTANCE = 24.0;
    private static final double TRACER_START_DISTANCE = 150.0;
    private static AmethystESP INSTANCE;
    private final Setting<Integer> simulationDistance;
    private final Setting<Integer> threshold;
    private final Setting<Boolean> tracers;
    private final Setting<Boolean> chunkMarkers;
    private final Setting<Integer> chunkAlpha;
    public final Map<ChunkPos, Set<BlockPos>> foundPositions = new ConcurrentHashMap<>();
    public final Set<ChunkPos> markedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> notifiedChunks = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingQueue<long[]> scanQueue = new LinkedBlockingQueue<>();
    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scannerRunning = new AtomicBoolean(false);
    private final ExecutorService scanExecutor =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "amethyst-esp-scan");
                        thread.setDaemon(true);
                        thread.setPriority(4);
                        return thread;
                    });
    private int tickCounter;
    private static final int[][] NEIGHBORS =
            new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final Color ESP_COLOR = new Color(180, 100, 255);
    private static final Color CHUNK_COLOR = new Color(180, 100, 255);
    private final LinkedBlockingQueue<AmethystESP.ToastRequest> pendingToasts;

    public AmethystESP(Category category) {
        super(
                category,
                "amethyst-esp",
                "Finds amethyst geodes by scanning for their distinctive hidden light pattern.");
        this.title = "Amethyst ESP";
        SettingGroup generalGroup = this.settings.getGeneral();
        this.simulationDistance =
                generalGroup.add(
                        new IntSetting.Builder()
                                .name("simulation-distance")
                                .description("Chunk radius around the player to scan.")
                                .defaultValue(8)
                                .range(1, 32)
                                .sliderRange(1, 32)
                                .build());
        this.threshold =
                new IntSetting.Builder()
                        .name("threshold")
                        .description(
                                "Minimum exposed zero-light air hits required to mark an amethyst"
                                    + " geode chunk.")
                        .defaultValue(12)
                        .range(1, 100)
                        .sliderRange(1, 100)
                        .build();
        this.tracers =
                generalGroup.add(
                        new BooleanSetting.Builder()
                                .name("tracers")
                                .description("Draw tracers to detected positions.")
                                .defaultValue(true)
                                .build());
        this.chunkMarkers =
                new BooleanSetting.Builder()
                        .name("chunk-markers")
                        .description("Render the purple chunk marker above detected geodes.")
                        .defaultValue(false)
                        .build();
        this.chunkAlpha =
                new IntSetting.Builder()
                        .name("alpha")
                        .description("Opacity of the purple detected-chunk overlay.")
                        .defaultValue(100)
                        .range(0, 255)
                        .sliderRange(0, 255)
                        .build();
        INSTANCE = this;
        this.pendingToasts = new LinkedBlockingQueue<>();
    }

    public static void install() {
        ModuleManager.get().register(new AmethystESP(Categories.MAIN));
    }

    public static AmethystESP instance() {
        return INSTANCE;
    }

    @Override
    public void onActivate() {
        this.foundPositions.clear();
        this.markedChunks.clear();
        this.notifiedChunks.clear();
        this.scanQueue.clear();
        this.queuedChunks.clear();
        this.pendingToasts.clear();
        this.tickCounter = 0;
        this.enqueueFullScan();
    }

    @Override
    public void onDeactivate() {
        this.foundPositions.clear();
        this.markedChunks.clear();
        this.notifiedChunks.clear();
        this.scanQueue.clear();
        this.queuedChunks.clear();
        this.pendingToasts.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        AmethystESP.ToastRequest toast;
        while ((toast = this.pendingToasts.poll()) != null) {
            this.showToast(toast.foundCount, toast.threshold);
        }

        if (this.mc.world != null && this.mc.player != null) {
            this.pruneFarClusters();
            if (++this.tickCounter % 20 == 0) {
                ChunkPos playerChunk = this.mc.player.getChunkPos();
                int radius = this.simulationDistance.getValue();

                int minChunkX = playerChunk.x - radius;
                int maxChunkX = playerChunk.x + radius;
                int minChunkZ = playerChunk.z - radius;
                int maxChunkZ = playerChunk.z + radius;

                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        this.enqueueChunk(chunkX, chunkZ);
                    }
                }

                this.startScanner();
            }
        }
    }

    private void enqueueFullScan() {
        if (this.mc.world != null && this.mc.player != null) {
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            int radius = this.simulationDistance.getValue();

            int minChunkX = playerChunk.x - radius;
            int maxChunkX = playerChunk.x + radius;
            int minChunkZ = playerChunk.z - radius;
            int maxChunkZ = playerChunk.z + radius;

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    this.enqueueChunk(chunkX, chunkZ);
                }
            }

            this.startScanner();
        }
    }

    public void enqueueChunk(int chunkX, int chunkZ) {
        if (this.queuedChunks.add(ChunkPos.toLong(chunkX, chunkZ))) {
            this.scanQueue.offer(new long[] {chunkX, chunkZ});
        }
    }

    private void startScanner() {
        if (!this.scannerRunning.get() && !this.scanQueue.isEmpty()) {
            if (this.scannerRunning.compareAndSet(false, true)) {
                this.scanExecutor.submit(this::drainScanQueue);
            }
        }
    }

    private void drainScanQueue() {
        try {
            long[] entry;
            try {
                while ((entry = this.scanQueue.poll()) != null && this.isActive()) {
                    int chunkX = (int) entry[0];
                    int chunkZ = (int) entry[1];
                    this.queuedChunks.remove(ChunkPos.toLong(chunkX, chunkZ));
                    ClientWorld world = this.mc.world;
                    if (world != null) {
                        WorldChunk chunk =
                                world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
                        if (chunk != null) {
                            this.scanChunk(world, chunk);
                        }
                    }
                }
            } catch (RuntimeException ignored) {
            }
        } finally {
            this.scannerRunning.set(false);
            if (this.isActive() && !this.scanQueue.isEmpty()) {
                this.startScanner();
            }
        }
    }

    private void scanChunk(World world, WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minimumHits = this.threshold.getValue();
        BlockPos geodeCenter = this.findGeodeCenter(world, chunk);
        if (geodeCenter == null) {
            this.foundPositions.remove(chunkPos);
            this.markedChunks.remove(chunkPos);
            this.notifiedChunks.remove(chunkPos);
            AmethystScanner.scanChunk(this, world, chunk);
            return;
        }

        Set<BlockPos> hits = new HashSet<>();
        collectExposedAir(world, geodeCenter, hits);

        if (hits.isEmpty()) {
            this.foundPositions.remove(chunkPos);
            this.markedChunks.remove(chunkPos);
            this.notifiedChunks.remove(chunkPos);
        } else {
            this.foundPositions.put(chunkPos, hits);
            if (hits.size() >= minimumHits) {
                this.markedChunks.add(chunkPos);
                if (this.notifiedChunks.add(chunkPos)) {
                    this.pendingToasts.offer(
                            new AmethystESP.ToastRequest(hits.size(), minimumHits));
                }
            } else {
                this.markedChunks.remove(chunkPos);
                this.notifiedChunks.remove(chunkPos);
            }
        }

        AmethystScanner.scanChunk(this, world, chunk);
    }

    private static void collectExposedAir(World world, BlockPos geodeCenter, Set<BlockPos> hits) {
        Mutable pos = new Mutable();
        Mutable neighborPos = new Mutable();
        Mutable verifyPos = new Mutable();

        for (int offsetX = -8; offsetX <= 8; offsetX++) {
            for (int offsetY = -8; offsetY <= 8; offsetY++) {
                for (int offsetZ = -8; offsetZ <= 8; offsetZ++) {
                    pos.set(
                            geodeCenter.getX() + offsetX,
                            geodeCenter.getY() + offsetY,
                            geodeCenter.getZ() + offsetZ);
                    BlockState state = world.getBlockState(pos);
                    if ((state.isOf(Blocks.AIR) || state.isOf(Blocks.AMETHYST_CLUSTER))
                            && world.getLightLevel(LightType.BLOCK, pos) == 0) {
                        boolean brightNeighbor = false;
                        int dimX = Integer.MIN_VALUE;
                        int dimY = 0;
                        int dimZ = 0;

                        for (int[] direction : NEIGHBORS) {
                            neighborPos.set(
                                    pos.getX() + direction[0],
                                    pos.getY() + direction[1],
                                    pos.getZ() + direction[2]);
                            int neighborLight = world.getLightLevel(LightType.BLOCK, neighborPos);
                            if (neighborLight > 4) {
                                brightNeighbor = true;
                                break;
                            }

                            if (dimX == Integer.MIN_VALUE
                                    && neighborLight == 4
                                    && world.getBlockState(neighborPos).isOf(Blocks.AIR)) {
                                dimX = neighborPos.getX();
                                dimY = neighborPos.getY();
                                dimZ = neighborPos.getZ();
                            }
                        }

                        if (!brightNeighbor && dimX != Integer.MIN_VALUE) {
                            boolean brightAroundDim = false;

                            for (int[] direction : NEIGHBORS) {
                                verifyPos.set(
                                        dimX + direction[0],
                                        dimY + direction[1],
                                        dimZ + direction[2]);
                                if (world.getLightLevel(LightType.BLOCK, verifyPos) > 4) {
                                    brightAroundDim = true;
                                    break;
                                }
                            }

                            if (!brightAroundDim) {
                                hits.add(pos.toImmutable());
                                if (hits.size() >= 9000) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public int scanChunkAndCount(World world, WorldChunk chunk) {
        this.scanChunk(world, chunk);
        return this.foundPositions.getOrDefault(chunk.getPos(), Set.of()).size();
    }

    private void pruneFarClusters() {
        if (this.mc.player != null) {
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            int maxDistance = this.simulationDistance.getValue() + 6;

            for (ChunkPos chunkPos : List.copyOf(this.foundPositions.keySet())) {
                int distance =
                        Math.max(
                                Math.abs(playerChunk.x - chunkPos.x),
                                Math.abs(playerChunk.z - chunkPos.z));
                if (distance > maxDistance) {
                    this.foundPositions.remove(chunkPos);
                    this.markedChunks.remove(chunkPos);
                    this.notifiedChunks.remove(chunkPos);
                }
            }
        }
    }

    private void removeChunk(ChunkPos chunkPos) {
        this.foundPositions.remove(chunkPos);
        this.markedChunks.remove(chunkPos);
        this.notifiedChunks.remove(chunkPos);
    }

    @EventHandler
    private void onRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.mc.player != null) {
            if (!this.foundPositions.isEmpty()) {
                Camera camera = this.mc.gameRenderer.getCamera();
                Vec3d cameraPos = camera.getCameraPos();
                Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
                Vec3d right = Vec3d.fromPolar(0.0F, camera.getYaw() + 90.0F).normalize();
                Vec3d up = right.crossProduct(forward).normalize();
                Vec3d tracerStart = cameraPos.add(forward.multiply(150.0));
                Color boxColor = this.withAlpha(ESP_COLOR, 180);
                Color chunkColor = this.withAlpha(CHUNK_COLOR, this.chunkAlpha.getValue());
                Color tracerColor = this.withAlpha(ESP_COLOR, 220);
                List<AmethystESP.BlockPoint> points = new ArrayList<>();

                for (Entry<ChunkPos, Set<BlockPos>> entry : this.foundPositions.entrySet()) {
                    ChunkPos chunkPos = entry.getKey();
                    Set<BlockPos> positions = entry.getValue();
                    if (positions != null && !positions.isEmpty()) {
                        if (this.markedChunks.contains(chunkPos) && this.chunkMarkers.getValue()) {
                            event.renderer.stableBoxCompat(
                                    chunkPos.getStartX(),
                                    55.0,
                                    chunkPos.getStartZ(),
                                    chunkPos.getEndX() + 1,
                                    55.05,
                                    chunkPos.getEndZ() + 1,
                                    chunkColor,
                                    chunkColor,
                                    BoxRenderMode.Both,
                                    0);
                        }

                        int count = 0;

                        for (BlockPos blockPos : positions) {
                            if (count++ >= 400) {
                                break;
                            }

                            points.add(
                                    new AmethystESP.BlockPoint(
                                            blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                        }
                    }
                }

                for (AmethystESP.BlockPoint point : points) {
                    event.renderer.stableBoxCompat(
                            point.x() + 0.0625,
                            point.y(),
                            point.z() + 0.0625,
                            point.x() + 0.9375,
                            point.y() + 0.875,
                            point.z() + 0.9375,
                            boxColor,
                            boxColor,
                            BoxRenderMode.Sides,
                            0);
                }

                if (this.tracers.getValue()) {
                    for (AmethystESP.BlockPoint point : points) {
                        Vec3d relative =
                                new Vec3d(
                                        point.x() + 0.5 - cameraPos.x,
                                        point.y() + 0.5 - cameraPos.y,
                                        point.z() + 0.5 - cameraPos.z);
                        Vec3d tracerEnd =
                                cameraPos.add(
                                        this.getSpreadTracerEnd(
                                                relative, forward, right, up, 24.0, 2.75));
                        renderLineAdapter(
                                event.renderer,
                                tracerStart.x,
                                tracerStart.y,
                                tracerStart.z,
                                tracerEnd.x,
                                tracerEnd.y,
                                tracerEnd.z,
                                tracerColor);
                    }
                }
            }
        }
    }

    private Vec3d getSpreadTracerEnd(
            Vec3d relative,
            Vec3d forward,
            Vec3d right,
            Vec3d up,
            double endDistance,
            double spread) {
        Vec3d direction = relative.normalize();
        double forwardDot = direction.dotProduct(forward);
        if (forwardDot >= 0.0) {
            return direction.multiply(endDistance);
        } else {
            double rightOffset = direction.dotProduct(right) * spread;
            double upOffset = direction.dotProduct(up) * spread;
            return forward.multiply(endDistance)
                    .add(right.multiply(rightOffset))
                    .add(up.multiply(upOffset));
        }
    }

    private void clearState() {
        this.foundPositions.clear();
        this.markedChunks.clear();
        this.notifiedChunks.clear();
        this.scanQueue.clear();
        this.queuedChunks.clear();
        this.tickCounter = 0;
        AmethystCaveScanner.load();
    }

    private BlockPos findGeodeCenter(World world, WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long sumX = 0L;
        long sumY = 0L;
        long sumZ = 0L;
        int count = 0;
        Mutable pos = new Mutable();

        for (int y = -58; y <= 30; y++) {
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

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.r, color.g, color.b, Math.min(255, alpha));
    }

    private void showToast(int foundCount, int threshold) {}

    private static void renderLineAdapter(
            GizmoRenderer renderer,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            Color color) {
        renderer.blockEspHybridTracer(new Vec3d(endX, endY, endZ), color, 1.0F);
    }

    private record BlockPoint(double x, double y, double z) {}

    private static final class ToastRequest {
        private final int foundCount;
        private final int threshold;

        private ToastRequest(int foundCount, int threshold) {
            this.foundCount = foundCount;
            this.threshold = threshold;
        }
    }
}
