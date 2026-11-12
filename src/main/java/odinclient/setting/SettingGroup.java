package odinclient.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class SettingGroup implements Iterable<Setting<?>> {
    public final String name;
    private final List<Setting<?>> settings = new ArrayList<>();

    SettingGroup(String name) {
        this.name = name;
    }

    public <T> Setting<T> add(Setting<T> setting) {
        this.settings.add(setting);
        return setting;
    }

    @Override
    public Iterator<Setting<?>> iterator() {
        return this.settings.iterator();
    }
}
