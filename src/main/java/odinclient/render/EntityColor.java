package odinclient.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class EntityColor extends Color {
    public EntityColor(int r, int g, int b) {
        super(r, g, b);
    }

    public EntityColor(int r, int g, int b, int a) {
        super(r, g, b, a);
    }
}
