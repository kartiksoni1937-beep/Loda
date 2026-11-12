package odinclient.ui.scale;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

public final class GuiScaling {
    private GuiScaling() {}

    public static float getScale() {
        return ScreenScaleCompat.isScopeActive() ? 1.0F : ScreenScaleCompat.getScale(0);
    }

    public static int getScaledWidth() {
        return ScreenScaleCompat.getScaledWidth();
    }

    public static int getScaledHeight() {
        return ScreenScaleCompat.getScaledHeight();
    }

    public static int toScaledInt(int value) {
        return (int) Math.floor(value / getScale());
    }

    public static double toScaled(double value) {
        return value / getScale();
    }

    public static Click toScaledClick(Click click) {
        return click == null
                ? null
                : new Click(toScaled(click.x()), toScaled(click.y()), click.buttonInfo());
    }

    public static void pushScale(DrawContext context) {
        context.getMatrices().pushMatrix();
        float scale = getScale();
        context.getMatrices().scale(scale, scale);
    }

    public static void popScale(DrawContext context) {
        context.getMatrices().popMatrix();
    }
}
