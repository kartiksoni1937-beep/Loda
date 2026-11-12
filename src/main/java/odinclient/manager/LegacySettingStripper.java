package odinclient.manager;

import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.SettingGroupList;

import java.util.Iterator;

public final class LegacySettingStripper {
    private LegacySettingStripper() {}

    public static void removeLegacySettings(
            SettingGroupList groups,
            Setting<Boolean> enabledSetting,
            Setting<Integer> valueSetting) {
        enabledSetting.setValue(Boolean.TRUE);
        for (SettingGroup group : groups) {
            Iterator<Setting<?>> settingIterator = group.iterator();

            while (settingIterator.hasNext()) {
                Setting<?> setting = settingIterator.next();
                if (setting == enabledSetting || setting == valueSetting) {
                    settingIterator.remove();
                }
            }
        }
    }
}
