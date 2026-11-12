package odinclient.ui.hud;

public final class HudLayout {
    private static final int PADDING = 5;
    private static final int TEXT_OFFSET = 3;
    private static final int ROW_HEIGHT = 7;

    private HudLayout() {}

    public static HudLayout.HudPanelLayout computeLayout(
            boolean watermark,
            boolean metrics,
            boolean coords,
            int watermarkWidth,
            int metricsWidth,
            int coordsWidth) {
        int rows = 0;
        int contentWidth = 0;
        int watermarkY = -1;
        int metricsY = -1;
        int coordsY = -1;
        if (watermark) {
            rows = 1;
            watermarkY = 3;
            contentWidth = Math.max(0, watermarkWidth);
        }

        if (metrics) {
            metricsY = 3 + rows++ * 7;
            contentWidth = Math.max(contentWidth, metricsWidth);
        }

        if (coords) {
            coordsY = 3 + rows++ * 7;
            contentWidth = Math.max(contentWidth, coordsWidth);
        }

        int width = rows == 0 ? 0 : contentWidth + 10;
        return new HudLayout.HudPanelLayout(
                width, computeHeight(watermark, metrics, coords), watermarkY, metricsY, coordsY);
    }

    public static int computeHeight(boolean watermark, boolean metrics, boolean coords) {
        int rows = (watermark ? 1 : 0) + (metrics ? 1 : 0) + (coords ? 1 : 0);
        return rows == 0 ? 0 : 5 + rows * 7;
    }

    public static String formatCoords(int x, int y, int z) {
        return "X " + x + "   Y " + y + "   Z " + z;
    }

    public record HudPanelLayout(
            int width, int height, int watermarkY, int metricsY, int coordsY) {}
}
