package odinclient.ui.draw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import odinclient.ui.screen.ClickGuiScreen;

import java.lang.reflect.Method;

public final class GuiTextBridge {
    private static Method drawGuiTextMethod;
    private static Method guiTextWidthMethod;

    private GuiTextBridge() {}

    public static void drawText(
            DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        ClickGuiScreen screen = getClickGuiScreen();
        if (screen == null || !tryDrawGuiText(screen, context, text, x, y, color)) {
            context.drawTextWithShadow(textRenderer, text, x, y, color);
        }
    }

    public static void drawCenteredText(
            DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        ClickGuiScreen screen = getClickGuiScreen();
        if (screen != null) {
            Integer textWidth = width(screen, text);
            if (textWidth != null
                    && tryDrawGuiText(screen, context, text, x - textWidth / 2, y, color)) {
                return;
            }
        }

        context.drawCenteredTextWithShadow(textRenderer, text, x, y, color);
    }

    private static ClickGuiScreen getClickGuiScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen instanceof ClickGuiScreen screen
                ? screen
                : null;
    }

    private static boolean tryDrawGuiText(
            ClickGuiScreen screen, DrawContext context, String text, int x, int y, int color) {
        try {
            if (drawGuiTextMethod == null) {
                drawGuiTextMethod =
                        ClickGuiScreen.class.getMethod(
                                "odinclient$drawGuiText",
                                DrawContext.class,
                                String.class,
                                int.class,
                                int.class,
                                int.class);
            }

            drawGuiTextMethod.invoke(screen, context, text, x, y, color);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static Integer width(ClickGuiScreen screen, String text) {
        try {
            if (guiTextWidthMethod == null) {
                guiTextWidthMethod =
                        ClickGuiScreen.class.getMethod("odinclient$guiTextWidth", String.class);
            }

            return (Integer) guiTextWidthMethod.invoke(screen, text);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
