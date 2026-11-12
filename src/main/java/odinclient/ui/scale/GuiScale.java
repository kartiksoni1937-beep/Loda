package odinclient.ui.scale;

public final class GuiScale {
    public static final int GUI_SCALE_FACTOR = 2;

    private GuiScale() {}

    public static float getScale(int guiScale) {
        return 2.0F / Math.max(1, guiScale);
    }

    public static int getScaledSize(int size) {
        return Math.max(1, (int) Math.ceil(Math.max(0, size) / 2.0));
    }

    public static float getFitScale(int width, int guiScale, float baseWidth) {
        return getWidthFitScale(getScaledSize(width), baseWidth) * getScale(guiScale);
    }

    public static float getWidthFitScale(int width, float baseWidth) {
        return Math.max(0.5F, Math.min(1.6F, width * 0.98F / baseWidth));
    }

    public static float getBoundsFitScale(
            int width, int height, float contentWidth, float contentHeight) {
        return Math.min(width * 0.84F / contentWidth, height * 0.86F / contentHeight);
    }

    public static boolean isConfigScreen(String className) {
        return "odinclient.ui.screen.ConfigScreen".equals(className)
                || "odinclient.ui.screen.HudPositionEditorScreen".equals(className)
                || "odinclient.ui.screen.IgnoredPlayersScreen".equals(className);
    }
}
