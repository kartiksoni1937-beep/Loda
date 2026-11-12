package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import odinclient.module.hud.ThemeModule;
import odinclient.module.render.sky.NoPersonSetting;
import odinclient.module.render.sky.SkyArts;
import odinclient.setting.type.EnumSetting;
import odinclient.ui.draw.BoldText;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.ScreenScaleCompat;

public final class ArtworkGalleryScreen extends Screen {
    private static final int MAX_WIDTH = 430;
    private static final int MAX_HEIGHT = 440;
    private static final int CARD_GAP = 7;
    private static final int COLUMNS = 3;
    private static final int HEADER_HEIGHT = 49;
    private static final int FOOTER_HEIGHT = 41;
    private static final float[][] cornerInsetCache = new float[24][];
    private final Screen parent;
    private final EnumSetting<?> setting;
    private final boolean isPanelStyle;
    private SkyArts.Artwork selectedArtwork;
    private boolean noneSelected;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private float scale = 1.0F;

    private ArtworkGalleryScreen(Screen parent, EnumSetting<?> setting) {
        super(Text.literal("Artwork Gallery"));
        this.parent = parent;
        this.setting = setting;
        this.isPanelStyle = parent instanceof PanelGuiScreen || parent instanceof OdinGuiScreen;
        this.selectedArtwork =
                setting.getValue() instanceof SkyArts.Artwork artwork
                        ? artwork
                        : SkyArts.Artwork.BLONDE;
        this.noneSelected = NoPersonSetting.isEnabled();
    }

    public static void open(Screen parent, EnumSetting<?> setting) {
        if (setting != null) {
            if (!"sky".equals(setting.name)) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.setScreen(new ArtworkGalleryScreen(parent, setting));
                }
            } else if (!(parent instanceof PanelGuiScreen) && !(parent instanceof OdinGuiScreen)) {
                SkySelectionScreen.open(parent, setting);
            } else {
                SkyGalleryScreen.open(parent, setting);
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.scale = ScreenScaleCompat.getScale(this.width);
        int scaledMouseX = Math.round(mouseX / this.scale);
        int scaledMouseY = Math.round(mouseY / this.scale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(this.scale, this.scale);
        this.updateLayout();
        int accent = ThemeModule.getAccentColorAt(0.5F);
        context.fill(0, 0, this.getScaledWidth(), this.getScaledHeight(), -1207696117);
        if (this.isPanelStyle) {
            DashboardRenderer.drawRoundedHighlight(
                    context,
                    this.panelX + 2,
                    this.panelY + 5,
                    this.panelWidth,
                    this.panelHeight,
                    12,
                    1962934272);
            this.drawRoundedRect(
                    context,
                    this.panelX,
                    this.panelY,
                    this.panelWidth,
                    this.panelHeight,
                    12,
                    -66449899);
            this.drawRoundedRect(
                    context,
                    this.panelX + 2,
                    this.panelY + 2,
                    this.panelWidth - 4,
                    47,
                    10,
                    -200470243);
        } else {
            this.drawRoundedRect(
                    context,
                    this.panelX - 3,
                    this.panelY - 3,
                    this.panelWidth + 6,
                    this.panelHeight + 6,
                    13,
                    withAlpha(accent, 34));
            this.drawRoundedRect(
                    context,
                    this.panelX,
                    this.panelY,
                    this.panelWidth,
                    this.panelHeight,
                    11,
                    withAlpha(accent, 112));
            this.drawRoundedRect(
                    context,
                    this.panelX + 1,
                    this.panelY + 1,
                    this.panelWidth - 2,
                    this.panelHeight - 2,
                    10,
                    -83227112);
            this.drawRoundedRect(
                    context,
                    this.panelX + 2,
                    this.panelY + 2,
                    this.panelWidth - 4,
                    47,
                    9,
                    -200470243);
        }

        if (!this.isPanelStyle) {
            this.drawRoundedRect(
                    context, this.panelX + 14, this.panelY + 12, 3, 22, 2, withAlpha(accent, 245));
        }

        int textInset = this.isPanelStyle ? 14 : 25;
        this.drawText(
                context, "ARTWORK GALLERY", this.panelX + textInset, this.panelY + 10, -789254);
        this.drawText(
                context,
                "CHOOSE A PERSON OR NONE",
                this.panelX + textInset,
                this.panelY + 25,
                -7827550);
        int closeX = this.panelX + this.panelWidth - 37;
        int closeY = this.panelY + 12;
        boolean closeHovered = isInside(scaledMouseX, scaledMouseY, closeX, closeY, 23, 23);
        this.drawRoundedRect(
                context, closeX, closeY, 23, 23, 6, closeHovered ? -14018523 : -15262935);
        this.drawCenteredText(
                context, "X", closeX + 11, closeY + 7, closeHovered ? -789254 : -7827550);
        int dividerColor = this.isPanelStyle ? -14407116 : withAlpha(accent, 165);
        context.fill(
                this.panelX + 13,
                this.panelY + 49 - 1,
                this.panelX + this.panelWidth - 13,
                this.panelY + 49,
                dividerColor);
        SkyArts.Artwork[] artworks = SkyArts.Artwork.values();
        int gridX = this.panelX + 13;
        int gridY = this.panelY + 49 + 10;
        int cardWidth = (this.panelWidth - 26 - 14) / 3;
        int cardHeight = (this.panelHeight - 49 - 41 - 25 - 14) / 3;

        for (int i = 0; i <= artworks.length; i++) {
            boolean isNone = i == 0;
            SkyArts.Artwork artwork = isNone ? null : artworks[i - 1];
            int cardX = gridX + i % 3 * (cardWidth + 7);
            int cardY = gridY + i / 3 * (cardHeight + 7);
            boolean selected;
            if (isNone) {
                selected = this.noneSelected;
            } else {
                selected = !this.noneSelected && artwork == this.selectedArtwork;
            }

            boolean hovered =
                    isInside(scaledMouseX, scaledMouseY, cardX, cardY, cardWidth, cardHeight);
            int background;
            if (selected && this.isPanelStyle) {
                background = -14405575;
            } else if (selected) {
                background = -15264987;
            } else if (hovered) {
                background = -15262934;
            } else {
                background = -15657694;
            }

            if (selected && !this.isPanelStyle) {
                this.drawRoundedRect(
                        context,
                        cardX - 2,
                        cardY - 2,
                        cardWidth + 4,
                        cardHeight + 4,
                        9,
                        withAlpha(accent, 35));
            }

            if (this.isPanelStyle) {
                this.drawRoundedRect(context, cardX, cardY, cardWidth, cardHeight, 8, background);
            } else {
                int border;
                if (selected) {
                    border = withAlpha(accent, 245);
                } else if (hovered) {
                    border = withAlpha(accent, 150);
                } else {
                    border = -14341574;
                }

                this.drawRoundedRect(context, cardX, cardY, cardWidth, cardHeight, 8, border);
                this.drawRoundedRect(
                        context,
                        cardX + 1,
                        cardY + 1,
                        cardWidth - 2,
                        cardHeight - 2,
                        7,
                        background);
            }

            int previewX = cardX + 5;
            int previewY = cardY + 5;
            int previewWidth = cardWidth - 10;
            int previewHeight = cardHeight - 25;
            this.drawRoundedRect(
                    context, previewX, previewY, previewWidth, previewHeight, 5, -16184300);
            if (isNone) {
                this.drawNoPersonLabel(context, previewX, previewY, previewWidth, previewHeight);
            } else {
                this.drawArtworkPreview(
                        context,
                        artwork,
                        previewX + 3,
                        previewY + 2,
                        previewWidth - 6,
                        previewHeight - 4);
            }

            String label = isNone ? "NONE / OFF" : artwork.getDisplayName().toUpperCase();
            String truncatedLabel =
                    BoldText.truncateToWidth(this.textRenderer, label, cardWidth - 31);
            this.drawText(
                    context,
                    truncatedLabel,
                    cardX + 7,
                    cardY + cardHeight - 15,
                    selected ? -789254 : -3551782);
            int checkX = cardX + cardWidth - 18;
            int checkY = cardY + cardHeight - 17;
            this.drawRoundedRect(
                    context,
                    checkX,
                    checkY,
                    10,
                    10,
                    4,
                    selected ? withAlpha(accent, 245) : -14078403);
            if (selected) {
                this.drawCheckMark(context, checkX, checkY);
            }
        }

        int footerY = this.panelY + this.panelHeight - 41;
        context.fill(
                this.panelX + 13,
                footerY,
                this.panelX + this.panelWidth - 13,
                footerY + 1,
                -14538953);
        int buttonY = footerY + 10;
        this.drawDialogButton(
                context,
                this.panelX + this.panelWidth - 171,
                buttonY,
                72,
                22,
                "CANCEL",
                scaledMouseX,
                scaledMouseY,
                false,
                accent);
        this.drawDialogButton(
                context,
                this.panelX + this.panelWidth - 91,
                buttonY,
                78,
                22,
                "APPLY",
                scaledMouseX,
                scaledMouseY,
                true,
                accent);
        context.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return true;
        } else {
            this.updateLayout();
            double mouseX = click.x() / this.scale;
            double mouseY = click.y() / this.scale;
            if (isInside(
                    mouseX, mouseY, this.panelX + this.panelWidth - 37, this.panelY + 12, 23, 23)) {
                this.closeToParent();
                return true;
            } else {
                SkyArts.Artwork[] artworks = SkyArts.Artwork.values();
                int gridX = this.panelX + 13;
                int gridY = this.panelY + 49 + 10;
                int cardWidth = (this.panelWidth - 26 - 14) / 3;
                int cardHeight = (this.panelHeight - 49 - 41 - 25 - 14) / 3;

                for (int i = 0; i <= artworks.length; i++) {
                    if (isInside(
                            mouseX,
                            mouseY,
                            gridX + i % 3 * (cardWidth + 7),
                            gridY + i / 3 * (cardHeight + 7),
                            cardWidth,
                            cardHeight)) {
                        if (i == 0) {
                            this.noneSelected = true;
                        } else {
                            this.selectedArtwork = artworks[i - 1];
                            this.noneSelected = false;
                        }

                        return true;
                    }
                }

                int buttonY = this.panelY + this.panelHeight - 41 + 10;
                int applyX = this.panelX + this.panelWidth - 91;
                if (isInside(
                        mouseX, mouseY, this.panelX + this.panelWidth - 171, buttonY, 72, 22)) {
                    this.closeToParent();
                } else if (isInside(mouseX, mouseY, applyX, buttonY, 78, 22)) {
                    if (!this.noneSelected) {
                        this.setting.setValueFromString(this.selectedArtwork.name());
                    }

                    NoPersonSetting.setEnabled(this.noneSelected);
                    this.closeToParent();
                } else if (!isInside(
                        mouseX,
                        mouseY,
                        this.panelX,
                        this.panelY,
                        this.panelWidth,
                        this.panelHeight)) {
                    this.closeToParent();
                }

                return true;
            }
        }
    }

    @Override
    public void close() {
        this.closeToParent();
    }

    private void updateLayout() {
        this.panelWidth = Math.min(430, Math.max(310, this.getScaledWidth() - 24));
        this.panelHeight = Math.min(440, Math.max(290, this.getScaledHeight() - 20));
        this.panelX = (this.getScaledWidth() - this.panelWidth) / 2;
        this.panelY = Math.max(10, (this.getScaledHeight() - this.panelHeight) / 2);
    }

    private int getScaledWidth() {
        return ScreenScaleCompat.getScaledWidth();
    }

    private int getScaledHeight() {
        return ScreenScaleCompat.getScaledHeight();
    }

    private void drawArtworkPreview(
            DrawContext context, SkyArts.Artwork artwork, int x, int y, int width, int height) {
        float fit =
                Math.min(
                        (float) width / artwork.getTextureWidth(),
                        (float) height / artwork.getTextureHeight());
        int drawWidth = Math.max(1, Math.round(artwork.getTextureWidth() * fit));
        int drawHeight = Math.max(1, Math.round(artwork.getTextureHeight() * fit));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                artwork.getTexture(),
                drawX,
                drawY,
                0.0F,
                0.0F,
                drawWidth,
                drawHeight,
                artwork.getTextureWidth(),
                artwork.getTextureHeight(),
                artwork.getTextureWidth(),
                artwork.getTextureHeight());
    }

    private void drawNoPersonLabel(DrawContext context, int x, int y, int width, int height) {
        this.drawCenteredText(context, "NO PERSON", x + width / 2, y + (height - 8) / 2, -9208694);
    }

    private void drawDialogButton(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            String label,
            int mouseX,
            int mouseY,
            boolean primary,
            int accent) {
        boolean hovered = isInside(mouseX, mouseY, x, y, width, height);
        int background;
        if (this.isPanelStyle) {
            if (primary) {
                background = hovered ? -13418425 : -14207940;
            } else {
                background = hovered ? -14800334 : -15393242;
            }
        } else if (primary) {
            background = withAlpha(accent, hovered ? 116 : 82);
        } else if (hovered) {
            background = -14933712;
        } else {
            background = -15394523;
        }

        if (this.isPanelStyle) {
            this.drawRoundedRect(context, x, y, width, height, 6, background);
        } else {
            int border;
            if (primary) {
                border = withAlpha(accent, hovered ? 245 : 190);
            } else if (hovered) {
                border = withAlpha(accent, 130);
            } else {
                border = -14012608;
            }

            this.drawRoundedRect(context, x, y, width, height, 6, border);
            this.drawRoundedRect(context, x + 1, y + 1, width - 2, height - 2, 5, background);
        }

        int textColor = !primary && !hovered ? -7169622 : -789254;
        this.drawCenteredText(context, label, x + width / 2, y + 7, textColor);
    }

    private void drawCheckMark(DrawContext context, int x, int y) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(0.5F, 0.5F);

        try {
            int baseX = x * 2;
            int baseY = y * 2;
            context.fill(baseX + 4, baseY + 9, baseX + 7, baseY + 11, -526084);
            context.fill(baseX + 7, baseY + 11, baseX + 10, baseY + 14, -526084);
            context.fill(baseX + 10, baseY + 8, baseX + 13, baseY + 12, -526084);
            context.fill(baseX + 13, baseY + 5, baseX + 16, baseY + 9, -526084);
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private void closeToParent() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        BoldText.draw(context, this.textRenderer, text, x, y, color);
    }

    private void drawCenteredText(DrawContext context, String text, int centerX, int y, int color) {
        this.drawText(
                context, text, centerX - BoldText.width(this.textRenderer, text) / 2, y, color);
    }

    private void drawRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        DashboardRenderer.drawRoundedRect(context, x, y, width, height, radius, color);
    }

    private static int lerpColor(int from, int to, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        return Math.round((from >>> 24) * (1.0F - t) + (to >>> 24) * t) << 24
                | Math.round((from >> 16 & 0xFF) * (1.0F - t) + (to >> 16 & 0xFF) * t) << 16
                | Math.round((from >> 8 & 0xFF) * (1.0F - t) + (to >> 8 & 0xFF) * t) << 8
                | Math.round((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
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

    private void drawAntialiasedSpan(
            DrawContext context, int x, int y, int width, float inset, int color) {
        float left = x + inset;
        float right = x + width - inset;
        int fullLeft = (int) Math.ceil(left);
        int fullRight = (int) Math.floor(right);
        if (fullRight > fullLeft) {
            context.fill(fullLeft, y, fullRight, y + 1, color);
        }

        int alpha = color >>> 24;
        float leftCoverage = fullLeft - left;
        if (leftCoverage > 0.001F) {
            int rgb = color & 16777215;
            context.fill(
                    fullLeft - 1,
                    y,
                    fullLeft,
                    y + 1,
                    Math.max(1, Math.round(alpha * leftCoverage)) << 24 | rgb);
        }

        float rightCoverage = right - fullRight;
        if (rightCoverage > 0.001F) {
            int rgb = color & 16777215;
            context.fill(
                    fullRight,
                    y,
                    fullRight + 1,
                    y + 1,
                    Math.max(1, Math.round(alpha * rightCoverage)) << 24 | rgb);
        }
    }

    private static int withAlpha(int color, int alpha) {
        int rgb = color & 16777215;
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }

    private static boolean isInside(
            double pointX, double pointY, int x, int y, int width, int height) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }
}
