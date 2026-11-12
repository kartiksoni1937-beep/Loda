package odinclient.module.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.event.ChunkLoadEvent;
import odinclient.event.EventHandler;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.VisibilityCondition;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.IntSetting;
import odinclient.util.ScannerExecutor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SusChunkFinder extends Module {
    private static final int SCAN_BATCH_SIZE = 4;
    private static final int MAX_VIEW_RADIUS = 32;
    private static final int CHUNK_SELECT_PERCENT = 90;
    private static final long RENDER_INTERVAL_NANOS = 20000000L;
    private static final long SCAN_SETTLE_NANOS = 150000000L;
    private static final long IDLE_SETTLE_NANOS = 750000000L;
    private static final int KELP_MASK = 1;
    private static final int CAVE_VINES_MASK = 2;
    private static final int VINES_MASK = 4;
    private static final int AMETHYST_MASK = 8;
    private static final int BAMBOO_MASK = 16;
    private static final int BEE_NEST_MASK = 32;
    private static final int ROTATED_DEEPSLATE_MASK = 64;
    private static final int GEODE_MIN_Y = -64;
    private static final int GEODE_MAX_Y = 64;
    private static final int GEODE_SEARCH_RADIUS = 8;
    private static final int MIN_GEODE_HITS = 6;
    private static final int MAX_MARKER_RADIUS = 32;
    private static final int MAX_GEODE_BLOCKS = 9000;
    private static final int[][] DIRECTION_OFFSETS =
            new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int AREA_HALF_SIZE = 4;
    private static final int[] SENSITIVITY_THRESHOLDS =
            new int[] {0, 3, 15, 24, 33, 42, 50, 61, 73, 86, 96};
    private static final Block KELP = block("kelp");
    private static final Block KELP_PLANT = block("kelp_plant");
    private static final Block CAVE_VINES = block("cave_vines");
    private static final Block CAVE_VINES_PLANT = block("cave_vines_plant");
    private static final Block VINE = block("vine");
    private static final Block BAMBOO = block("bamboo");
    private static final Block BEE_NEST = block("bee_nest");
    private static final Block DEEPSLATE = block("deepslate");
    private static final SusChunkFinder.PlantStats EMPTY_STATS =
            new SusChunkFinder.PlantStats(0, 0, 0, 0);
    private static final SusChunkFinder.GrowthEvidence EMPTY_EVIDENCE =
            new SusChunkFinder.GrowthEvidence(
                    EMPTY_STATS, EMPTY_STATS, EMPTY_STATS, EMPTY_STATS, 0, 0);
    private final Setting<Boolean> kelpEnabled;
    private final Setting<Integer> kelpSensitivity;
    private final Setting<Boolean> caveVinesEnabled;
    private final Setting<Integer> caveVinesSensitivity;
    private final Setting<Boolean> vinesEnabled;
    private final Setting<Integer> vinesSensitivity;
    private final Setting<Boolean> amethystEnabled;
    private final Setting<Integer> amethystSensitivity;
    private final Setting<Boolean> bambooEnabled;
    private final Setting<Integer> bambooSensitivity;
    private final Setting<Boolean> beeNestEnabled;
    private final Setting<Integer> beeNestSensitivity;
    private final Setting<Boolean> rotatedDeepslateEnabled;
    private final Setting<Integer> rotatedDeepslateSensitivity;
    private final Setting<EntityColor> markerColor;
    private final ConcurrentHashMap<ChunkPos, Integer> suspiciousScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Integer> naturalAmethystCounts =
            new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, Integer> chunkSuspiciousEvidence =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Boolean> chunkNaturalAmethyst =
            new ConcurrentHashMap<>();
    private final Set<ChunkPos> grownChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> candidateChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, Long> growthWeightScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Integer> growthFaceScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Long> chunkGrowthWeights = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Integer> chunkGrowthFaces = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Integer> chunkMaturity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, SusChunkFinder.GrowthEvidence> chunkEvidence =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, SusChunkFinder.PlantStats> kelpStats =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, SusChunkFinder.PlantStats> caveVineStats =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, SusChunkFinder.PlantStats> vineStats =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, SusChunkFinder.PlantStats> bambooStats =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Integer> beeNestScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Integer> rotatedDeepslateScores =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Integer> chunkScanMasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, SusChunkFinder.ChunkScanTask> pendingScans =
            new ConcurrentHashMap<>();
    private final Set<ChunkPos> queuedChunks = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingQueue<ChunkPos> scanQueue = new LinkedBlockingQueue<>();
    private final ThreadLocal<SusChunkFinder.ScanBuffers> scanBuffers =
            ThreadLocal.withInitial(SusChunkFinder.ScanBuffers::new);
    private volatile int maturityThreshold;
    private volatile long dataVersion;
    private volatile long lastScanNanos;
    private volatile List<SusChunkFinder.ChunkRect> markerAreas = List.of();
    private List<SusChunkFinder.ChunkRect> lastRenderedAreas = List.of();
    private List<ChunkAreaMerger.ChunkArea> clippedAreas = List.of();
    private ChunkPos lastPlayerChunk;
    private volatile List<SusChunkFinder.ChunkRect> stableMarkerAreas = List.of();
    private volatile long lastComputedVersion = -1L;
    private volatile long lastStableVersion = -1L;
    private long nextComputeNanos;
    private volatile int lastSettingsHash = Integer.MIN_VALUE;
    private ExecutorService scanExecutor;
    private ExecutorService markerExecutor;
    private final AtomicBoolean scanRunning = new AtomicBoolean();
    private final AtomicBoolean markerRunning = new AtomicBoolean();
    private volatile long generation;
    private int lastScanMask = -1;
    private volatile long lastChunkEventNanos;
    private volatile boolean rescanDone;
    private volatile String lastServerAddress;

    public SusChunkFinder(Category category) {
        super(
                category,
                "sus-chunk-finder",
                "Finds chunks with unusually mature natural growth or player-altered blocks.");
        this.title = "Sus Chunk Finder";
        SettingGroup general = this.settings.getGeneral();
        this.kelpEnabled =
                general.add(
                        new BooleanSetting.Builder()
                                .name("kelp")
                                .description(
                                        "Detect unusually tall kelp that indicates prolonged chunk"
                                            + " activity.")
                                .defaultValue(false)
                                .build());
        this.kelpSensitivity =
                general.add(
                        createSensitivitySetting(
                                "kelp-sensitivity",
                                "Required kelp maturity: 1 is just above natural average, 10 is"
                                    + " extreme.",
                                () -> this.kelpEnabled.getValue()));
        this.caveVinesEnabled =
                general.add(
                        new BooleanSetting.Builder()
                                .name("cave-vines")
                                .description("Detect unusually long cave-vine growth.")
                                .defaultValue(false)
                                .build());
        this.caveVinesSensitivity =
                general.add(
                        createSensitivitySetting(
                                "cave-vines-sensitivity",
                                "Required cave-vine maturity for this detector.",
                                () -> this.caveVinesEnabled.getValue()));
        this.vinesEnabled =
                general.add(
                        new BooleanSetting.Builder()
                                .name("vines")
                                .description("Detect unusually long vertical vine growth.")
                                .defaultValue(false)
                                .build());
        this.vinesSensitivity =
                general.add(
                        createSensitivitySetting(
                                "vines-sensitivity",
                                "Required vine maturity for this detector.",
                                () -> this.vinesEnabled.getValue()));
        this.amethystEnabled =
                general.add(
                        new BooleanSetting.Builder()
                                .name("amethyst")
                                .description(
                                        "Use the existing amethyst maturity and cluster detector.")
                                .defaultValue(true)
                                .build());
        this.amethystSensitivity =
                general.add(
                        createSensitivitySetting(
                                "amethyst-sensitivity",
                                "Required amethyst evidence for the existing smart detector.",
                                () -> this.amethystEnabled.getValue()));
        this.bambooEnabled =
                general.add(
                        new BooleanSetting.Builder()
                                .name("bamboo")
                                .description("Detect unusually tall mature bamboo.")
                                .defaultValue(false)
                                .build());
        this.bambooSensitivity =
                general.add(
                        createSensitivitySetting(
                                "bamboo-sensitivity",
                                "Required bamboo maturity for this detector.",
                                () -> this.bambooEnabled.getValue()));
        this.beeNestEnabled =
                general.add(
                        new BooleanSetting.Builder()
                                .name("bee-nest")
                                .description(
                                        "Detect bee nests whose honey level shows prolonged"
                                            + " activity.")
                                .defaultValue(false)
                                .build());
        this.beeNestSensitivity =
                general.add(
                        createSensitivitySetting(
                                "bee-nest-sensitivity",
                                "Required honey level: 1 is above average, 10 requires a full"
                                    + " nest.",
                                () -> this.beeNestEnabled.getValue()));
        this.rotatedDeepslateEnabled =
                general.add(
                        new BooleanSetting.Builder()
                                .name("rotated-deepslate")
                                .description("Detect deepslate placed on a non-natural axis.")
                                .defaultValue(false)
                                .build());
        this.rotatedDeepslateSensitivity =
                general.add(
                        createSensitivitySetting(
                                "rotated-deepslate-sensitivity",
                                "Required amount of sideways deepslate for this detector.",
                                () -> this.rotatedDeepslateEnabled.getValue()));
        this.markerColor =
                general.add(
                        new ColorSetting.Builder()
                                .name("marker-color")
                                .description(
                                        "Color and opacity of the merged suspicious-area marking.")
                                .defaultValue(new EntityColor(255, 0, 0, 80))
                                .build());
    }

    @Override
    public void onActivate() {
        this.lastServerAddress = this.getServerAddress();
        this.resetState(false);
        this.startExecutors();
        this.rescanLoadedChunks(this.getScanMask());
    }

    @Override
    public void onDeactivate() {
        this.stopExecutors();
        this.resetState(false);
        this.lastServerAddress = null;
    }

    @EventHandler
    private void onWorldJoin(WorldJoinEvent event) {
        String address = this.getServerAddress();
        boolean sameServer = address != null && address.equals(this.lastServerAddress);
        this.resetState(sameServer);
        this.lastServerAddress = address;
        if (this.scanExecutor == null || this.scanExecutor.isShutdown()) {
            this.startExecutors();
        }

        this.rescanLoadedChunks(this.getScanMask());
    }

    @EventHandler
    private void onWorldLeave(WorldLeaveEvent event) {
        this.resetState(this.lastServerAddress != null);
    }

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        if (this.mc.world != null && this.scanExecutor != null && !this.scanExecutor.isShutdown()) {
            int scanMask = this.getScanMask();
            if (scanMask != 0) {
                this.lastChunkEventNanos = System.nanoTime();
                this.rescanDone = false;
                ClientWorld world = this.mc.world;
                String brand =
                        this.mc.getNetworkHandler() == null
                                ? null
                                : this.mc.getNetworkHandler().getBrand();
                this.queueChunkScan(
                        event.getChunk(),
                        world,
                        scanMask,
                        brand != null && brand.contains("DonutFolia"));
            }
        }
    }

    private void queueChunkScan(WorldChunk chunk, World world, int scanMask, boolean donutFolia) {
        if (chunk != null && scanMask != 0 && world != null) {
            ChunkPos chunkPos = chunk.getPos();
            this.pendingScans.put(
                    chunkPos,
                    new SusChunkFinder.ChunkScanTask(
                            chunk, world, scanMask, donutFolia, this.generation, 0));
            if (this.queuedChunks.add(chunkPos)) {
                this.scanQueue.offer(chunkPos);
            }

            this.scheduleScanDrain();
        }
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.world != null) {
            int scanMask = this.getScanMask();
            if (scanMask != this.lastScanMask) {
                this.lastChunkEventNanos = System.nanoTime();
                this.rescanDone = false;
                this.rescanLoadedChunks(scanMask);
            }

            int settingsHash = this.computeSettingsHash();
            long version = this.dataVersion;
            long now = System.nanoTime();
            this.markScanSettled(scanMask, now);
            boolean settingsChanged = settingsHash != this.lastSettingsHash;
            boolean versionChanged = version != this.lastComputedVersion;
            boolean settled =
                    this.rescanDone
                            && this.scanQueue.isEmpty()
                            && this.pendingScans.isEmpty()
                            && this.queuedChunks.isEmpty()
                            && now - this.lastChunkEventNanos >= IDLE_SETTLE_NANOS
                            && now - this.lastScanNanos >= SCAN_SETTLE_NANOS
                            && version != this.lastStableVersion;
            if ((settingsChanged || (versionChanged || settled) && now >= this.nextComputeNanos)
                    && this.markerExecutor != null
                    && !this.markerExecutor.isShutdown()
                    && this.markerRunning.compareAndSet(false, true)) {
                this.nextComputeNanos = now + RENDER_INTERVAL_NANOS;
                long currentGeneration = this.generation;

                try {
                    this.markerExecutor.execute(
                            () -> {
                                try {
                                    this.recomputeMarkers(version, settingsHash, currentGeneration);
                                } finally {
                                    this.markerRunning.set(false);
                                }
                            });
                } catch (RuntimeException e) {
                    this.markerRunning.set(false);
                }
            }

            this.renderMarkers(event);
        }
    }

    private void recomputeMarkers(long version, int settingsHash, long scanGeneration) {
        HashMap<ChunkPos, Long> scores = new HashMap<>();
        HashSet<ChunkPos> selected = new HashSet<>();
        if (this.amethystEnabled.getValue()) {
            this.addAmethystScores(scores, selected);
        }

        this.addPlantScores(
                scores,
                selected,
                this.kelpStats,
                this.kelpEnabled.getValue(),
                this.kelpSensitivity.getValue(),
                0,
                0,
                0);
        this.addPlantScores(
                scores,
                selected,
                this.caveVineStats,
                this.caveVinesEnabled.getValue(),
                this.caveVinesSensitivity.getValue(),
                3,
                9,
                5);
        this.addPlantScores(
                scores,
                selected,
                this.vineStats,
                this.vinesEnabled.getValue(),
                this.vinesSensitivity.getValue(),
                0,
                0,
                0);
        this.addPlantScores(
                scores,
                selected,
                this.bambooStats,
                this.bambooEnabled.getValue(),
                this.bambooSensitivity.getValue(),
                29,
                42,
                25);
        this.addCountScores(
                scores,
                selected,
                this.beeNestScores,
                this.beeNestEnabled.getValue(),
                this.beeNestSensitivity.getValue());
        this.addCountScores(
                scores,
                selected,
                this.rotatedDeepslateScores,
                this.rotatedDeepslateEnabled.getValue(),
                this.rotatedDeepslateSensitivity.getValue());
        selected.addAll(selectSpacedChunks(scores));
        ArrayList<ChunkPos> selectedList = new ArrayList<>(selected);
        List<SusChunkFinder.ChunkRect> rects =
                selectedList.isEmpty()
                        ? List.of()
                        : List.copyOf(mergeChunksIntoRects(selectedList));
        if (scanGeneration == this.generation) {
            long lastScan = this.lastScanNanos;
            boolean stable =
                    this.scanQueue.isEmpty()
                            && this.pendingScans.isEmpty()
                            && this.queuedChunks.isEmpty()
                            && this.rescanDone
                            && (this.lastChunkEventNanos == 0L
                                    || System.nanoTime() - this.lastChunkEventNanos
                                            >= IDLE_SETTLE_NANOS)
                            && (lastScan == 0L
                                    || System.nanoTime() - lastScan >= SCAN_SETTLE_NANOS);
            if (stable) {
                this.stableMarkerAreas = mergeMarkerAreas(this.stableMarkerAreas, rects);
                this.markerAreas = this.stableMarkerAreas;
                this.lastStableVersion = version;
            } else {
                this.markerAreas = mergeMarkerAreas(this.stableMarkerAreas, rects);
            }

            this.lastComputedVersion = version;
            this.lastSettingsHash = settingsHash;
        }
    }

    private static List<SusChunkFinder.ChunkRect> mergeMarkerAreas(
            List<SusChunkFinder.ChunkRect> stable, List<SusChunkFinder.ChunkRect> fresh) {
        if (stable.isEmpty()) {
            return fresh;
        } else if (fresh.isEmpty()) {
            return stable;
        } else {
            ArrayList<SusChunkFinder.ChunkRect> merged = new ArrayList<>(stable);

            for (SusChunkFinder.ChunkRect rect : fresh) {
                boolean overlaps = false;

                for (SusChunkFinder.ChunkRect existing : stable) {
                    if (rectsOverlap(existing, rect)) {
                        overlaps = true;
                        break;
                    }
                }

                if (!overlaps) {
                    merged.add(rect);
                }
            }

            return List.copyOf(merged);
        }
    }

    private static boolean rectsOverlap(
            SusChunkFinder.ChunkRect first, SusChunkFinder.ChunkRect second) {
        return first.minimumX < second.maximumXExclusive
                && first.maximumXExclusive > second.minimumX
                && first.minimumZ < second.maximumZExclusive
                && first.maximumZExclusive > second.minimumZ;
    }

    private void renderMarkers(WorldRenderEvent event) {
        List<SusChunkFinder.ChunkRect> areas = this.markerAreas;
        if (!areas.isEmpty() && this.mc.player != null) {
            EntityColor color = this.markerColor.getValue();
            int argb = color.toArgb();
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            if (this.lastRenderedAreas != areas
                    || this.lastPlayerChunk == null
                    || this.lastPlayerChunk.x != playerChunk.x
                    || this.lastPlayerChunk.z != playerChunk.z) {
                ArrayList<ChunkAreaMerger.ChunkArea> clipped = new ArrayList<>();

                for (SusChunkFinder.ChunkRect rect : areas) {
                    ChunkAreaMerger.ChunkArea area =
                            new ChunkAreaMerger.ChunkArea(
                                            rect.minimumX,
                                            rect.minimumZ,
                                            rect.maximumXExclusive,
                                            rect.maximumZExclusive)
                                    .clip(playerChunk.x, playerChunk.z, MAX_MARKER_RADIUS);
                    if (area != null) {
                        clipped.add(area);
                    }
                }

                this.clippedAreas = List.copyOf(clipped);
                this.lastRenderedAreas = areas;
                this.lastPlayerChunk = playerChunk;
            }

            ChunkMarkerRenderer.setChunkAreas(this.clippedAreas, argb);
        }
    }

    private int computeSettingsHash() {
        int hash = 1;
        hash = 31 * hash + Boolean.hashCode(this.kelpEnabled.getValue());
        hash = 31 * hash + this.kelpSensitivity.getValue();
        hash = 31 * hash + Boolean.hashCode(this.caveVinesEnabled.getValue());
        hash = 31 * hash + this.caveVinesSensitivity.getValue();
        hash = 31 * hash + Boolean.hashCode(this.vinesEnabled.getValue());
        hash = 31 * hash + this.vinesSensitivity.getValue();
        hash = 31 * hash + Boolean.hashCode(this.amethystEnabled.getValue());
        hash = 31 * hash + this.amethystSensitivity.getValue();
        hash = 31 * hash + Boolean.hashCode(this.bambooEnabled.getValue());
        hash = 31 * hash + this.bambooSensitivity.getValue();
        hash = 31 * hash + Boolean.hashCode(this.beeNestEnabled.getValue());
        hash = 31 * hash + this.beeNestSensitivity.getValue();
        hash = 31 * hash + Boolean.hashCode(this.rotatedDeepslateEnabled.getValue());
        return 31 * hash + this.rotatedDeepslateSensitivity.getValue();
    }

    private void rescanLoadedChunks(int scanMask) {
        ClientWorld world = this.mc.world;
        if (world != null
                && this.mc.player != null
                && this.scanExecutor != null
                && !this.scanExecutor.isShutdown()) {
            this.lastScanMask = scanMask;
            if (scanMask != 0) {
                ChunkPos playerChunk = this.mc.player.getChunkPos();
                int radius =
                        Math.max(
                                2,
                                Math.min(
                                        MAX_VIEW_RADIUS,
                                        this.mc.options.getViewDistance().getValue() + 1));
                String brand =
                        this.mc.getNetworkHandler() == null
                                ? null
                                : this.mc.getNetworkHandler().getBrand();
                boolean donutFolia = brand != null && brand.contains("DonutFolia");

                for (int ring = 0; ring <= radius; ring++) {
                    for (int i = -ring; i <= ring; i++) {
                        this.queueChunkAt(
                                playerChunk.x + i,
                                playerChunk.z - ring,
                                world,
                                scanMask,
                                donutFolia);
                        if (ring != 0) {
                            this.queueChunkAt(
                                    playerChunk.x + i,
                                    playerChunk.z + ring,
                                    world,
                                    scanMask,
                                    donutFolia);
                        }
                    }

                    for (int j = -ring + 1; j < ring; j++) {
                        this.queueChunkAt(
                                playerChunk.x - ring,
                                playerChunk.z + j,
                                world,
                                scanMask,
                                donutFolia);
                        if (ring != 0) {
                            this.queueChunkAt(
                                    playerChunk.x + ring,
                                    playerChunk.z + j,
                                    world,
                                    scanMask,
                                    donutFolia);
                        }
                    }
                }
            }
        }
    }

    private void queueChunkAt(
            int chunkX, int chunkZ, World world, int scanMask, boolean donutFolia) {
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            WorldChunk chunk =
                    this.mc
                            .world
                            .getChunkManager()
                            .getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            this.queueChunkScan(chunk, world, scanMask, donutFolia);
        }
    }

    private void markScanSettled(int scanMask, long now) {
        if (scanMask != 0
                && !this.rescanDone
                && this.scanExecutor != null
                && !this.scanExecutor.isShutdown()
                && this.scanQueue.isEmpty()
                && this.pendingScans.isEmpty()
                && this.queuedChunks.isEmpty()
                && this.lastChunkEventNanos != 0L
                && now - this.lastChunkEventNanos >= IDLE_SETTLE_NANOS) {
            this.rescanDone = true;
            this.rescanLoadedChunks(scanMask);
        }
    }

    private void startExecutors() {
        this.stopExecutors();
        this.scanExecutor = ScannerExecutor.getExecutor();
        this.markerExecutor =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "OdinClient-SusChunk-Markers");
                            thread.setDaemon(true);
                            thread.setPriority(4);
                            return thread;
                        });
    }

    private void stopExecutors() {
        this.scanExecutor = null;
        if (this.markerExecutor != null && !this.markerExecutor.isShutdown()) {
            this.markerExecutor.shutdownNow();
        }

        this.markerExecutor = null;
        this.scanRunning.set(false);
        this.markerRunning.set(false);
        this.scanQueue.clear();
        this.pendingScans.clear();
        this.queuedChunks.clear();
    }

    private void scheduleScanDrain() {
        ExecutorService executor = this.scanExecutor;
        if (executor != null
                && !executor.isShutdown()
                && !this.scanQueue.isEmpty()
                && this.isActive()) {
            if (this.scanRunning.compareAndSet(false, true)) {
                try {
                    executor.execute(this::drainScanQueue);
                } catch (RuntimeException e) {
                    this.scanRunning.set(false);
                }
            }
        }
    }

    private void drainScanQueue() {
        try {
            for (int i = 0; i < SCAN_BATCH_SIZE && this.isActive(); i++) {
                ScannerExecutor.awaitResume();
                ChunkPos chunkPos = this.scanQueue.poll();
                if (chunkPos == null) {
                    break;
                }

                SusChunkFinder.ChunkScanTask task;
                try {
                    while ((task = this.pendingScans.remove(chunkPos)) != null) {
                        try {
                            this.scanChunk(task);
                        } catch (LinkageError | RuntimeException e) {
                            if (task.attempt < 2
                                    && task.generation == this.generation
                                    && task.world == this.mc.world) {
                                this.pendingScans.put(
                                        chunkPos,
                                        new SusChunkFinder.ChunkScanTask(
                                                task.chunk,
                                                task.world,
                                                task.scanMask,
                                                task.donutFolia,
                                                task.generation,
                                                task.attempt + 1));
                            }
                            break;
                        }
                    }
                } finally {
                    this.queuedChunks.remove(chunkPos);
                    if (this.pendingScans.containsKey(chunkPos)
                            && this.queuedChunks.add(chunkPos)) {
                        this.scanQueue.offer(chunkPos);
                    }
                }
            }
        } finally {
            this.scanRunning.set(false);
            if (this.isActive() && !this.scanQueue.isEmpty()) {
                this.scheduleScanDrain();
            }
        }
    }

    private synchronized void resetState(boolean keepData) {
        List<SusChunkFinder.ChunkRect> areas =
                keepData && this.amethystEnabled.getValue() ? this.buildAmethystAreas() : List.of();
        Set<ChunkPos> grown = keepData ? new HashSet<>(this.grownChunks) : Set.of();
        Set<ChunkPos> candidates = keepData ? new HashSet<>(this.candidateChunks) : Set.of();
        HashMap<ChunkPos, Integer> evidence = new HashMap<>();
        if (keepData) {
            for (ChunkPos chunk : grown) {
                evidence.put(
                        chunk,
                        this.chunkSuspiciousEvidence.getOrDefault(
                                chunk, this.getAmethystThreshold()));
            }

            for (ChunkPos candidate : candidates) {
                evidence.putIfAbsent(
                        candidate, this.chunkSuspiciousEvidence.getOrDefault(candidate, 0));
            }
        }

        this.generation++;
        this.scanQueue.clear();
        this.pendingScans.clear();
        this.queuedChunks.clear();
        this.suspiciousScores.clear();
        this.naturalAmethystCounts.clear();
        this.scannedChunks.clear();
        this.chunkSuspiciousEvidence.clear();
        this.chunkNaturalAmethyst.clear();
        this.grownChunks.clear();
        this.candidateChunks.clear();
        this.growthWeightScores.clear();
        this.growthFaceScores.clear();
        this.chunkGrowthWeights.clear();
        this.chunkGrowthFaces.clear();
        this.chunkMaturity.clear();
        this.chunkEvidence.clear();
        this.kelpStats.clear();
        this.caveVineStats.clear();
        this.vineStats.clear();
        this.bambooStats.clear();
        this.beeNestScores.clear();
        this.rotatedDeepslateScores.clear();
        this.chunkScanMasks.clear();
        this.maturityThreshold = 0;
        this.lastScanNanos = 0L;
        this.lastChunkEventNanos = System.nanoTime();
        this.rescanDone = false;
        this.dataVersion++;
        this.grownChunks.addAll(grown);
        this.candidateChunks.addAll(candidates);
        this.chunkSuspiciousEvidence.putAll(evidence);
        this.markerAreas = areas;
        this.stableMarkerAreas = areas;
        this.lastComputedVersion = -1L;
        this.lastStableVersion = -1L;
        this.nextComputeNanos = 0L;
        this.lastSettingsHash = Integer.MIN_VALUE;
        this.lastScanMask = -1;
    }

    private String getServerAddress() {
        ServerInfo serverInfo = this.mc.getCurrentServerEntry();
        return serverInfo != null && serverInfo.address != null && !serverInfo.address.isBlank()
                ? serverInfo.address.toLowerCase(Locale.ROOT)
                : null;
    }

    private int getScanMask() {
        int mask = 0;
        if (this.kelpEnabled.getValue()) {
            mask |= 1;
        }

        if (this.caveVinesEnabled.getValue()) {
            mask |= 2;
        }

        if (this.vinesEnabled.getValue()) {
            mask |= 4;
        }

        if (this.amethystEnabled.getValue()) {
            mask |= 8;
        }

        if (this.bambooEnabled.getValue()) {
            mask |= 16;
        }

        if (this.beeNestEnabled.getValue()) {
            mask |= 32;
        }

        if (this.rotatedDeepslateEnabled.getValue()) {
            mask |= 64;
        }

        return mask;
    }

    private void scanChunk(SusChunkFinder.ChunkScanTask task) {
        if (this.isActive() && task.generation == this.generation && task.world == this.mc.world) {
            WorldChunk chunk = task.chunk;
            ChunkPos chunkPos = chunk.getPos();
            boolean wasGrown = (task.scanMask & 8) != 0 && this.grownChunks.contains(chunkPos);
            int effectiveMask = wasGrown ? task.scanMask & -9 : task.scanMask;
            SusChunkFinder.ChunkScanResult result =
                    scanChunkBlocks(
                            chunkPos,
                            chunk,
                            task.world,
                            task.donutFolia,
                            effectiveMask,
                            this.scanBuffers.get());
            synchronized (this) {
                if (task.generation == this.generation && task.world == this.mc.world) {
                    this.scannedChunks.add(chunkPos);
                    int oldMask = this.chunkScanMasks.getOrDefault(chunkPos, 0);
                    SusChunkFinder.GrowthEvidence oldEvidence = this.chunkEvidence.get(chunkPos);
                    this.chunkScanMasks.put(chunkPos, task.scanMask);
                    this.chunkEvidence.put(chunkPos, result.growthEvidence);
                    if (((oldMask | task.scanMask) & 1) != 0) {
                        this.updatePlantStats(
                                this.kelpStats,
                                chunkPos,
                                oldEvidence == null ? EMPTY_STATS : oldEvidence.kelp,
                                result.growthEvidence.kelp);
                    }

                    if (((oldMask | task.scanMask) & 2) != 0) {
                        this.updatePlantStats(
                                this.caveVineStats,
                                chunkPos,
                                oldEvidence == null ? EMPTY_STATS : oldEvidence.caveVines,
                                result.growthEvidence.caveVines);
                    }

                    if (((oldMask | task.scanMask) & 4) != 0) {
                        this.updatePlantStats(
                                this.vineStats,
                                chunkPos,
                                oldEvidence == null ? EMPTY_STATS : oldEvidence.vines,
                                result.growthEvidence.vines);
                    }

                    if (((oldMask | task.scanMask) & 16) != 0) {
                        this.updatePlantStats(
                                this.bambooStats,
                                chunkPos,
                                oldEvidence == null ? EMPTY_STATS : oldEvidence.bamboo,
                                result.growthEvidence.bamboo);
                    }

                    if ((task.scanMask & 32) != 0) {
                        this.applyMaxScore(
                                this.beeNestScores,
                                chunkPos,
                                scaleCount(result.growthEvidence.maximumBeeNestHoney, 1, 5));
                    }

                    if ((task.scanMask & 64) != 0) {
                        this.applyMaxScore(
                                this.rotatedDeepslateScores,
                                chunkPos,
                                scaleCount(result.growthEvidence.rotatedDeepslateCount, 0, 10));
                    }

                    boolean amethystScan = (task.scanMask & 8) != 0 && !wasGrown;
                    if (amethystScan) {
                        int oldSuspicious = this.chunkSuspiciousEvidence.getOrDefault(chunkPos, 0);
                        boolean oldNatural =
                                Boolean.TRUE.equals(this.chunkNaturalAmethyst.get(chunkPos));
                        long oldWeight = this.chunkGrowthWeights.getOrDefault(chunkPos, 0L);
                        int oldFaces = this.chunkGrowthFaces.getOrDefault(chunkPos, 0);
                        if ((oldMask & 8) != 0 && oldSuspicious > 0) {
                            forEachChunkInRadius(
                                    chunkPos,
                                    pos ->
                                            this.suspiciousScores.compute(
                                                    pos,
                                                    (key, value) ->
                                                            subtractOrRemoveInt(
                                                                    oldSuspicious, value)));
                        }

                        if ((oldMask & 8) != 0 && oldNatural) {
                            forEachChunkInRadius(
                                    chunkPos,
                                    pos ->
                                            this.naturalAmethystCounts.compute(
                                                    pos,
                                                    (key, value) -> subtractOrRemoveInt(1, value)));
                        }

                        if ((oldMask & 8) != 0 && oldFaces > 0) {
                            forEachChunkInRadius(
                                    chunkPos,
                                    pos ->
                                            this.growthFaceScores.compute(
                                                    pos,
                                                    (key, value) ->
                                                            subtractOrRemoveInt(oldFaces, value)));
                            if (oldWeight > 0L) {
                                forEachChunkInRadius(
                                        chunkPos,
                                        pos ->
                                                this.growthWeightScores.compute(
                                                        pos,
                                                        (key, value) ->
                                                                subtractOrRemoveLong(
                                                                        oldWeight, value)));
                            }
                        }

                        int suspicious = result.suspiciousEvidence;
                        this.chunkSuspiciousEvidence.put(chunkPos, suspicious);
                        this.chunkNaturalAmethyst.put(chunkPos, result.naturalAmethyst);
                        int faces =
                                !result.amethystGeodeObserved
                                        ? 0
                                        : Math.max(1, Math.min(32, result.growthFaces));
                        int maturity;
                        if (result.amethystGeodeObserved && result.growthFaces > 0) {
                            long faceScale = result.growthFaces * 100L;
                            maturity =
                                    (int)
                                            Math.min(
                                                    1000L,
                                                    result.maturityPoints * 1000L / faceScale);
                        } else {
                            maturity = 0;
                        }

                        long weight = (long) maturity * faces;
                        this.chunkGrowthFaces.put(chunkPos, faces);
                        this.chunkMaturity.put(chunkPos, maturity);
                        this.chunkGrowthWeights.put(chunkPos, weight);
                        if (faces > 0) {
                            forEachChunkInRadius(
                                    chunkPos,
                                    pos -> this.growthFaceScores.merge(pos, faces, Integer::sum));
                            if (weight > 0L) {
                                forEachChunkInRadius(
                                        chunkPos,
                                        pos ->
                                                this.growthWeightScores.merge(
                                                        pos, weight, Long::sum));
                            }
                        }

                        if (result.naturalAmethyst) {
                            forEachChunkInRadius(
                                    chunkPos,
                                    pos -> this.naturalAmethystCounts.merge(pos, 1, Integer::sum));
                        }

                        if (suspicious > 0) {
                            forEachChunkInRadius(
                                    chunkPos,
                                    pos ->
                                            this.suspiciousScores.merge(
                                                    pos, suspicious, Integer::sum));
                        }

                        if (suspicious >= this.getAmethystThreshold()) {
                            this.grownChunks.add(chunkPos);
                            this.candidateChunks.remove(chunkPos);
                        } else if (!result.amethystGeodeObserved && !result.naturalAmethyst) {
                            this.candidateChunks.remove(chunkPos);
                        } else {
                            this.candidateChunks.add(chunkPos);
                        }
                    }

                    this.lastScanNanos = System.nanoTime();
                    this.dataVersion++;
                }
            }
        }
    }

    private int getAmethystThreshold() {
        int sensitivity = clampSensitivity(this.amethystSensitivity.getValue());
        return 32 + ((sensitivity - 1) * 68 + 4) / 9;
    }

    private void addAmethystScores(Map<ChunkPos, Long> scores, Set<ChunkPos> selected) {
        List<SusChunkFinder.ChunkRect> areas = this.buildAmethystAreas();
        if (!areas.isEmpty()) {
            HashSet<Long> packed = new HashSet<>();

            for (SusChunkFinder.ChunkRect rect : areas) {
                for (int x = rect.minimumX; x < rect.maximumXExclusive; x++) {
                    for (int z = rect.minimumZ; z < rect.maximumZExclusive; z++) {
                        packed.add(packChunkCoords(x, z));
                    }
                }
            }

            HashMap<ChunkPos, Long> areaScores = new HashMap<>();

            for (long packedPos : packed) {
                ChunkPos chunkPos = new ChunkPos(unpackChunkX(packedPos), unpackChunkZ(packedPos));
                if (this.isChunkSurrounded(chunkPos)) {
                    long score = 0L;

                    for (ChunkPos grownChunk : this.grownChunks) {
                        int evidence = this.chunkSuspiciousEvidence.getOrDefault(grownChunk, 0);
                        if (evidence > 0
                                && Math.abs(chunkPos.x - grownChunk.x) <= 4
                                && Math.abs(chunkPos.z - grownChunk.z) <= 4) {
                            score += 200L + Math.min(9000, evidence) * 12L;
                        }
                    }

                    if (score > 0L) {
                        areaScores.put(chunkPos, score);
                    }
                }
            }

            mergeScores(scores, areaScores);
            selected.addAll(selectTopChunks(areaScores, CHUNK_SELECT_PERCENT));
        }
    }

    private synchronized List<SusChunkFinder.ChunkRect> buildAmethystAreas() {
        HashSet<ChunkPos> seeds = new HashSet<>();

        for (Entry<ChunkPos, Integer> entry : this.chunkSuspiciousEvidence.entrySet()) {
            if (entry.getValue() > 0) {
                seeds.add(entry.getKey());
            }
        }

        seeds.addAll(this.candidateChunks);
        seeds.addAll(this.grownChunks);
        if (seeds.isEmpty()) {
            return List.of();
        } else {
            ArrayList<ChunkPos> seedList = new ArrayList<>(seeds);
            ArrayList<SusChunkFinder.ChunkCluster> clusters = new ArrayList<>();
            HashSet<ChunkPos> visited = new HashSet<>();
            int threshold = this.getAmethystThreshold();

            for (ChunkPos start : seedList) {
                if (!visited.contains(start)) {
                    ArrayList<ChunkPos> members = new ArrayList<>();
                    ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
                    queue.add(start);
                    visited.add(start);
                    int minX = start.x;
                    int maxX = start.x;
                    int minZ = start.z;
                    int maxZ = start.z;
                    int totalHits = 0;
                    int maxHits = 0;

                    while (!queue.isEmpty()) {
                        ChunkPos current = queue.removeFirst();
                        members.add(current);
                        int hits = this.chunkSuspiciousEvidence.getOrDefault(current, 0);
                        totalHits += hits;
                        maxHits = Math.max(maxHits, hits);
                        minX = Math.min(minX, current.x);
                        maxX = Math.max(maxX, current.x);
                        minZ = Math.min(minZ, current.z);
                        maxZ = Math.max(maxZ, current.z);

                        for (ChunkPos other : seedList) {
                            if (!visited.contains(other)
                                    && Math.abs(current.x - other.x) <= 1
                                    && Math.abs(current.z - other.z) <= 1
                                    && Math.max(maxX, other.x) - Math.min(minX, other.x) <= 1
                                    && Math.max(maxZ, other.z) - Math.min(minZ, other.z) <= 1) {
                                visited.add(other);
                                queue.addLast(other);
                            }
                        }
                    }

                    boolean isGrown = maxHits >= threshold || totalHits >= threshold;
                    if (!isGrown) {
                        for (ChunkPos memberChunk : members) {
                            if (this.grownChunks.contains(memberChunk)) {
                                isGrown = true;
                                break;
                            }
                        }
                    }

                    clusters.add(
                            new SusChunkFinder.ChunkCluster(
                                    minX,
                                    maxX,
                                    minZ,
                                    maxZ,
                                    List.copyOf(members),
                                    totalHits,
                                    maxHits,
                                    isGrown));
                }
            }

            ArrayList<SusChunkFinder.ChunkCluster> grownClusters = new ArrayList<>();
            ArrayList<SusChunkFinder.ChunkCluster> candidateClusters = new ArrayList<>();
            HashSet<ChunkPos> candidateChunks = new HashSet<>();

            for (SusChunkFinder.ChunkCluster found : clusters) {
                if (found.grown) {
                    grownClusters.add(found);
                    this.grownChunks.addAll(found.chunks);
                } else {
                    candidateClusters.add(found);
                    candidateChunks.addAll(found.chunks);
                }
            }

            candidateChunks.removeAll(this.grownChunks);
            this.candidateChunks.clear();
            this.candidateChunks.addAll(candidateChunks);
            if (grownClusters.isEmpty()) {
                return List.of();
            } else {
                int halfSize = 4;
                ArrayList<int[]> boxes = new ArrayList<>();
                if (grownClusters.size() == 1) {
                    SusChunkFinder.ChunkCluster only = grownClusters.get(0);
                    boxes.add(
                            new int[] {
                                only.centreX() - halfSize,
                                only.centreZ() - halfSize,
                                only.centreX() + halfSize,
                                only.centreZ() + halfSize
                            });
                } else {
                    int count = grownClusters.size();
                    boolean[][] adjacent = new boolean[count][count];

                    for (int a = 0; a < count; a++) {
                        SusChunkFinder.ChunkCluster first = grownClusters.get(a);

                        for (int b = a + 1; b < count; b++) {
                            SusChunkFinder.ChunkCluster second = grownClusters.get(b);
                            int distX = Math.abs(first.centreX() - second.centreX());
                            int distZ = Math.abs(first.centreZ() - second.centreZ());
                            if (distX <= 2 * halfSize && distZ <= 2 * halfSize) {
                                adjacent[a][b] = true;
                                adjacent[b][a] = true;
                            }
                        }
                    }

                    HashSet<Integer> indices = new HashSet<>();

                    for (int idx = 0; idx < count; idx++) {
                        indices.add(idx);
                    }

                    ArrayList<Set<Integer>> cliques = new ArrayList<>();
                    findCliques(new HashSet<>(), indices, new HashSet<>(), adjacent, cliques);
                    HashSet<Integer> merged = new HashSet<>();

                    for (Set<Integer> clique : cliques) {
                        if (clique.size() > 1) {
                            int overlapMinX = Integer.MIN_VALUE;
                            int overlapMaxX = Integer.MAX_VALUE;
                            int overlapMinZ = Integer.MIN_VALUE;
                            int overlapMaxZ = Integer.MAX_VALUE;

                            for (int index : clique) {
                                SusChunkFinder.ChunkCluster member = grownClusters.get(index);
                                overlapMinX = Math.max(overlapMinX, member.centreX() - halfSize);
                                overlapMaxX = Math.min(overlapMaxX, member.centreX() + halfSize);
                                overlapMinZ = Math.max(overlapMinZ, member.centreZ() - halfSize);
                                overlapMaxZ = Math.min(overlapMaxZ, member.centreZ() + halfSize);
                            }

                            if (overlapMinX <= overlapMaxX && overlapMinZ <= overlapMaxZ) {
                                boxes.add(
                                        new int[] {
                                            overlapMinX, overlapMinZ, overlapMaxX, overlapMaxZ
                                        });
                                merged.addAll(clique);
                            }
                        }
                    }

                    for (int n = 0; n < count; n++) {
                        if (!merged.contains(n)) {
                            SusChunkFinder.ChunkCluster single = grownClusters.get(n);
                            boxes.add(
                                    new int[] {
                                        single.centreX() - halfSize,
                                        single.centreZ() - halfSize,
                                        single.centreX() + halfSize,
                                        single.centreZ() + halfSize
                                    });
                        }
                    }
                }

                ArrayList<SusChunkFinder.ChunkRect> rects = new ArrayList<>();
                HashSet<String> seenRects = new HashSet<>();

                for (int[] box : boxes) {
                    int boxMinX = box[0];
                    int boxMinZ = box[1];
                    int boxMaxX = box[2];
                    int boxMaxZ = box[3];
                    int width = boxMaxX - boxMinX + 1;
                    int depth = boxMaxZ - boxMinZ + 1;
                    if (width > 0 && depth > 0) {
                        ArrayList<SusChunkFinder.ChunkCluster> nearby = new ArrayList<>();

                        for (SusChunkFinder.ChunkCluster candidate : candidateClusters) {
                            if (candidate.centreX() - halfSize <= boxMaxX
                                    && candidate.centreX() + halfSize >= boxMinX
                                    && candidate.centreZ() - halfSize <= boxMaxZ
                                    && candidate.centreZ() + halfSize >= boxMinZ) {
                                nearby.add(candidate);
                            }
                        }

                        if (nearby.isEmpty()) {
                            addUniqueRect(rects, seenRects, boxMinX, boxMinZ, boxMaxX, boxMaxZ);
                        } else {
                            boolean[][] free = new boolean[width][depth];
                            int freeCount = 0;

                            for (int dx = 0; dx < width; dx++) {
                                int cellX = boxMinX + dx;

                                for (int dz = 0; dz < depth; dz++) {
                                    int cellZ = boxMinZ + dz;
                                    boolean covered = false;

                                    for (SusChunkFinder.ChunkCluster cluster : nearby) {
                                        if (cellX >= cluster.centreX() - halfSize
                                                && cellX <= cluster.centreX() + halfSize
                                                && cellZ >= cluster.centreZ() - halfSize
                                                && cellZ <= cluster.centreZ() + halfSize) {
                                            covered = true;
                                            break;
                                        }
                                    }

                                    free[dx][dz] = !covered;
                                    if (!covered) {
                                        freeCount++;
                                    }
                                }
                            }

                            if (freeCount == 0) {
                                addUniqueRect(rects, seenRects, boxMinX, boxMinZ, boxMaxX, boxMaxZ);
                            } else {
                                for (int row = 0; row < depth; row++) {
                                    for (int col = 0; col < width; col++) {
                                        if (free[col][row]) {
                                            int runWidth = 0;

                                            while (col + runWidth < width
                                                    && free[col + runWidth][row]) {
                                                runWidth++;
                                            }

                                            int runHeight;
                                            for (runHeight = 1;
                                                    row + runHeight < depth;
                                                    runHeight++) {
                                                boolean rowFilled = true;

                                                for (int rx = 0; rx < runWidth; rx++) {
                                                    if (!free[col + rx][row + runHeight]) {
                                                        rowFilled = false;
                                                        break;
                                                    }
                                                }

                                                if (!rowFilled) {
                                                    break;
                                                }
                                            }

                                            for (int ry = 0; ry < runHeight; ry++) {
                                                for (int fx = 0; fx < runWidth; fx++) {
                                                    free[col + fx][row + ry] = false;
                                                }
                                            }

                                            addUniqueRect(
                                                    rects,
                                                    seenRects,
                                                    boxMinX + col,
                                                    boxMinZ + row,
                                                    boxMinX + col + runWidth - 1,
                                                    boxMinZ + row + runHeight - 1);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                return List.copyOf(rects);
            }
        }
    }

    private static void addUniqueRect(
            List<SusChunkFinder.ChunkRect> rects,
            Set<String> seen,
            int minX,
            int minZ,
            int maxX,
            int maxZ) {
        String key = minX + ":" + minZ + ":" + maxX + ":" + maxZ;
        if (seen.add(key)) {
            rects.add(new SusChunkFinder.ChunkRect(minX, minZ, maxX + 1, maxZ + 1));
        }
    }

    private static void findCliques(
            Set<Integer> current,
            Set<Integer> candidates,
            Set<Integer> excluded,
            boolean[][] adjacency,
            List<Set<Integer>> result) {
        if (candidates.isEmpty() && excluded.isEmpty()) {
            if (!current.isEmpty()) {
                result.add(new HashSet<>(current));
            }
        } else {
            for (int vertex : new ArrayList<>(candidates)) {
                HashSet<Integer> nextCurrent = new HashSet<>(current);
                nextCurrent.add(vertex);
                HashSet<Integer> nextCandidates = new HashSet<>();

                for (int candidate : candidates) {
                    if (adjacency[vertex][candidate]) {
                        nextCandidates.add(candidate);
                    }
                }

                HashSet<Integer> nextExcluded = new HashSet<>();

                for (int excludedVertex : excluded) {
                    if (adjacency[vertex][excludedVertex]) {
                        nextExcluded.add(excludedVertex);
                    }
                }

                findCliques(nextCurrent, nextCandidates, nextExcluded, adjacency, result);
                candidates.remove(vertex);
                excluded.add(vertex);
            }
        }
    }

    private void updateGeodeSizeEstimate() {
        ArrayList<Integer> values = new ArrayList<>();

        for (Entry<ChunkPos, Integer> entry : this.chunkMaturity.entrySet()) {
            if (entry.getValue() > 0) {
                int faces = Math.min(8, this.chunkGrowthFaces.getOrDefault(entry.getKey(), 0));

                for (int i = 0; i < faces; i++) {
                    values.add(entry.getValue());
                }
            }
        }

        if (values.isEmpty()) {
            this.maturityThreshold = 0;
        } else {
            Collections.sort(values);
            int index = (int) Math.ceil(values.size() * 0.75) - 1;
            this.maturityThreshold = values.get(Math.max(0, index));
        }
    }

    private int countScannedNeighbors(ChunkPos chunkPos) {
        int count = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0)
                        && this.scannedChunks.contains(
                                new ChunkPos(chunkPos.x + dx, chunkPos.z + dz))) {
                    count++;
                }
            }
        }

        return count;
    }

    private static void forEachChunkInRadius(ChunkPos center, Consumer<ChunkPos> action) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                action.accept(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
    }

    private static void forEachNeighborChunk(ChunkPos center, Consumer<ChunkPos> action) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                action.accept(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
    }

    private static Integer subtractOrRemoveInt(int amount, Integer current) {
        if (current == null) {
            return null;
        } else {
            int remaining = current - amount;
            return remaining > 0 ? remaining : null;
        }
    }

    private static Long subtractOrRemoveLong(long amount, Long current) {
        if (current == null) {
            return null;
        } else {
            long remaining = current - amount;
            return remaining > 0L ? remaining : null;
        }
    }

    private static IntSetting createSensitivitySetting(
            String name, String description, VisibilityCondition visibility) {
        return new IntSetting.Builder()
                .name(name)
                .description(description)
                .defaultValue(3)
                .range(1, 10)
                .sliderRange(1, 10)
                .visibleWhen(visibility)
                .build();
    }

    private void addPlantScores(
            Map<ChunkPos, Long> scores,
            Set<ChunkPos> selected,
            Map<ChunkPos, SusChunkFinder.PlantStats> stats,
            boolean enabled,
            int sensitivity,
            int baseEvidence,
            int baseAbove,
            int baseHigh) {
        if (enabled) {
            HashMap<ChunkPos, Long> plantScores = new HashMap<>();
            int minScore = SENSITIVITY_THRESHOLDS[clampSensitivity(sensitivity)];

            for (Entry<ChunkPos, SusChunkFinder.PlantStats> entry : stats.entrySet()) {
                SusChunkFinder.PlantStats plantStats = entry.getValue();
                if (hasEnoughMaturePlants(plantStats, baseAbove)) {
                    int score = computePlantScore(plantStats, baseEvidence, baseAbove, baseHigh);
                    ChunkPos chunkPos = entry.getKey();
                    if (score >= minScore) {
                        long weight = 200L + score * 6L;
                        forEachChunkInRadius(
                                chunkPos,
                                pos -> {
                                    if (this.isChunkSurrounded(pos)) {
                                        plantScores.merge(pos, weight, Long::sum);
                                    }
                                });
                    }
                }
            }

            mergeScores(scores, plantScores);
            selected.addAll(selectSpacedChunks(plantScores));
        }
    }

    private void addCountScores(
            Map<ChunkPos, Long> scores,
            Set<ChunkPos> selected,
            Map<ChunkPos, Integer> counts,
            boolean enabled,
            int sensitivity) {
        if (enabled) {
            HashMap<ChunkPos, Long> countScores = new HashMap<>();
            int minCount = clampSensitivity(sensitivity) * 10;

            for (Entry<ChunkPos, Integer> entry : counts.entrySet()) {
                int count = entry.getValue();
                ChunkPos chunkPos = entry.getKey();
                if (count >= minCount && this.isChunkSurrounded(chunkPos)) {
                    long weight = 200L + count * 6L;
                    countScores.merge(chunkPos, weight, Long::sum);
                }
            }

            mergeScores(scores, countScores);
            selected.addAll(selectSpacedChunks(countScores));
        }
    }

    private static void mergeScores(Map<ChunkPos, Long> target, Map<ChunkPos, Long> source) {
        for (Entry<ChunkPos, Long> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    private boolean isChunkSurrounded(ChunkPos chunkPos) {
        return this.mc.world != null
                && this.mc.world.isChunkLoaded(chunkPos.x, chunkPos.z)
                && this.countScannedNeighbors(chunkPos) >= 3;
    }

    private void applyMaxScore(
            ConcurrentHashMap<ChunkPos, Integer> scores, ChunkPos center, int score) {
        if (score > 0) {
            forEachChunkInRadius(center, pos -> scores.merge(pos, score, Math::max));
        }
    }

    private void updatePlantStats(
            ConcurrentHashMap<ChunkPos, SusChunkFinder.PlantStats> statsMap,
            ChunkPos center,
            SusChunkFinder.PlantStats oldStats,
            SusChunkFinder.PlantStats newStats) {
        forEachNeighborChunk(
                center,
                pos ->
                        statsMap.compute(
                                pos,
                                (key, existing) -> {
                                    SusChunkFinder.PlantStats current =
                                            existing == null ? EMPTY_STATS : existing;
                                    SusChunkFinder.PlantStats updated =
                                            current.subtract(oldStats).add(newStats);
                                    return updated.plantCount > 0 ? updated : null;
                                }));
    }

    private static int computePlantScore(
            SusChunkFinder.PlantStats stats, int baseEvidence, int baseAbove, int baseHigh) {
        if (stats != null && stats.plantCount > 0) {
            int divisor = Math.max(10, stats.plantCount);
            int missing = divisor - stats.plantCount;
            int evidenceAvg =
                    Math.min(100, (stats.evidencePoints + missing * baseEvidence) / divisor);
            int aboveAvg =
                    Math.min(100, (stats.aboveVanillaPlants * 100 + missing * baseAbove) / divisor);
            int highAvg = Math.min(100, (stats.highPlants * 100 + missing * baseHigh) / divisor);
            int evidenceScale = scaleAboveThreshold(evidenceAvg, baseEvidence);
            int aboveScale = scaleAboveThreshold(aboveAvg, baseAbove);
            int highScale = scaleAboveThreshold(highAvg, baseHigh);
            return evidenceScale > 0 && aboveScale > 0
                    ? Math.min(
                            100, (evidenceScale * 50 + aboveScale * 5 + highScale * 45 + 50) / 100)
                    : 0;
        } else {
            return 0;
        }
    }

    private static boolean hasEnoughMaturePlants(SusChunkFinder.PlantStats stats, int baseAbove) {
        if (stats != null && stats.plantCount >= 3) {
            int percent = Math.min(95, baseAbove + 10);
            int required = Math.max(3, stats.plantCount * percent / 100 + 1);
            return stats.aboveVanillaPlants >= required;
        } else {
            return false;
        }
    }

    private static int scaleAboveThreshold(int value, int threshold) {
        return value <= threshold
                ? 0
                : Math.min(100, (value - threshold) * 100 / Math.max(1, 100 - threshold));
    }

    private static int scaleCount(int value, int min, int max) {
        if (value <= min) {
            return 0;
        } else {
            int range = Math.max(1, max - min);
            return Math.min(100, ((value - min) * 100 + range - 1) / range);
        }
    }

    private static int clampSensitivity(int sensitivity) {
        return Math.max(1, Math.min(10, sensitivity));
    }

    private static Block block(String name) {
        return Registries.BLOCK.get(Identifier.of("minecraft", name));
    }

    private static SusChunkFinder.ChunkScanResult scanChunkBlocks(
            ChunkPos chunkPos,
            WorldChunk chunk,
            World world,
            boolean donutFolia,
            int scanMask,
            SusChunkFinder.ScanBuffers buffers) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomSection = chunk.getBottomSectionCoord();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        boolean scanKelp = (scanMask & 1) != 0;
        boolean scanCaveVines = (scanMask & 2) != 0;
        boolean scanVines = (scanMask & 4) != 0;
        boolean scanAmethyst = (scanMask & 8) != 0;
        boolean scanBamboo = (scanMask & 16) != 0;
        boolean scanBeeNest = (scanMask & 32) != 0;
        boolean scanDeepslate = (scanMask & 64) != 0;
        int blockMask = donutFolia ? scanMask & -9 : scanMask;
        Predicate<BlockState> blockFilter = state -> matchesScanMask(state, blockMask);
        buffers.reset(scanMask);
        int geodeHits = 0;
        boolean exposedBudding = false;
        boolean amethystBlockSeen = false;
        boolean budSeen = false;
        boolean clusterSeen = false;
        int growthFaces = 0;
        int maturityPoints = 0;
        int maxHoney = 0;
        int rotatedDeepslate = 0;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section != null && !section.isEmpty()) {
                if (donutFolia && scanAmethyst) {
                    amethystBlockSeen |= section.hasAny(SusChunkFinder::isAmethystBlock);
                    budSeen |= section.hasAny(SusChunkFinder::isAmethystBud);
                    clusterSeen |= section.hasAny(s -> s.isOf(Blocks.AMETHYST_CLUSTER));
                }

                if (blockMask != 0 && section.hasAny(blockFilter)) {
                    int baseY = bottomSection + sectionIndex << 4;

                    for (int localY = 0; localY < 16; localY++) {
                        int y = baseY + localY;

                        for (int localX = 0; localX < 16; localX++) {
                            int x = startX + localX;

                            for (int localZ = 0; localZ < 16; localZ++) {
                                int z = startZ + localZ;
                                BlockState blockState =
                                        section.getBlockState(localX, localY, localZ);
                                int column = localZ << 4 | localX;
                                if (scanKelp && isKelp(blockState)) {
                                    updateColumnRun(
                                            y,
                                            column,
                                            buffers.kelpLastY,
                                            buffers.kelpRunLength,
                                            buffers.kelpMaxRun);
                                    if (blockState.isOf(KELP)) {
                                        buffers.kelpAge[column] = getIntProperty(blockState, "age");
                                    }
                                }

                                if (scanCaveVines && isCaveVines(blockState)) {
                                    updateColumnRun(
                                            y,
                                            column,
                                            buffers.caveVinesLastY,
                                            buffers.caveVinesRunLength,
                                            buffers.caveVinesMaxRun);
                                    if (blockState.isOf(CAVE_VINES)) {
                                        buffers.caveVinesAge[column] =
                                                getIntProperty(blockState, "age");
                                    }
                                }

                                if (scanVines && blockState.isOf(VINE)) {
                                    updateColumnRun(
                                            y,
                                            column,
                                            buffers.vinesLastY,
                                            buffers.vinesRunLength,
                                            buffers.vinesMaxRun);
                                    buffers.vinesScore[column] =
                                            Math.min(
                                                    100,
                                                    buffers.vinesScore[column]
                                                            + Math.max(
                                                                            0,
                                                                            countVineSides(
                                                                                            blockState)
                                                                                    - 1)
                                                                    * 25);
                                }

                                if (scanBamboo && blockState.isOf(BAMBOO)) {
                                    updateColumnRun(
                                            y,
                                            column,
                                            buffers.bambooLastY,
                                            buffers.bambooRunLength,
                                            buffers.bambooMaxRun);
                                }

                                if (scanBeeNest && blockState.isOf(BEE_NEST)) {
                                    maxHoney =
                                            Math.max(
                                                    maxHoney,
                                                    getIntProperty(blockState, "honey_level"));
                                }

                                if (scanDeepslate
                                        && blockState.isOf(DEEPSLATE)
                                        && !"y".equals(getStringProperty(blockState, "axis"))) {
                                    rotatedDeepslate++;
                                }

                                if (!donutFolia && scanAmethyst) {
                                    boolean isCluster = blockState.isOf(Blocks.AMETHYST_CLUSTER);
                                    boolean isBud = isAmethystBud(blockState);
                                    if (isAmethystBlock(blockState)) {
                                        amethystBlockSeen = true;
                                    }

                                    if (blockState.isOf(Blocks.BUDDING_AMETHYST)) {
                                        buffers.buddingAmethystPositions.add(new BlockPos(x, y, z));
                                    }

                                    if (isCluster) {
                                        growthFaces++;
                                        maturityPoints += 100;
                                    } else if (isBud && blockState.contains(Properties.FACING)) {
                                        growthFaces++;
                                        maturityPoints += getAmethystGrowthScore(blockState);
                                        Direction facing = blockState.get(Properties.FACING);
                                        BlockPos sourcePos =
                                                new BlockPos(x, y, z).offset(facing.getOpposite());
                                        if (world.getBlockState(sourcePos)
                                                .isOf(Blocks.BUDDING_AMETHYST)) {
                                            exposedBudding = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (scanAmethyst && donutFolia) {
            if (budSeen) {
                exposedBudding = true;
            }

            if (!amethystBlockSeen) {
                growthFaces = 0;
                maturityPoints = 0;
            } else {
                growthFaces = 1;
                maturityPoints = clusterSeen ? 100 : (budSeen ? 20 : 0);
            }
        } else if (scanAmethyst) {
            int faceCount = 0;
            int pointTotal = 0;

            for (BlockPos buddingPos : buffers.buddingAmethystPositions) {
                for (Direction direction : Direction.values()) {
                    BlockState neighbor = world.getBlockState(buddingPos.offset(direction));
                    int growthScore;
                    if (!neighbor.isAir() && !neighbor.isOf(Blocks.WATER)) {
                        if (neighbor.contains(Properties.FACING)
                                && neighbor.get(Properties.FACING) == direction) {
                            growthScore = getAmethystGrowthScore(neighbor);
                        } else {
                            growthScore = -1;
                        }
                    } else {
                        growthScore = 0;
                    }

                    if (growthScore >= 0) {
                        faceCount++;
                        pointTotal += growthScore;
                    }
                }
            }

            if (faceCount == 0 && amethystBlockSeen) {
                faceCount = Math.max(1, growthFaces);
                pointTotal = maturityPoints;
            }

            growthFaces = faceCount;
            maturityPoints = pointTotal;
        }

        if (scanAmethyst) {
            geodeHits = countGeodeHits(world, chunk, chunkPos);
        }

        SusChunkFinder.GrowthEvidence evidence =
                new SusChunkFinder.GrowthEvidence(
                        scanKelp
                                ? buildKelpStats(buffers.kelpMaxRun, buffers.kelpAge)
                                : EMPTY_STATS,
                        scanCaveVines
                                ? buildCaveVineStats(buffers.caveVinesMaxRun, buffers.caveVinesAge)
                                : EMPTY_STATS,
                        scanVines
                                ? buildVineStats(buffers.vinesMaxRun, buffers.vinesScore)
                                : EMPTY_STATS,
                        scanBamboo ? buildBambooStats(buffers.bambooMaxRun) : EMPTY_STATS,
                        maxHoney,
                        rotatedDeepslate);
        return new SusChunkFinder.ChunkScanResult(
                geodeHits,
                exposedBudding,
                amethystBlockSeen,
                growthFaces,
                maturityPoints,
                evidence);
    }

    private static int countGeodeHits(World world, WorldChunk chunk, ChunkPos chunkPos) {
        BlockPos center = findGeodeCenter(world, chunkPos);
        SusChunkFinder.GeodeProbeResult probe =
                center == null
                        ? new SusChunkFinder.GeodeProbeResult(0, false)
                        : probeGeodeNearCenter(world, chunkPos, center);
        int hits = probe.hits;
        if (hits < 6) {
            hits = findGeodeByLightPockets(world, chunkPos);
        }

        return hits;
    }

    private static SusChunkFinder.GeodeProbeResult probeGeodeNearCenter(
            World world, ChunkPos chunkPos, BlockPos center) {
        HashSet<BlockPos> clusterBlocks = new HashSet<>();
        boolean exposedBasalt = false;
        Mutable pos = new Mutable();
        Mutable neighborPos = new Mutable();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int minX = Math.max(startX - 2, center.getX() - 8);
        int maxX = Math.min(startX + 17, center.getX() + 8);
        int minY = Math.max(-64, center.getY() - 8);
        int maxY = Math.min(64, center.getY() + 8);
        int minZ = Math.max(startZ - 2, center.getZ() - 8);
        int maxZ = Math.min(startZ + 17, center.getZ() + 8);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(Blocks.SMOOTH_BASALT)) {
                        for (int[] dir : DIRECTION_OFFSETS) {
                            neighborPos.set(x + dir[0], y + dir[1], z + dir[2]);
                            BlockState adjacent = world.getBlockState(neighborPos);
                            boolean open =
                                    adjacent.isAir()
                                            || adjacent.isOf(Blocks.WATER)
                                            || adjacent.isOf(Blocks.LAVA)
                                            || !adjacent.getFluidState().isEmpty();
                            if (open && !adjacent.isOf(Blocks.AMETHYST_CLUSTER)) {
                                exposedBasalt = true;
                                break;
                            }
                        }
                    } else if (state.isOf(Blocks.AMETHYST_CLUSTER)) {
                        clusterBlocks.add(pos.toImmutable());
                    } else if (clusterBlocks.size() < MAX_GEODE_BLOCKS) {
                        boolean transparent =
                                state.isAir()
                                        || state.isOf(Blocks.WATER)
                                        || state.isOf(Blocks.LAVA);
                        if (!transparent) {
                            z = skipLitAirRun(world, x, y, z);
                        } else {
                            int light = world.getLightLevel(LightType.BLOCK, pos);
                            if (light == 4) {
                                z = skipLitAirRun(world, x, y, z);
                            } else if (light == 0) {
                                int maxLight = 0;
                                boolean nextToLitAir = false;

                                for (int[] offset : DIRECTION_OFFSETS) {
                                    neighborPos.set(x + offset[0], y + offset[1], z + offset[2]);
                                    int neighborLight =
                                            world.getLightLevel(LightType.BLOCK, neighborPos);
                                    maxLight = Math.max(maxLight, neighborLight);
                                    BlockState neighborState = world.getBlockState(neighborPos);
                                    if (neighborLight == 4
                                            && (neighborState.isAir()
                                                    || neighborState.isOf(
                                                            Blocks.AMETHYST_CLUSTER))) {
                                        nextToLitAir = true;
                                    }
                                }

                                if (maxLight == 4 && nextToLitAir) {
                                    clusterBlocks.add(pos.toImmutable());
                                }

                                if (maxLight >= 4) {
                                    z = skipLitAirRun(world, x, y, z);
                                }
                            }
                        }
                    }
                }
            }
        }

        return new SusChunkFinder.GeodeProbeResult(
                largestBlockCluster(clusterBlocks, 36.0), exposedBasalt);
    }

    private static int findGeodeByLightPockets(World world, ChunkPos chunkPos) {
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        Mutable pos = new Mutable();
        Mutable neighborPos = new Mutable();
        ArrayList<BlockPos> pockets = new ArrayList<>();
        HashSet<Long> litColumns = new HashSet<>();

        for (int y = -64; y <= 64; y++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = startX + dx;

                for (int dz = 0; dz < 16; dz++) {
                    int z = startZ + dz;
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (isTorch(state)) {
                        return 0;
                    }

                    int light = world.getLightLevel(LightType.BLOCK, pos);
                    if (light >= 6) {
                        litColumns.add(packCoords(x >> 3, z >> 3));
                    } else if (light >= 3 && light <= 5) {
                        boolean nextToTorch = false;

                        for (int[] torchOffset : DIRECTION_OFFSETS) {
                            neighborPos.set(
                                    x + torchOffset[0], y + torchOffset[1], z + torchOffset[2]);
                            if (isTorch(world.getBlockState(neighborPos))) {
                                nextToTorch = true;
                                break;
                            }
                        }

                        if (nextToTorch) {
                            return 0;
                        }

                        boolean isolatedPocket = true;

                        for (int[] offset : DIRECTION_OFFSETS) {
                            neighborPos.set(x + offset[0], y + offset[1], z + offset[2]);
                            int neighborLight = world.getLightLevel(LightType.BLOCK, neighborPos);
                            if (neighborLight >= 6) {
                                litColumns.add(packCoords(x >> 3, z >> 3));
                                isolatedPocket = false;
                                break;
                            }

                            if (neighborLight > light) {
                                isolatedPocket = false;
                                break;
                            }
                        }

                        if (isolatedPocket) {
                            pockets.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }

        ArrayList<BlockPos> isolated = new ArrayList<>();

        for (BlockPos pocket : pockets) {
            if (!litColumns.contains(packCoords(pocket.getX() >> 3, pocket.getZ() >> 3))) {
                isolated.add(pocket);
            }
        }

        if (isolated.size() < 6) {
            return 0;
        } else {
            List<List<BlockPos>> groups = groupBlocksByDistance(isolated, 121.0);
            int best = 0;

            for (List<BlockPos> group : groups) {
                int score = scoreSphericalCluster(world, group, pos);
                if (score > best) {
                    best = score;
                }
            }

            return best;
        }
    }

    private static int scoreSphericalCluster(World world, List<BlockPos> blocks, Mutable pos) {
        int count = blocks.size();
        if (count < 6) {
            return 0;
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;

        for (BlockPos block : blocks) {
            sumX += block.getX();
            sumY += block.getY();
            sumZ += block.getZ();
        }

        double meanX = sumX / count;
        double meanY = sumY / count;
        double meanZ = sumZ / count;
        double[][] matrix = new double[4][4];
        double[] vector = new double[4];

        for (BlockPos block : blocks) {
            double px = block.getX() - meanX;
            double py = block.getY() - meanY;
            double pz = block.getZ() - meanZ;
            double squared = px * px + py * py + pz * pz;
            double[] row = {px, py, pz, 1.0};

            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    matrix[i][j] += row[i] * row[j];
                }

                vector[i] += row[i] * squared;
            }
        }

        double[] solution = solveLinearSystem(matrix, vector);
        if (solution == null) {
            return 0;
        }

        double offsetX = solution[0] / 2.0;
        double offsetY = solution[1] / 2.0;
        double offsetZ = solution[2] / 2.0;
        double radiusSquared =
                solution[3] + offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        if (radiusSquared <= 0.0) {
            return 0;
        }

        double radius = Math.sqrt(radiusSquared);
        if (radius < 3.0 || radius > 7.0) {
            return 0;
        }

        double centerX = meanX + offsetX;
        double centerY = meanY + offsetY;
        double centerZ = meanZ + offsetZ;
        double deviationSum = 0.0;
        double dirX = 0.0;
        double dirY = 0.0;
        double dirZ = 0.0;
        HashSet<Integer> octants = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos point : blocks) {
            double dx = point.getX() - centerX;
            double dy = point.getY() - centerY;
            double dz = point.getZ() - centerZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 1.0E-4) {
                distance = 1.0;
            }

            deviationSum += Math.abs(distance - radius);
            dirX += dx / distance;
            dirY += dy / distance;
            dirZ += dz / distance;
            octants.add((dx >= 0.0 ? 1 : 0) | (dy >= 0.0 ? 1 : 0) << 1 | (dz >= 0.0 ? 1 : 0) << 2);
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
            minZ = Math.min(minZ, point.getZ());
            maxZ = Math.max(maxZ, point.getZ());
        }

        double meanDeviation = deviationSum / count;
        double directionBias = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ) / count;
        if (meanDeviation > 1.35 || octants.size() < 3 || directionBias > 0.72) {
            return 0;
        }

        pos.set((int) Math.round(centerX), (int) Math.round(centerY), (int) Math.round(centerZ));
        if (world.getLightLevel(LightType.BLOCK, pos) > 3) {
            return 0;
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        return (sizeX >= 3 || sizeZ >= 3) && sizeX + sizeZ >= 6 && sizeY >= 2 ? count : 0;
    }

    private static int largestBlockCluster(Set<BlockPos> blocks, double maxDistanceSquared) {
        if (blocks.size() < 6) {
            return 0;
        } else {
            ArrayList<BlockPos> list = new ArrayList<>(blocks);
            boolean[] visited = new boolean[list.size()];
            int best = 0;

            for (int start = 0; start < list.size(); start++) {
                if (!visited[start]) {
                    int size = 0;
                    ArrayDeque<Integer> queue = new ArrayDeque<>();
                    queue.add(start);
                    visited[start] = true;

                    while (!queue.isEmpty()) {
                        BlockPos current = list.get(queue.removeFirst());
                        size++;

                        for (int i = 0; i < list.size(); i++) {
                            if (!visited[i]
                                    && current.getSquaredDistance(list.get(i))
                                            <= maxDistanceSquared) {
                                visited[i] = true;
                                queue.addLast(i);
                            }
                        }
                    }

                    if (size >= 6) {
                        best = Math.max(best, size);
                    }
                }
            }

            return best;
        }
    }

    private static List<List<BlockPos>> groupBlocksByDistance(
            List<BlockPos> blocks, double maxDistanceSquared) {
        ArrayList<List<BlockPos>> groups = new ArrayList<>();
        boolean[] visited = new boolean[blocks.size()];

        for (int start = 0; start < blocks.size(); start++) {
            if (!visited[start]) {
                ArrayList<BlockPos> group = new ArrayList<>();
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(start);
                visited[start] = true;

                while (!queue.isEmpty()) {
                    BlockPos current = blocks.get(queue.removeFirst());
                    group.add(current);

                    for (int i = 0; i < blocks.size(); i++) {
                        if (!visited[i]
                                && current.getSquaredDistance(blocks.get(i))
                                        <= maxDistanceSquared) {
                            visited[i] = true;
                            queue.addLast(i);
                        }
                    }
                }

                if (group.size() >= 6) {
                    groups.add(group);
                }
            }
        }

        return groups;
    }

    private static int skipLitAirRun(World world, int x, int y, int startZ) {
        int z = startZ + 1;
        Mutable pos = new Mutable();

        while (true) {
            pos.set(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && !state.isOf(Blocks.AMETHYST_CLUSTER)) {
                return z;
            }

            if (world.getLightLevel(LightType.BLOCK, pos) != 4) {
                return z - 1;
            }

            z++;
        }
    }

    private static BlockPos findGeodeCenter(World world, ChunkPos chunkPos) {
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        Mutable pos = new Mutable();
        int sumX = 0;
        int sumY = 0;
        int sumZ = 0;
        int count = 0;

        for (int y = -64; y <= 64; y++) {
            for (int x = startX; x < startX + 16; x++) {
                for (int z = startZ; z < startZ + 16; z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(Blocks.AMETHYST_BLOCK)
                            || state.isOf(Blocks.BUDDING_AMETHYST)
                            || state.isOf(Blocks.CALCITE)
                            || state.isOf(Blocks.SMOOTH_BASALT)) {
                        sumX += x;
                        sumY += y;
                        sumZ += z;
                        count++;
                    }
                }
            }
        }

        return count < 5 ? null : new BlockPos(sumX / count, sumY / count, sumZ / count);
    }

    private static double[] solveLinearSystem(double[][] matrix, double[] vector) {
        double[][] augmented = new double[4][5];

        for (int i = 0; i < 4; i++) {
            System.arraycopy(matrix[i], 0, augmented[i], 0, 4);
            augmented[i][4] = vector[i];
        }

        for (int k = 0; k < 4; k++) {
            int pivot = k;

            for (int r = k + 1; r < 4; r++) {
                if (Math.abs(augmented[r][k]) > Math.abs(augmented[pivot][k])) {
                    pivot = r;
                }
            }

            double[] swap = augmented[k];
            augmented[k] = augmented[pivot];
            augmented[pivot] = swap;
            if (Math.abs(augmented[k][k]) < 1.0E-9) {
                return null;
            }

            for (int row = k + 1; row < 4; row++) {
                double factor = augmented[row][k] / augmented[k][k];

                for (int col = k; col <= 4; col++) {
                    augmented[row][col] -= factor * augmented[k][col];
                }
            }
        }

        double[] result = new double[4];

        for (int n = 3; n >= 0; n--) {
            double sum = 0.0;

            for (int m = n + 1; m < 4; m++) {
                sum += augmented[n][m] * result[m];
            }

            result[n] = (augmented[n][4] - sum) / augmented[n][n];
        }

        return result;
    }

    private static boolean isTorch(BlockState state) {
        return state.isOf(Blocks.TORCH) || state.isOf(Blocks.SOUL_TORCH);
    }

    private static long packCoords(int x, int z) {
        return (long) x << 32 | z & 4294967295L;
    }

    private static boolean matchesScanMask(BlockState state, int mask) {
        return (mask & 1) != 0 && isKelp(state)
                || (mask & 2) != 0 && isCaveVines(state)
                || (mask & 4) != 0 && state.isOf(VINE)
                || (mask & 8) != 0 && isAmethystBlock(state)
                || (mask & 16) != 0 && state.isOf(BAMBOO)
                || (mask & 32) != 0 && state.isOf(BEE_NEST)
                || (mask & 64) != 0 && state.isOf(DEEPSLATE);
    }

    private static int[] createFilledArray(int value) {
        int[] array = new int[256];
        Arrays.fill(array, value);
        return array;
    }

    private static void updateColumnRun(
            int y, int column, int[] lastY, int[] runLength, int[] maxRun) {
        int run = lastY[column] == y - 1 ? runLength[column] + 1 : 1;
        lastY[column] = y;
        runLength[column] = run;
        maxRun[column] = Math.max(maxRun[column], run);
    }

    private static SusChunkFinder.PlantStats buildKelpStats(int[] maxRuns, int[] ages) {
        int plantCount = 0;
        int evidence = 0;
        int above = 0;
        int high = 0;

        for (int i = 0; i < maxRuns.length; i++) {
            int run = maxRuns[i];
            if (run > 0) {
                int age = ages[i];
                plantCount++;
                int lengthScore = scaleBetween(run, 11, 26);
                int ageScore = age >= 25 ? 55 : (age == 24 ? 35 : (age >= 0 && age < 20 ? 40 : 0));
                evidence += Math.min(100, lengthScore + ageScore);
                if (run >= 12) {
                    above++;
                }

                if (run >= 14) {
                    high++;
                }
            }
        }

        return new SusChunkFinder.PlantStats(plantCount, evidence, above, high);
    }

    private static SusChunkFinder.PlantStats buildCaveVineStats(int[] maxRuns, int[] ages) {
        int plantCount = 0;
        int evidence = 0;
        int above = 0;
        int high = 0;

        for (int i = 0; i < maxRuns.length; i++) {
            int run = maxRuns[i];
            if (run > 0) {
                int age = ages[i];
                boolean young = age >= 0 && age < 23;
                plantCount++;
                evidence += Math.max(caveVineLengthScore(run), young ? 70 : 0);
                if (run >= 8 || young) {
                    above++;
                }

                if (run >= 14) {
                    high++;
                }
            }
        }

        return new SusChunkFinder.PlantStats(plantCount, evidence, above, high);
    }

    private static SusChunkFinder.PlantStats buildVineStats(int[] maxRuns, int[] scores) {
        int plantCount = 0;
        int evidence = 0;
        int above = 0;
        int high = 0;

        for (int i = 0; i < maxRuns.length; i++) {
            int run = maxRuns[i];
            if (run > 0) {
                int score = Math.min(100, scores[i]);
                plantCount++;
                evidence += Math.max(scaleBetween(run, 5, 32), score);
                if (run >= 6 || score >= 50) {
                    above++;
                }

                if (run >= 14 || score >= 75) {
                    high++;
                }
            }
        }

        return new SusChunkFinder.PlantStats(plantCount, evidence, above, high);
    }

    private static SusChunkFinder.PlantStats buildBambooStats(int[] maxRuns) {
        int plantCount = 0;
        int evidence = 0;
        int above = 0;
        int high = 0;

        for (int run : maxRuns) {
            if (run > 0) {
                plantCount++;
                evidence += scaleBetween(run, 10, 16);
                if (run >= 12) {
                    above++;
                }

                if (run >= 14) {
                    high++;
                }
            }
        }

        return new SusChunkFinder.PlantStats(plantCount, evidence, above, high);
    }

    private static int caveVineLengthScore(int length) {
        if (length <= 7) {
            return 0;
        } else if (length <= 13) {
            return (length - 7) * 35 / 6;
        } else {
            return length <= 20
                    ? 35 + (length - 13) * 25 / 7
                    : Math.min(100, 60 + (length - 20) * 40 / 6);
        }
    }

    private static int scaleBetween(int value, int min, int max) {
        return value <= min ? 0 : Math.min(100, (value - min) * 100 / Math.max(1, max - min));
    }

    private static boolean isTrackedPlantBlock(BlockState state) {
        return isKelp(state)
                || isCaveVines(state)
                || state.isOf(VINE)
                || state.isOf(BAMBOO)
                || state.isOf(BEE_NEST)
                || state.isOf(DEEPSLATE);
    }

    private static boolean isKelp(BlockState state) {
        return state.isOf(KELP) || state.isOf(KELP_PLANT);
    }

    private static boolean isCaveVines(BlockState state) {
        return state.isOf(CAVE_VINES) || state.isOf(CAVE_VINES_PLANT);
    }

    private static int countVineSides(BlockState state) {
        int count = 0;

        for (String side : new String[] {"up", "north", "east", "south", "west"}) {
            if (getBooleanProperty(state, side)) {
                count++;
            }
        }

        return count;
    }

    private static int getIntProperty(BlockState state, String name) {
        Comparable<?> value = getPropertyValue(state, name);
        if (value instanceof Number number) {
            return number.intValue();
        } else if (value == null) {
            return 0;
        } else {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    private static String getStringProperty(BlockState state, String name) {
        Comparable<?> value = getPropertyValue(state, name);
        return value == null ? "" : value.toString();
    }

    private static boolean getBooleanProperty(BlockState state, String name) {
        Comparable<?> value = getPropertyValue(state, name);
        return value instanceof Boolean flag
                ? flag
                : value != null && Boolean.parseBoolean(value.toString());
    }

    private static Comparable<?> getPropertyValue(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (name.equals(property.getName())) {
                return state.getEntries().get(property);
            }
        }

        return null;
    }

    private static boolean isAmethystBlock(BlockState state) {
        return state.isOf(Blocks.AMETHYST_BLOCK)
                || state.isOf(Blocks.BUDDING_AMETHYST)
                || state.isOf(Blocks.AMETHYST_CLUSTER)
                || isAmethystBud(state);
    }

    private static boolean isAmethystBud(BlockState state) {
        return state.isOf(Blocks.SMALL_AMETHYST_BUD)
                || state.isOf(Blocks.MEDIUM_AMETHYST_BUD)
                || state.isOf(Blocks.LARGE_AMETHYST_BUD);
    }

    private static int getAmethystGrowthScore(BlockState state) {
        if (state.isOf(Blocks.AMETHYST_CLUSTER)) {
            return 100;
        } else if (state.isOf(Blocks.LARGE_AMETHYST_BUD)) {
            return 70;
        } else if (state.isOf(Blocks.MEDIUM_AMETHYST_BUD)) {
            return 45;
        } else {
            return state.isOf(Blocks.SMALL_AMETHYST_BUD) ? 20 : -1;
        }
    }

    private static List<ChunkPos> selectTopChunks(Map<ChunkPos, Long> scores, int percent) {
        HashMap<Long, Long> packedScores = new HashMap<>();

        for (Entry<ChunkPos, Long> entry : scores.entrySet()) {
            packedScores.put(packChunkCoords(entry.getKey().x, entry.getKey().z), entry.getValue());
        }

        HashSet<Long> remaining = new HashSet<>(packedScores.keySet());
        ArrayList<Long> selected = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        int clampedPercent = Math.max(0, Math.min(100, percent));

        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().next();
            remaining.remove(seed);
            queue.add(seed);
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            ArrayList<Long> component = new ArrayList<>();

            while (!queue.isEmpty()) {
                long current = queue.removeFirst();
                long currentScore = packedScores.get(current);
                component.add(current);
                min = Math.min(min, currentScore);
                max = Math.max(max, currentScore);
                int chunkX = unpackChunkX(current);
                int chunkZ = unpackChunkZ(current);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            long neighbor = packChunkCoords(chunkX + dx, chunkZ + dz);
                            if (remaining.remove(neighbor)) {
                                queue.addLast(neighbor);
                            }
                        }
                    }
                }
            }

            if (max == min) {
                addCentralChunk(component, selected);
            } else {
                double cutoff = min + ((double) max - min) * clampedPercent / 100.0;

                for (long packed : component) {
                    if (packedScores.get(packed) >= cutoff) {
                        selected.add(packed);
                    }
                }
            }
        }

        ArrayList<ChunkPos> result = new ArrayList<>(selected.size());

        for (long selectedPacked : selected) {
            result.add(new ChunkPos(unpackChunkX(selectedPacked), unpackChunkZ(selectedPacked)));
        }

        return result;
    }

    private static List<ChunkPos> selectSpacedChunks(Map<ChunkPos, Long> scores) {
        List<ChunkPos> top = selectTopChunks(scores, CHUNK_SELECT_PERCENT);
        top.sort(
                (a, b) -> {
                    int byScore =
                            Long.compare(scores.getOrDefault(b, 0L), scores.getOrDefault(a, 0L));
                    if (byScore != 0) {
                        return byScore;
                    } else {
                        int byX = Integer.compare(a.x, b.x);
                        return byX != 0 ? byX : Integer.compare(a.z, b.z);
                    }
                });
        int spacing = 5;
        ArrayList<ChunkPos> spaced = new ArrayList<>();

        for (ChunkPos chunk : top) {
            boolean tooClose = false;

            for (ChunkPos kept : spaced) {
                if (Math.abs(chunk.x - kept.x) < spacing && Math.abs(chunk.z - kept.z) < spacing) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                spaced.add(chunk);
            }
        }

        return spaced;
    }

    private static void addCentralChunk(List<Long> component, List<Long> output) {
        if (!component.isEmpty()) {
            double meanX =
                    component.stream()
                            .mapToDouble(SusChunkFinder::unpackChunkX)
                            .average()
                            .orElse(0.0);
            double meanZ =
                    component.stream()
                            .mapToDouble(SusChunkFinder::unpackChunkZ)
                            .average()
                            .orElse(0.0);
            double minDistance = Double.POSITIVE_INFINITY;

            for (long packed : component) {
                double dx = unpackChunkX(packed) - meanX;
                double dz = unpackChunkZ(packed) - meanZ;
                minDistance = Math.min(minDistance, dx * dx + dz * dz);
            }

            for (long candidate : component) {
                double offsetX = unpackChunkX(candidate) - meanX;
                double offsetZ = unpackChunkZ(candidate) - meanZ;
                if (Math.abs(offsetX * offsetX + offsetZ * offsetZ - minDistance) < 1.0E-9) {
                    output.add(candidate);
                }
            }
        }
    }

    private static List<SusChunkFinder.ChunkRect> mergeChunksIntoRects(List<ChunkPos> chunks) {
        HashSet<Long> remaining = new HashSet<>();

        for (ChunkPos chunk : chunks) {
            remaining.add(packChunkCoords(chunk.x, chunk.z));
        }

        ArrayList<SusChunkFinder.ChunkRect> rects = new ArrayList<>();

        while (!remaining.isEmpty()) {
            long start = remaining.iterator().next();

            for (long packed : remaining) {
                int candidateZ = unpackChunkZ(packed);
                int startZ = unpackChunkZ(start);
                if (candidateZ < startZ
                        || candidateZ == startZ && unpackChunkX(packed) < unpackChunkX(start)) {
                    start = packed;
                }
            }

            int minX = unpackChunkX(start);
            int minZ = unpackChunkZ(start);
            int maxX = minX + 1;

            while (remaining.contains(packChunkCoords(maxX, minZ))) {
                maxX++;
            }

            int maxZ = minZ + 1;

            while (isRowFilled(remaining, minX, maxX, maxZ)) {
                maxZ++;
            }

            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    remaining.remove(packChunkCoords(x, z));
                }
            }

            rects.add(new SusChunkFinder.ChunkRect(minX, minZ, maxX, maxZ));
        }

        return rects;
    }

    private static boolean isRowFilled(Set<Long> cells, int minX, int maxX, int z) {
        for (int x = minX; x < maxX; x++) {
            if (!cells.contains(packChunkCoords(x, z))) {
                return false;
            }
        }

        return true;
    }

    private static long packChunkCoords(int x, int z) {
        return (long) x << 32 | z & 4294967295L;
    }

    private static int unpackChunkX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackChunkZ(long packed) {
        return (int) packed;
    }

    private record ChunkCluster(
            int minimumX,
            int maximumX,
            int minimumZ,
            int maximumZ,
            List<ChunkPos> chunks,
            int totalHits,
            int maximumHits,
            boolean grown) {
        private int centreX() {
            return (int) Math.round((this.minimumX + this.maximumX) / 2.0);
        }

        private int centreZ() {
            return (int) Math.round((this.minimumZ + this.maximumZ) / 2.0);
        }
    }

    private record ChunkRect(
            int minimumX, int minimumZ, int maximumXExclusive, int maximumZExclusive) {}

    private record ChunkScanResult(
            int suspiciousEvidence,
            boolean naturalAmethyst,
            boolean amethystGeodeObserved,
            int growthFaces,
            int maturityPoints,
            SusChunkFinder.GrowthEvidence growthEvidence) {}

    private record ChunkScanTask(
            WorldChunk chunk,
            World world,
            int scanMask,
            boolean donutFolia,
            long generation,
            int attempt) {}

    private record GeodeProbeResult(int hits, boolean exposedBudding) {}

    private record GrowthEvidence(
            SusChunkFinder.PlantStats kelp,
            SusChunkFinder.PlantStats caveVines,
            SusChunkFinder.PlantStats vines,
            SusChunkFinder.PlantStats bamboo,
            int maximumBeeNestHoney,
            int rotatedDeepslateCount) {}

    private record PlantStats(
            int plantCount, int evidencePoints, int aboveVanillaPlants, int highPlants) {
        private SusChunkFinder.PlantStats add(SusChunkFinder.PlantStats other) {
            return new SusChunkFinder.PlantStats(
                    this.plantCount + other.plantCount,
                    this.evidencePoints + other.evidencePoints,
                    this.aboveVanillaPlants + other.aboveVanillaPlants,
                    this.highPlants + other.highPlants);
        }

        private SusChunkFinder.PlantStats subtract(SusChunkFinder.PlantStats other) {
            return new SusChunkFinder.PlantStats(
                    Math.max(0, this.plantCount - other.plantCount),
                    Math.max(0, this.evidencePoints - other.evidencePoints),
                    Math.max(0, this.aboveVanillaPlants - other.aboveVanillaPlants),
                    Math.max(0, this.highPlants - other.highPlants));
        }
    }

    private static final class ScanBuffers {
        private final ArrayList<BlockPos> buddingAmethystPositions = new ArrayList<>();
        private final int[] kelpLastY = new int[256];
        private final int[] kelpRunLength = new int[256];
        private final int[] kelpMaxRun = new int[256];
        private final int[] kelpAge = new int[256];
        private final int[] caveVinesLastY = new int[256];
        private final int[] caveVinesRunLength = new int[256];
        private final int[] caveVinesMaxRun = new int[256];
        private final int[] caveVinesAge = new int[256];
        private final int[] vinesLastY = new int[256];
        private final int[] vinesRunLength = new int[256];
        private final int[] vinesMaxRun = new int[256];
        private final int[] vinesScore = new int[256];
        private final int[] bambooLastY = new int[256];
        private final int[] bambooRunLength = new int[256];
        private final int[] bambooMaxRun = new int[256];

        private void reset(int scanMask) {
            this.buddingAmethystPositions.clear();
            if ((scanMask & 1) != 0) {
                resetColumnArrays(this.kelpLastY, this.kelpRunLength, this.kelpMaxRun);
                Arrays.fill(this.kelpAge, -1);
            }

            if ((scanMask & 2) != 0) {
                resetColumnArrays(
                        this.caveVinesLastY, this.caveVinesRunLength, this.caveVinesMaxRun);
                Arrays.fill(this.caveVinesAge, -1);
            }

            if ((scanMask & 4) != 0) {
                resetColumnArrays(this.vinesLastY, this.vinesRunLength, this.vinesMaxRun);
                Arrays.fill(this.vinesScore, 0);
            }

            if ((scanMask & 16) != 0) {
                resetColumnArrays(this.bambooLastY, this.bambooRunLength, this.bambooMaxRun);
            }
        }

        private static void resetColumnArrays(int[] lastY, int[] runLength, int[] maxRun) {
            Arrays.fill(lastY, Integer.MIN_VALUE);
            Arrays.fill(runLength, 0);
            Arrays.fill(maxRun, 0);
        }
    }
}
