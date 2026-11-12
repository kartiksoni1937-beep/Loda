package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import odinclient.module.Module;
import odinclient.module.hud.ThemeModule;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.IntSetting;
import odinclient.ui.draw.BoldText;
import odinclient.ui.hud.ItemToastRenderer;

import java.lang.reflect.Field;
import java.util.List;

public final class HudPositionEditorScreen extends Screen {
    private static final int TEXT_PRIMARY_COLOR = -1380105;
    private static final int TEXT_SECONDARY_COLOR = -5984838;
    private static final int PANEL_COLOR = -1039331306;
    private final Screen parentScreen;
    private final Module module;
    private final IntSetting xSetting;
    private final IntSetting ySetting;
    private final int originalX;
    private final int originalY;
    private boolean dragging;
    private boolean closing;
    private int dragOffsetX;
    private int dragOffsetY;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private int doneButtonX;
    private int cancelButtonX;
    private int buttonY;
    private int buttonWidth;

    public HudPositionEditorScreen(
            Screen parent, Module module, IntSetting xSetting, IntSetting ySetting) {
        super(Text.literal("HUD Position Editor"));
        this.parentScreen = parent;
        this.module = module;
        this.xSetting = xSetting;
        this.ySetting = ySetting;
        this.originalX = xSetting.getValue();
        this.originalY = ySetting.getValue();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int accent = this.getAccentColor();
        context.fill(0, 0, this.width, this.height, 1375731712);
        this.drawGuideGrid(context);
        int panelWidth = Math.min(310, Math.max(190, this.width - 24));
        int panelX = (this.width - panelWidth) / 2;
        this.drawRoundedRect(context, panelX, 10, panelWidth, 35, 6, -1039331306);
        this.drawRoundedRect(
                context, panelX + 8, 12, panelWidth - 16, 1, 0, withAlpha(accent, 220));
        BoldText.draw(context, this.textRenderer, "HUD POSITION EDITOR", panelX + 9, 17, -1380105);
        BoldText.draw(
                context,
                this.textRenderer,
                "DRAG THE HIGHLIGHTED HUD ELEMENT",
                panelX + 9,
                29,
                -5984838);
        this.updatePreviewBounds();
        if (this.isNotifierModule()) {
            this.drawNotifierPreview(context);
        } else if (!this.module.isActive()) {
            this.drawPreview(context, accent);
        }

        this.drawSelectionCorners(context, accent);
        String positionText =
                "X " + this.xSetting.getValue() + "%   Y " + this.ySetting.getValue() + "%";
        int textWidth = BoldText.width(this.textRenderer, positionText);
        this.drawRoundedRect(
                context,
                this.previewX + Math.max(0, (this.previewWidth - textWidth - 10) / 2),
                Math.max(49, this.previewY - 15),
                textWidth + 10,
                12,
                4,
                -652993497);
        BoldText.draw(
                context,
                this.textRenderer,
                positionText,
                this.previewX + Math.max(5, (this.previewWidth - textWidth) / 2),
                Math.max(51, this.previewY - 13),
                -1380105);
        this.buttonWidth = 72;
        this.cancelButtonX = (this.width - (this.buttonWidth * 2 + 8)) / 2;
        this.doneButtonX = this.cancelButtonX + this.buttonWidth + 8;
        this.buttonY = this.height - 29;
        this.drawButton(
                context,
                this.cancelButtonX,
                this.buttonY,
                "CANCEL",
                isInside(mouseX, mouseY, this.cancelButtonX, this.buttonY, this.buttonWidth, 18),
                false,
                accent);
        this.drawButton(
                context,
                this.doneButtonX,
                this.buttonY,
                "DONE",
                isInside(mouseX, mouseY, this.doneButtonX, this.buttonY, this.buttonWidth, 18),
                true,
                accent);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return true;
        } else {
            double mx = click.x();
            double my = click.y();
            if (isInside(mx, my, this.doneButtonX, this.buttonY, this.buttonWidth, 18)) {
                this.closeEditor(true);
                return true;
            } else if (isInside(mx, my, this.cancelButtonX, this.buttonY, this.buttonWidth, 18)) {
                this.closeEditor(false);
                return true;
            } else if (isInside(
                    mx, my, this.previewX, this.previewY, this.previewWidth, this.previewHeight)) {
                this.dragging = true;
                this.dragOffsetX = (int) Math.round(mx) - this.previewX;
                this.dragOffsetY = (int) Math.round(my) - this.previewY;
                return true;
            } else {
                return true;
            }
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.dragging) {
            this.dragTo(click.x(), click.y());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.dragging) {
            this.dragTo(click.x(), click.y());
            this.dragging = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 256) {
            this.closeEditor(false);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removed() {
        if (!this.closing) {
            this.restoreOriginalPosition();
        }
    }

    private void dragTo(double mx, double my) {
        int maxX = Math.max(1, this.width - this.previewWidth);
        int maxY = Math.max(1, this.height - this.previewHeight);
        int left = clamp((int) Math.round(mx) - this.dragOffsetX, 0, maxX);
        int top = clamp((int) Math.round(my) - this.dragOffsetY, 0, maxY);
        int percentX = clamp(Math.round(left * 100.0F / maxX), 0, 100);
        int percentY = clamp(Math.round(top * 100.0F / maxY), 0, 100);
        if (this.xSetting.getValue() != percentX) {
            this.xSetting.setValue(percentX);
        }

        if (this.ySetting.getValue() != percentY) {
            this.ySetting.setValue(percentY);
        }

        this.updatePreviewBounds();
    }

    private void updatePreviewBounds() {
        float scale = (float) this.getNumericSetting("scale", this.getNumericSetting("size", 1.0));
        if ("spotify-hud".equals(this.module.name)) {
            this.previewWidth = Math.round((float) this.getNumericSetting("width", 195.0) * scale);
            this.previewHeight = Math.round(62.0F * scale);
        } else if ("staf-tracker".equals(this.module.name)) {
            int staffCount = Math.max(1, this.getStaffCount());
            this.previewWidth = Math.round(106.0F * scale);
            this.previewHeight = Math.round((17 + staffCount * 16) * scale);
        } else if ("weather-notifier".equals(this.module.name)) {
            this.previewWidth =
                    Math.round(ItemToastRenderer.getToastWidth("Rain", "Started raining") * scale);
            this.previewHeight = Math.round(20.0F * scale);
        } else if ("block-notifier".equals(this.module.name)) {
            this.previewWidth =
                    Math.round(
                            ItemToastRenderer.getToastWidth("Diamond Ore", "at 120, 12, -340")
                                    * scale);
            this.previewHeight = Math.round(20.0F * scale);
        } else if ("region-map".equals(this.module.name)) {
            this.previewWidth = Math.round(156.0F * scale);
            this.previewHeight = Math.round(195.0F * scale);
        } else {
            this.previewWidth = Math.round(120.0F * scale);
            this.previewHeight = Math.round(40.0F * scale);
        }

        this.previewWidth = clamp(this.previewWidth, 42, Math.max(42, this.width - 8));
        this.previewHeight = clamp(this.previewHeight, 24, Math.max(24, this.height - 62));
        int maxX = Math.max(0, this.width - this.previewWidth);
        int maxY = Math.max(0, this.height - this.previewHeight);
        this.previewX = Math.round(maxX * this.xSetting.getValue() / 100.0F);
        this.previewY = Math.round(maxY * this.ySetting.getValue() / 100.0F);
    }

    private void drawGuideGrid(DrawContext context) {
        for (int i = 1; i < 4; i++) {
            int x = this.width * i / 4;
            int y = this.height * i / 4;
            context.fill(x, 48, x + 1, Math.max(49, this.height - 35), 419430399);
            context.fill(0, y, this.width, y + 1, 419430399);
        }
    }

    private void drawSelectionCorners(DrawContext context, int accent) {
        int color = withAlpha(accent, this.dragging ? 255 : 225);
        int x = this.previewX;
        int y = this.previewY;
        int width = this.previewWidth;
        int height = this.previewHeight;
        int length = Math.min(12, Math.max(5, Math.min(width, height) / 4));
        context.fill(x, y, x + length, y + 2, color);
        context.fill(x, y, x + 2, y + length, color);
        context.fill(x + width - length, y, x + width, y + 2, color);
        context.fill(x + width - 2, y, x + width, y + length, color);
        context.fill(x, y + height - 2, x + length, y + height, color);
        context.fill(x, y + height - length, x + 2, y + height, color);
        context.fill(x + width - length, y + height - 2, x + width, y + height, color);
        context.fill(x + width - 2, y + height - length, x + width, y + height, color);
    }

    private void drawPreview(DrawContext context, int accent) {
        if ("spotify-hud".equals(this.module.name)) {
            this.drawSpotifyPreview(context, accent);
        } else if ("staf-tracker".equals(this.module.name)) {
            this.drawStaffPreview(context, accent);
        } else if ("region-map".equals(this.module.name)) {
            this.drawRegionMapPreview(context, accent);
        } else {
            this.drawRoundedRect(
                    context,
                    this.previewX,
                    this.previewY,
                    this.previewWidth,
                    this.previewHeight,
                    5,
                    -451930337);
            this.drawRoundedRect(
                    context,
                    this.previewX + 5,
                    this.previewY + 3,
                    this.previewWidth - 10,
                    1,
                    0,
                    withAlpha(accent, 210));
            BoldText.draw(
                    context,
                    this.textRenderer,
                    this.getPreviewTitle(),
                    this.previewX + 7,
                    this.previewY + 8,
                    -1380105);
            BoldText.draw(
                    context,
                    this.textRenderer,
                    "DRAG ME",
                    this.previewX + 7,
                    this.previewY + 20,
                    withAlpha(accent, 230));
        }
    }

    private void drawSpotifyPreview(DrawContext context, int accent) {
        int x = this.previewX;
        int y = this.previewY;
        int width = this.previewWidth;
        int height = this.previewHeight;
        this.drawRoundedRect(context, x, y, width, height, Math.max(5, height / 8), -317976296);
        int padding = Math.max(5, height / 7);
        int coverSize = Math.max(18, height - padding * 2);
        this.drawRoundedRect(
                context,
                x + padding,
                y + padding,
                coverSize,
                coverSize,
                Math.max(3, coverSize / 8),
                withAlpha(accent, 72));
        this.drawRoundedRect(
                context,
                x + padding + 2,
                y + padding + 2,
                coverSize - 4,
                coverSize - 4,
                Math.max(2, coverSize / 9),
                -14669257);
        int textX = x + padding + coverSize + Math.max(6, padding / 2);
        int barsBottom = y + padding + 8;
        context.fill(textX, barsBottom - 4, textX + 2, barsBottom, withAlpha(accent, 245));
        context.fill(textX + 3, barsBottom - 7, textX + 5, barsBottom, withAlpha(accent, 245));
        context.fill(textX + 6, barsBottom - 5, textX + 8, barsBottom, withAlpha(accent, 245));
        BoldText.draw(
                context,
                this.textRenderer,
                "NOW PLAYING",
                textX + 11,
                y + padding,
                withAlpha(accent, 245));
        BoldText.draw(
                context,
                this.textRenderer,
                "Spotify HUD Preview",
                textX,
                y + padding + 13,
                -1380105);
        BoldText.draw(context, this.textRenderer, "Spotify", textX, y + padding + 25, -5984838);
        int progressY = y + height - Math.max(7, padding);
        int progressRight = x + width - padding;
        context.fill(textX, progressY, progressRight, progressY + 2, 1258291199);
        context.fill(
                textX,
                progressY,
                textX + Math.max(8, (progressRight - textX) * 2 / 3),
                progressY + 2,
                withAlpha(accent, 235));
    }

    private void drawStaffPreview(DrawContext context, int accent) {
        int x = this.previewX;
        int y = this.previewY;
        int width = this.previewWidth;
        int height = this.previewHeight;
        this.drawRoundedRect(context, x, y, width, height, 5, -368044514);
        this.drawRoundedRect(context, x + 5, y + 2, width - 10, 1, 0, withAlpha(accent, 205));
        BoldText.draw(context, this.textRenderer, "ONLINE STAFF", x + 6, y + 6, -1380105);
        this.drawRoundedRect(context, x + width - 17, y + 4, 12, 8, 3, withAlpha(accent, 38));
        BoldText.draw(
                context, this.textRenderer, "1", x + width - 13, y + 5, withAlpha(accent, 235));
        int rowY = y + 17;
        this.drawRoundedRect(
                context, x + 3, rowY, width - 6, Math.max(14, height - 20), 3, 218103807);
        this.drawRoundedRect(context, x + 6, rowY + 2, 11, 11, 3, withAlpha(accent, 68));
        BoldText.draw(context, this.textRenderer, "Staff Preview", x + 22, rowY + 4, -1380105);
        this.drawRoundedRect(context, x + width - 10, rowY + 6, 4, 4, 2, -598343801);
    }

    private void drawRegionMapPreview(DrawContext context, int accent) {
        int x = this.previewX;
        int y = this.previewY;
        int width = this.previewWidth;
        int cellSize = Math.max(4, (width - Math.max(6, Math.round(12.0F * width / 156.0F))) / 9);
        this.drawRoundedRect(context, x, y, width, this.previewHeight, 6, -368044514);
        this.drawRoundedRect(context, x + 5, y + 2, width - 10, 1, 0, withAlpha(accent, 220));
        BoldText.draw(context, this.textRenderer, "REGION MAP", x + 7, y + 7, -1380105);
        int gridX = x + 6;
        int gridY = y + 20;

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int cellX = gridX + col * cellSize;
                int cellY = gridY + row * cellSize;
                int cellColor = row == 4 && col == 4 ? withAlpha(accent, 132) : 570490641;
                context.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, cellColor);
            }
        }

        int centerX = gridX + 4 * cellSize + cellSize / 2;
        int centerY = gridY + 4 * cellSize + cellSize / 2;
        this.drawRoundedRect(context, centerX - 2, centerY - 2, 4, 4, 2, withAlpha(accent, 245));
    }

    private boolean isNotifierModule() {
        return "weather-notifier".equals(this.module.name)
                || "block-notifier".equals(this.module.name);
    }

    private void drawNotifierPreview(DrawContext context) {
        float scale = (float) this.getNumericSetting("size", 1.0);
        if ("weather-notifier".equals(this.module.name)) {
            ItemToastRenderer.drawToast(
                    context,
                    new ItemStack(Items.WATER_BUCKET),
                    "Rain",
                    "Started raining",
                    this.previewX,
                    this.previewY,
                    scale,
                    0.72F);
        } else {
            ItemToastRenderer.drawToast(
                    context,
                    new ItemStack(Items.DIAMOND_ORE),
                    "Diamond Ore",
                    "at 120, 12, -340",
                    this.previewX,
                    this.previewY,
                    scale,
                    0.72F);
        }
    }

    private void drawButton(
            DrawContext context,
            int x,
            int y,
            String label,
            boolean hovered,
            boolean primary,
            int accent) {
        int color =
                primary
                        ? withAlpha(accent, hovered ? 235 : 190)
                        : (hovered ? -534105022 : -652532689);
        this.drawRoundedRect(context, x, y, this.buttonWidth, 18, 5, color);
        int labelWidth = BoldText.width(this.textRenderer, label);
        BoldText.draw(
                context,
                this.textRenderer,
                label,
                x + (this.buttonWidth - labelWidth) / 2,
                y + 5,
                -1380105);
    }

    private void closeEditor(boolean save) {
        if (!save) {
            this.restoreOriginalPosition();
        }

        this.closing = true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parentScreen);
        }
    }

    private void restoreOriginalPosition() {
        if (this.xSetting.getValue() != this.originalX) {
            this.xSetting.setValue(this.originalX);
        }

        if (this.ySetting.getValue() != this.originalY) {
            this.ySetting.setValue(this.originalY);
        }
    }

    private double getNumericSetting(String name, double fallback) {
        for (SettingGroup group : this.module.settings) {
            for (Setting setting : group) {
                if (name.equals(setting.name) && setting.getValue() instanceof Number number) {
                    return number.doubleValue();
                }
            }
        }

        return fallback;
    }

    private int getStaffCount() {
        try {
            Field field = this.module.getClass().getDeclaredField("cachedStaff");
            field.setAccessible(true);
            Object value = field.get(this.module);
            if (value instanceof List<?> list) {
                return list.size();
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return 1;
    }

    private String getPreviewTitle() {
        if ("staf-tracker".equals(this.module.name)) {
            return "ONLINE STAFF";
        } else {
            return "spotify-hud".equals(this.module.name)
                    ? "SPOTIFY HUD"
                    : this.module.name.replace('-', ' ').toUpperCase();
        }
    }

    private int getAccentColor() {
        return ThemeModule.getAccentColorAt(0.5F);
    }

    private void drawRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(0.5F, 0.5F);
            this.drawRoundedRectScaled(
                    context, x * 2, y * 2, width * 2, height * 2, radius * 2, color);
            context.getMatrices().popMatrix();
        }
    }

    private void drawRoundedRectScaled(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            context.fill(x, y, x + width, y + height, color);
        } else {
            context.fill(x, y + r, x + width, y + height - r, color);

            for (int i = 0; i < r; i++) {
                float dy = r - i - 0.5F;
                float inset = r - (float) Math.sqrt(r * r - dy * dy);
                this.drawRoundedRow(context, x, y + i, width, inset, color);
                this.drawRoundedRow(context, x, y + height - i - 1, width, inset, color);
            }
        }
    }

    private void drawRoundedRow(
            DrawContext context, int x, int y, int width, float inset, int color) {
        float left = x + inset;
        float right = x + width - inset;
        int leftPixel = (int) Math.ceil(left);
        int rightPixel = (int) Math.floor(right);
        if (rightPixel > leftPixel) {
            context.fill(leftPixel, y, rightPixel, y + 1, color);
        }

        int alpha = color >>> 24;
        float leftCoverage = leftPixel - left;
        if (leftCoverage > 0.001F) {
            int leftAlpha = Math.max(1, Math.round(alpha * leftCoverage));
            context.fill(leftPixel - 1, y, leftPixel, y + 1, withAlpha(color, leftAlpha));
        }

        float rightCoverage = right - rightPixel;
        if (rightCoverage > 0.001F) {
            context.fill(
                    rightPixel,
                    y,
                    rightPixel + 1,
                    y + 1,
                    withAlpha(color, Math.max(1, Math.round(alpha * rightCoverage))));
        }
    }

    private static int withAlpha(int color, int alpha) {
        return clamp(alpha, 0, 255) << 24 | color & 16777215;
    }

    private static boolean isInside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
