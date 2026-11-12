package odinclient.ui.draw;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public final class DrawContextHolder {
    private static final ThreadLocal<DrawContext> current = new ThreadLocal<>();

    private DrawContextHolder() {}

    public static void set(DrawContext context) {
        current.set(context);
    }

    public static DrawContext get() {
        return current.get();
    }

    public static void clear() {
        current.remove();
    }
}
