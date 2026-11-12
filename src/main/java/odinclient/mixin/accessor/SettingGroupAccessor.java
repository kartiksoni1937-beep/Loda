package odinclient.mixin.accessor;

import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(SettingGroup.class)
public interface SettingGroupAccessor {
    @Accessor(value = "settings", remap = false)
    List<Setting<?>> odinclient$orderedSettings();
}
