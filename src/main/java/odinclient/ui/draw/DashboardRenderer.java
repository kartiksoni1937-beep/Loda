package odinclient.ui.draw;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.StyleSpriteSource.Font;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import odinclient.module.hud.ThemeModule;

import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

public final class DashboardRenderer {
    private static final int MAX_CACHE_SIZE = 2048;
    private static final Map<String, MutableText> boldTextCache = new HashMap<>();
    private static final Map<String, MutableText> boldSmallTextCache = new HashMap<>();
    private static final Map<String, MutableText> microTextCache = new HashMap<>();
    private static final Map<String, Integer> boldWidthCache = new HashMap<>();
    private static final Map<String, Integer> boldSmallWidthCache = new HashMap<>();
    private static final Map<String, Integer> microWidthCache = new HashMap<>();
    private static final StyleSpriteSource boldFont =
            new Font(Identifier.of("odinclient", "dashboard"));
    private static final Style boldStyle = Style.EMPTY.withFont(boldFont);
    private static final StyleSpriteSource boldSmallFont =
            new Font(Identifier.of("odinclient", "dashboard_small"));
    private static final Style boldSmallStyle = Style.EMPTY.withFont(boldSmallFont);
    private static final StyleSpriteSource microFont =
            new Font(Identifier.of("odinclient", "dashboard_tiny"));
    private static final Style microStyle = Style.EMPTY.withFont(microFont);
    private static ThemeModule theme;

    private DashboardRenderer() {}

    public static MutableText boldText(String text) {
        return getCachedText(boldTextCache, text == null ? "" : text, boldStyle);
    }

    public static int width(TextRenderer textRenderer, String text) {
        return Math.round(SdfFontRenderer.width(SdfFontRenderer.FontStyle.BOLD, text));
    }

    public static MutableText boldSmallText(String text) {
        return getCachedText(boldSmallTextCache, text == null ? "" : text, boldSmallStyle);
    }

    public static int boldSmallWidth(TextRenderer textRenderer, String text) {
        return Math.round(SdfFontRenderer.width(SdfFontRenderer.FontStyle.BOLD_SMALL, text));
    }

    public static MutableText microText(String text) {
        return getCachedText(microTextCache, text == null ? "" : text, microStyle);
    }

    public static int microWidth(TextRenderer textRenderer, String text) {
        return Math.round(SdfFontRenderer.width(SdfFontRenderer.FontStyle.MICRO, text));
    }

    public static void drawBoldText(
            DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        SdfFontRenderer.drawTextAtTop(context, SdfFontRenderer.FontStyle.BOLD, text, x, y, color);
    }

    public static void drawBoldSmallText(
            DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        SdfFontRenderer.drawTextAtTop(
                context, SdfFontRenderer.FontStyle.BOLD_SMALL, text, x, y, color);
    }

    public static void drawMicroText(
            DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        SdfFontRenderer.drawTextAtTop(context, SdfFontRenderer.FontStyle.MICRO, text, x, y, color);
    }

    public static void drawCenteredBoldText(
            DrawContext context,
            TextRenderer textRenderer,
            String text,
            int centerX,
            int y,
            int color) {
        drawBoldText(
                context, textRenderer, text, centerX - width(textRenderer, text) / 2, y, color);
    }

    public static void drawRightAlignedBoldText(
            DrawContext context,
            TextRenderer textRenderer,
            String text,
            int rightX,
            int y,
            int color) {
        drawBoldText(context, textRenderer, text, rightX - width(textRenderer, text), y, color);
    }

    public static void drawOutlinedRoundedRect(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int fillColor,
            int borderColor) {
        drawRoundedRect(context, x, y, width, height, radius, borderColor);
        drawRoundedRect(
                context, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fillColor);
    }

    public static void drawBorderedRoundedRect(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int fillColor,
            int borderColor) {
        drawRoundedRect(context, x, y, width, height, radius, borderColor);
        drawRoundedRect(
                context, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fillColor);
    }

    public static void drawRing(
            DrawContext context,
            int centerX,
            int centerY,
            int radius,
            int thickness,
            int color,
            int innerColor) {
        int outerRadius = Math.max(1, radius);
        int innerRadius = Math.max(1, outerRadius - Math.max(1, thickness));
        if (innerColor >>> 24 == 0) {
            SdfShapeRenderer.drawRing(
                    context, centerX, centerY, outerRadius, outerRadius - innerRadius, color);
        } else {
            SdfShapeRenderer.fillCircle(context, centerX, centerY, outerRadius, color);
            SdfShapeRenderer.fillCircle(context, centerX, centerY, innerRadius, innerColor);
        }
    }

    public static void drawProgressRing(
            DrawContext context,
            int centerX,
            int centerY,
            int radius,
            int thickness,
            float progress,
            int ringColor,
            int arcColor,
            int innerColor) {
        int outerRadius = Math.max(2, radius);
        int ringThickness = Math.max(1, Math.min(thickness, outerRadius - 1));
        int innerRadius = outerRadius - ringThickness;
        drawRing(context, centerX, centerY, outerRadius, ringThickness, ringColor, innerColor);
        float clampedProgress = Math.max(0.0F, Math.min(1.0F, progress));
        if (clampedProgress <= 1.0E-4F) {
            return;
        }

        float scaledProgress = clampedProgress * 64;
        int segmentCount = Math.min(64, (int) Math.ceil(scaledProgress));
        int segmentStart = innerRadius * 2 + 1;
        int segmentEnd = outerRadius * 2 - 1;
        int halfWidth =
                Math.max(
                        1,
                        (int)
                                Math.ceil(
                                        3.141592653589793
                                                * (segmentStart + segmentEnd)
                                                / (2.0 * 64)));
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.scale(0.5F, 0.5F);

        try {
            for (int i = 0; i < segmentCount; i++) {
                float segmentAlpha = Math.max(0.0F, Math.min(1.0F, scaledProgress - i));
                float angle =
                        (float) (-1.5707963267948966 + (i + 0.5) * 3.141592653589793 * 2.0 / 64);
                matrices.pushMatrix();
                matrices.rotate(angle);
                context.fill(
                        segmentStart,
                        -halfWidth,
                        segmentEnd,
                        halfWidth,
                        scaleAlpha(arcColor, segmentAlpha));
                matrices.popMatrix();
            }
        } finally {
            matrices.popMatrix();
        }
    }

    public static void drawRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) {
            SdfShapeRenderer.fill(
                    context,
                    x,
                    y,
                    width,
                    height,
                    Math.min((float) scaleRadius(radius), Math.min(width, height) / 2.0F),
                    color);
        }
    }

    public static void drawRoundedHighlight(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        drawRoundedRect(context, x, y, width, height, radius, color);
    }

    private static int scaleRadius(int radius) {
        if (radius <= 0) {
            return 0;
        } else {
            return Math.max(0, Math.round(radius * (ThemeModule.getAnimationSteps() / 3.0F)));
        }
    }

    private static MutableText getCachedText(
            Map<String, MutableText> cache, String text, Style style) {
        MutableText cached = cache.get(text);
        if (cached != null) {
            return cached;
        } else {
            if (cache.size() >= 2048) {
                cache.clear();
            }

            MutableText created = Text.literal(text).setStyle(style);
            cache.put(text, created);
            return created;
        }
    }

    private static int getCachedWidth(
            Map<String, Integer> cache, String text, TextRenderer textRenderer, boolean small) {
        Integer cached = cache.get(text);
        if (cached != null) {
            return cached;
        } else {
            if (cache.size() >= 2048) {
                cache.clear();
            }

            MutableText styled = small ? boldSmallText(text) : boldText(text);
            int width = textRenderer.getWidth(styled.asOrderedText());
            cache.put(text, width);
            return width;
        }
    }

    private static int scaleAlpha(int color, float factor) {
        return Math.round((color >>> 24) * Math.max(0.0F, Math.min(1.0F, factor))) << 24
                | color & 16777215;
    }
}
