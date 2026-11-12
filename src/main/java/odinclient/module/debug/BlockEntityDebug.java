package odinclient.module.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.PacketEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class BlockEntityDebug extends Module {
    private static final int MIN_Y = -10;
    private final Setting<Boolean> showNbt;
    private final Setting<Boolean> deduplicate;
    private final Setting<Boolean> tracers;
    private final Map<BlockPos, String> detected = new ConcurrentHashMap<>();

    public BlockEntityDebug() {
        super(
                Categories.MAIN,
                "block-entity-debug",
                "Detects and renders block entities below Y=-10 from raw server packets.");
        this.title = "Block Entity Debug";
        this.showNbt =
                this.addBooleanSetting(
                        "show-nbt", "Include NBT data in the OdinClient chat alert.", true);
        this.deduplicate =
                this.addBooleanSetting("deduplicate", "Only alert once per block position.", true);
        this.tracers =
                this.addBooleanSetting(
                        "tracers", "Draw tracer lines to detected block entities.", true);
    }

    private Setting<Boolean> addBooleanSetting(
            String name, String description, boolean defaultValue) {
        return this.settings
                .getGeneral()
                .add(
                        new BooleanSetting.Builder()
                                .name(name)
                                .description(description)
                                .defaultValue(defaultValue)
                                .build());
    }

    @Override
    public void onDeactivate() {
        this.detected.clear();
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (this.mc.player != null && !this.detected.isEmpty()) {
            int viewDistance = this.mc.options.getClampedViewDistance();
            int playerChunkX = (int) Math.floor(this.mc.player.getX()) >> 4;
            int playerChunkZ = (int) Math.floor(this.mc.player.getZ()) >> 4;

            for (Entry<BlockPos, String> entry : this.detected.entrySet()) {
                BlockPos pos = entry.getKey();
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (Math.abs(chunkX - playerChunkX) <= viewDistance
                        && Math.abs(chunkZ - playerChunkZ) <= viewDistance) {
                    Color sideColor = getTypeColor(entry.getValue(), 125);
                    Color lineColor = getTypeColor(entry.getValue(), 255);
                    event.renderer.box(
                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            pos.getX() + 1,
                            pos.getY() + 1,
                            pos.getZ() + 1,
                            sideColor,
                            sideColor,
                            BoxRenderMode.Sides,
                            0);
                    if (Boolean.TRUE.equals(this.tracers.getValue())) {
                        event.renderer.blockEspHybridTracer(
                                new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                                lineColor,
                                1.0F);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof BlockEntityUpdateS2CPacket update) {
            this.handleBlockEntityUpdate(update);
        } else if (event.packet instanceof ChunkDataS2CPacket chunkData) {
            this.handleChunkData(chunkData, chunkData.getChunkX(), chunkData.getChunkZ());
        }
    }

    private void handleBlockEntityUpdate(BlockEntityUpdateS2CPacket packet) {
        BlockPos pos = packet.getPos();
        if (pos.getY() < -10) {
            String typeName = getTypeName(packet.getBlockEntityType());
            if (!Boolean.TRUE.equals(this.deduplicate.getValue())
                    || !this.detected.containsKey(pos)) {
                this.detected.put(pos.toImmutable(), typeName);
                StringBuilder message = buildAlertMessage("Update", typeName, pos);
                if (Boolean.TRUE.equals(this.showNbt.getValue()) && packet.getNbt() != null) {
                    message.append(" nbt=").append(packet.getNbt());
                }

                this.sendAlert(message.toString());
            }
        }
    }

    private void handleChunkData(ChunkDataS2CPacket packet, int chunkX, int chunkZ) {
        Object blockEntityData = null;

        for (Field packetField : getInstanceFields(packet.getClass())) {
            if (!packetField.getType().isPrimitive()) {
                Object fieldValue = readField(packetField, packet);
                if (fieldValue != null) {
                    boolean hasBytes = false;
                    boolean hasList = false;

                    for (Field inner : getInstanceFields(fieldValue.getClass())) {
                        hasBytes |= inner.getType() == byte[].class;
                        hasList |= List.class.isAssignableFrom(inner.getType());
                    }

                    if (hasBytes && hasList) {
                        blockEntityData = fieldValue;
                        break;
                    }
                }
            }
        }

        if (blockEntityData != null) {
            List<?> entries = null;

            for (Field listField : getInstanceFields(blockEntityData.getClass())) {
                if (List.class.isAssignableFrom(listField.getType())) {
                    Object listValue = readField(listField, blockEntityData);
                    if (listValue instanceof List<?> list) {
                        entries = list;
                        break;
                    }
                }
            }

            if (entries != null) {
                for (Object entry : entries) {
                    this.handleChunkBlockEntity(entry, chunkX, chunkZ);
                }
            }
        }
    }

    private void handleChunkBlockEntity(Object entry, int chunkX, int chunkZ) {
        List<Integer> ints = new ArrayList<>(2);
        BlockEntityType<?> type = null;
        NbtCompound nbt = null;

        for (Field field : getInstanceFields(entry.getClass())) {
            Object value = readField(field, entry);
            if (field.getType() == int.class && value instanceof Integer) {
                ints.add((Integer) value);
            } else if (value instanceof BlockEntityType) {
                type = (BlockEntityType<?>) value;
            } else if (value instanceof NbtCompound) {
                nbt = (NbtCompound) value;
            }
        }

        if (ints.size() >= 2) {
            int packedXZ = ints.get(0);
            int y = ints.get(1);
            if (y < -10) {
                int x = chunkX * 16 + (packedXZ >> 4 & 15);
                int z = chunkZ * 16 + (packedXZ & 15);
                BlockPos pos = new BlockPos(x, y, z);
                String typeName = getTypeName(type);
                if (!Boolean.TRUE.equals(this.deduplicate.getValue())
                        || !this.detected.containsKey(pos)) {
                    this.detected.put(pos, typeName);
                    StringBuilder message = buildAlertMessage("Chunk", typeName, pos);
                    if (Boolean.TRUE.equals(this.showNbt.getValue()) && nbt != null) {
                        message.append(" nbt=").append(nbt);
                    }

                    this.sendAlert(message.toString());
                }
            }
        }
    }

    private void sendAlert(String message) {
        this.mc.execute(() -> this.info("%s", message));
    }

    private static StringBuilder buildAlertMessage(String kind, String typeName, BlockPos pos) {
        return new StringBuilder("[")
                .append(kind)
                .append("] ")
                .append(typeName)
                .append(" at (")
                .append(pos.getX())
                .append(", ")
                .append(pos.getY())
                .append(", ")
                .append(pos.getZ())
                .append(")");
    }

    private static String getTypeName(BlockEntityType<?> type) {
        Identifier id = type == null ? null : Registries.BLOCK_ENTITY_TYPE.getId(type);
        return id == null ? "unknown" : id.toString();
    }

    private static Color getTypeColor(String typeName, int alpha) {
        return switch (typeName) {
            case "minecraft:trapped_chest" -> new Color(200, 91, 0, alpha);
            case "minecraft:chest" -> new Color(156, 91, 0, alpha);
            case "minecraft:ender_chest" -> new Color(117, 0, 255, alpha);
            case "minecraft:mob_spawner" -> new Color(138, 126, 166, alpha);
            case "minecraft:shulker_box" -> new Color(134, 0, 158, alpha);
            case "minecraft:furnace" -> new Color(125, 125, 125, alpha);
            case "minecraft:barrel" -> new Color(255, 140, 140, alpha);
            case "minecraft:enchanting_table" -> new Color(80, 80, 255, alpha);
            case "minecraft:piston" -> new Color(35, 226, 0, alpha);
            default -> new Color(255, 255, 255, alpha);
        };
    }

    private static Object readField(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Field> getInstanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();

        for (Class<?> current = type;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }

        return fields;
    }

    public static String[] appendModuleName(String[] names) {
        for (String name : names) {
            if (name.equals("BlockEntityDebug")) {
                return names;
            }
        }

        String[] result = Arrays.copyOf(names, names.length + 1);
        result[result.length - 1] = "BlockEntityDebug";
        return result;
    }
}
