package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import odinclient.module.hud.ThemeModule;
import odinclient.setting.Setting;
import odinclient.setting.type.StringListSetting;
import odinclient.ui.draw.BoldText;

import java.util.ArrayList;
import java.util.List;

public final class IgnoredPlayersScreen extends Screen {
    private static final int TEXT_COLOR = -985864;
    private static final int MUTED_TEXT_COLOR = -6247496;
    private static final int PANEL_BACKGROUND_COLOR = -1039331306;
    private static final int ROW_BACKGROUND_COLOR = -1927271882;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 230;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 5;
    private final Screen parent;
    private final StringListSetting setting;
    private final List<String> names = new ArrayList<>();
    private TextFieldWidget nameField;
    private String inputText = "";
    private int panelX;
    private int panelY;
    private int inputX;
    private int inputY;
    private int inputWidth;
    private int addButtonX;
    private int listX;
    private int listY;
    private int listWidth;
    private int cancelButtonX;
    private int doneButtonX;
    private int footerButtonY;
    private int scrollOffset;
    private boolean closed;

    public IgnoredPlayersScreen(Screen parent, StringListSetting setting) {
        super(Text.literal("Ignored Player Names"));
        this.parent = parent;
        this.setting = setting;
        List<String> values = setting.getValue();
        if (values != null) {
            for (String value : values) {
                this.addInitialName(value);
            }
        }
    }

    public static void open(Screen parent, StringListSetting setting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new IgnoredPlayersScreen(parent, setting));
        }
    }

    @Override
    protected void init() {
        this.updateLayout();
        this.nameField =
                new TextFieldWidget(
                        this.textRenderer,
                        this.inputX,
                        this.inputY,
                        this.inputWidth,
                        18,
                        Text.literal("Player name"));
        this.nameField.setMaxLength(32);
        this.nameField.setDrawsBackground(false);
        this.nameField.setPlaceholder(Text.literal("Enter player name..."));
        this.nameField.setChangedListener(text -> this.inputText = text);
        this.nameField.setText(this.inputText);
        this.nameField.setFocused(true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.updateLayout();
        int accent = this.getAccentColor();
        context.fill(0, 0, this.width, this.height, 2013265920);
        this.drawRoundedRect(context, this.panelX, this.panelY, 300, 230, 9, withAlpha(accent, 74));
        this.drawRoundedRect(context, this.panelX + 1, this.panelY + 1, 298, 228, 8, -1039331306);
        this.drawRoundedRect(
                context, this.panelX + 7, this.panelY + 8, 3, 25, 1, withAlpha(accent, 235));
        this.drawText(context, "IGNORED PLAYERS", this.panelX + 16, this.panelY + 10, -985864);
        this.drawText(
                context,
                "PLAYERS SPAWNER PROTECT WILL IGNORE",
                this.panelX + 16,
                this.panelY + 23,
                -6247496);
        String countText = this.names.size() + (this.names.size() == 1 ? " PLAYER" : " PLAYERS");
        int badgeWidth = this.textWidth(countText) + 10;
        int badgeX = this.panelX + 300 - badgeWidth - 12;
        this.drawRoundedRect(
                context, badgeX, this.panelY + 9, badgeWidth, 13, 4, withAlpha(accent, 35));
        this.drawText(context, countText, badgeX + 5, this.panelY + 11, withAlpha(accent, 245));
        this.drawRoundedRect(
                context,
                this.inputX - 3,
                this.inputY - 2,
                this.inputWidth + 6,
                22,
                5,
                withAlpha(1515562, 224));
        this.drawRoundedRect(
                context,
                this.inputX - 3,
                this.inputY + 18,
                this.inputWidth + 6,
                1,
                0,
                withAlpha(accent, this.nameField.isFocused() ? 235 : 85));
        this.nameField.setDimensionsAndPosition(this.inputWidth, 18, this.inputX, this.inputY);
        String displayText =
                this.inputText != null && !this.inputText.isEmpty()
                        ? this.inputText
                        : "PLAYER NAME...";
        int displayColor = this.inputText != null && !this.inputText.isEmpty() ? -985864 : -6247496;
        this.drawText(
                context,
                this.trimToWidth(displayText, this.inputWidth - 10),
                this.inputX + 2,
                this.inputY + 4,
                displayColor);
        if (this.nameField.isFocused() && System.currentTimeMillis() / 450L % 2L == 0L) {
            int cursorX =
                    this.inputX
                            + 2
                            + this.textWidth(
                                    this.trimToWidth(this.inputText, this.inputWidth - 12));
            this.drawRoundedRect(
                    context, cursorX, this.inputY + 3, 1, 10, 0, withAlpha(accent, 235));
        }

        this.drawButton(
                context,
                this.addButtonX,
                this.inputY - 2,
                48,
                22,
                "ADD",
                isInside(mouseX, mouseY, this.addButtonX, this.inputY - 2, 48, 22),
                true,
                accent);
        this.drawRoundedRect(context, this.listX, this.listY, this.listWidth, 114, 6, -1710748397);
        int end = Math.min(this.names.size(), this.scrollOffset + 5);

        for (int i = this.scrollOffset; i < end; i++) {
            int row = i - this.scrollOffset;
            int rowY = this.listY + 2 + row * 22;
            String name = this.names.get(i);
            this.drawRoundedRect(
                    context, this.listX + 3, rowY + 1, this.listWidth - 6, 19, 4, -1927271882);
            this.drawRoundedRect(
                    context, this.listX + 7, rowY + 7, 5, 5, 2, withAlpha(accent, 225));
            this.drawText(
                    context,
                    this.trimToWidth(name, this.listWidth - 82),
                    this.listX + 18,
                    rowY + 6,
                    -985864);
            int buttonX = this.listX + this.listWidth - 51;
            boolean hovered = isInside(mouseX, mouseY, buttonX, rowY + 3, 43, 15);
            this.drawRoundedRect(
                    context, buttonX, rowY + 3, 43, 15, 4, hovered ? -397072848 : -1808326101);
            String removeLabel = "REMOVE";
            this.drawText(
                    context,
                    removeLabel,
                    buttonX + (43 - this.textWidth(removeLabel)) / 2,
                    rowY + 6,
                    hovered ? -12332 : -1791311);
        }

        if (this.names.isEmpty()) {
            String emptyLabel = "NO IGNORED PLAYERS";
            this.drawText(
                    context,
                    emptyLabel,
                    this.listX + (this.listWidth - this.textWidth(emptyLabel)) / 2,
                    this.listY + 49,
                    -6247496);
        }

        if (this.names.size() > 5) {
            int trackY = this.listY + 5;
            int thumbHeight = Math.max(14, 520 / this.names.size());
            int maxScroll = this.getMaxScroll();
            int thumbY =
                    trackY
                            + (maxScroll == 0
                                    ? 0
                                    : (104 - thumbHeight) * this.scrollOffset / maxScroll);
            this.drawRoundedRect(
                    context, this.listX + this.listWidth - 3, trackY, 2, 104, 1, 822083583);
            this.drawRoundedRect(
                    context,
                    this.listX + this.listWidth - 3,
                    thumbY,
                    2,
                    thumbHeight,
                    1,
                    withAlpha(accent, 215));
        }

        this.drawButton(
                context,
                this.cancelButtonX,
                this.footerButtonY,
                76,
                20,
                "CANCEL",
                isInside(mouseX, mouseY, this.cancelButtonX, this.footerButtonY, 76, 20),
                false,
                accent);
        this.drawButton(
                context,
                this.doneButtonX,
                this.footerButtonY,
                76,
                20,
                "DONE",
                isInside(mouseX, mouseY, this.doneButtonX, this.footerButtonY, 76, 20),
                true,
                accent);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return true;
        } else {
            double mouseX = click.x();
            double mouseY = click.y();
            if (isInside(mouseX, mouseY, this.addButtonX, this.inputY - 2, 48, 22)) {
                this.addCurrentName();
                return true;
            } else if (isInside(mouseX, mouseY, this.cancelButtonX, this.footerButtonY, 76, 20)) {
                this.closeScreen(false);
                return true;
            } else if (isInside(mouseX, mouseY, this.doneButtonX, this.footerButtonY, 76, 20)) {
                this.closeScreen(true);
                return true;
            } else {
                int end = Math.min(this.names.size(), this.scrollOffset + 5);

                for (int i = this.scrollOffset; i < end; i++) {
                    if (isInside(
                            mouseX,
                            mouseY,
                            this.listX + this.listWidth - 51,
                            this.listY + 2 + (i - this.scrollOffset) * 22 + 3,
                            43,
                            15)) {
                        this.names.remove(i);
                        this.scrollOffset = Math.min(this.scrollOffset, this.getMaxScroll());
                        return true;
                    }
                }

                this.nameField.onClick(click, doubled);
                return true;
            }
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInside(mouseX, mouseY, this.listX, this.listY, this.listWidth, 114)) {
            this.scrollOffset =
                    clamp(
                            this.scrollOffset - (int) Math.signum(verticalAmount),
                            0,
                            this.getMaxScroll());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == 256) {
            this.closeScreen(false);
            return true;
        } else if (key != 257 && key != 335) {
            return this.nameField.keyPressed(input) || super.keyPressed(input);
        } else {
            this.addCurrentName();
            return true;
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        return this.nameField.charTyped(input) || super.charTyped(input);
    }

    @Override
    public void removed() {
        if (!this.closed) {
            this.closeScreen(false);
        }
    }

    private void updateLayout() {
        this.panelX = (this.width - 300) / 2;
        this.panelY = Math.max(12, (this.height - 230) / 2);
        this.inputX = this.panelX + 17;
        this.inputY = this.panelY + 51;
        this.inputWidth = 204;
        this.addButtonX = this.panelX + 300 - 65;
        this.listX = this.panelX + 13;
        this.listY = this.panelY + 78;
        this.listWidth = 274;
        this.footerButtonY = this.panelY + 230 - 28;
        this.cancelButtonX = this.panelX + 61;
        this.doneButtonX = this.panelX + 300 - 61 - 76;
    }

    private void addInitialName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!trimmed.isEmpty() && !this.containsName(trimmed)) {
            this.names.add(trimmed);
        }
    }

    private void addCurrentName() {
        String trimmed = this.inputText == null ? "" : this.inputText.trim();
        if (!trimmed.isEmpty() && !this.containsName(trimmed)) {
            this.names.add(trimmed);
            this.inputText = "";
            this.nameField.setText("");
            this.scrollOffset = this.getMaxScroll();
            this.nameField.setFocused(true);
        }
    }

    private boolean containsName(String target) {
        for (String name : this.names) {
            if (name.equalsIgnoreCase(target)) {
                return true;
            }
        }

        return false;
    }

    public static boolean matches(Setting<?> setting, String name) {
        if (setting != null && name != null && !name.isBlank()) {
            Object value = setting.getValue();
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String && name.equalsIgnoreCase(((String) item).trim())) {
                        return true;
                    }
                }

                return false;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private int getMaxScroll() {
        return Math.max(0, this.names.size() - 5);
    }

    private void closeScreen(boolean save) {
        if (!this.closed) {
            this.closed = true;
            if (save) {
                this.setting.setValue(new ArrayList<>(this.names));
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.setScreen(this.parent);
            }
        }
    }

    private int getAccentColor() {
        return ThemeModule.getAccentColorAt(0.5F);
    }

    private void drawButton(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            String label,
            boolean hovered,
            boolean filled,
            int accent) {
        int color =
                filled
                        ? withAlpha(accent, hovered ? 238 : 190)
                        : (hovered ? -450218942 : -1189403601);
        this.drawRoundedRect(context, x, y, width, height, 5, color);
        this.drawText(context, label, x + (width - this.textWidth(label)) / 2, y + 6, -985864);
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        BoldText.draw(context, this.textRenderer, text, x, y, color);
    }

    private int textWidth(String text) {
        return BoldText.width(this.textRenderer, text);
    }

    private String trimToWidth(String text, int maxWidth) {
        return BoldText.truncateToWidth(this.textRenderer, text, maxWidth);
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
                this.drawCornerRow(context, x, y + i, width, inset, color);
                this.drawCornerRow(context, x, y + height - i - 1, width, inset, color);
            }
        }
    }

    private void drawCornerRow(
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
            context.fill(
                    leftPixel - 1,
                    y,
                    leftPixel,
                    y + 1,
                    withAlpha(color, Math.max(1, Math.round(alpha * leftCoverage))));
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
