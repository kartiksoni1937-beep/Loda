package odinclient.module.render.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.manager.LegacySettingStripper;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Environment(EnvType.CLIENT)
public final class StorageEsp extends Module {
    private static final int CHUNK_RADIUS = 8;
    private static final int SCAN_INTERVAL_TICKS = 40;
    private final Setting<Boolean> chests =
            this.booleanSetting("chests", "Highlights regular and trapped chests.", false);
    private final Setting<Boolean> enderChests =
            this.booleanSetting("ender-chests", "Highlights ender chests.", false);
    private final Setting<Boolean> spawners =
            this.booleanSetting("spawners", "Highlights mob spawners.", false);
    private final Setting<Boolean> shulkerBoxes =
            this.booleanSetting("shulker-boxes", "Highlights shulker boxes.", false);
    private final Setting<Boolean> useShulkerDyes =
            this.booleanSetting("use-shulker-dyes", "Uses each shulker box's dye color.", false);
    private final Setting<Boolean> furnaces =
            this.booleanSetting(
                    "furnaces", "Highlights furnaces, blast furnaces and smokers.", false);
    private final Setting<Boolean> barrels =
            this.booleanSetting("barrels", "Highlights barrels.", false);
    private final Setting<Boolean> enchantingTables =
            this.booleanSetting("enchanting-tables", "Highlights enchanting tables.", false);
    private final Setting<Boolean> hoppers =
            this.booleanSetting("hoppers", "Highlights hoppers.", false);
    private final Setting<Boolean> movingPistons =
            this.booleanSetting("moving-pistons", "Highlights moving piston storage.", false);
    private final Setting<Boolean> tracers =
            this.booleanSetting("tracers", "Draws tracers to highlighted storage blocks.", false);
    private final Setting<Double> tracerWeight =
            this.settings
                    .getGeneral()
                    .add(
                            new NumberSetting.Builder()
                                    .name("tracer-weight")
                                    .description("Width of storage tracers.")
                                    .defaultValue(1.0)
                                    .range(0.1, 5.0)
                                    .sliderRange(0.1, 5.0)
                                    .build());
    private final Setting<Boolean> filled =
            this.booleanSetting("filled", "Fills highlighted storage boxes.", false);
    private final Setting<Integer> outlineAlpha =
            this.alpha("outline-alpha", "Opacity of storage outlines.", 220);
    private final Setting<Integer> fillAlpha =
            this.alpha("fill-alpha", "Opacity of storage fills.", 100);
    private final Setting<Integer> renderDistance =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("render-distance")
                                    .description("Legacy storage render distance.")
                                    .defaultValue(500)
                                    .range(10, 2048)
                                    .sliderRange(10, 2048)
                                    .build());
    private final Setting<EntityColor> chestColor = this.color("chest-color", 156, 91, 0);
    private final Setting<EntityColor> enderChestColor =
            this.color("ender-chest-color", 117, 0, 255);
    private final Setting<EntityColor> spawnerColor = this.color("spawner-color", 138, 126, 166);
    private final Setting<EntityColor> shulkerColor = this.color("shulker-color", 134, 0, 158);
    private final Setting<EntityColor> furnaceColor = this.color("furnace-color", 125, 125, 125);
    private final Setting<EntityColor> barrelColor = this.color("barrel-color", 255, 140, 140);
    private final Setting<EntityColor> enchantingColor =
            this.color("enchanting-color", 80, 80, 255);
    private final Setting<EntityColor> pistonColor = this.color("piston-color", 35, 226, 0);
    private final Setting<EntityColor> hopperColor = this.color("hopper-color", 35, 226, 0);
    private Map<BlockPos, Block> storageBlocks = Map.of();
    private List<StorageEsp.StorageBlock> sortedBlocks = List.of();
    private final Map<Long, Color> colorCache = new HashMap<>();
    private Object lastWorld;
    private int ticksSinceScan;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private int lastFilterHash = Integer.MIN_VALUE;

    public StorageEsp(Category category) {
        super(category, "storage-esp", "Highlights storage blocks through walls.");
        LegacySettingStripper.removeLegacySettings(this.settings, this.filled, this.renderDistance);
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        this.resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world != null && this.mc.player != null) {
            if (this.lastWorld != this.mc.world) {
                this.resetState();
                this.lastWorld = this.mc.world;
            }

            int chunkX = this.mc.player.getChunkPos().x;
            int chunkZ = this.mc.player.getChunkPos().z;
            int filterHash = this.computeFilterHash();
            boolean changed =
                    chunkX != this.lastChunkX
                            || chunkZ != this.lastChunkZ
                            || filterHash != this.lastFilterHash;
            if (changed || ++this.ticksSinceScan >= 40) {
                this.ticksSinceScan = 0;
                this.lastChunkX = chunkX;
                this.lastChunkZ = chunkZ;
                this.lastFilterHash = filterHash;
                this.scanStorageBlocks();
            }
        } else {
            this.resetState();
        }
    }

    private void scanStorageBlocks() {
        int playerChunkX = this.mc.player.getChunkPos().x;
        int playerChunkZ = this.mc.player.getChunkPos().z;
        Map<BlockPos, Block> found = new HashMap<>();

        for (int chunkX = playerChunkX - 8; chunkX <= playerChunkX + 8; chunkX++) {
            for (int chunkZ = playerChunkZ - 8; chunkZ <= playerChunkZ + 8; chunkZ++) {
                WorldChunk chunk =
                        this.mc.world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
                if (chunk != null && !chunk.isEmpty()) {
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        Block block = blockEntity.getCachedState().getBlock();
                        if (this.isTypeEnabled(block)) {
                            found.put(blockEntity.getPos().toImmutable(), block);
                        }
                    }
                }
            }
        }

        this.storageBlocks = Map.copyOf(found);
        double playerX = this.mc.player.getX();
        double playerY = this.mc.player.getY();
        double playerZ = this.mc.player.getZ();
        ArrayList<StorageEsp.StorageBlock> blocks = new ArrayList<>(this.storageBlocks.size());

        for (Entry<BlockPos, Block> entry : this.storageBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            blocks.add(
                    new StorageEsp.StorageBlock(
                            pos,
                            entry.getValue(),
                            new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        }

        blocks.sort(
                Comparator.comparingDouble(
                        b -> distanceSquaredToCenter(b.position(), playerX, playerY, playerZ)));
        this.sortedBlocks = List.copyOf(blocks);
    }

    private boolean isTypeEnabled(Block block) {
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
            return this.chests.getValue();
        } else if (block == Blocks.ENDER_CHEST) {
            return this.enderChests.getValue();
        } else if (block == Blocks.SPAWNER) {
            return this.spawners.getValue();
        } else if (block instanceof ShulkerBoxBlock) {
            return this.shulkerBoxes.getValue();
        } else if (block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER) {
            return this.furnaces.getValue();
        } else if (block == Blocks.BARREL) {
            return this.barrels.getValue();
        } else if (block == Blocks.ENCHANTING_TABLE) {
            return this.enchantingTables.getValue();
        } else {
            return block == Blocks.MOVING_PISTON
                    ? this.hoppers.getValue()
                    : block == Blocks.HOPPER && Boolean.TRUE.equals(this.movingPistons.getValue());
        }
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.world != null && this.mc.player != null && !this.storageBlocks.isEmpty()) {
            List<StorageEsp.StorageBlock> blocks = this.sortedBlocks;
            int size = blocks.size();

            for (int i = 0; i < size; i++) {
                StorageEsp.StorageBlock storageBlock = blocks.get(i);
                BlockPos pos = storageBlock.position();
                int rgb = this.getBlockColorRgb(storageBlock.block());
                Color outlineColor = this.getCachedColor(rgb, this.outlineAlpha.getValue());
                Color fillColor =
                        this.filled.getValue()
                                ? this.getCachedColor(rgb, this.fillAlpha.getValue())
                                : Color.CLEAR;
                PistonEspRenderer.drawBlockBox(
                        event.renderer,
                        pos.getX() + 0.0,
                        pos.getY(),
                        pos.getZ() + 0.0,
                        pos.getX() + 1.0,
                        pos.getY() + 1.0,
                        pos.getZ() + 1.0,
                        fillColor,
                        outlineColor,
                        this.filled.getValue() && this.fillAlpha.getValue() > 0
                                ? BoxRenderMode.Both
                                : BoxRenderMode.Lines,
                        storageBlock.block());
                if (this.tracers.getValue()) {
                    PistonEspRenderer.drawBlockTracer(
                            event.renderer,
                            storageBlock.center(),
                            outlineColor,
                            this.tracerWeight.getValue().floatValue(),
                            storageBlock.block(),
                            pos);
                }
            }
        }

        PistonEspRenderer.renderMovingPistons(this, event);
    }

    private int getBlockColorRgb(Block block) {
        if (block instanceof ShulkerBoxBlock shulkerBox) {
            if (this.useShulkerDyes.getValue()) {
                DyeColor dye = shulkerBox.getColor();
                if (dye != null) {
                    return dye.getEntityColor() & 16777215;
                }
            }

            return toRgb(this.shulkerColor.getValue());
        } else if (block == Blocks.TRAPPED_CHEST || block == Blocks.CHEST) {
            return toRgb(this.chestColor.getValue());
        } else if (block == Blocks.ENDER_CHEST) {
            return toRgb(this.enderChestColor.getValue());
        } else if (block == Blocks.SPAWNER) {
            return toRgb(this.spawnerColor.getValue());
        } else if (block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER) {
            return toRgb(this.furnaceColor.getValue());
        } else if (block == Blocks.BARREL) {
            return toRgb(this.barrelColor.getValue());
        } else if (block == Blocks.ENCHANTING_TABLE) {
            return toRgb(this.enchantingColor.getValue());
        } else {
            return block == Blocks.MOVING_PISTON
                    ? toRgb(this.pistonColor.getValue())
                    : toRgb(this.hopperColor.getValue());
        }
    }

    private Color getCachedColor(int rgb, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        long cacheKey = (long) clampedAlpha << 24 | rgb & 16777215L;
        Color color = this.colorCache.get(cacheKey);
        if (color == null) {
            if (this.colorCache.size() > 96) {
                this.colorCache.clear();
            }

            color = new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, clampedAlpha);
            this.colorCache.put(cacheKey, color);
        }

        return color;
    }

    private Setting<Boolean> booleanSetting(String name, String description, boolean defaultValue) {
        return this.settings
                .getGeneral()
                .add(
                        new BooleanSetting.Builder()
                                .name(name)
                                .description(description)
                                .defaultValue(defaultValue)
                                .build());
    }

    private Setting<Integer> alpha(String name, String description, int defaultValue) {
        return this.settings
                .getGeneral()
                .add(
                        new IntSetting.Builder()
                                .name(name)
                                .description(description)
                                .defaultValue(defaultValue)
                                .range(0, 255)
                                .sliderRange(0, 255)
                                .build());
    }

    private Setting<EntityColor> color(String name, int r, int g, int b) {
        return this.settings
                .getGeneral()
                .add(
                        new ColorSetting.Builder()
                                .name(name)
                                .description("Color used for this storage type.")
                                .defaultValue(new EntityColor(r, g, b))
                                .build());
    }

    private static int toRgb(EntityColor color) {
        return color.r << 16 | color.g << 8 | color.b;
    }

    private static double distanceSquaredToCenter(BlockPos pos, double x, double y, double z) {
        double dx = pos.getX() + 0.5 - x;
        double dy = pos.getY() + 0.5 - y;
        double dz = pos.getZ() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private int computeFilterHash() {
        int hash = 1;
        hash = 31 * hash + (Boolean.TRUE.equals(this.chests.getValue()) ? 1 : 0);
        hash = 31 * hash + (Boolean.TRUE.equals(this.enderChests.getValue()) ? 1 : 0);
        hash = 31 * hash + (Boolean.TRUE.equals(this.spawners.getValue()) ? 1 : 0);
        hash = 31 * hash + (Boolean.TRUE.equals(this.shulkerBoxes.getValue()) ? 1 : 0);
        hash = 31 * hash + (Boolean.TRUE.equals(this.furnaces.getValue()) ? 1 : 0);
        hash = 31 * hash + (Boolean.TRUE.equals(this.barrels.getValue()) ? 1 : 0);
        hash = 31 * hash + (Boolean.TRUE.equals(this.enchantingTables.getValue()) ? 1 : 0);
        hash = 31 * hash + (Boolean.TRUE.equals(this.hoppers.getValue()) ? 1 : 0);
        return 31 * hash + (Boolean.TRUE.equals(this.movingPistons.getValue()) ? 1 : 0);
    }

    private void resetState() {
        this.storageBlocks = Map.of();
        this.sortedBlocks = List.of();
        this.colorCache.clear();
        this.lastWorld = null;
        this.ticksSinceScan = 0;
        this.lastChunkX = Integer.MIN_VALUE;
        this.lastChunkZ = Integer.MIN_VALUE;
        this.lastFilterHash = Integer.MIN_VALUE;
    }

    public boolean isPistonEspEnabled() {
        return this.movingPistons.getValue();
    }

    public boolean isFilled() {
        return this.filled.getValue();
    }

    public boolean isTracersEnabled() {
        return this.tracers.getValue();
    }

    public float getTracerWidth() {
        return this.tracerWeight.getValue().floatValue();
    }

    public Color getPistonFillColor() {
        EntityColor color = this.pistonColor.getValue();
        return new Color(color.r, color.g, color.b, this.fillAlpha.getValue());
    }

    public Color getPistonOutlineColor() {
        EntityColor color = this.pistonColor.getValue();
        return new Color(color.r, color.g, color.b, this.outlineAlpha.getValue());
    }

    private record StorageBlock(BlockPos position, Block block, Vec3d center) {}

    record ChunkOffset(int x, int z) {}
}
