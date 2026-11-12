package odinclient.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class SettingGroupList implements Iterable<SettingGroup> {
    private final List<SettingGroup> groups = new ArrayList<>();

    public SettingGroupList() {
        this.groups.add(new SettingGroup("General"));
    }

    public SettingGroup getGeneral() {
        return this.groups.getFirst();
    }

    public SettingGroup addGroup(String name) {
        SettingGroup group = new SettingGroup(name);
        this.groups.add(group);
        return group;
    }

    public int getGroupCount() {
        return this.groups.size();
    }

    @Override
    public Iterator<SettingGroup> iterator() {
        return this.groups.iterator();
    }
}
