package odinclient.ui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.GuiScaling;
import odinclient.ui.screen.ConfigScreen;

public final class ConfigsButton {
    private static final int BUTTON_WIDTH = 104;
    private static final int BUTTON_HEIGHT = 20;

    private ConfigsButton() {}

    public static void renderButton(Screen screen, DrawContext context, int mouseX, int mouseY) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int x = (screenWidth - 104) / 2;
        int y = screenHeight - 28;
        boolean hovered =
                isHovered(
                        mouseX * GuiScaling.getScale(),
                        mouseY * GuiScaling.getScale(),
                        x,
                        y,
                        104,
                        20);
        DashboardRenderer.drawRoundedRect(context, x + 1, y + 3, 104, 20, 5, 1694498816);
        int fillColor = hovered ? -316396740 : -451272915;
        int borderColor = hovered ? -430257153 : -1236774806;

        DashboardRenderer.drawBorderedRoundedRect(
                context, x, y, 104, 20, 5, fillColor, borderColor);
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        String label = "CONFIGS";
        int textX = (x + x + 104 - DashboardRenderer.width(textRenderer, label)) / 2;
        int textY = y + 6;
        int textColor = hovered ? -1 : -1512458;

        DashboardRenderer.drawBoldText(context, textRenderer, label, textX, textY, textColor);
    }

    public static boolean handleClick(Screen parent, Click click) {
        if (click.button() != 0) {
            return false;
        } else {
            MinecraftClient client = MinecraftClient.getInstance();
            int screenWidth = client.getWindow().getScaledWidth();
            int y = client.getWindow().getScaledHeight() - 28;
            int x = (screenWidth - 104) / 2;
            if (!isHovered(click.x(), click.y(), x, y, 104, 20)) {
                return false;
            } else {
                client.setScreen(new ConfigScreen(parent));
                return true;
            }
        }
    }

    private static boolean isHovered(
            double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
