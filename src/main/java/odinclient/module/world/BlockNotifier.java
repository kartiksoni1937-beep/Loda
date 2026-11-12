package odinclient.module.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.event.BlockUpdateEvent;
import odinclient.event.ChunkLoadEvent;
import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BlockColorMapSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.hud.ItemToastRenderer;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class BlockNotifier extends Module {
    private static final Category CATEGORY = resolveCategory();
    private static final int SCAN_WORKERS =
            Math.max(2, Math.min(3, Runtime.getRuntime().availableProcessors() / 4));
    private final Set<BlockPos> discovered = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<BlockNotifier.Notification> queue =
            new ConcurrentLinkedQueue<>();
    private final ExecutorService scanExecutor = createScanExecutor();
    private BlockNotifier.Notification current;
    private long shownAt;
    private volatile long scanGeneration;
    private final BlockColorMapSetting blocks =
            this.addSetting(
                    new BlockColorMapSetting.Builder()
                            .name("blocks")
                            .description(
                                    "Select blocks that should trigger a notification and assign"
                                        + " their colors.")
                            .onChange(value -> this.clearNotifications())
                            .build());
    private final Setting<Integer> displaySeconds =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("display-seconds")
                                    .description("How long each notification remains visible.")
                                    .defaultValue(5)
                                    .range(1, 15)
                                    .sliderRange(1, 15)
                                    .build());
    private final Setting<Integer> xPosition =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("x-position")
                                    .description("Horizontal notification position.")
                                    .defaultValue(50)
                                    .range(0, 100)
                                    .sliderRange(0, 100)
                                    .build());
    private final Setting<Integer> yPosition =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("y-position")
                                    .description("Vertical notification position.")
                                    .defaultValue(2)
                                    .range(0, 100)
                                    .sliderRange(0, 100)
                                    .build());
    private final Setting<Double> size =
            this.settings
                    .getGeneral()
                    .add(
                            new NumberSetting.Builder()
                                    .name("size")
                                    .description("Notification scale.")
                                    .defaultValue(1.0)
                                    .range(0.5, 2.0)
                                    .sliderRange(0.5, 2.0)
                                    .build());

    public BlockNotifier() {
        super(
                CATEGORY,
                "odinclient-block-notifier",
                "Shows a notification with coordinates when a selected block is detected.");
    }

    private BlockColorMapSetting addSetting(BlockColorMapSetting setting) {
        this.settings.getGeneral().add(setting);
        return setting;
    }

    private static Category resolveCategory() {
        try {
            return (Category)
                    Class.forName("odinclient.core.Categories").getField("MAIN").get(null);
        } catch (ClassCastException | ReflectiveOperationException e) {
            return new Category("OdinClient", ItemStack.EMPTY);
        }
    }

    @Override
    public void onActivate() {
        this.scanGeneration++;
        this.clearNotifications();
    }

    @Override
    public void onDeactivate() {
        this.scanGeneration++;
        this.clearNotifications();
    }

    @EventHandler
    private void onJoin(WorldJoinEvent event) {
        this.scanGeneration++;
        this.clearNotifications();
    }

    @EventHandler
    private void onLeave(WorldLeaveEvent event) {
        this.scanGeneration++;
        this.clearNotifications();
    }

    @EventHandler
    private void onChunk(ChunkLoadEvent event) {
        if (this.mc.world != null && !this.blocks.getBlocks().isEmpty()) {
            WorldChunk chunk = event.getChunk();
            Set<Block> watched = Set.copyOf(this.blocks.getBlocks());
            long generation = this.scanGeneration;
            this.scanExecutor.submit(() -> this.scanChunk(chunk, watched, generation));
        }
    }

    private void scanChunk(WorldChunk chunk, Set<Block> watched, long generation) {
        if (generation == this.scanGeneration && !watched.isEmpty()) {
            ChunkPos chunkPos = chunk.getPos();
            ChunkSection[] sections = chunk.getSectionArray();
            int bottomY = chunk.getBottomY();
            Predicate<BlockState> predicate = s -> watched.contains(s.getBlock());

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                if (generation != this.scanGeneration || Thread.currentThread().isInterrupted()) {
                    return;
                }

                ChunkSection section = sections[sectionIndex];
                if (section != null && !section.isEmpty() && section.hasAny(predicate)) {
                    int baseY = bottomY + sectionIndex * 16;

                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                Block block = state.getBlock();
                                if (watched.contains(block)) {
                                    this.discover(
                                            block,
                                            new BlockPos(
                                                    chunkPos.getStartX() + x,
                                                    baseY + y,
                                                    chunkPos.getStartZ() + z),
                                            chunk,
                                            generation);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static ExecutorService createScanExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(
                SCAN_WORKERS,
                runnable -> {
                    Thread thread =
                            new Thread(
                                    runnable,
                                    "OdinClient-BlockNotifier-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(1);
                    return thread;
                });
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (this.mc.world != null && !this.blocks.getBlocks().isEmpty()) {
            Block block = event.newState.getBlock();
            BlockPos pos = event.pos.toImmutable();
            if (this.blocks.hasBlock(block)) {
                this.discover(block, pos, null, this.scanGeneration);
            } else {
                this.discovered.remove(pos);
            }
        }
    }

    private void discover(Block block, BlockPos pos, WorldChunk chunk, long generation) {
        if (generation == this.scanGeneration) {
            BlockPos immutablePos = pos.toImmutable();
            if (this.discovered.add(immutablePos)) {
                String title = this.getDisplayName(block, immutablePos, chunk);
                String coordinates =
                        "at "
                                + immutablePos.getX()
                                + ", "
                                + immutablePos.getY()
                                + ", "
                                + immutablePos.getZ();
                this.queue.add(new BlockNotifier.Notification(block, title, coordinates));
            }
        }
    }

    @EventHandler
    private void onRender2d(Render2DEvent event) {
        long now = Util.getMeasuringTimeMs();
        long durationMs = this.displaySeconds.getValue().longValue() * 1000L;
        if (this.current != null && now - this.shownAt >= durationMs) {
            this.current = null;
        }

        if (this.current == null) {
            this.current = this.queue.poll();
            this.shownAt = now;
        }

        if (this.current != null
                && this.mc.currentScreen == null
                && !this.mc.getDebugHud().shouldShowDebugHud()) {
            float progress =
                    Math.max(
                            0.0F,
                            Math.min(
                                    1.0F,
                                    1.0F
                                            - (float) (now - this.shownAt)
                                                    / (float) Math.max(1L, durationMs)));
            ItemToastRenderer.drawAnchoredToast(
                    event.context,
                    new ItemStack(this.current.block().asItem()),
                    this.current.title(),
                    this.current.coordinates(),
                    event.scaledWidth,
                    event.scaledHeight,
                    this.xPosition.getValue(),
                    this.yPosition.getValue(),
                    this.size.getValue().floatValue(),
                    progress);
        }
    }

    private void clearNotifications() {
        this.discovered.clear();
        this.queue.clear();
        this.current = null;
        this.shownAt = 0L;
    }

    private String getDisplayName(Block block, BlockPos pos, WorldChunk chunk) {
        if ((chunk != null ? chunk.getBlockEntity(pos) : this.mc.world.getBlockEntity(pos))
                instanceof MobSpawnerBlockEntity spawner) {
            String mobName = this.getSpawnerMob(spawner);
            if (!mobName.isEmpty()) {
                return mobName + " Spawner";
            }
        }

        return block.getName().getString();
    }

    private String getSpawnerMob(MobSpawnerBlockEntity spawner) {
        try {
            NbtCompound nbt =
                    spawner.createNbtWithIdentifyingData(this.mc.world.getRegistryManager());
            Optional<NbtCompound> spawnData = nbt.getCompound("SpawnData");
            if (spawnData.isEmpty()) {
                return "";
            }

            Optional<NbtCompound> entityData = spawnData.get().getCompound("entity");
            if (entityData.isEmpty()) {
                return "";
            }

            String id = entityData.get().getString("id").orElse("");
            if (id.isEmpty()) {
                return "";
            }

            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            StringBuilder builder = new StringBuilder();

            for (String word : path.replace('_', ' ').split(" ")) {
                if (!word.isEmpty()) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }

                    builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                }
            }

            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public String getInfoString() {
        return Integer.toString(this.blocks.getValue().size());
    }

    private record Notification(Block block, String title, String coordinates) {}
}
