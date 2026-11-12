package odinclient.module.spawner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum SpawnerFilterMode {
    AllowAll("Allow All"),
    Specific("Specific");

    private final String title;

    private SpawnerFilterMode(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return this.title;
    }
}
