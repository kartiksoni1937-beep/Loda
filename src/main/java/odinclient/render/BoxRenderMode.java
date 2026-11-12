package odinclient.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum BoxRenderMode {
    Lines,
    Sides,
    Both;
}
