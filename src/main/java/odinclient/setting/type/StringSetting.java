package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

@Environment(EnvType.CLIENT)
public final class StringSetting extends Setting<String> {
    public final String hint;

    private StringSetting(StringSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
        this.hint = builder.hint;
    }

    protected String parseValue(String value) {
        return value;
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<StringSetting.Builder, StringSetting, String> {
        private String hint = "";

        public Builder() {
            this.defaultValue = "";
        }

        public StringSetting.Builder hint(String text) {
            this.hint = text;
            return this;
        }

        public StringSetting build() {
            return new StringSetting(this);
        }
    }
}
