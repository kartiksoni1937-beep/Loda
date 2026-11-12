package odinclient.setting.type;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class EntityTypeListSetting extends Setting<List<EntityType<?>>> {
    private final Predicate<EntityType<?>> filter;
    private final Map<EntityType<?>, EntityColor> colors;

    private EntityTypeListSetting(EntityTypeListSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
        this.colors = new LinkedHashMap<>();
        this.filter = builder.filter;

        for (EntityType<?> type : builder.defaultValue) {
            if (type != null) {
                this.colors.put(type, defaultColor(type));
            }
        }
    }

    public boolean setValue(List<EntityType<?>> types) {
        if (types == null) {
            return false;
        } else {
            ArrayList<EntityType<?>> result = new ArrayList<>();

            for (EntityType<?> type : types) {
                if (type != null && this.isAllowed(type) && !result.contains(type)) {
                    result.add(type);
                }
            }

            this.colors.keySet().retainAll(result);

            for (EntityType<?> selected : result) {
                this.colors.computeIfAbsent(selected, EntityTypeListSetting::defaultColor);
            }

            return super.setValue(result);
        }
    }

    protected List<EntityType<?>> parseValue(String text) {
        ArrayList<EntityType<?>> types = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            LinkedHashMap<EntityType<?>, EntityColor> parsedColors = new LinkedHashMap<>();

            for (String part : text.split(",")) {
                String entry = part.trim();
                if (!entry.isEmpty()) {
                    int separator = entry.lastIndexOf('=');
                    String idText = separator > 0 ? entry.substring(0, separator).trim() : entry;

                    Identifier id = Identifier.tryParse(idText);
                    if (id != null && Registries.ENTITY_TYPE.containsId(id)) {
                        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
                        if (this.isAllowed(type) && !types.contains(type)) {
                            types.add(type);
                            EntityColor parsed =
                                    separator > 0
                                            ? parseColor(entry.substring(separator + 1).trim())
                                            : null;
                            parsedColors.put(type, parsed != null ? parsed : defaultColor(type));
                        }
                    }
                }
            }

            this.colors.clear();
            this.colors.putAll(parsedColors);
            return types;
        } else {
            return types;
        }
    }

    public boolean isAllowed(EntityType<?> type) {
        return type != null && (this.filter == null || this.filter.test(type));
    }

    public boolean isSelected(EntityType<?> type) {
        return this.getValue().contains(type);
    }

    public EntityColor getColor(EntityType<?> type) {
        EntityColor color = this.colors.get(type);
        return color == null ? null : copyColor(color);
    }

    public void toggle(EntityType<?> type) {
        this.toggle(type, defaultColor(type));
    }

    public void toggle(EntityType<?> type, EntityColor color) {
        if (this.isAllowed(type)) {
            ArrayList<EntityType<?>> types = new ArrayList<>(this.getValue());
            if (types.remove(type)) {
                this.colors.remove(type);
            } else {
                types.add(type);
                this.colors.put(type, copyColor(color == null ? defaultColor(type) : color));
            }

            this.setValue(types);
        }
    }

    public void setColor(EntityType<?> type, EntityColor color) {
        if (this.isSelected(type) && color != null) {
            this.colors.put(type, copyColor(color));
            super.setValue(new ArrayList<>(this.getValue()));
        }
    }

    public String serialize() {
        Comparator<EntityType<?>> comparator = (a, b) -> id(a).compareTo(id(b));
        boolean includeColors = "mobs".equals(this.name);
        Function<EntityType<?>, String> formatter =
                type ->
                        includeColors
                                ? id(type) + "=" + formatColor(this.getColorOrDefault(type))
                                : id(type);
        return this.getValue().stream()
                .sorted(comparator)
                .map(formatter)
                .collect(Collectors.joining(","));
    }

    public static EntityColor defaultColor(EntityType<?> type) {
        String typeId = type == null ? "minecraft:pig" : id(type);
        int rgb =
                java.awt.Color.HSBtoRGB(
                        (typeId.hashCode() & Integer.MAX_VALUE) / 2.1474836E9F, 0.72F, 1.0F);
        return new EntityColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255);
    }

    private EntityColor getColorOrDefault(EntityType<?> type) {
        EntityColor color = this.colors.get(type);
        return color == null ? defaultColor(type) : color;
    }

    private static String id(EntityType<?> type) {
        return Registries.ENTITY_TYPE.getId(type).toString();
    }

    private static EntityColor copyColor(EntityColor color) {
        return new EntityColor(color.r, color.g, color.b, color.a);
    }

    private static EntityColor parseColor(String text) {
        try {
            String digits = text.startsWith("#") ? text.substring(1) : text;

            long value = Long.parseLong(digits, 16);
            return digits.length() <= 6
                    ? new EntityColor(
                            (int) (value >> 16) & 0xFF,
                            (int) (value >> 8) & 0xFF,
                            (int) value & 0xFF,
                            255)
                    : new EntityColor(
                            (int) (value >> 24) & 0xFF,
                            (int) (value >> 16) & 0xFF,
                            (int) (value >> 8) & 0xFF,
                            (int) value & 0xFF);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String formatColor(EntityColor color) {
        return String.format("#%02X%02X%02X%02X", color.r, color.g, color.b, color.a);
    }

    public static final class Builder
            extends SettingBuilder<
                    EntityTypeListSetting.Builder, EntityTypeListSetting, List<EntityType<?>>> {
        private Predicate<EntityType<?>> filter;

        public Builder() {
            this.defaultValue = new ArrayList<>();
        }

        public EntityTypeListSetting.Builder defaultValue(EntityType<?>... types) {
            this.defaultValue = new ArrayList<>(Arrays.asList(types));
            return this;
        }

        public EntityTypeListSetting.Builder filter(Predicate<EntityType<?>> predicate) {
            this.filter = predicate;
            return this;
        }

        public EntityTypeListSetting build() {
            return new EntityTypeListSetting(this);
        }
    }
}
