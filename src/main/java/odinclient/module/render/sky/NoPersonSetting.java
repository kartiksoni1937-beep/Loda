package odinclient.module.render.sky;

import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;

public final class NoPersonSetting {
    private static Setting<Boolean> setting;

    private NoPersonSetting() {}

    public static void initSetting(SkyArts skyArts) {
        setting =
                skyArts.settings
                        .getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("no-person")
                                        .description(
                                                "Do not render a person in the sky; keep the"
                                                    + " selected background.")
                                        .defaultValue(false)
                                        .visibleWhen(() -> false)
                                        .build());
    }

    public static boolean isEnabled() {
        return setting != null && Boolean.TRUE.equals(setting.getValue());
    }

    public static void setEnabled(boolean enabled) {
        if (setting != null) {
            setting.setValue(enabled);
        }
    }
}
