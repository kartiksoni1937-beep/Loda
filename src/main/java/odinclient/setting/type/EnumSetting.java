package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

import java.util.Arrays;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class EnumSetting<T extends Enum<T>> extends Setting<T> {
    private EnumSetting(EnumSetting.Builder<T> builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
    }

    protected T parseValue(String text) {
        for (T constant : this.getValue().getDeclaringClass().getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(text)) {
                return constant;
            }
        }

        throw new IllegalArgumentException(text);
    }

    @Override
    public List<String> a_() {
        return Arrays.stream(this.getValue().getDeclaringClass().getEnumConstants())
                .map(Enum::name)
                .toList();
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder<T extends Enum<T>>
            extends SettingBuilder<EnumSetting.Builder<T>, EnumSetting<T>, T> {
        public EnumSetting<T> build() {
            if (this.defaultValue == null) {
                throw new IllegalStateException("Enum setting requires a default value");
            } else {
                return new EnumSetting<>(this);
            }
        }
    }
}
