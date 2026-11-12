package odinclient.module.render.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.core.Categories;
import odinclient.event.ChunkLoadEvent;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.IntSetting;
import odinclient.util.ScannerExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Environment(EnvType.CLIENT)
public final class HoleESP extends Module {
    private static final int MAX_RANGE = 32;
    private static final int RENDER_DISTANCE = 512;
    private static final int SCAN_WIDTH = 20;
    private static final byte AIR = 0;
    private static final byte SOLID = 1;
    private final Setting<Integer> minDepth;
    private final Setting<Boolean> showOneByOne;
    private final Setting<EntityColor> oneByOneColor;
    private final Setting<Boolean> showThreeByOne;
    private final Setting<EntityColor> threeByOneColor;
    private final Setting<Integer> alpha;
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, List<HoleESP.Hole>> holesByChunk = new ConcurrentHashMap<>();
    private volatile ExecutorService executor;
    private volatile long scanGeneration;
    private volatile Object trackedWorld;
    private boolean initialScanDone;

    public HoleESP() {
        super(
                Categories.MAIN,
                "hole-esp",
                "Highlights deep 1x1 and 3x1 vertical holes with separate colors.");
        this.title = "Hole ESP";
        SettingGroup general = this.settings.getGeneral();
        this.minDepth =
                general.add(
                        new IntSetting.Builder()
                                .name("min-depth")
                                .description(
                                        "Minimum uninterrupted vertical depth required before a"
                                            + " hole is highlighted.")
                                .defaultValue(4)
                                .range(2, 32)
                                .sliderRange(2, 32)
                                .build());
        this.showOneByOne =
                general.add(
                        new BooleanSetting.Builder()
                                .name("1x1-holes")
                                .description("Highlight enclosed one-block-wide vertical holes.")
                                .defaultValue(true)
                                .build());
        this.oneByOneColor =
                general.add(
                        new ColorSetting.Builder()
                                .name("1x1-color")
                                .description("Color used for 1x1 holes.")
                                .defaultValue(new EntityColor(255, 35, 35, 255))
                                .build());
        this.showThreeByOne =
                general.add(
                        new BooleanSetting.Builder()
                                .name("3x1-holes")
                                .description("Highlight enclosed three-block-long vertical holes.")
                                .defaultValue(true)
                                .build());
        this.threeByOneColor =
                general.add(
                        new ColorSetting.Builder()
                                .name("3x1-color")
                                .description("Color used for 3x1 holes.")
                                .defaultValue(new EntityColor(255, 225, 35, 255))
                                .build());
        this.alpha =
                general.add(
                        new IntSetting.Builder()
                                .name("alpha")
                                .description("Opacity of the filled hole markers.")
                                .defaultValue(120)
                                .range(0, 255)
                                .sliderRange(0, 255)
                                .build());
    }

    @Override
    public void onActivate() {
        if (this.mc.world != this.trackedWorld) {
            this.reset();
            this.trackedWorld = this.mc.world;
        }

        this.ensureExecutor();
        this.initialScanDone = false;
        this.scanSurroundingChunks();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    public String getInfoString() {
        int total = 0;

        for (List<HoleESP.Hole> holes : this.holesByChunk.values()) {
            total += holes.size();
        }

        return total == 0 ? null : "Holes:" + total;
    }

    @EventHandler
    private void onWorldJoin(WorldJoinEvent event) {
        this.reset();
        this.trackedWorld = this.mc.world;
        this.ensureExecutor();
    }

    @EventHandler
    private void onWorldLeave(WorldLeaveEvent event) {
        this.reset();
    }

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        if (this.mc.world != null) {
            ChunkPos chunkPos = event.getChunk().getPos();
            this.queueChunkAndNeighbors(chunkPos.x, chunkPos.z);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (this.mc.world != this.trackedWorld) {
                this.reset();
                this.trackedWorld = this.mc.world;
            }

            this.ensureExecutor();
            if (!this.initialScanDone) {
                this.scanSurroundingChunks();
            }

            int scanned = 0;

            for (long packed : this.pendingChunks) {
                this.scanChunk(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed));
                if (++scanned >= 16) {
                    break;
                }
            }
        }
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.player != null && this.mc.world != null && this.mc.world == this.trackedWorld) {
            boolean showOneByOneHoles = Boolean.TRUE.equals(this.showOneByOne.getValue());
            boolean showThreeByOneHoles = Boolean.TRUE.equals(this.showThreeByOne.getValue());
            if (showOneByOneHoles || showThreeByOneHoles) {
                int minimumDepth = this.minDepth.getValue();
                int alphaValue = this.alpha.getValue();
                Color oneByOne = color(this.oneByOneColor.getValue(), alphaValue);
                Color threeByOne = color(this.threeByOneColor.getValue(), alphaValue);
                double playerX = this.mc.player.getX();
                double playerZ = this.mc.player.getZ();
                double maxDistanceSq = 262144.0;

                for (Entry<Long, List<HoleESP.Hole>> entry : this.holesByChunk.entrySet()) {
                    int chunkX = ChunkPos.getPackedX(entry.getKey());
                    int chunkZ = ChunkPos.getPackedZ(entry.getKey());
                    if (this.mc.world.isChunkLoaded(chunkX, chunkZ)) {
                        for (HoleESP.Hole hole : entry.getValue()) {
                            if (hole.depth >= minimumDepth
                                    && (hole.type == HoleESP.HoleType.ONE_BY_ONE
                                            ? showOneByOneHoles
                                            : showThreeByOneHoles)) {
                                double dx = hole.x + hole.sizeX * 0.5 - playerX;
                                double dz = hole.z + hole.sizeZ * 0.5 - playerZ;
                                if (dx * dx + dz * dz <= maxDistanceSq) {
                                    Color holeColor =
                                            hole.type == HoleESP.HoleType.ONE_BY_ONE
                                                    ? oneByOne
                                                    : threeByOne;
                                    event.renderer.stableBox(
                                            hole.x,
                                            hole.y,
                                            hole.z,
                                            hole.x + hole.sizeX,
                                            hole.y + hole.depth,
                                            hole.z + hole.sizeZ,
                                            holeColor,
                                            holeColor,
                                            BoxRenderMode.Sides);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void scanSurroundingChunks() {
        if (!this.initialScanDone && this.mc.player != null && this.mc.world != null) {
            this.initialScanDone = true;
            ChunkPos center = this.mc.player.getChunkPos();

            for (int radius = 0; radius <= 32; radius++) {
                for (int x = center.x - radius; x <= center.x + radius; x++) {
                    this.scanChunk(x, center.z - radius);
                    if (radius > 0) {
                        this.scanChunk(x, center.z + radius);
                    }
                }

                for (int z = center.z - radius + 1; z < center.z + radius; z++) {
                    this.scanChunk(center.x - radius, z);
                    if (radius > 0) {
                        this.scanChunk(center.x + radius, z);
                    }
                }
            }
        }
    }

    private void queueChunkAndNeighbors(int chunkX, int chunkZ) {
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                this.scanChunk(x, z);
            }
        }
    }

    private void scanChunk(int chunkX, int chunkZ) {
        ClientWorld clientWorld = this.mc.world;
        if (clientWorld != null && clientWorld.isChunkLoaded(chunkX, chunkZ)) {
            long key = ChunkPos.toLong(chunkX, chunkZ);
            if (this.scannedChunks.contains(key)) {
                this.pendingChunks.remove(key);
            } else if (!this.areNeighborsLoaded(clientWorld, chunkX, chunkZ)) {
                this.pendingChunks.add(key);
            } else {
                WorldChunk chunk =
                        this.mc
                                .world
                                .getChunkManager()
                                .getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk != null && !chunk.isEmpty()) {
                    if (!this.scannedChunks.add(key)) {
                        this.pendingChunks.remove(key);
                    } else {
                        this.pendingChunks.remove(key);
                        long generation = this.scanGeneration;
                        ExecutorService service = this.executor;
                        if (service != null && !service.isShutdown()) {
                            try {
                                service.submit(
                                        () -> {
                                            List<HoleESP.Hole> holes =
                                                    this.findHoles(
                                                            clientWorld,
                                                            chunkX,
                                                            chunkZ,
                                                            generation);
                                            if (generation == this.scanGeneration
                                                    && clientWorld == this.trackedWorld) {
                                                this.holesByChunk.put(key, holes);
                                            }
                                        });
                            } catch (RejectedExecutionException e) {
                                this.scannedChunks.remove(key);
                            }
                        } else {
                            this.scannedChunks.remove(key);
                        }
                    }
                } else {
                    this.pendingChunks.add(key);
                }
            }
        }
    }

    private boolean areNeighborsLoaded(World world, int chunkX, int chunkZ) {
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }

        return true;
    }

    private List<HoleESP.Hole> findHoles(World world, int chunkX, int chunkZ, long generation) {
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        int bottomY = world.getBottomY();
        int topLimit = bottomY + world.getHeight();
        int topY = bottomY + 2;

        for (int x = originX - 1; x <= originX + 18; x++) {
            for (int z = originZ - 1; z <= originZ + 18; z++) {
                topY = Math.max(topY, world.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
            }
        }

        topY = Math.min(topLimit, topY);
        int depthRange = Math.max(2, topY - bottomY);
        byte[] solidity = new byte[400 * depthRange];
        Mutable mutable = new Mutable();

        for (int cx = 0; cx < 20; cx++) {
            if (Thread.currentThread().isInterrupted() || generation != this.scanGeneration) {
                return List.of();
            }

            for (int cz = 0; cz < 20; cz++) {
                int worldX = originX + cx - 1;
                int worldZ = originZ + cz - 1;

                for (int y = 0; y < depthRange; y++) {
                    mutable.set(worldX, bottomY + y, worldZ);
                    solidity[columnIndex(cx, cz, y, depthRange)] = getSolidity(world, mutable);
                }
            }
        }

        List<HoleESP.Hole> holes = new ArrayList<>();

        for (int dx = 0; dx < 16; dx++) {
            int localX = dx + 1;

            for (int dz = 0; dz < 16; dz++) {
                int localZ = dz + 1;
                collectHoles(
                        solidity,
                        holes,
                        originX + dx,
                        originZ + dz,
                        bottomY,
                        localX,
                        localZ,
                        depthRange,
                        HoleESP.HoleType.ONE_BY_ONE);
                collectHoles(
                        solidity,
                        holes,
                        originX + dx,
                        originZ + dz,
                        bottomY,
                        localX,
                        localZ,
                        depthRange,
                        HoleESP.HoleType.THREE_BY_ONE_X);
                collectHoles(
                        solidity,
                        holes,
                        originX + dx,
                        originZ + dz,
                        bottomY,
                        localX,
                        localZ,
                        depthRange,
                        HoleESP.HoleType.THREE_BY_ONE_Z);
            }
        }

        return List.copyOf(holes);
    }

    private static void collectHoles(
            byte[] solidity,
            List<HoleESP.Hole> holes,
            int worldX,
            int worldZ,
            int bottomY,
            int localX,
            int localZ,
            int height,
            HoleESP.HoleType type) {
        int start = 0;

        while (start < height) {
            if (!isHoleAt(solidity, localX, localZ, start, height, type)) {
                start++;
            } else {
                int end = start + 1;

                while (end < height && isHoleAt(solidity, localX, localZ, end, height, type)) {
                    end++;
                }

                if (end - start >= 2) {
                    int sizeX = type == HoleESP.HoleType.THREE_BY_ONE_X ? 3 : 1;
                    int sizeZ = type == HoleESP.HoleType.THREE_BY_ONE_Z ? 3 : 1;
                    HoleESP.HoleType holeType =
                            type == HoleESP.HoleType.ONE_BY_ONE
                                    ? HoleESP.HoleType.ONE_BY_ONE
                                    : HoleESP.HoleType.THREE_BY_ONE_X;
                    holes.add(
                            new HoleESP.Hole(
                                    worldX,
                                    bottomY + start,
                                    worldZ,
                                    sizeX,
                                    sizeZ,
                                    end - start,
                                    holeType));
                }

                start = end;
            }
        }
    }

    private static boolean isHoleAt(
            byte[] solidity, int x, int z, int y, int height, HoleESP.HoleType type) {
        return switch (type) {
            case ONE_BY_ONE -> isOneByOneHole(solidity, x, z, y, height);
            case THREE_BY_ONE_X -> isThreeByOneXHole(solidity, x, z, y, height);
            case THREE_BY_ONE_Z -> isThreeByOneZHole(solidity, x, z, y, height);
        };
    }

    private static byte getSolidity(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return (byte) (state.getOutlineShape(world, pos).isEmpty() ? 0 : 1);
    }

    private static boolean isOneByOneHole(byte[] solidity, int x, int z, int y, int height) {
        return isOpenAt(solidity, x, z, y, height)
                && isSolidAt(solidity, x - 1, z, y, height)
                && isSolidAt(solidity, x + 1, z, y, height)
                && isSolidAt(solidity, x, z - 1, y, height)
                && isSolidAt(solidity, x, z + 1, y, height);
    }

    private static boolean isThreeByOneXHole(byte[] solidity, int x, int z, int y, int height) {
        return isOpenAt(solidity, x, z, y, height)
                && isOpenAt(solidity, x + 1, z, y, height)
                && isOpenAt(solidity, x + 2, z, y, height)
                && isSolidAt(solidity, x - 1, z, y, height)
                && isSolidAt(solidity, x + 3, z, y, height)
                && isSolidAt(solidity, x, z - 1, y, height)
                && isSolidAt(solidity, x, z + 1, y, height)
                && isSolidAt(solidity, x + 1, z - 1, y, height)
                && isSolidAt(solidity, x + 1, z + 1, y, height)
                && isSolidAt(solidity, x + 2, z - 1, y, height)
                && isSolidAt(solidity, x + 2, z + 1, y, height);
    }

    private static boolean isThreeByOneZHole(byte[] solidity, int x, int z, int y, int height) {
        return isOpenAt(solidity, x, z, y, height)
                && isOpenAt(solidity, x, z + 1, y, height)
                && isOpenAt(solidity, x, z + 2, y, height)
                && isSolidAt(solidity, x, z - 1, y, height)
                && isSolidAt(solidity, x, z + 3, y, height)
                && isSolidAt(solidity, x - 1, z, y, height)
                && isSolidAt(solidity, x + 1, z, y, height)
                && isSolidAt(solidity, x - 1, z + 1, y, height)
                && isSolidAt(solidity, x + 1, z + 1, y, height)
                && isSolidAt(solidity, x - 1, z + 2, y, height)
                && isSolidAt(solidity, x + 1, z + 2, y, height);
    }

    private static boolean isOpenAt(byte[] solidity, int x, int z, int y, int height) {
        return y >= 0 && y < height && solidity[columnIndex(x, z, y, height)] == 0;
    }

    private static boolean isSolidAt(byte[] solidity, int x, int z, int y, int height) {
        return y >= 0 && y < height && solidity[columnIndex(x, z, y, height)] == 1;
    }

    private static int columnIndex(int x, int z, int y, int height) {
        return (x * 20 + z) * height + y;
    }

    private synchronized void ensureExecutor() {
        if (this.executor == null || this.executor.isShutdown()) {
            this.executor = ScannerExecutor.getExecutor();
        }
    }

    private synchronized void reset() {
        this.scanGeneration++;
        ExecutorService oldExecutor = this.executor;
        this.executor = null;
        if (oldExecutor != null && !oldExecutor.isShutdown()) {
            oldExecutor.shutdownNow();
        }

        this.pendingChunks.clear();
        this.scannedChunks.clear();
        this.holesByChunk.clear();
        this.initialScanDone = false;
        this.trackedWorld = null;
    }

    private static Color color(EntityColor entityColor, int alpha) {
        return new Color(entityColor.r, entityColor.g, entityColor.b, alpha);
    }

    private record Hole(
            int x, int y, int z, int sizeX, int sizeZ, int depth, HoleESP.HoleType type) {}

    private static enum HoleType {
        ONE_BY_ONE,
        THREE_BY_ONE_X,
        THREE_BY_ONE_Z;
    }
}
