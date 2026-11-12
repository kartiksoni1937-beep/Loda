package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import odinclient.module.hud.ThemeModule;
import odinclient.module.render.sky.SkyArts;
import odinclient.setting.type.EnumSetting;
import odinclient.ui.draw.BoldText;
import odinclient.ui.scale.ScreenScaleCompat;

public final class SkySelectionScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int CARD_WIDTH = 132;
    private static final int CARD_HEIGHT = 78;
    private static final int CARD_GAP = 8;
    private static final int COLUMNS = 2;
    private static final float[][] cornerInsetCache = new float[24][];
    private final Screen parent;
    private final EnumSetting<?> setting;
    private int panelX;
    private int panelY;
    private int panelHeight;
    private int page;

    private SkySelectionScreen(Screen parent, EnumSetting<?> setting) {
        super(Text.literal("Sky Selection"));
        this.parent = parent;
        this.setting = setting;
        Object value = setting.getValue();
        if (value instanceof SkyArts.Sky) {
            this.page = ((SkyArts.Sky) value).ordinal() / 4;
        }
    }

    public static void open(Screen parent, EnumSetting<?> setting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new SkySelectionScreen(parent, setting));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        float scale = ScreenScaleCompat.getScale(0);

        try (ScreenScaleCompat.ScaleScope ignored = ScreenScaleCompat.enter(context)) {
            this.renderScaled(
                    context, Math.round(mouseX / scale), Math.round(mouseY / scale), deltaTicks);
        }
    }

    private void renderScaled(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.updateLayout();
        int accent = ThemeModule.getAccentColorAt(0.5F);
        context.fill(
                0,
                0,
                ScreenScaleCompat.getScaledWidth(),
                ScreenScaleCompat.getScaledHeight(),
                -1459617792);
        this.drawRoundedRect(
                context,
                this.panelX,
                this.panelY,
                300,
                this.panelHeight,
                8,
                withAlpha(accent, 108));
        this.drawRoundedRect(
                context,
                this.panelX + 1,
                this.panelY + 1,
                298,
                this.panelHeight - 2,
                7,
                -435351278);
        this.drawRoundedRect(
                context, this.panelX + 12, this.panelY + 10, 2, 22, 1, withAlpha(accent, 245));
        this.drawLabel(context, "SKY ARTS", this.panelX + 21, this.panelY + 10, -986896);
        this.drawLabel(
                context, "CHOOSE A BACKGROUND SKY", this.panelX + 21, this.panelY + 23, -7828336);
        context.fill(
                this.panelX + 12,
                this.panelY + 37,
                this.panelX + 300 - 12,
                this.panelY + 38,
                1076374832);
        SkyArts.Sky[] skies = getSkies();
        int start = this.page * 4;
        int count = Math.min(4, skies.length - start);

        for (int index = start; index < start + count; index++) {
            SkyArts.Sky sky = skies[index];
            int slot = index - start;
            int cardX = this.getCardX(slot, count);
            int cardY = this.getCardY(slot);
            boolean selected = this.setting.getValue() == sky;
            boolean hovered = isInside(mouseX, mouseY, cardX, cardY, 132, 78);
            int borderColor = hovered ? withAlpha(accent, 142) : 1882338868;
            this.drawRoundedRect(context, cardX, cardY, 132, 78, 5, borderColor);
            int innerX = cardX + 1;
            int innerY = cardY + 1;
            int fillColor = hovered ? -535158752 : -803857637;
            this.drawRoundedRect(context, innerX, innerY, 130, 76, 4, fillColor);
            int imageX = cardX + 5;
            int imageY = cardY + 5;
            this.drawRoundedRect(context, imageX, imageY, 122, 48, 3, -1340991719);
            if (sky.hasTexture()) {
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        sky.getTexture(),
                        imageX,
                        imageY,
                        sky.getAtlasU(),
                        sky.getAtlasV(122, 48),
                        122,
                        48,
                        1024,
                        sky.getRegionHeight(122, 48),
                        sky.atlasWidth(),
                        sky.atlasHeight());
            } else {
                this.drawLabel(context, "VANILLA SKY", imageX + 23, imageY + 20, -8091251);
            }

            int nameY = cardY + 61;
            this.drawLabel(
                    context,
                    BoldText.truncateToWidth(
                            this.textRenderer, sky.getDisplayName().toUpperCase(), 100),
                    cardX + 7,
                    nameY,
                    -1973277);
            int badgeX = cardX + 132 - 16;
            int badgeY = cardY + 59;
            int badgeColor = selected ? withAlpha(accent, 245) : -14210004;
            this.drawRoundedRect(context, badgeX, badgeY, 9, 9, 3, badgeColor);
            if (selected) {
                this.drawCheckmark(context, badgeX, badgeY);
            }
        }

        int doneX = this.panelX + 300 - 81;
        int footerY = this.panelY + this.panelHeight - 23;
        this.drawRoundedRect(context, this.panelX + 13, footerY, 25, 16, 4, -14343889);
        this.drawRoundedRect(context, this.panelX + 94, footerY, 25, 16, 4, -14343889);
        this.drawLabel(context, "<", this.panelX + 22, footerY + 3, -1973277);
        this.drawLabel(context, ">", this.panelX + 103, footerY + 3, -1973277);
        this.drawLabel(
                context,
                this.page + 1 + "/" + getPageCount(),
                this.panelX + 48,
                footerY + 3,
                -1973277);
        int doneAlpha = isInside(mouseX, mouseY, doneX, footerY, 68, 16) ? 238 : 188;
        this.drawRoundedRect(context, doneX, footerY, 68, 16, 4, withAlpha(accent, doneAlpha));
        String doneLabel = "DONE";
        this.drawLabel(
                context,
                doneLabel,
                doneX + (68 - BoldText.width(this.textRenderer, doneLabel)) / 2,
                footerY + 3,
                -592138);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return true;
        } else {
            this.updateLayout();
            float scale = ScreenScaleCompat.getScale(0);
            double x = click.x() / scale;
            double y = click.y() / scale;
            SkyArts.Sky[] skies = getSkies();
            int start = this.page * 4;
            int count = Math.min(4, skies.length - start);

            for (int index = start; index < start + count; index++) {
                int slot = index - start;
                if (isInside(x, y, this.getCardX(slot, count), this.getCardY(slot), 132, 78)) {
                    this.setting.setValueFromString(skies[index].name());
                    return true;
                }
            }

            int doneX = this.panelX + 300 - 81;
            int footerY = this.panelY + this.panelHeight - 23;
            if (isInside(x, y, this.panelX + 13, footerY, 25, 16)) {
                this.page = Math.max(0, this.page - 1);
                return true;
            } else if (isInside(x, y, this.panelX + 94, footerY, 25, 16)) {
                this.page = Math.min(getPageCount() - 1, this.page + 1);
                return true;
            } else {
                if (isInside(x, y, doneX, footerY, 68, 16)) {
                    this.returnToParent();
                }

                return true;
            }
        }
    }

    @Override
    public void close() {
        this.returnToParent();
    }

    private void updateLayout() {
        this.panelHeight = 232 + Math.max(0, 1) * 8;
        this.panelX = (ScreenScaleCompat.getScaledWidth() - 300) / 2;
        this.panelY = Math.max(10, (ScreenScaleCompat.getScaledHeight() - this.panelHeight) / 2);
    }

    private int getCardX(int slot, int count) {
        int rowStart = slot / 2 * 2;
        int rowCount = Math.min(2, count - rowStart);
        int rowWidth = rowCount * 132 + Math.max(0, rowCount - 1) * 8;
        int offset = (slot - rowStart) * 140;
        return this.panelX + (300 - rowWidth) / 2 + offset;
    }

    private int getCardY(int slot) {
        return this.panelY + 43 + slot / 2 * 86;
    }

    private void returnToParent() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    private void drawCheckmark(DrawContext context, int x, int y) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(0.5F, 0.5F);

        try {
            int baseX = x * 2;
            int baseY = y * 2;
            context.fill(baseX + 4, baseY + 8, baseX + 6, baseY + 10, -525313);
            context.fill(baseX + 6, baseY + 10, baseX + 8, baseY + 12, -525313);
            context.fill(baseX + 8, baseY + 8, baseX + 10, baseY + 11, -525313);
            context.fill(baseX + 10, baseY + 6, baseX + 12, baseY + 9, -525313);
            context.fill(baseX + 12, baseY + 4, baseX + 14, baseY + 7, -525313);
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private void drawLabel(DrawContext context, String text, int x, int y, int color) {
        BoldText.draw(context, this.textRenderer, text, x, y, color);
    }

    private void drawRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) {
            int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
            if (r == 0) {
                context.fill(x, y, x + width, y + height, color);
            } else {
                context.fill(x, y + r, x + width, y + height - r, color);
                float[] insets = this.getCornerInsets(r);

                for (int i = 0; i < r; i++) {
                    this.drawCornerRow(context, x, y + i, width, insets[i], color);
                    this.drawCornerRow(context, x, y + height - i - 1, width, insets[i], color);
                }
            }
        }
    }

    private float[] getCornerInsets(int radius) {
        if (radius >= cornerInsetCache.length) {
            return computeCornerInsets(radius);
        } else {
            float[] insets = cornerInsetCache[radius];
            if (insets == null) {
                insets = computeCornerInsets(radius);
                cornerInsetCache[radius] = insets;
            }

            return insets;
        }
    }

    private static float[] computeCornerInsets(int radius) {
        float[] insets = new float[radius];

        for (int i = 0; i < radius; i++) {
            float dy = radius - i - 0.5F;
            insets[i] = radius - (float) Math.sqrt(radius * radius - dy * dy);
        }

        return insets;
    }

    private void drawCornerRow(
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

    private static int withAlpha(int color, int alpha) {
        int rgb = color & 16777215;
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }

    private static boolean isInside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    private static SkyArts.Sky[] getSkies() {
        return SkyArts.Sky.values();
    }

    private static int getPageCount() {
        return (getSkies().length + 3) / 4;
    }
}
