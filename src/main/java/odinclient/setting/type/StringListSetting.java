package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class StringListSetting extends Setting<List<String>> {
    private StringListSetting(StringListSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
    }

    protected List<String> parseValue(String text) {
        return text.isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(text.split("\\s*,\\s*")));
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<StringListSetting.Builder, StringListSetting, List<String>> {
        public Builder() {
            this.defaultValue = new ArrayList<>();
        }

        public StringListSetting build() {
            return new StringListSetting(this);
        }
    }
}
