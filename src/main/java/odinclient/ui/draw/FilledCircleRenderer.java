package odinclient.ui.draw;

import net.minecraft.client.gui.DrawContext;

public final class FilledCircleRenderer {
    private static final int SMALL_RADIUS_THRESHOLD = 2;
    private static final double SMALL_CIRCLE_RADIUS = 1.5;

    private FilledCircleRenderer() {}

    public static void fill(DrawContext context, int x, int y, int radius, int color) {
        int alpha = color >>> 24;
        if (context != null && radius > 0 && alpha != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(0.5F, 0.5F);

            try {
                if (radius <= 2) {
                    fillCircle(context, x * 2 + 1.0, y * 2 + 1.0, 1.5, color, alpha);
                } else {
                    fillCircle(context, x * 2, y * 2, radius * 2, color, alpha);
                }
            } finally {
                context.getMatrices().popMatrix();
            }
        }
    }

    private static void fillCircle(
            DrawContext context,
            double centerX,
            double centerY,
            double radius,
            int color,
            int alpha) {
        double radiusSquared = radius * radius;
        int top = (int) Math.floor(centerY - radius);
        int bottom = (int) Math.ceil(centerY + radius);

        for (int row = top; row < bottom; row++) {
            double dy = row + 0.5 - centerY;
            double halfWidthSquared = radiusSquared - dy * dy;
            if (!(halfWidthSquared <= 0.0)) {
                double halfWidth = Math.sqrt(halfWidthSquared);
                double left = centerX - halfWidth;
                double right = centerX + halfWidth;
                int startX = (int) Math.ceil(left);
                int endX = (int) Math.floor(right);
                if (endX > startX) {
                    context.fill(startX, row, endX, row + 1, color);
                }

                fillEdgePixel(context, startX - 1, row, startX - left, color, alpha);
                fillEdgePixel(context, endX, row, right - endX, color, alpha);
            }
        }
    }

    private static void fillEdgePixel(
            DrawContext context, int x, int y, double coverage, int color, int alpha) {
        if (!(coverage <= 0.001)) {
            int edgeAlpha = (int) Math.round(alpha * Math.min(1.0, coverage));
            if (edgeAlpha >= 4) {
                context.fill(x, y, x + 1, y + 1, withAlpha(color, edgeAlpha));
            }
        }
    }

    public static void fillLegacy(
            DrawContext context, int x, int y, int radius, int color, int alpha) {}

    private static int withAlpha(int color, int alpha) {
        int rgb = color & 16777215;
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }
}
