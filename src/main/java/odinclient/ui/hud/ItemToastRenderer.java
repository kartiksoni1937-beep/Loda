package odinclient.ui.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import odinclient.manager.ModuleManager;
import odinclient.module.Module;
import odinclient.module.hud.ThemeModule;
import odinclient.ui.draw.BoldText;

public final class ItemToastRenderer {
    public static final int TOAST_HEIGHT = 20;
    private static final int TITLE_COLOR = -1511948;
    private static final int SUBTITLE_COLOR = -6576460;

    private ItemToastRenderer() {}

    public static int getToastWidth(String title, String subtitle) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            int titleWidth = BoldText.width(client.textRenderer, title == null ? "" : title);
            int subtitleWidth =
                    BoldText.width(client.textRenderer, subtitle == null ? "" : subtitle);
            return Math.max(
                    80, Math.min(118, Math.round(Math.max(titleWidth, subtitleWidth) * 0.5F) + 22));
        } else {
            return 118;
        }
    }

    public static void drawAnchoredToast(
            DrawContext context,
            ItemStack stack,
            String title,
            String subtitle,
            int areaWidth,
            int areaHeight,
            int percentX,
            int percentY,
            float scale,
            float progress) {
        int toastWidth = getToastWidth(title, subtitle);
        float clampedScale = Math.max(0.5F, Math.min(2.0F, scale));
        int scaledWidth = Math.round(toastWidth * clampedScale);
        int scaledHeight = Math.round(20.0F * clampedScale);
        drawToast(
                context,
                stack,
                title,
                subtitle,
                Math.round(
                        Math.max(0, areaWidth - scaledWidth) * clampInt(percentX, 0, 100) / 100.0F),
                Math.round(
                        Math.max(0, areaHeight - scaledHeight)
                                * clampInt(percentY, 0, 100)
                                / 100.0F),
                clampedScale,
                progress);
    }

    public static void drawToast(
            DrawContext context,
            ItemStack stack,
            String title,
            String subtitle,
            int x,
            int y,
            float scale,
            float progress) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null && context != null) {
            int width = getToastWidth(title, subtitle);
            float clampedScale = Math.max(0.5F, Math.min(2.0F, scale));
            int themeColor = getThemeColor();
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) x, (float) y);
            context.getMatrices().scale(clampedScale, clampedScale);
            drawHalfScaleRoundedRect(context, 0, 1, width, 20, 3, 1275068416);
            drawHalfScaleRoundedRect(context, 0, 0, width, 20, 3, -803988700);
            if (stack != null && !stack.isEmpty()) {
                drawScaledItem(context, stack, 5, 5, 0.5F);
            }

            float textScale = 0.5F;
            int textWidth = Math.max(20, Math.round((width - 19) / textScale));
            drawScaledText(
                    context,
                    BoldText.truncateToWidth(client.textRenderer, title, textWidth),
                    15,
                    3,
                    textScale,
                    -1511948);
            drawScaledText(
                    context,
                    BoldText.truncateToWidth(client.textRenderer, subtitle, textWidth),
                    15,
                    9,
                    textScale,
                    -6576460);
            int barWidth = width - 10;
            int filledWidth = Math.round(barWidth * Math.max(0.0F, Math.min(1.0F, progress)));
            context.fill(5, 17, 5 + barWidth, 18, 1883723097);
            context.fill(5, 17, 5 + filledWidth, 18, themeColor);
            context.getMatrices().popMatrix();
        }
    }

    private static void drawScaledText(
            DrawContext context, String text, int x, int y, float scale, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) x, (float) y);
            context.getMatrices().scale(scale, scale);
            BoldText.draw(context, client.textRenderer, text, 0, 0, color);
            context.getMatrices().popMatrix();
        }
    }

    private static void drawScaledItem(
            DrawContext context, ItemStack stack, int x, int y, float scale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) x, (float) y);
        context.getMatrices().scale(scale, scale);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    private static int getThemeColor() {
        Module module = ModuleManager.get().getByName("theme");
        return module instanceof ThemeModule theme ? theme.color.getValue().toArgb() : -14447361;
    }

    private static void drawHalfScaleRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(0.5F, 0.5F);
            drawRoundedRect(context, x * 2, y * 2, width * 2, height * 2, radius * 2, color);
            context.getMatrices().popMatrix();
        }
    }

    private static void drawRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        int corner = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (corner == 0) {
            context.fill(x, y, x + width, y + height, color);
        } else {
            context.fill(x, y + corner, x + width, y + height - corner, color);

            for (int i = 0; i < corner; i++) {
                float dy = corner - i - 0.5F;
                float inset = corner - (float) Math.sqrt(corner * corner - dy * dy);
                drawRoundedRow(context, x, y + i, width, inset, color);
                drawRoundedRow(context, x, y + height - i - 1, width, inset, color);
            }
        }
    }

    private static void drawRoundedRow(
            DrawContext context, int x, int y, int width, float inset, int color) {
        float left = x + inset;
        float right = x + width - inset;
        int leftEdge = (int) Math.ceil(left);
        int rightEdge = (int) Math.floor(right);
        if (rightEdge > leftEdge) {
            context.fill(leftEdge, y, rightEdge, y + 1, color);
        }

        int alpha = color >>> 24;
        float leftCoverage = leftEdge - left;
        if (leftCoverage > 0.001F) {
            context.fill(
                    leftEdge - 1,
                    y,
                    leftEdge,
                    y + 1,
                    Math.max(1, Math.round(alpha * leftCoverage)) << 24 | color & 16777215);
        }

        float rightCoverage = right - rightEdge;
        if (rightCoverage > 0.001F) {
            context.fill(
                    rightEdge,
                    y,
                    rightEdge + 1,
                    y + 1,
                    Math.max(1, Math.round(alpha * rightCoverage)) << 24 | color & 16777215);
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
