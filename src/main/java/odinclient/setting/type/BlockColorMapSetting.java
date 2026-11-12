package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public final class BlockColorMapSetting extends Setting<Map<Block, EntityColor>> {
    private int blockSetRevision;

    private BlockColorMapSetting(BlockColorMapSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                copyMap(builder.defaultValue),
                builder.visibility,
                builder.changeListener);
    }

    public boolean setValue(Map<Block, EntityColor> value) {
        if (value == null) {
            return false;
        } else {
            boolean blocksChanged = !this.getValue().keySet().equals(value.keySet());

            boolean changed = super.setValue(copyMap(value));
            if (changed && blocksChanged) {
                this.blockSetRevision++;
            }

            return changed;
        }
    }

    protected Map<Block, EntityColor> parseValue(String text) {
        Map<Block, EntityColor> result = new LinkedHashMap<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(";")) {
                int separator = part.lastIndexOf('=');
                if (separator > 0 && separator < part.length() - 1) {
                    Identifier id = Identifier.tryParse(part.substring(0, separator).trim());
                    if (id != null && Registries.BLOCK.containsId(id)) {
                        EntityColor color = parseColor(part.substring(separator + 1).trim());
                        if (color != null) {
                            result.put(Registries.BLOCK.get(id), color);
                        }
                    }
                }
            }

            return result;
        } else {
            return result;
        }
    }

    public boolean hasBlock(Block block) {
        return this.getValue().containsKey(block);
    }

    public Set<Block> getBlocks() {
        return Set.copyOf(this.getValue().keySet());
    }

    public EntityColor getColor(Block block) {
        return this.getValue().get(block);
    }

    public void toggle(Block block, EntityColor color) {
        Map<Block, EntityColor> copy = copyMap(this.getValue());
        if (copy.containsKey(block)) {
            copy.remove(block);
        } else {
            copy.put(block, copyColor(color));
        }

        this.setValue(copy);
    }

    public void setColor(Block block, EntityColor color) {
        if (this.hasBlock(block)) {
            Map<Block, EntityColor> copy = copyMap(this.getValue());
            copy.put(block, copyColor(color));
            this.setValue(copy);
        }
    }

    public int getBlockSetRevision() {
        return this.blockSetRevision;
    }

    public String serialize() {
        return this.getValue().entrySet().stream()
                .sorted(
                        Entry.comparingByKey(
                                (first, second) ->
                                        Registries.BLOCK
                                                .getId(first)
                                                .toString()
                                                .compareTo(
                                                        Registries.BLOCK.getId(second).toString())))
                .map(
                        entry ->
                                Registries.BLOCK.getId(entry.getKey())
                                        + "="
                                        + formatColor(entry.getValue()))
                .collect(Collectors.joining(";"));
    }

    private static Map<Block, EntityColor> copyMap(Map<Block, EntityColor> source) {
        Map<Block, EntityColor> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach(
                    (block, color) -> {
                        if (block != null && color != null) {
                            copy.put(block, copyColor(color));
                        }
                    });
        }

        return copy;
    }

    private static EntityColor copyColor(EntityColor color) {
        return new EntityColor(color.r, color.g, color.b, color.a);
    }

    private static EntityColor parseColor(String text) {
        try {
            String hex = text.replace("#", "");
            long rgba = Long.parseLong(hex, 16);
            return hex.length() <= 6
                    ? new EntityColor(
                            (int) (rgba >> 16) & 0xFF, (int) (rgba >> 8) & 0xFF, (int) rgba & 0xFF)
                    : new EntityColor(
                            (int) (rgba >> 24) & 0xFF,
                            (int) (rgba >> 16) & 0xFF,
                            (int) (rgba >> 8) & 0xFF,
                            (int) rgba & 0xFF);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String formatColor(EntityColor color) {
        return String.format("%02x%02x%02x%02x", color.r, color.g, color.b, color.a);
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<
                    BlockColorMapSetting.Builder, BlockColorMapSetting, Map<Block, EntityColor>> {
        public Builder() {
            this.defaultValue = new LinkedHashMap<>();
        }

        public BlockColorMapSetting build() {
            return new BlockColorMapSetting(this);
        }
    }
}
