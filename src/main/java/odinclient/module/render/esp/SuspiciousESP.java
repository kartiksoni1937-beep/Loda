package odinclient.module.render.esp;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.module.world.AmethystScanner;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SuspiciousESP extends Module {
    private static final int CLUSTER_RADIUS = 8;
    private static final Color GREEN_LINE = new Color(35, 255, 70, 255);
    private static final Color RED_LINE = new Color(255, 45, 45, 255);
    private static final Color AMETHYST_LINE = new Color(180, 100, 255, 255);
    private final SettingGroup general = this.settings.getGeneral();
    private final Setting<Boolean> wanderingTraders = this.bool("wandering-traders", true);
    private final Setting<Boolean> villagers = this.bool("villagers", true);
    private final Setting<Boolean> llamas = this.bool("llamas", true);
    private final Setting<Boolean> pillagers = this.bool("pillagers", true);
    private final Setting<Boolean> amethystClusters = this.bool("amethyst-clusters", true);
    private final Setting<Integer> alpha =
            this.general.add(
                    new IntSetting.Builder()
                            .name("alpha")
                            .description("Opacity of the filled suspicious ESP boxes.")
                            .defaultValue(100)
                            .range(0, 255)
                            .sliderRange(0, 255)
                            .build());
    private final Setting<Boolean> tracers = this.bool("tracers", true);
    private final Map<Long, Set<BlockPos>> clustersByChunk = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SuspiciousESP.ScanRequest> scanQueue =
            new ConcurrentLinkedQueue<>();
    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scannerRunning = new AtomicBoolean();
    private final ExecutorService scanner =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "Odin-Suspicious-Amethyst-Scanner");
                        thread.setDaemon(true);
                        thread.setPriority(1);
                        return thread;
                    });
    private volatile Object scannedWorld;
    private volatile long scanGeneration;
    private int scanTicks;
    private int cachedAlpha = -2147483648;
    private Color cachedFriendlyFill;
    private Color cachedHostileFill;
    private Color cachedAmethystFill;
    private List<Entity> cachedSuspiciousEntities = List.of();
    private int entityCacheTicks;

    public SuspiciousESP() {
        super(
                Categories.MAIN,
                "suspicious-esp",
                "Highlights suspicious entities and fully-grown amethyst clusters.");
        this.title = "Suspicious ESP";
    }

    private Setting<Boolean> bool(String name, boolean defaultValue) {
        return this.general.add(
                new BooleanSetting.Builder().name(name).defaultValue(defaultValue).build());
    }

    @Override
    public void onActivate() {
        this.resetScanner(this.mc.world);
        if (Boolean.TRUE.equals(this.amethystClusters.getValue())) {
            this.enqueueVisibleChunks();
            this.startScanner();
        }
    }

    @Override
    public void onDeactivate() {
        this.scanGeneration++;
        this.scanQueue.clear();
        this.queuedChunks.clear();
        this.clustersByChunk.clear();
        this.cachedSuspiciousEntities = List.of();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientWorld world = this.mc.world;
        if (world != null && this.mc.player != null) {
            if (this.scannedWorld != world) {
                this.resetScanner(world);
            }

            if (Boolean.TRUE.equals(this.amethystClusters.getValue()) && ++this.scanTicks >= 20) {
                this.scanTicks = 0;
                this.pruneFarChunks();
                this.enqueueVisibleChunks();
                this.startScanner();
            }

            if (++this.entityCacheTicks >= 2) {
                this.entityCacheTicks = 0;
                this.refreshEntityCache(world);
            }
        } else if (this.scannedWorld != null) {
            this.resetScanner(null);
        }
    }

    @EventHandler
    private void onRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.mc.player != null) {
            int alphaValue = this.alpha.getValue();
            if (alphaValue != this.cachedAlpha) {
                this.cachedAlpha = alphaValue;
                this.cachedFriendlyFill = new Color(35, 255, 70, alphaValue);
                this.cachedHostileFill = new Color(255, 45, 45, alphaValue);
                this.cachedAmethystFill = new Color(180, 100, 255, alphaValue);
            }

            Color friendlyFill = this.cachedFriendlyFill;
            Color hostileFill = this.cachedHostileFill;
            Color amethystFill = this.cachedAmethystFill;
            boolean showTraders = Boolean.TRUE.equals(this.wanderingTraders.getValue());
            boolean showVillagers = Boolean.TRUE.equals(this.villagers.getValue());
            boolean showLlamas = Boolean.TRUE.equals(this.llamas.getValue());
            boolean showPillagers = Boolean.TRUE.equals(this.pillagers.getValue());
            if (showTraders || showVillagers || showLlamas || showPillagers) {
                for (Entity entity : this.cachedSuspiciousEntities) {
                    if (entity != null && entity.isAlive() && entity != this.mc.player) {
                        Color fill = null;
                        Color line = null;
                        if (showTraders && entity instanceof WanderingTraderEntity) {
                            fill = friendlyFill;
                            line = GREEN_LINE;
                        } else if (showVillagers && entity instanceof VillagerEntity) {
                            fill = friendlyFill;
                            line = GREEN_LINE;
                        } else if (showLlamas && entity instanceof LlamaEntity) {
                            fill = friendlyFill;
                            line = GREEN_LINE;
                        } else if (showPillagers && entity instanceof PillagerEntity) {
                            fill = hostileFill;
                            line = RED_LINE;
                        }

                        if (fill != null) {
                            this.renderEntity(event, entity, fill, line);
                        }
                    }
                }
            }

            if (Boolean.TRUE.equals(this.amethystClusters.getValue())) {
                ChunkPos playerChunk = this.mc.player.getChunkPos();

                for (Entry<Long, Set<BlockPos>> entry : this.clustersByChunk.entrySet()) {
                    int chunkX = ChunkPos.getPackedX(entry.getKey());
                    int chunkZ = ChunkPos.getPackedZ(entry.getKey());
                    if (Math.abs(chunkX - playerChunk.x) <= 8
                            && Math.abs(chunkZ - playerChunk.z) <= 8) {
                        for (BlockPos pos : entry.getValue()) {
                            event.renderer.stableBox(
                                    pos.getX(),
                                    pos.getY(),
                                    pos.getZ(),
                                    pos.getX() + 1.0,
                                    pos.getY() + 1.0,
                                    pos.getZ() + 1.0,
                                    amethystFill,
                                    AMETHYST_LINE,
                                    BoxRenderMode.Sides);
                            if (Boolean.TRUE.equals(this.tracers.getValue())) {
                                event.renderer.blockEspHybridTracer(
                                        Vec3d.ofCenter(pos), AMETHYST_LINE, 1.0F);
                            }
                        }
                    }
                }
            }
        }
    }

    private void refreshEntityCache(ClientWorld world) {
        boolean traderEnabled = Boolean.TRUE.equals(this.wanderingTraders.getValue());
        boolean villagerEnabled = Boolean.TRUE.equals(this.villagers.getValue());
        boolean llamaEnabled = Boolean.TRUE.equals(this.llamas.getValue());
        boolean pillagerEnabled = Boolean.TRUE.equals(this.pillagers.getValue());
        if (!traderEnabled && !villagerEnabled && !llamaEnabled && !pillagerEnabled) {
            this.cachedSuspiciousEntities = List.of();
        } else {
            List<Entity> found = new ArrayList<>();

            for (Entity entity : world.getEntities()) {
                if (entity != null
                        && entity.isAlive()
                        && entity != this.mc.player
                        && (traderEnabled && entity instanceof WanderingTraderEntity
                                || villagerEnabled && entity instanceof VillagerEntity
                                || llamaEnabled && entity instanceof LlamaEntity
                                || pillagerEnabled && entity instanceof PillagerEntity)) {
                    found.add(entity);
                }
            }

            this.cachedSuspiciousEntities = found.isEmpty() ? List.of() : List.copyOf(found);
        }
    }

    private void renderEntity(WorldRenderEvent event, Entity entity, Color fill, Color line) {
        Box box = entity.getBoundingBox().expand(0.04);
        event.renderer.stableBox(
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ,
                fill,
                line,
                BoxRenderMode.Sides);
        if (Boolean.TRUE.equals(this.tracers.getValue())) {
            event.renderer.blockEspHybridTracer(box.getCenter(), line, 0.9F);
        }
    }

    private synchronized void resetScanner(Object world) {
        this.scanGeneration++;
        this.scannedWorld = world;
        this.scanTicks = 0;
        this.scanQueue.clear();
        this.queuedChunks.clear();
        this.clustersByChunk.clear();
        this.cachedSuspiciousEntities = List.of();
        this.entityCacheTicks = 0;
    }

    private void enqueueVisibleChunks() {
        ClientWorld world = this.mc.world;
        if (world != null && this.mc.player != null && world == this.scannedWorld) {
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            long generation = this.scanGeneration;

            for (int radius = 0; radius <= 8; radius++) {
                int minX = playerChunk.x - radius;
                int maxX = playerChunk.x + radius;
                int minZ = playerChunk.z - radius;
                int maxZ = playerChunk.z + radius;

                for (int x = minX; x <= maxX; x++) {
                    this.enqueue(world, x, minZ, generation);
                    if (maxZ != minZ) {
                        this.enqueue(world, x, maxZ, generation);
                    }
                }

                for (int z = minZ + 1; z < maxZ; z++) {
                    this.enqueue(world, minX, z, generation);
                    if (maxX != minX) {
                        this.enqueue(world, maxX, z, generation);
                    }
                }
            }
        }
    }

    private void enqueue(ClientWorld world, int chunkX, int chunkZ, long generation) {
        long key = ChunkPos.toLong(chunkX, chunkZ);
        if (this.queuedChunks.add(key)) {
            this.scanQueue.offer(
                    new SuspiciousESP.ScanRequest(world, chunkX, chunkZ, key, generation));
        }
    }

    private void startScanner() {
        if (!this.scanQueue.isEmpty() && this.scannerRunning.compareAndSet(false, true)) {
            this.scanner.execute(this::drainScanner);
        }
    }

    private void drainScanner() {
        try {
            SuspiciousESP.ScanRequest request;
            try {
                while (this.isActive() && (request = this.scanQueue.poll()) != null) {
                    this.queuedChunks.remove(request.key);
                    if (request.generation == this.scanGeneration
                            && request.world == this.scannedWorld) {
                        WorldChunk chunk =
                                request.world
                                        .getChunkManager()
                                        .getWorldChunk(request.chunkX, request.chunkZ, false);
                        if (chunk != null) {
                            Set<BlockPos> clusters = AmethystScanner.findAmethystClusters(chunk);
                            if (request.generation == this.scanGeneration) {
                                if (clusters.isEmpty()) {
                                    this.clustersByChunk.remove(request.key);
                                } else {
                                    this.clustersByChunk.put(request.key, clusters);
                                }
                            }
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

    private void pruneFarChunks() {
        if (this.mc.player != null) {
            ChunkPos playerChunk = this.mc.player.getChunkPos();
            this.clustersByChunk
                    .keySet()
                    .removeIf(
                            packed ->
                                    Math.abs(ChunkPos.getPackedX(packed) - playerChunk.x) > 10
                                            || Math.abs(ChunkPos.getPackedZ(packed) - playerChunk.z)
                                                    > 10);
        }
    }

    public static String[] appendClassNames(String[] names) {
        ArrayList<String> result = new ArrayList<>(List.of(names));
        if (!result.contains("SuspiciousESP")) {
            result.add("SuspiciousESP");
        }

        if (!result.contains("MobESP")) {
            result.add("MobESP");
        }

        if (!result.contains("Sprint")) {
            result.add("Sprint");
        }

        return result.toArray(String[]::new);
    }

    private record ScanRequest(
            ClientWorld world, int chunkX, int chunkZ, long key, long generation) {}
}
