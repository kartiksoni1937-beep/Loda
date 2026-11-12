package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

@Environment(EnvType.CLIENT)
public final class ColorSetting extends Setting<EntityColor> {
    private ColorSetting(ColorSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
    }

    protected EntityColor parseValue(String text) {
        String hex = text.trim().replace("#", "");
        long value = Long.parseLong(hex, 16);
        return hex.length() <= 6
                ? new EntityColor(
                        (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF)
                : new EntityColor(
                        (int) (value >> 24) & 0xFF,
                        (int) (value >> 16) & 0xFF,
                        (int) (value >> 8) & 0xFF,
                        (int) value & 0xFF);
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<ColorSetting.Builder, ColorSetting, EntityColor> {
        public Builder() {
            this.defaultValue = new EntityColor(255, 255, 255);
        }

        public ColorSetting.Builder defaultValue(EntityColor color) {
            this.defaultValue = color;
            return this;
        }

        public ColorSetting build() {
            return new ColorSetting(this);
        }
    }
}
