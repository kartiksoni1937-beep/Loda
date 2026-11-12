package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

@Environment(EnvType.CLIENT)
public final class BooleanSetting extends Setting<Boolean> {
    private BooleanSetting(BooleanSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
    }

    protected Boolean parseValue(String text) {
        return Boolean.parseBoolean(text);
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<BooleanSetting.Builder, BooleanSetting, Boolean> {
        public Builder() {
            this.defaultValue = false;
        }

        public BooleanSetting build() {
            return new BooleanSetting(this);
        }
    }
}
