package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

@Environment(EnvType.CLIENT)
public final class KeybindSetting extends Setting<Integer> {
    private KeybindSetting(KeybindSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
    }

    protected Integer parseValue(String text) {
        int keyCode = Integer.parseInt(text.trim());
        return (keyCode < -1 || keyCode > 348) && (keyCode < 1000 || keyCode > 1007) ? -1 : keyCode;
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<KeybindSetting.Builder, KeybindSetting, Integer> {
        public Builder() {
            this.defaultValue = -1;
        }

        public KeybindSetting build() {
            return new KeybindSetting(this);
        }
    }
}
