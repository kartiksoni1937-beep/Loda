package odinclient.module.render.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.core.Categories;
import odinclient.module.Module;

@Environment(EnvType.CLIENT)
public final class ChinaHead extends Module {
    private static ChinaHead instance;

    public ChinaHead() {
        super(
                Categories.MAIN,
                "china-head",
                "A gently rotating straw hat fitted to your head in every pose.");
        this.title = "China Head";
        instance = this;
    }

    public static boolean isEnabled() {
        return instance != null && instance.isActive();
    }
}
