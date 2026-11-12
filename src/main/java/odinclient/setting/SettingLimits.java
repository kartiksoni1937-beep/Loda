package odinclient.setting;

import odinclient.setting.type.StringSetting;

public final class SettingLimits {
    private SettingLimits() {}

    public static int getMaxLength(StringSetting setting) {
        return setting != null && "webhook".equals(setting.name) ? 2048 : 32;
    }
}
