package odinclient.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.type.BooleanSetting;

@Environment(EnvType.CLIENT)
public final class FontSettings {
    private static final FontSettings INSTANCE = new FontSettings();
    public final Setting<Boolean> customFont =
            new BooleanSetting.Builder().name("custom-font").defaultValue(false).build();

    private FontSettings() {}

    public static FontSettings getInstance() {
        return INSTANCE;
    }
}
