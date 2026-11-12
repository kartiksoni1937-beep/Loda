package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.module.Category;

@Deprecated
@Environment(EnvType.CLIENT)
public final class LegacyAutoCrystal extends AutoCrystal {
    public LegacyAutoCrystal(Category category) {
        super(category);
    }
}
