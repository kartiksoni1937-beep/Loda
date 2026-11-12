package odinclient.module.spawner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.VisibilityCondition;

@Environment(EnvType.CLIENT)
public final class SpecificSpawnersVisible implements VisibilityCondition {
    private final Setting<?> specificModeSetting;

    public SpecificSpawnersVisible(Setting<?> specificModeSetting) {
        this.specificModeSetting = specificModeSetting;
    }

    @Override
    public boolean isVisible() {
        return this.specificModeSetting != null
                && this.specificModeSetting.getValue() == SpawnerFilterMode.Specific;
    }
}
