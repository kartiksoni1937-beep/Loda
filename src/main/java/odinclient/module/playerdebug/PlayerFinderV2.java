package odinclient.module.playerdebug;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import odinclient.core.Categories;
import odinclient.event.ChunkLoadEvent;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.ColorSetting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlayerFinderV2 extends Module {
    private static PlayerFinderV2 instance;
    private static final int SECTION_BLOCK_COUNT = 4096;
    private static final Set<Block> IGNORED_OLD_BLOCKS =
            Set.of(Blocks.AMETHYST_BLOCK, Blocks.STONE, Blocks.FROSTED_ICE);
    private static final Set<Block> IGNORED_NEW_BLOCKS =
            Set.of(
                    Blocks.FIRE,
                    Blocks.FROSTED_ICE,
                    Blocks.COAL_ORE,
                    Blocks.DEEPSLATE_COAL_ORE,
                    Blocks.IRON_ORE,
                    Blocks.DEEPSLATE_IRON_ORE,
                    Blocks.COPPER_ORE,
                    Blocks.DEEPSLATE_COPPER_ORE,
                    Blocks.GOLD_ORE,
                    Blocks.DEEPSLATE_GOLD_ORE,
                    Blocks.REDSTONE_ORE,
                    Blocks.DEEPSLATE_REDSTONE_ORE,
                    Blocks.EMERALD_ORE,
                    Blocks.DEEPSLATE_EMERALD_ORE,
                    Blocks.LAPIS_ORE,
                    Blocks.DEEPSLATE_LAPIS_ORE,
                    Blocks.DIAMOND_ORE,
                    Blocks.DEEPSLATE_DIAMOND_ORE,
                    Blocks.OAK_LEAVES,
                    Blocks.ACACIA_LEAVES,
                    Blocks.BIRCH_LEAVES,
                    Blocks.JUNGLE_LEAVES,
                    Blocks.DARK_OAK_LEAVES,
                    Blocks.PALE_OAK_LEAVES,
                    Blocks.CHERRY_LEAVES,
                    Blocks.AZALEA_LEAVES,
                    Blocks.FLOWERING_AZALEA_LEAVES,
                    Blocks.MOSSY_COBBLESTONE,
                    Blocks.RAW_COPPER_BLOCK,
                    Blocks.STONE,
                    Blocks.OBSERVER,
                    Blocks.REPEATER,
                    Blocks.COMPARATOR,
                    Blocks.REDSTONE_TORCH,
                    Blocks.REDSTONE_LAMP,
                    Blocks.REDSTONE_WIRE,
                    Blocks.DROPPER);
    private final ChunkBlockSnapshotStore chunkSnapshots =
            new ChunkBlockSnapshotStore(Block.getRawIdFromState(Blocks.AIR.getDefaultState()));
    private final Setting<EntityColor> sideColor;
    private final Setting<EntityColor> lineColor;
    private final List<PlayerFinderV2.ChunkCluster> clusters = new ArrayList<>();
    private final Set<ChunkPos> clusterChunks = new HashSet<>();
    private final Set<ChunkPos> flaggedChunks = new HashSet<>();
    private final LinkedHashMap<BlockPos, PlayerFinderV2.ChunkFlag> flags = new LinkedHashMap<>();
    private final Map<BlockPos, PlayerFinderV2.ChunkFlag> savedFlags = new LinkedHashMap<>();
    private List<ChunkPos> scanChunks = List.of();
    private final ArrayDeque<ChunkPos> pendingChunks = new ArrayDeque<>();
    private ChunkPos lastPlayerChunk;
    private long generation;
    private boolean underground;
    private boolean hasSavedFlags;
    private int restoreTicksLeft;
    private String savedServerAddress = "";

    public PlayerFinderV2() {
        super(
                Categories.MAIN,
                "player-debug-v2",
                "Compares received chunk sections and marks accepted block changes with pillars.");
        this.title = "Playerfinder v2";
        SettingGroup group = this.settings.addGroup("Render Flag");
        this.sideColor =
                group.add(
                        new ColorSetting.Builder()
                                .name("side-color")
                                .description("Detection pillar fill.")
                                .defaultValue(new EntityColor(255, 40, 40, 160))
                                .build());
        this.lineColor =
                group.add(
                        new ColorSetting.Builder()
                                .name("line-color")
                                .description("Detection pillar outline.")
                                .defaultValue(new EntityColor(255, 40, 40, 255))
                                .build());
        instance = this;
    }

    @Override
    public void onActivate() {
        this.chunkSnapshots.clear();
        this.pendingChunks.clear();
        this.scanChunks = List.of();
        this.lastPlayerChunk = null;
        this.clusters.clear();
        this.generation = 0L;
        this.underground = false;
        if (this.hasSavedFlags && this.savedServerAddress.equals(this.getServerAddress())) {
            this.restoreFlags();
        } else {
            this.clearFlags();
        }

        if (!this.clusterChunks.isEmpty()) {
            this.clusters.add(
                    new PlayerFinderV2.ChunkCluster(
                            new HashSet<>(this.clusterChunks), this.generation));
        }

        this.updateUndergroundState();
    }

    @Override
    public void onDeactivate() {
        this.chunkSnapshots.clear();
        this.pendingChunks.clear();
        this.scanChunks = List.of();
        this.lastPlayerChunk = null;
    }

    @EventHandler
    private void onWorldLeave(WorldLeaveEvent event) {
        this.savedFlags.clear();
        this.savedFlags.putAll(this.flags);
        this.savedServerAddress = this.getServerAddress();
        this.hasSavedFlags = true;
        this.restoreTicksLeft = 40;
        this.generation++;
        this.chunkSnapshots.clear();
        this.pendingChunks.clear();
        this.scanChunks = List.of();
        this.lastPlayerChunk = null;
        this.underground = false;
    }

    @EventHandler
    private void onWorldJoin(WorldJoinEvent event) {
        this.chunkSnapshots.clear();
        this.pendingChunks.clear();
        this.scanChunks = List.of();
        this.lastPlayerChunk = null;
        if (this.hasSavedFlags && this.savedServerAddress.equals(this.getServerAddress())) {
            this.restoreFlags();
            this.restoreTicksLeft = 40;
        } else {
            this.hasSavedFlags = false;
            this.savedFlags.clear();
            this.clearFlags();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.isInOverworld()) {
            if (this.hasSavedFlags) {
                if (this.savedServerAddress.equals(this.getServerAddress())) {
                    this.restoreFlags();
                }

                if (--this.restoreTicksLeft <= 0) {
                    this.hasSavedFlags = false;
                }
            }

            this.updateUndergroundState();
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            if (this.lastPlayerChunk == null || !this.lastPlayerChunk.equals(playerChunk)) {
                List<ChunkPos> chunks = this.getScanChunks();
                this.pendingChunks.removeIf(c -> !chunks.contains(c));

                for (ChunkPos chunk : chunks) {
                    if (!this.scanChunks.contains(chunk) && !this.pendingChunks.contains(chunk)) {
                        this.pendingChunks.add(chunk);
                    }
                }

                this.scanChunks = chunks;
                this.chunkSnapshots.removeOutside(playerChunk.x, playerChunk.z, 9);
            }

            for (int i = 0; i < 4 && !this.pendingChunks.isEmpty(); i++) {
                ChunkPos next = this.pendingChunks.removeFirst();
                if (this.isInScanRange(next.x, next.z)
                        && !this.chunkSnapshots.hasSnapshot(next.x, next.z)) {
                    this.snapshotChunk(next);
                }
            }

            this.lastPlayerChunk = playerChunk;
        }
    }

    public static void onChunkDataPacket(int chunkX, int chunkZ) {
        PlayerFinderV2 finder = instance;
        if (finder != null
                && finder.isActive()
                && finder.mc.isOnThread()
                && finder.isInOverworld()) {
            if (finder.isInScanRange(chunkX, chunkZ)
                    && !finder.chunkSnapshots.hasSnapshot(chunkX, chunkZ)) {
                finder.snapshotChunk(new ChunkPos(chunkX, chunkZ));
            }
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        if (this.isInOverworld()) {
            this.updateUndergroundState();
            ChunkPos chunkPos = event.getChunk().getPos();
            if (this.isInScanRange(chunkPos.x, chunkPos.z)) {
                for (ChunkBlockSnapshotStore.BlockChange change :
                        this.chunkSnapshots.diff(
                                chunkPos.x, chunkPos.z, readChunkBlockIds(event.getChunk()))) {
                    Block newBlock = Block.getStateFromRawId(change.newState()).getBlock();
                    Block oldBlock = Block.getStateFromRawId(change.oldState()).getBlock();
                    if (!IGNORED_NEW_BLOCKS.contains(newBlock)
                            && !IGNORED_OLD_BLOCKS.contains(oldBlock)) {
                        this.recordChange(
                                chunkPos,
                                new BlockPos(change.worldX(), change.worldY(), change.worldZ()));
                        break;
                    }
                }

                this.chunkSnapshots.remove(chunkPos.x, chunkPos.z);
            }
        }
    }

    private void recordChange(ChunkPos chunkPos, BlockPos pos) {
        if (!this.underground) {
            Set<ChunkPos> neighborhood = getNeighborhood(chunkPos);
            PlayerFinderV2.ChunkCluster bestCluster = null;
            int bestOverlap = 0;

            for (PlayerFinderV2.ChunkCluster cluster : this.clusters) {
                int overlap = countOverlap(cluster.chunks, neighborhood);
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    bestCluster = cluster;
                }
            }

            if (bestCluster == null || bestCluster.generation == this.generation) {
                if (bestCluster == null) {
                    this.clusters.add(
                            new PlayerFinderV2.ChunkCluster(neighborhood, this.generation));
                    PlayerFinderAlertSound.play();
                }

                this.flaggedChunks.add(chunkPos);
                this.rebuildClusterChunks();
                this.flags.put(
                        pos,
                        new PlayerFinderV2.ChunkFlag(chunkPos, pos, System.currentTimeMillis()));
                if (this.flags.size() > 4096) {
                    Iterator<BlockPos> iterator = this.flags.keySet().iterator();
                    iterator.next();
                    iterator.remove();
                }

                this.info("playerfinder v2 change found");
            }
        }
    }

    private void updateUndergroundState() {
        if (this.mc.player != null) {
            boolean isUnderground = this.mc.player.getY() < 0.0;
            if (isUnderground && !this.underground) {
                this.generation++;
            }

            this.underground = isUnderground;
        }
    }

    private boolean isInOverworld() {
        return this.mc.world != null
                && this.mc.player != null
                && this.mc.world.getRegistryKey() == World.OVERWORLD;
    }

    private boolean isInScanRange(int chunkX, int chunkZ) {
        ChunkPos playerChunk = this.mc.player.getChunkPos();
        long dz = Math.abs((long) chunkZ - playerChunk.z);
        long dx = Math.abs((long) chunkX - playerChunk.x);
        return dz <= 8L && dx <= 8L - Math.max(0L, 3L - (8L - dz));
    }

    private void snapshotChunk(ChunkPos chunkPos) {
        if (this.mc.world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
            this.chunkSnapshots.store(
                    chunkPos.x,
                    chunkPos.z,
                    readChunkBlockIds(this.mc.world.getChunk(chunkPos.x, chunkPos.z)));
        }
    }

    private List<ChunkPos> getScanChunks() {
        ChunkPos playerChunk = this.mc.player.getChunkPos();
        ArrayList<ChunkPos> chunks = new ArrayList<>();

        for (int dz = -8; dz <= 8; dz++) {
            int halfWidth = 8 - Math.max(0, 3 - (8 - Math.abs(dz)));

            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                chunks.add(new ChunkPos(playerChunk.x + dx, playerChunk.z + dz));
            }
        }

        return chunks;
    }

    public static int[][] readChunkBlockIds(Chunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int[][] blockIds = new int[sections.length][];

        for (int i = 6; i <= 23 && i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section != null && !section.isEmpty()) {
                int[] ids = new int[4096];
                int index = 0;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            ids[index++] = Block.getRawIdFromState(section.getBlockState(x, y, z));
                        }
                    }
                }

                blockIds[i] = ids;
            }
        }

        return blockIds;
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.isInOverworld()) {
            for (PlayerFinderV2.ChunkFlag flag : this.flags.values()) {
                double x = flag.pos.getX() + 0.3;
                double z = flag.pos.getZ() + 0.3;
                event.renderer.stableBox(
                        x,
                        -63.0,
                        z,
                        x + 0.4,
                        321.0,
                        z + 0.4,
                        this.sideColor.getValue(),
                        this.lineColor.getValue(),
                        BoxRenderMode.Both);
            }
        }
    }

    private static Set<ChunkPos> getNeighborhood(ChunkPos center) {
        HashSet<ChunkPos> result = new HashSet<>();

        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                result.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }

        return result;
    }

    private static int countOverlap(Set<ChunkPos> first, Set<ChunkPos> second) {
        int count = 0;
        Set<ChunkPos> smaller = first.size() <= second.size() ? first : second;
        Set<ChunkPos> larger = smaller == first ? second : first;

        for (ChunkPos chunk : smaller) {
            if (larger.contains(chunk)) {
                count++;
            }
        }

        return count;
    }

    private void rebuildClusterChunks() {
        this.clusterChunks.clear();

        for (PlayerFinderV2.ChunkCluster cluster : this.clusters) {
            this.clusterChunks.addAll(cluster.chunks);
        }
    }

    private String getServerAddress() {
        ServerInfo serverInfo = this.mc.getCurrentServerEntry();
        return serverInfo == null ? "singleplayer" : serverInfo.address;
    }

    private void restoreFlags() {
        this.flags.putAll(this.savedFlags);

        for (PlayerFinderV2.ChunkFlag flag : this.savedFlags.values()) {
            this.flaggedChunks.add(flag.key);
            this.clusterChunks.addAll(getNeighborhood(flag.key));
        }
    }

    private void clearFlags() {
        this.clusterChunks.clear();
        this.flaggedChunks.clear();
        this.clusters.clear();
        this.flags.clear();
    }

    public void clearAll() {
        this.chunkSnapshots.clear();
        this.pendingChunks.clear();
        this.clearFlags();
        this.savedFlags.clear();
        this.hasSavedFlags = false;
        this.scanChunks = List.of();
        this.lastPlayerChunk = null;
        this.info("Playerfinder v2 snapshots and flags cleared.");
    }

    public List<PlayerFinderV2.ChunkFlag> getRecentFlags() {
        return this.flags.values().stream()
                .sorted(Comparator.comparingLong(PlayerFinderV2.ChunkFlag::lastSeen).reversed())
                .limit(10L)
                .toList();
    }

    public String status() {
        return (this.isActive() ? "Active" : "Off")
                + " | chunk comparison | range 8 | flags "
                + this.flags.size()
                + " | Y32..319";
    }

    public String getInfoString() {
        return Integer.toString(this.flags.size());
    }

    private record ChunkCluster(Set<ChunkPos> chunks, long generation) {}

    public record ChunkFlag(ChunkPos key, BlockPos pos, long lastSeen) {}
}
