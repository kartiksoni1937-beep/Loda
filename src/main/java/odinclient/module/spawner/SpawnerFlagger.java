package odinclient.module.spawner;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.event.TickEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.module.render.visual.DeepslateTint;
import odinclient.render.BoxRenderMode;
import odinclient.render.EntityColor;
import odinclient.render.TextBackend;
import odinclient.render.projection.ScreenProjection;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.EntityTypeListSetting;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.NumberSetting;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

public class SpawnerFlagger extends Module {
    private static final int CHUNK_RADIUS = 8;
    private static final int SCAN_INTERVAL = 20;
    private final SettingGroup general = this.settings.getGeneral();
    private final SettingGroup render = this.settings.addGroup("Render");
    private final Setting<Boolean> flagWholeChunk =
            this.render.add(
                    new BooleanSetting.Builder()
                            .name("flag-whole-chunk")
                            .description(
                                    "Flags the full 16x16 chunk instead of the individual spawner.")
                            .defaultValue(false)
                            .build());
    private final Setting<Double> pillarHeight =
            this.render.add(
                    new NumberSetting.Builder()
                            .name("pillar-height")
                            .description(
                                    "How tall the pillar above each spawner or flagged chunk is.")
                            .defaultValue(4.0)
                            .min(0.0)
                            .sliderRange(0.0, 384.0)
                            .build());
    private final Setting<Double> pillarWidth =
            this.render.add(
                    new NumberSetting.Builder()
                            .name("pillar-width")
                            .description("How thick the beam above each spawner is.")
                            .defaultValue(0.25)
                            .min(0.02)
                            .sliderRange(0.02, 1.0)
                            .visibleWhen(() -> !Boolean.TRUE.equals(this.flagWholeChunk.getValue()))
                            .build());
    private final Setting<BoxRenderMode> shapeMode =
            this.render.add(
                    new EnumSetting.Builder<BoxRenderMode>()
                            .name("shape-mode")
                            .description("How the spawner box is rendered.")
                            .defaultValue(BoxRenderMode.Both)
                            .visibleWhen(() -> !Boolean.TRUE.equals(this.flagWholeChunk.getValue()))
                            .build());
    private final Setting<EntityColor> sideColor =
            this.render.add(
                    new ColorSetting.Builder()
                            .name("side-color")
                            .defaultValue(new EntityColor(225, 25, 25, 50))
                            .visibleWhen(() -> !Boolean.TRUE.equals(this.flagWholeChunk.getValue()))
                            .build());
    private final Setting<EntityColor> lineColor =
            this.render.add(
                    new ColorSetting.Builder()
                            .name("line-color")
                            .defaultValue(new EntityColor(225, 25, 25, 255))
                            .visibleWhen(() -> !Boolean.TRUE.equals(this.flagWholeChunk.getValue()))
                            .build());
    private final Setting<EntityColor> flagColor =
            this.render.add(
                    new ColorSetting.Builder()
                            .name("flag-color")
                            .description("Color of the full-chunk pillar.")
                            .defaultValue(new EntityColor(174, 87, 255, 90))
                            .visibleWhen(() -> Boolean.TRUE.equals(this.flagWholeChunk.getValue()))
                            .build());
    private final Setting<Double> textScale =
            this.render.add(
                    new NumberSetting.Builder()
                            .name("text-scale")
                            .description("Size of the name tag.")
                            .defaultValue(1.0)
                            .min(0.5)
                            .sliderRange(0.5, 3.0)
                            .build());
    private final Setting<Double> textThickness =
            this.render.add(
                    new NumberSetting.Builder()
                            .name("text-thickness")
                            .description(
                                    "Fakes a bolder font by overdrawing the text. 0 = normal,"
                                        + " higher = thicker.")
                            .defaultValue(0.0)
                            .min(0.0)
                            .sliderRange(0.0, 1.5)
                            .build());
    private final Setting<EntityColor> textColor =
            this.render.add(
                    new ColorSetting.Builder()
                            .name("text-color")
                            .defaultValue(new EntityColor(255, 255, 255, 255))
                            .build());
    private final List<SpawnerFlagger.SpawnerEntry> spawners = new ArrayList<>();
    private int scanTimer;
    private final Setting<Boolean> showNametag =
            this.render.add(
                    new BooleanSetting.Builder()
                            .name("show-nametag")
                            .description("Shows the mob name tag fixed above the spawner.")
                            .defaultValue(true)
                            .build());
    private final Setting<SpawnerFilterMode> allowedSpawners =
            this.general.add(
                    new EnumSetting.Builder<SpawnerFilterMode>()
                            .name("allowed-spawners")
                            .description(
                                    "Choose whether every spawner is flagged or only selected"
                                        + " spawner types.")
                            .defaultValue(SpawnerFilterMode.AllowAll)
                            .build());
    private final Setting<List<EntityType<?>>> specificSpawners =
            this.general.add(
                    new EntityTypeListSetting.Builder()
                            .name("specific-spawners")
                            .description(
                                    "Spawner types to flag when Allowed Spawners is set to"
                                        + " Specific.")
                            .defaultValue()
                            .filter(new SpawnerMobFilter()::test)
                            .visibleWhen(new SpecificSpawnersVisible(this.allowedSpawners))
                            .build());

    public SpawnerFlagger() {
        super(
                Categories.MAIN,
                "spawner-flagger",
                "Highlights mob spawners and labels which mob they spawn.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!this.isActive()) {
            this.spawners.clear();
            this.scanTimer = 0;
        } else if (this.mc.world != null && this.mc.player != null && ++this.scanTimer >= 20) {
            this.scanTimer = 0;
            this.spawners.clear();
            int chunkX = this.mc.player.getChunkPos().x;
            int chunkZ = this.mc.player.getChunkPos().z;

            for (int cx = chunkX - CHUNK_RADIUS; cx <= chunkX + CHUNK_RADIUS; cx++) {
                for (int cz = chunkZ - CHUNK_RADIUS; cz <= chunkZ + CHUNK_RADIUS; cz++) {
                    WorldChunk chunk = this.mc.world.getChunk(cx, cz);
                    if (chunk != null) {
                        for (Entry<BlockPos, BlockEntity> entry :
                                chunk.getBlockEntities().entrySet()) {
                            if (entry.getValue() instanceof MobSpawnerBlockEntity spawner) {
                                this.spawners.add(
                                        new SpawnerFlagger.SpawnerEntry(
                                                entry.getKey().toImmutable(),
                                                this.getSpawnerName(spawner)));
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.isActive()) {
            boolean wholeChunk = Boolean.TRUE.equals(this.flagWholeChunk.getValue());
            HashSet<Long> seenChunks = wholeChunk ? new HashSet<>() : null;

            for (SpawnerFlagger.SpawnerEntry spawner : this.spawners) {
                if (SpawnerFilter.matchesFilter(
                        this.allowedSpawners.getValue(),
                        this.specificSpawners.getValue(),
                        spawner.name())) {
                    BlockPos pos = spawner.pos();
                    if (wholeChunk) {
                        int chunkX = Math.floorDiv(pos.getX(), 16);
                        int chunkZ = Math.floorDiv(pos.getZ(), 16);
                        long chunkKey = (long) chunkX << 32 ^ chunkZ & 0xFFFFFFFFL;
                        if (seenChunks.add(chunkKey)) {
                            double minX = chunkX * 16.0;
                            double minZ = chunkZ * 16.0;
                            EntityColor color = this.flagColor.getValue();
                            event.renderer.box(
                                    minX,
                                    pos.getY(),
                                    minZ,
                                    minX + 16.0,
                                    pos.getY() + this.pillarHeight.getValue(),
                                    minZ + 16.0,
                                    color,
                                    color,
                                    BoxRenderMode.Both,
                                    0);
                        }
                    } else {
                        event.renderer.box(
                                pos.getX(),
                                pos.getY(),
                                pos.getZ(),
                                pos.getX() + 1,
                                pos.getY() + 1,
                                pos.getZ() + 1,
                                this.sideColor.getValue(),
                                this.lineColor.getValue(),
                                this.shapeMode.getValue(),
                                0);
                        double centerX = pos.getX() + 0.5;
                        double centerZ = pos.getZ() + 0.5;
                        double halfWidth = this.pillarWidth.getValue() / 2.0;
                        event.renderer.box(
                                centerX - halfWidth,
                                pos.getY(),
                                centerZ - halfWidth,
                                centerX + halfWidth,
                                pos.getY() + this.pillarHeight.getValue(),
                                centerZ + halfWidth,
                                DeepslateTint.tintFill(this.sideColor.getValue(), pos),
                                DeepslateTint.tintOutline(this.lineColor.getValue(), pos),
                                this.shapeMode.getValue(),
                                0);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (Boolean.TRUE.equals(this.showNametag.getValue())
                && this.isActive()
                && !this.spawners.isEmpty()) {
            TextBackend text = TextBackend.get();
            double scale = this.textScale.getValue();

            for (SpawnerFlagger.SpawnerEntry spawner : this.spawners) {
                if (SpawnerFilter.matchesFilter(
                        this.allowedSpawners.getValue(),
                        this.specificSpawners.getValue(),
                        spawner.name())) {
                    Vector3d position =
                            new Vector3d(
                                    spawner.pos().getX() + 0.5,
                                    spawner.pos().getY() + 1.25,
                                    spawner.pos().getZ() + 0.5);
                    if (ScreenProjection.project(position, scale)) {
                        ScreenProjection.pushTransform(position);
                        text.begin();
                        double x = -text.getWidth(spawner.name()) / 2.0;
                        double thickness = this.textThickness.getValue();
                        if (thickness > 0.0) {
                            text.render(
                                    spawner.name(), x - thickness, 0.0, this.textColor.getValue());
                            text.render(
                                    spawner.name(), x + thickness, 0.0, this.textColor.getValue());
                            text.render(spawner.name(), x, -thickness, this.textColor.getValue());
                            text.render(spawner.name(), x, thickness, this.textColor.getValue());
                        }

                        text.render(spawner.name(), x, 0.0, this.textColor.getValue());
                        text.end();
                        ScreenProjection.popTransform();
                    }
                }
            }
        }
    }

    private String getSpawnerName(MobSpawnerBlockEntity spawner) {
        try {
            NbtCompound nbt =
                    spawner.createNbtWithIdentifyingData(this.mc.world.getRegistryManager());
            Optional<NbtCompound> spawnData = nbt.getCompound("SpawnData");
            if (spawnData.isPresent()) {
                Optional<NbtCompound> entity = spawnData.get().getCompound("entity");
                if (entity.isPresent()) {
                    String id = entity.get().getString("id").orElse("");
                    return this.formatEntityId(id);
                }
            }
        } catch (Exception ignored) {
        }

        return "Empty Spawner";
    }

    private String formatEntityId(String id) {
        if (id == null || id.isEmpty()) {
            return "Empty Spawner";
        }

        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        name = name.replace('_', ' ');
        StringBuilder builder = new StringBuilder();

        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                builder.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(' ');
            }
        }

        return builder.toString().trim() + " Spawner";
    }

    @Override
    public void onDeactivate() {
        this.spawners.clear();
        this.scanTimer = 0;
    }

    private record SpawnerEntry(BlockPos pos, String name) {}
}
