package odinclient.module.render.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.module.Category;
import odinclient.module.Module;

@Environment(EnvType.CLIENT)
public final class Fullbright extends Module {
    private static Fullbright instance;

    public Fullbright(Category category) {
        super(category, "fullbright", "Makes dark areas fully visible without potion effects.");
        instance = this;
    }

    public static boolean isEnabled() {
        return instance != null && instance.isActive();
    }
}
