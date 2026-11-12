package odinclient.module.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.core.Categories;
import odinclient.event.ChunkLoadEvent;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.module.hud.regionmap.RegionBorderBuilder;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.util.ScannerExecutor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Environment(EnvType.CLIENT)
public final class ChunkFinder extends Module {
    private static final int NEAR_RADIUS = 7;
    private static final int MAX_RADIUS = 32;
    private static final int MAX_PENDING_SCANS = 8;
    private static final int MAX_TRACERS = 64;
    private static final int FILL_ALPHA = 110;
    private static final EntityColor FILL_COLOR = new EntityColor(255, 220, 45, FILL_ALPHA);
    private static final EntityColor TRACER_COLOR = new EntityColor(255, 220, 45, 220);
    private static final EntityColor OUTLINE_COLOR = new EntityColor(255, 220, 45, 245);
    private static final int MAX_GEODE_CLUSTER_SIZE = 2048;
    private static final double TRACER_Y = 63.0;
    private static final int FLAG_HOLES = 1;
    private static final int FLAG_FISH = 2;
    private static final int FLAG_HOSTILE = 4;
    private static final int FLAG_CACTUS = 8;
    private static final int FLAG_GEODES = 16;
    private static final int FLAG_SUGAR_CANE = 32;
    private static final int[] NEIGHBOR_DX = new int[] {1, -1, 0, 0, 0, 0};
    private static final int[] NEIGHBOR_DY = new int[] {0, 0, 1, -1, 0, 0};
    private static final int[] NEIGHBOR_DZ = new int[] {0, 0, 0, 0, 1, -1};
    private final Setting<Boolean> holes;
    private final Setting<Boolean> fish;
    private final Setting<Boolean> hostileMobs;
    private final Setting<Boolean> minedGeodes;
    private final Setting<Boolean> cactus;
    private final Setting<Boolean> sugarCane;
    private final Setting<Boolean> smartAdjustment;
    private final Setting<Boolean> chatAlerts;
    private final Setting<Boolean> tracers;
    private final ArrayDeque<Long> nearScanQueue = new ArrayDeque<>();
    private final ArrayDeque<Long> farScanQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunks = new HashSet<>();
    private final Set<Long> queuedFarChunks = new HashSet<>();
    private final Map<Long, Integer> scannedFlags = new ConcurrentHashMap<>();
    private final Map<Long, ChunkFinder.ChunkScanResult> scanResults = new ConcurrentHashMap<>();
    private final Map<Long, Integer> detectedChunks = new HashMap<>();
    private final Map<Long, Integer> chunkRevisions = new ConcurrentHashMap<>();
    private final Map<Long, Integer> announcedFlags = new HashMap<>();
    private final AtomicInteger pendingScans = new AtomicInteger();
    private final AtomicBoolean mergeRunning = new AtomicBoolean();
    private volatile List<ChunkAreaMerger.ChunkArea> markers = List.of();
    private List<ChunkAreaMerger.ChunkArea> borderSourceMarkers = List.of();
    private RegionBorderBuilder.BorderResult borderResult = RegionBorderBuilder.BorderResult.EMPTY;
    private ChunkPos borderPlayerChunk;
    private volatile ChunkFinder.MergedMarkers pendingMerge;
    private volatile long markerVersion;
    private volatile long appliedVersion = -1L;
    private volatile boolean lastSmartAdjustment = true;
    private int nextMergeTick;
    private volatile Map<Long, ChunkFinder.MobCounts> mobs = Map.of();
    private volatile Object currentWorld;
    private volatile long generation;
    private int tickCounter;
    private int lastScanFlags = -1;
    private int nextAnnounceTick;
    private int farScanCursor;

    public ChunkFinder() {
        super(
                Categories.MAIN,
                "chunkfinder",
                "Marks search areas from enclosed holes, selected mobs, mined geodes and fully"
                    + " grown plants.");
        this.title = "Sus Chunkfinder v2";
        SettingGroup general = this.settings.getGeneral();
        this.holes =
                general.add(
                        new BooleanSetting.Builder()
                                .name("1x1x1-holes")
                                .description(
                                        "Find one-block air cavities with a three-block solid"
                                            + " enclosure from Y 18 through Y 50, without adjacent"
                                            + " ice.")
                                .defaultValue(true)
                                .build());
        this.fish =
                general.add(
                        new BooleanSetting.Builder()
                                .name("fish")
                                .description(
                                        "Detect every loaded fish species, including cod, salmon,"
                                            + " pufferfish and tropical fish.")
                                .defaultValue(true)
                                .build());
        this.hostileMobs =
                general.add(
                        new BooleanSetting.Builder()
                                .name("hostile-mobs")
                                .description("Detect loaded hostile mobs in a chunk.")
                                .defaultValue(true)
                                .build());
        this.minedGeodes =
                general.add(
                        new BooleanSetting.Builder()
                                .name("mined-geodes")
                                .description(
                                        "Flag geodes when at least 80% of the estimated amethyst"
                                            + " layer is gone.")
                                .defaultValue(true)
                                .build());
        this.cactus =
                general.add(
                        new BooleanSetting.Builder()
                                .name("fully-grown-cactus")
                                .description(
                                        "Scan loaded chunks up to 32 chunks away. Require five"
                                            + " cactus columns, each three or more blocks tall,"
                                            + " within a 3x3-chunk area. Can also occur naturally.")
                                .defaultValue(false)
                                .build());
        this.sugarCane =
                general.add(
                        new BooleanSetting.Builder()
                                .name("fully-grown-sugar-cane")
                                .description(
                                        "Scan loaded chunks up to 32 chunks away. Require three"
                                            + " sugar cane columns, each three or more blocks tall,"
                                            + " within a 3x3-chunk area. Can also occur naturally.")
                                .defaultValue(false)
                                .build());
        this.smartAdjustment =
                general.add(
                        new BooleanSetting.Builder()
                                .name("smart-adjustment")
                                .description(
                                        "Combine nearby detections into their shared search area;"
                                            + " flag sizes vary with the evidence.")
                                .defaultValue(true)
                                .build());
        this.chatAlerts =
                general.add(
                        new BooleanSetting.Builder()
                                .name("chat-alerts")
                                .description(
                                        "Show a local chat message when a new chunk signal is"
                                            + " found.")
                                .defaultValue(true)
                                .build());
        this.tracers =
                general.add(
                        new BooleanSetting.Builder()
                                .name("tracers")
                                .description(
                                        "Draw tracers to the exact center of each adjusted flag.")
                                .defaultValue(false)
                                .build());
    }

    @Override
    public void onActivate() {
        this.reset();
        this.currentWorld = this.mc.world;
        this.queueNearbyChunks();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    public String getInfoString() {
        if (this.mc.player == null) {
            return null;
        } else {
            int count = this.detectedChunks.size();
            return count == 0 ? null : "Chunks:" + count;
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        ChunkPos chunkPos = event.getChunk().getPos();

        for (int x = chunkPos.x - 1; x <= chunkPos.x + 1; x++) {
            for (int z = chunkPos.z - 1; z <= chunkPos.z + 1; z++) {
                this.queueChunk(x, z, true);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world != null && this.mc.player != null) {
            if (this.currentWorld != this.mc.world) {
                this.reset();
                this.currentWorld = this.mc.world;
                this.queueNearbyChunks();
            }

            int scanFlags = this.getEnabledScanFlags() | 768;
            if (scanFlags != this.lastScanFlags) {
                this.generation++;
                this.lastScanFlags = scanFlags;
                this.scanResults.clear();
                this.scannedFlags.clear();
                this.queuedChunks.clear();
                this.queuedFarChunks.clear();
                this.nearScanQueue.clear();
                this.farScanQueue.clear();
                this.chunkRevisions.clear();
                this.farScanCursor = 0;
                this.queueNearbyChunks();
            }

            if (++this.tickCounter % 20 == 0) {
                this.countMobsPerChunk();
                this.queueNearbyChunks();
                this.rescanFarChunks();
            }

            this.revalidateHoles();
            if (this.tickCounter % 100 == 0) {
                ChunkPos playerChunk = this.mc.player.getChunkPos();

                for (int x = playerChunk.x - 3; x <= playerChunk.x + 3; x++) {
                    for (int z = playerChunk.z - 3; z <= playerChunk.z + 3; z++) {
                        this.queueChunk(x, z, true);
                    }
                }
            }

            this.updateDetectedChunks();
            this.updateMarkerMerge();
            this.dispatchScans();
            this.announceNewFinding();
        }
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.mc.player != null && this.currentWorld == this.mc.world) {
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            List<ChunkAreaMerger.ChunkArea> currentMarkers = this.markers;
            if (this.borderSourceMarkers != currentMarkers
                    || this.borderPlayerChunk == null
                    || this.borderPlayerChunk.x != playerChunk.x
                    || this.borderPlayerChunk.z != playerChunk.z) {
                this.borderResult =
                        RegionBorderBuilder.build(
                                currentMarkers, playerChunk.x, playerChunk.z, MAX_RADIUS);
                this.borderSourceMarkers = currentMarkers;
                this.borderPlayerChunk = playerChunk;
            }

            List<ChunkAreaMerger.ChunkArea> areas = this.borderResult.areas();
            ChunkMarkerRenderer.setRegionOverlay(
                    this.borderResult, FILL_COLOR.toArgb(), OUTLINE_COLOR.toArgb());
            if (Boolean.TRUE.equals(this.tracers.getValue())) {
                double playerX = playerChunk.x * 16.0 + 8.0;
                double playerZ = playerChunk.z * 16.0 + 8.0;
                ArrayList<ChunkAreaMerger.ChunkArea> sorted = new ArrayList<>(areas);
                sorted.sort(
                        Comparator.comparingDouble(
                                a -> {
                                    double dx = a.centerBlockX() - playerX;
                                    double dz = a.centerBlockZ() - playerZ;
                                    return dx * dx + dz * dz;
                                }));

                for (int i = 0; i < Math.min(MAX_TRACERS, sorted.size()); i++) {
                    ChunkAreaMerger.ChunkArea area = sorted.get(i);
                    event.renderer.stableTracer(
                            new Vec3d(area.centerBlockX(), 63.04, area.centerBlockZ()),
                            TRACER_COLOR,
                            1.1F);
                }
            }
        }
    }

    private void updateMarkerMerge() {
        boolean adjusted = Boolean.TRUE.equals(this.smartAdjustment.getValue());
        if (adjusted != this.lastSmartAdjustment) {
            this.lastSmartAdjustment = adjusted;
            this.markerVersion++;
            this.nextMergeTick = 0;
        }

        ChunkFinder.MergedMarkers merged = this.pendingMerge;
        if (merged != null) {
            this.pendingMerge = null;
            if (merged.generation == this.generation
                    && merged.world == this.currentWorld
                    && merged.adjusted == adjusted) {
                this.markers = merged.markers;
                this.appliedVersion = merged.version;
            }
        }

        if (this.appliedVersion != this.markerVersion
                && this.tickCounter >= this.nextMergeTick
                && this.mergeRunning.compareAndSet(false, true)) {
            ArrayList<ChunkAreaMerger.ChunkCoord> coords =
                    new ArrayList<>(this.detectedChunks.size());

            for (long packed : this.detectedChunks.keySet()) {
                coords.add(
                        new ChunkAreaMerger.ChunkCoord(
                                ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed)));
            }

            long version = this.markerVersion;
            long mergeGeneration = this.generation;
            Object mergeWorld = this.currentWorld;
            this.nextMergeTick = this.tickCounter + 5;
            ScannerExecutor.getExecutor()
                    .execute(
                            () -> {
                                try {
                                    List<ChunkAreaMerger.ChunkArea> areas =
                                            ChunkAreaMerger.mergeChunks(coords, adjusted);
                                    this.pendingMerge =
                                            new ChunkFinder.MergedMarkers(
                                                    areas,
                                                    version,
                                                    mergeGeneration,
                                                    mergeWorld,
                                                    adjusted);
                                } finally {
                                    this.mergeRunning.set(false);
                                }
                            });
        }
    }

    private static boolean isWithinChunkRadius(long packed, ChunkPos center, int radius) {
        return Math.abs((long) ChunkPos.getPackedX(packed) - center.x) <= radius
                && Math.abs((long) ChunkPos.getPackedZ(packed) - center.z) <= radius;
    }

    private void updateDetectedChunks() {
        ChunkPos playerChunk = this.mc.player.getChunkPos();
        if (this.detectedChunks
                .entrySet()
                .removeIf(e -> !isWithinChunkRadius(e.getKey(), playerChunk, MAX_RADIUS))) {
            this.markerVersion++;
        }

        HashSet<Long> keys = new HashSet<>(this.scanResults.keySet());
        keys.addAll(this.mobs.keySet());

        for (long packed : keys) {
            if (isWithinChunkRadius(packed, playerChunk, MAX_RADIUS)) {
                int flags =
                        this.computeChunkFlags(
                                packed, this.scanResults.get(packed), this.mobs.get(packed));
                if (flags != 0) {
                    if (!this.detectedChunks.containsKey(packed)) {
                        this.markerVersion++;
                    }

                    this.detectedChunks.merge(packed, flags, (old, added) -> old | added);
                }
            }
        }

        if (this.tickCounter % 20 == 0) {
            this.scanResults
                    .keySet()
                    .removeIf(key -> !isWithinChunkRadius(key, playerChunk, MAX_RADIUS));
            this.scannedFlags
                    .keySet()
                    .removeIf(key -> !isWithinChunkRadius(key, playerChunk, MAX_RADIUS));
            this.chunkRevisions
                    .keySet()
                    .removeIf(key -> !isWithinChunkRadius(key, playerChunk, MAX_RADIUS));
            this.announcedFlags
                    .keySet()
                    .removeIf(key -> !isWithinChunkRadius(key, playerChunk, MAX_RADIUS));
            this.queuedChunks.removeIf(key -> !isWithinChunkRadius(key, playerChunk, MAX_RADIUS));
            this.queuedFarChunks.removeIf(
                    key -> !isWithinChunkRadius(key, playerChunk, MAX_RADIUS));
            this.nearScanQueue.removeIf(key -> !isWithinChunkRadius(key, playerChunk, MAX_RADIUS));
            this.farScanQueue.removeIf(key -> !isWithinChunkRadius(key, playerChunk, MAX_RADIUS));
        }
    }

    private int computeChunkFlags(
            long packed, ChunkFinder.ChunkScanResult scan, ChunkFinder.MobCounts mobCounts) {
        int flags = 0;
        int chunkX = ChunkPos.getPackedX(packed);
        int chunkZ = ChunkPos.getPackedZ(packed);
        boolean near = this.isInNearRange(chunkX, chunkZ);
        boolean inRange = this.isWithinPlayerRadius(chunkX, chunkZ, MAX_RADIUS);
        if (near
                && scan != null
                && !scan.holes.isEmpty()
                && Boolean.TRUE.equals(this.holes.getValue())) {
            flags |= 1;
        }

        if (near
                && scan != null
                && !scan.geodes.isEmpty()
                && Boolean.TRUE.equals(this.minedGeodes.getValue())) {
            flags |= 16;
        }

        boolean wantCactus =
                inRange
                        && scan != null
                        && !scan.cacti.isEmpty()
                        && Boolean.TRUE.equals(this.cactus.getValue());
        boolean wantSugarCane =
                inRange
                        && scan != null
                        && !scan.sugarCane.isEmpty()
                        && Boolean.TRUE.equals(this.sugarCane.getValue());
        if (wantCactus || wantSugarCane) {
            FarmDensityCheck.CropCounts crops = this.getCropCounts(packed);
            if (wantCactus && crops.enoughCactus()) {
                flags |= 8;
            }

            if (wantSugarCane && crops.enoughSugarCane()) {
                flags |= 32;
            }
        }

        if (near
                && mobCounts != null
                && mobCounts.fish > 0
                && Boolean.TRUE.equals(this.fish.getValue())) {
            flags |= 2;
        }

        if (near
                && mobCounts != null
                && mobCounts.hostile > 0
                && Boolean.TRUE.equals(this.hostileMobs.getValue())) {
            flags |= 4;
        }

        return flags;
    }

    private FarmDensityCheck.CropCounts getCropCounts(long packed) {
        return FarmDensityCheck.sumNeighborhood(
                ChunkPos.getPackedX(packed),
                ChunkPos.getPackedZ(packed),
                (x, z) -> {
                    if (!this.isWithinPlayerRadius(x, z, MAX_RADIUS)) {
                        return null;
                    } else {
                        ChunkFinder.ChunkScanResult scan =
                                this.scanResults.get(ChunkPos.toLong(x, z));
                        return scan == null
                                ? null
                                : new FarmDensityCheck.CropCounts(
                                        scan.cacti.size(), scan.sugarCane.size());
                    }
                });
    }

    private void announceNewFinding() {
        if (Boolean.TRUE.equals(this.chatAlerts.getValue())
                && this.tickCounter >= this.nextAnnounceTick) {
            HashSet<Long> keys = new HashSet<>(this.scanResults.keySet());
            keys.addAll(this.mobs.keySet());

            for (long packed : keys) {
                if (this.isWithinPlayerRadius(
                        ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), MAX_RADIUS)) {
                    ChunkFinder.ChunkScanResult scan = this.scanResults.get(packed);
                    ChunkFinder.MobCounts mobCounts = this.mobs.get(packed);
                    int newFlags =
                            this.computeChunkFlags(packed, scan, mobCounts)
                                    & ~this.announcedFlags.getOrDefault(packed, 0);
                    if (newFlags != 0) {
                        ArrayList<String> findings = new ArrayList<>();
                        if ((newFlags & 1) != 0) {
                            BlockPos hole = scan.holes.get(0);
                            findings.add(
                                    scan.holes.size()
                                            + " enclosed 1x1x1 hole(s), first at "
                                            + hole.getX()
                                            + ", "
                                            + hole.getY()
                                            + ", "
                                            + hole.getZ());
                        }

                        if ((newFlags & 2) != 0) {
                            findings.add(mobCounts.fish + " fish");
                        }

                        if ((newFlags & 4) != 0) {
                            findings.add(mobCounts.hostile + " hostile mob(s)");
                        }

                        if ((newFlags & 16) != 0) {
                            BlockPos geode = scan.geodes.get(0);
                            findings.add(
                                    "estimated 80%+ mined geode near "
                                            + geode.getX()
                                            + ", "
                                            + geode.getY()
                                            + ", "
                                            + geode.getZ());
                        }

                        if ((newFlags & 8) != 0) {
                            BlockPos cactus = scan.cacti.get(0);
                            findings.add(
                                    this.getCropCounts(packed).cactus()
                                            + " fully grown cactus column(s) in a 3x3-chunk area,"
                                            + " first at "
                                            + cactus.getX()
                                            + ", "
                                            + cactus.getY()
                                            + ", "
                                            + cactus.getZ());
                        }

                        if ((newFlags & 32) != 0) {
                            BlockPos cane = scan.sugarCane.get(0);
                            findings.add(
                                    this.getCropCounts(packed).sugarCane()
                                            + " fully grown sugar cane column(s) in a 3x3-chunk"
                                            + " area, first at "
                                            + cane.getX()
                                            + ", "
                                            + cane.getY()
                                            + ", "
                                            + cane.getZ());
                        }

                        int chunkX = ChunkPos.getPackedX(packed);
                        int chunkZ = ChunkPos.getPackedZ(packed);
                        this.info(
                                "Found %s in chunk %d, %d.",
                                String.join("; ", findings), chunkX, chunkZ);
                        this.announcedFlags.merge(packed, newFlags, (old, added) -> old | added);
                        this.nextAnnounceTick = this.tickCounter + 10;
                        return;
                    }
                }
            }
        }
    }

    private void countMobsPerChunk() {
        HashMap<Long, int[]> counts = new HashMap<>();

        for (Entity entity : this.mc.world.getEntities()) {
            if (entity.isAlive()) {
                int kind =
                        entity instanceof FishEntity
                                ? 0
                                : (entity instanceof HostileEntity ? 1 : -1);
                if (kind >= 0) {
                    ChunkPos chunkPos = entity.getChunkPos();
                    if (this.isInNearRange(chunkPos.x, chunkPos.z)) {
                        counts.computeIfAbsent(chunkPos.toLong(), key -> new int[2])[kind]++;
                    }
                }
            }
        }

        HashMap<Long, ChunkFinder.MobCounts> result = new HashMap<>();
        counts.forEach(
                (packed, perKind) ->
                        result.put(packed, new ChunkFinder.MobCounts(perKind[0], perKind[1])));
        this.mobs = Map.copyOf(result);
    }

    private boolean isInNearRange(int x, int z) {
        return this.isWithinPlayerRadius(x, z, NEAR_RADIUS);
    }

    private boolean isWithinPlayerRadius(int x, int z, int radius) {
        if (this.mc.player == null) {
            return false;
        } else {
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            return ChunkFinderFlags.isWithinChunks(x, z, playerChunk.x, playerChunk.z, radius);
        }
    }

    private int getEnabledScanFlags() {
        return (Boolean.TRUE.equals(this.holes.getValue()) ? 1 : 0)
                | (Boolean.TRUE.equals(this.minedGeodes.getValue()) ? 16 : 0)
                | (Boolean.TRUE.equals(this.cactus.getValue()) ? 8 : 0)
                | (Boolean.TRUE.equals(this.sugarCane.getValue()) ? 32 : 0);
    }

    private int getActiveFlagsForChunk(int x, int z) {
        if (this.mc.player == null) {
            return 0;
        } else {
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            return ChunkFinderFlags.getActiveFlags(
                    x, z, playerChunk.x, playerChunk.z, this.getEnabledScanFlags());
        }
    }

    private void revalidateHoles() {
        if (Boolean.TRUE.equals(this.holes.getValue())) {
            ClientWorld world = this.mc.world;
            for (Entry<Long, ChunkFinder.ChunkScanResult> entry : this.scanResults.entrySet()) {
                long packed = entry.getKey();
                ChunkFinder.ChunkScanResult scan = entry.getValue();
                if (!scan.holes.isEmpty()) {
                    boolean nearLoaded =
                            this.isInNearRange(
                                            ChunkPos.getPackedX(packed),
                                            ChunkPos.getPackedZ(packed))
                                    && world.isChunkLoaded(
                                            ChunkPos.getPackedX(packed),
                                            ChunkPos.getPackedZ(packed));
                    if (nearLoaded
                            || !scan.holes.stream()
                                    .allMatch(h -> HoleScanBounds.isInScanRange(h.getY()))) {
                        ArrayList<BlockPos> kept = new ArrayList<>(scan.holes.size());

                        for (BlockPos hole : scan.holes) {
                            if (HoleScanBounds.isInScanRange(hole.getY())
                                    && (!nearLoaded
                                            || world.getBlockState(hole).isOf(Blocks.AIR)
                                                    && isEnclosedHole(world, hole, 3))) {
                                kept.add(hole);
                            }
                        }

                        if (kept.size() != scan.holes.size()) {
                            ChunkFinder.ChunkScanResult updated =
                                    new ChunkFinder.ChunkScanResult(
                                            List.copyOf(kept),
                                            scan.geodes,
                                            scan.cacti,
                                            scan.sugarCane);
                            if (updated.holes.isEmpty()
                                    && scan.holes.stream()
                                            .anyMatch(
                                                    h ->
                                                            !HoleScanBounds.isInScanRange(h.getY())
                                                                    || nearLoaded
                                                                            && isNextToIce(
                                                                                    world, h))) {
                                int current = this.detectedChunks.getOrDefault(packed, 0);
                                int remaining = current & -2;
                                if (current != remaining) {
                                    if (remaining == 0) {
                                        this.detectedChunks.remove(packed);
                                        this.markerVersion++;
                                    } else {
                                        this.detectedChunks.put(packed, remaining);
                                    }
                                }
                            }

                            if (updated.isEmpty()) {
                                this.scanResults.remove(packed, scan);
                            } else {
                                this.scanResults.replace(packed, scan, updated);
                            }
                        }
                    }
                }
            }
        }
    }

    private void queueNearbyChunks() {
        if (this.mc.world != null && this.mc.player != null) {
            int flags = this.getEnabledScanFlags();
            if (flags != 0) {
                ChunkPos playerChunk = this.mc.player.getChunkPos();
                int scanRadius = ChunkFinderFlags.getScanRadius(flags);

                for (int ring = 0; ring <= scanRadius; ring++) {
                    for (int x = playerChunk.x - ring; x <= playerChunk.x + ring; x++) {
                        this.queueChunk(x, playerChunk.z - ring, false);
                        if (ring != 0) {
                            this.queueChunk(x, playerChunk.z + ring, false);
                        }
                    }

                    for (int z = playerChunk.z - ring + 1; z < playerChunk.z + ring; z++) {
                        this.queueChunk(playerChunk.x - ring, z, false);
                        this.queueChunk(playerChunk.x + ring, z, false);
                    }
                }
            }
        }
    }

    private void queueChunk(int x, int z, boolean rescan) {
        int activeFlags = this.getActiveFlagsForChunk(x, z);
        if (this.mc.world != null && activeFlags != 0 && this.mc.world.isChunkLoaded(x, z)) {
            long packed = ChunkPos.toLong(x, z);
            if (rescan) {
                this.scannedFlags.remove(packed);
                this.chunkRevisions.merge(packed, 1, Integer::sum);
            } else {
                this.chunkRevisions.putIfAbsent(packed, 0);
            }

            if ((activeFlags & ~this.scannedFlags.getOrDefault(packed, 0)) != 0) {
                boolean near = this.isInNearRange(x, z);
                if (this.queuedChunks.add(packed)) {
                    if (near) {
                        this.nearScanQueue.addLast(packed);
                    } else {
                        this.farScanQueue.addLast(packed);
                        this.queuedFarChunks.add(packed);
                    }
                } else if (near && this.queuedFarChunks.remove(packed)) {
                    this.farScanQueue.remove(packed);
                    this.nearScanQueue.addLast(packed);
                }
            }
        }
    }

    private void rescanFarChunks() {
        int farFlags = this.getEnabledScanFlags() & 40;
        if (farFlags != 0) {
            ChunkPos playerChunk = this.mc.player.getChunkPos();

            for (int i = 0; i < 64; i++) {
                int index = this.farScanCursor;
                this.farScanCursor = (index + 1) % 4225;
                int x = playerChunk.x + ChunkFinderFlags.unpackOffsetX(index);
                int z = playerChunk.z + ChunkFinderFlags.unpackOffsetZ(index);
                long packed = ChunkPos.toLong(x, z);
                if (!this.isInNearRange(x, z)
                        && !this.queuedChunks.contains(packed)
                        && (this.scannedFlags.getOrDefault(packed, 0) & farFlags) == farFlags) {
                    this.queueChunk(x, z, true);
                }
            }
        }
    }

    private void dispatchScans() {
        ClientWorld world = this.mc.world;

        while (this.pendingScans.get() < MAX_PENDING_SCANS
                && (!this.nearScanQueue.isEmpty() || !this.farScanQueue.isEmpty())) {
            long packed =
                    !this.nearScanQueue.isEmpty()
                            ? this.nearScanQueue.removeFirst()
                            : this.farScanQueue.removeFirst();
            this.queuedChunks.remove(packed);
            this.queuedFarChunks.remove(packed);
            int chunkX = ChunkPos.getPackedX(packed);
            int chunkZ = ChunkPos.getPackedZ(packed);
            int activeFlags = this.getActiveFlagsForChunk(chunkX, chunkZ);
            if (activeFlags != 0 && world.isChunkLoaded(chunkX, chunkZ)) {
                int missingFlags = activeFlags & ~this.scannedFlags.getOrDefault(packed, 0);
                if (missingFlags != 0) {
                    boolean neighborsReady =
                            (missingFlags & 17) == 0 || areNeighborsLoaded(world, chunkX, chunkZ);
                    int flags =
                            ChunkFinderFlags.filterPendingFlags(
                                    activeFlags,
                                    this.scannedFlags.getOrDefault(packed, 0),
                                    neighborsReady);
                    if (flags != 0) {
                        WorldChunk chunk =
                                this.mc
                                        .world
                                        .getChunkManager()
                                        .getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                        if (chunk != null && !chunk.isEmpty()) {
                            this.scannedFlags.merge(packed, flags, (old, added) -> old | added);
                            boolean scanHoles = (flags & 1) != 0;
                            boolean scanGeodes = (flags & 16) != 0;
                            boolean scanCactus = (flags & 8) != 0;
                            boolean scanSugarCane = (flags & 32) != 0;
                            long scanGeneration = this.generation;
                            int revision = this.chunkRevisions.getOrDefault(packed, 0);
                            this.pendingScans.incrementAndGet();
                            ScannerExecutor.getExecutor()
                                    .execute(
                                            () -> {
                                                try {
                                                    ChunkFinder.ChunkScanResult fresh =
                                                            this.scanChunk(
                                                                    world,
                                                                    chunk,
                                                                    chunkX,
                                                                    chunkZ,
                                                                    scanHoles,
                                                                    scanGeodes,
                                                                    scanCactus,
                                                                    scanSugarCane,
                                                                    3,
                                                                    scanGeneration);
                                                    this.scanResults.compute(
                                                            packed,
                                                            (key, existing) -> {
                                                                if (this.generation
                                                                                == scanGeneration
                                                                        && this.currentWorld
                                                                                == world
                                                                        && this.chunkRevisions
                                                                                        .getOrDefault(
                                                                                                packed,
                                                                                                -1)
                                                                                == revision) {
                                                                    ChunkFinder.ChunkScanResult
                                                                            previous =
                                                                                    existing == null
                                                                                            ? ChunkFinder
                                                                                                    .ChunkScanResult
                                                                                                    .EMPTY
                                                                                            : existing;
                                                                    ChunkFinder.ChunkScanResult
                                                                            merged =
                                                                                    new ChunkFinder
                                                                                            .ChunkScanResult(
                                                                                            ChunkFinderFlags
                                                                                                    .selectList(
                                                                                                            flags,
                                                                                                            1,
                                                                                                            previous.holes,
                                                                                                            fresh.holes),
                                                                                            ChunkFinderFlags
                                                                                                    .selectList(
                                                                                                            flags,
                                                                                                            16,
                                                                                                            previous.geodes,
                                                                                                            fresh.geodes),
                                                                                            ChunkFinderFlags
                                                                                                    .selectList(
                                                                                                            flags,
                                                                                                            8,
                                                                                                            previous.cacti,
                                                                                                            fresh.cacti),
                                                                                            ChunkFinderFlags
                                                                                                    .selectList(
                                                                                                            flags,
                                                                                                            32,
                                                                                                            previous.sugarCane,
                                                                                                            fresh.sugarCane));
                                                                    return merged.isEmpty()
                                                                            ? null
                                                                            : merged;
                                                                } else {
                                                                    return existing;
                                                                }
                                                            });
                                                } catch (Error | RuntimeException failure) {
                                                    this.scannedFlags.computeIfPresent(
                                                            packed,
                                                            (key, scanned) -> {
                                                                if (this.generation
                                                                                == scanGeneration
                                                                        && this.currentWorld
                                                                                == world
                                                                        && this.chunkRevisions
                                                                                        .getOrDefault(
                                                                                                packed,
                                                                                                -1)
                                                                                == revision) {
                                                                    int remaining =
                                                                            scanned & ~flags;
                                                                    return remaining == 0
                                                                            ? null
                                                                            : remaining;
                                                                } else {
                                                                    return scanned;
                                                                }
                                                            });
                                                    throw failure;
                                                } finally {
                                                    this.pendingScans.updateAndGet(
                                                            a -> Math.max(0, a - 1));
                                                }
                                            });
                        }
                    }
                }
            }
        }
    }

    private static boolean areNeighborsLoaded(World world, int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(chunkX + dx, chunkZ + dz)) {
                    return false;
                }
            }
        }

        return true;
    }

    private ChunkFinder.ChunkScanResult scanChunk(
            World world,
            WorldChunk chunk,
            int chunkX,
            int chunkZ,
            boolean scanHoles,
            boolean scanGeodes,
            boolean scanCactus,
            boolean scanSugarCane,
            int holeRadius,
            long scanGeneration) {
        ArrayList<BlockPos> holes = new ArrayList<>();
        ArrayList<BlockPos> geodeBlocks = new ArrayList<>();
        ArrayList<BlockPos> cacti = new ArrayList<>();
        ArrayList<BlockPos> sugarCane = new ArrayList<>();
        int bottomY = world.getBottomY();
        int topY = world.getTopYInclusive() - 1;
        ChunkSection[] sections = chunk.getSectionArray();
        Mutable pos = new Mutable();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            if (this.generation != scanGeneration) {
                return ChunkFinder.ChunkScanResult.EMPTY;
            }

            int sectionY = bottomY + sectionIndex * 16;
            ChunkSection section = sections[sectionIndex];
            boolean holeCandidate =
                    scanHoles
                            && sectionY + 15 >= 18
                            && sectionY <= 50
                            && section.hasAny(state -> state.isOf(Blocks.AIR))
                            && section.hasAny(state -> !state.isOf(Blocks.AIR));
            boolean geodeCandidate =
                    scanGeodes
                            && sectionY <= 80
                            && sectionY + 15 >= -64
                            && section.hasAny(ChunkFinder::isGeodeBlock);
            boolean cactusCandidate =
                    scanCactus && section.hasAny(state -> state.isOf(Blocks.CACTUS));
            boolean caneCandidate =
                    scanSugarCane && section.hasAny(state -> state.isOf(Blocks.SUGAR_CANE));
            if (holeCandidate || geodeCandidate || cactusCandidate || caneCandidate) {
                int minY = Math.max(sectionY, bottomY);
                int maxY = Math.min(sectionY + 15, topY);

                for (int y = minY; y <= maxY; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y - sectionY, z);
                            boolean isHole =
                                    holeCandidate
                                            && HoleScanBounds.isInScanRange(y)
                                            && state.isOf(Blocks.AIR);
                            boolean isGeode =
                                    geodeCandidate && y >= -64 && y <= 80 && isGeodeBlock(state);
                            boolean isCactus = cactusCandidate && state.isOf(Blocks.CACTUS);
                            boolean isCane = caneCandidate && state.isOf(Blocks.SUGAR_CANE);
                            if (isHole || isGeode || isCactus || isCane) {
                                pos.set(chunkX * 16 + x, y, chunkZ * 16 + z);
                                if (isHole && isEnclosedHole(world, pos, holeRadius)) {
                                    holes.add(pos.toImmutable());
                                }

                                if (isGeode) {
                                    geodeBlocks.add(pos.toImmutable());
                                }

                                if (isCactus && isFullyGrownColumn(world, pos, Blocks.CACTUS)) {
                                    cacti.add(pos.toImmutable());
                                }

                                if (isCane && isFullyGrownColumn(world, pos, Blocks.SUGAR_CANE)) {
                                    sugarCane.add(pos.toImmutable());
                                }
                            }
                        }
                    }
                }
            }
        }

        List<BlockPos> geodes =
                scanGeodes
                        ? this.findMinedGeodes(world, geodeBlocks, chunkX, chunkZ, scanGeneration)
                        : List.of();
        return new ChunkFinder.ChunkScanResult(
                List.copyOf(holes),
                List.copyOf(geodes),
                List.copyOf(cacti),
                List.copyOf(sugarCane));
    }

    private static boolean isFullyGrownColumn(World world, BlockPos pos, Block block) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        Mutable mutable = new Mutable();
        return FarmDensityCheck.hasClearColumn(
                y,
                world.getBottomY(),
                world.getTopYInclusive(),
                offset -> {
                    mutable.set(x, y + offset, z);
                    return world.getBlockState(mutable).isOf(block);
                });
    }

    private static boolean isEnclosedHole(World world, BlockPos pos, int radius) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (!HoleScanBounds.isInScanRange(y)) {
            return false;
        } else {
            Mutable mutable = new Mutable();

            for (int i = 0; i < 6; i++) {
                mutable.set(x + NEIGHBOR_DX[i], y + NEIGHBOR_DY[i], z + NEIGHBOR_DZ[i]);
                if (!isSolidFullBlock(world, mutable)) {
                    return false;
                }
            }

            for (int r = 1; r <= radius; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.abs(dx) == r || Math.abs(dy) == r || Math.abs(dz) == r) {
                                mutable.set(x + dx, y + dy, z + dz);
                                BlockState state = world.getBlockState(mutable);
                                if (!HoleScanBounds.isEnclosureValid(
                                        r,
                                        r == 1 && isIce(state),
                                        state.getFluidState().isEmpty()
                                                && state.isFullCube(world, mutable))) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }

            return true;
        }
    }

    private static boolean isIce(BlockState state) {
        return state.isOf(Blocks.ICE)
                || state.isOf(Blocks.PACKED_ICE)
                || state.isOf(Blocks.BLUE_ICE)
                || state.isOf(Blocks.FROSTED_ICE);
    }

    private static boolean isNextToIce(World world, BlockPos pos) {
        Mutable mutable = new Mutable();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        mutable.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        if (isIce(world.getBlockState(mutable))) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isSolidFullBlock(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.isFullCube(world, pos);
    }

    private static boolean isGeodeBlock(BlockState state) {
        return state.isOf(Blocks.CALCITE) || state.isOf(Blocks.SMOOTH_BASALT);
    }

    private List<BlockPos> findMinedGeodes(
            World world, List<BlockPos> geodeBlocks, int chunkX, int chunkZ, long scanGeneration) {
        if (geodeBlocks.isEmpty()) {
            return List.of();
        } else {
            HashSet<Long> visited = new HashSet<>();
            ArrayList<BlockPos> result = new ArrayList<>();
            Mutable mutable = new Mutable();

            for (BlockPos start : geodeBlocks) {
                if (this.generation != scanGeneration) {
                    return List.of();
                }

                if (visited.add(start.asLong())) {
                    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                    queue.add(start);
                    int calciteCount = 0;
                    int basaltCount = 0;
                    ArrayList<BlockPos> calcite = new ArrayList<>();
                    ArrayList<BlockPos> basalt = new ArrayList<>();
                    int minX = Integer.MAX_VALUE;
                    int minY = Integer.MAX_VALUE;
                    int minZ = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE;
                    int maxY = Integer.MIN_VALUE;
                    int maxZ = Integer.MIN_VALUE;
                    BlockPos anchor = start;

                    while (!queue.isEmpty()
                            && calciteCount + basaltCount < MAX_GEODE_CLUSTER_SIZE) {
                        BlockPos current = queue.removeFirst();
                        BlockState state = world.getBlockState(current);
                        if (state.isOf(Blocks.CALCITE)) {
                            calciteCount++;
                            calcite.add(current);
                        } else {
                            if (!state.isOf(Blocks.SMOOTH_BASALT)) {
                                continue;
                            }

                            basaltCount++;
                            basalt.add(current);
                        }

                        int x = current.getX();
                        int y = current.getY();
                        int z = current.getZ();
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                        if (x < anchor.getX() || x == anchor.getX() && z < anchor.getZ()) {
                            anchor = current;
                        }

                        for (int i = 0; i < 6; i++) {
                            mutable.set(x + NEIGHBOR_DX[i], y + NEIGHBOR_DY[i], z + NEIGHBOR_DZ[i]);
                            int neighborChunkX = Math.floorDiv(mutable.getX(), 16);
                            int neighborChunkZ = Math.floorDiv(mutable.getZ(), 16);
                            if (world.isChunkLoaded(neighborChunkX, neighborChunkZ)
                                    && isGeodeBlock(world.getBlockState(mutable))) {
                                long key = mutable.asLong();
                                if (visited.add(key)) {
                                    queue.add(mutable.toImmutable());
                                }
                            }
                        }
                    }

                    boolean enoughByCount = basaltCount >= 12 && calciteCount >= 8;
                    boolean enoughBySize =
                            basaltCount >= 48
                                    && maxX - minX >= 4
                                    && maxY - minY >= 4
                                    && maxZ - minZ >= 4;
                    if (queue.isEmpty()
                            && (enoughByCount || enoughBySize)
                            && maxX - minX <= 32
                            && maxY - minY <= 32
                            && maxZ - minZ <= 32
                            && Math.floorDiv(anchor.getX(), 16) == chunkX
                            && Math.floorDiv(anchor.getZ(), 16) == chunkZ
                            && isMinedGeode(
                                    world, calcite, basalt, minX, minY, minZ, maxX, maxY, maxZ)) {
                        result.add(
                                new BlockPos(
                                        (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2));
                    }
                }
            }

            return result;
        }
    }

    private static boolean isMinedGeode(
            World world,
            List<BlockPos> calcite,
            List<BlockPos> basalt,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) {
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        HashSet<Long> openings = new HashSet<>();
        Mutable mutable = new Mutable();

        for (BlockPos block : calcite) {
            double distance = distanceSquaredTo(block, centerX, centerY, centerZ);

            for (int i = 0; i < 6; i++) {
                mutable.set(
                        block.getX() + NEIGHBOR_DX[i],
                        block.getY() + NEIGHBOR_DY[i],
                        block.getZ() + NEIGHBOR_DZ[i]);
                if (!(distanceSquaredTo(mutable, centerX, centerY, centerZ) >= distance)) {
                    if (!world.isChunkLoaded(
                            Math.floorDiv(mutable.getX(), 16), Math.floorDiv(mutable.getZ(), 16))) {
                        return false;
                    }

                    if (!isGeodeBlock(world.getBlockState(mutable))) {
                        openings.add(mutable.asLong());
                    }
                }
            }
        }

        if (openings.size() < 12) {
            openings.clear();

            for (BlockPos basaltBlock : basalt) {
                double basaltDistance = distanceSquaredTo(basaltBlock, centerX, centerY, centerZ);

                for (int j = 0; j < 6; j++) {
                    mutable.set(
                            basaltBlock.getX() + 2 * NEIGHBOR_DX[j],
                            basaltBlock.getY() + 2 * NEIGHBOR_DY[j],
                            basaltBlock.getZ() + 2 * NEIGHBOR_DZ[j]);
                    if (!(distanceSquaredTo(mutable, centerX, centerY, centerZ)
                            >= basaltDistance)) {
                        if (!world.isChunkLoaded(
                                Math.floorDiv(mutable.getX(), 16),
                                Math.floorDiv(mutable.getZ(), 16))) {
                            return false;
                        }

                        if (!isGeodeBlock(world.getBlockState(mutable))) {
                            openings.add(mutable.asLong());
                        }
                    }
                }
            }
        }

        int openingCount = openings.size();
        if (openingCount < 12) {
            return false;
        } else {
            int amethystCount = 0;

            for (int y = minY - 2; y <= maxY + 2; y++) {
                for (int x = minX - 2; x <= maxX + 2; x++) {
                    for (int z = minZ - 2; z <= maxZ + 2; z++) {
                        if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
                            return false;
                        }

                        mutable.set(x, y, z);
                        BlockState state = world.getBlockState(mutable);
                        if (state.isOf(Blocks.AMETHYST_BLOCK)
                                || state.isOf(Blocks.BUDDING_AMETHYST)) {
                            if (++amethystCount * 5 > openingCount) {
                                return false;
                            }
                        }
                    }
                }
            }

            return true;
        }
    }

    private static double distanceSquaredTo(BlockPos pos, double x, double y, double z) {
        double dx = pos.getX() - x;
        double dy = pos.getY() - y;
        double dz = pos.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void reset() {
        this.generation++;
        this.nearScanQueue.clear();
        this.farScanQueue.clear();
        this.queuedChunks.clear();
        this.queuedFarChunks.clear();
        this.scannedFlags.clear();
        this.scanResults.clear();
        this.detectedChunks.clear();
        this.markerVersion++;
        this.markers = List.of();
        this.borderSourceMarkers = List.of();
        this.borderResult = RegionBorderBuilder.BorderResult.EMPTY;
        this.borderPlayerChunk = null;
        this.pendingMerge = null;
        this.appliedVersion = -1L;
        this.nextMergeTick = 0;
        this.chunkRevisions.clear();
        this.announcedFlags.clear();
        this.mobs = Map.of();
        this.tickCounter = 0;
        this.lastScanFlags = -1;
        this.nextAnnounceTick = 0;
        this.farScanCursor = 0;
        this.currentWorld = null;
    }

    private record ChunkScanResult(
            List<BlockPos> holes,
            List<BlockPos> geodes,
            List<BlockPos> cacti,
            List<BlockPos> sugarCane) {
        private static final ChunkFinder.ChunkScanResult EMPTY =
                new ChunkFinder.ChunkScanResult(List.of(), List.of(), List.of(), List.of());

        private boolean isEmpty() {
            return this.holes.isEmpty()
                    && this.geodes.isEmpty()
                    && this.cacti.isEmpty()
                    && this.sugarCane.isEmpty();
        }
    }

    private record MergedMarkers(
            List<ChunkAreaMerger.ChunkArea> markers,
            long version,
            long generation,
            Object world,
            boolean adjusted) {}

    private record MobCounts(int fish, int hostile) {}
}
