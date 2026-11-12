package odinclient.module.render.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

import odinclient.render.Color;

@Environment(EnvType.CLIENT)
public final class DeepslateTint {
    private DeepslateTint() {}

    public static Color tintFill(Color color, BlockPos pos) {
        return !isDeep(pos) ? color : new Color(40, 180, 255, alpha(color, 60));
    }

    public static Color tintOutline(Color color, BlockPos pos) {
        return !isDeep(pos) ? color : new Color(0, 230, 255, alpha(color, 255));
    }

    private static boolean isDeep(BlockPos pos) {
        return pos != null && pos.getY() < -58;
    }

    private static int alpha(Color color, int fallback) {
        return color == null ? fallback : color.a;
    }
}
