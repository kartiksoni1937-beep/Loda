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
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.ScreenScaleCompat;

public final class SkyGalleryScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 276;
    private final Screen parent;
    private final EnumSetting<?> setting;
    private SkyArts.Sky selectedSky;
    private int page;
    private int panelX;
    private int panelY;
    private float scale = 1.0F;

    private SkyGalleryScreen(Screen parent, EnumSetting<?> setting) {
        super(Text.literal("Sky Gallery"));
        this.parent = parent;
        this.setting = setting;
        this.selectedSky = setting.getValue() instanceof SkyArts.Sky sky ? sky : SkyArts.Sky.OFF;
        this.page = this.selectedSky.ordinal() / 4;
    }

    public static void open(Screen parent, EnumSetting<?> setting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && setting != null) {
            client.setScreen(new SkyGalleryScreen(parent, setting));
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
        this.updatePosition();
        int accent = ThemeModule.getAccentColorAt(0.5F);
        context.fill(0, 0, this.getScaledWidth(), this.getScaledHeight(), -1107098359);
        DashboardRenderer.drawRoundedHighlight(
                context, this.panelX + 2, this.panelY + 5, 420, 276, 12, 1962934272);
        DashboardRenderer.drawRoundedRect(
                context, this.panelX, this.panelY, 420, 276, 12, -66449899);
        this.drawText(context, "SKY GALLERY", this.panelX + 15, this.panelY + 12, -854792);
        this.drawText(
                context,
                "Choose the sky background used by Sky Arts.",
                this.panelX + 15,
                this.panelY + 27,
                -8551020);
        this.button(
                context,
                this.panelX + 420 - 37,
                this.panelY + 11,
                24,
                24,
                "X",
                scaledMouseX,
                scaledMouseY,
                false,
                accent);
        SkyArts.Sky[] skies = getSkies();
        int start = this.page * 4;

        for (int i = start; i < Math.min(start + 4, skies.length); i++) {
            int index = i - start;
            int cardX = this.panelX + 14 + index % 2 * 198;
            int cardY = this.panelY + 52 + index / 2 * 88;
            this.drawSkyCard(
                    context, skies[i], cardX, cardY, 190, 80, scaledMouseX, scaledMouseY, accent);
        }

        int footerY = this.panelY + 276 - 39;
        context.fill(this.panelX + 14, footerY - 6, this.panelX + 420 - 14, footerY - 5, -14407116);
        this.button(
                context,
                this.panelX + 14,
                footerY,
                42,
                25,
                "<",
                scaledMouseX,
                scaledMouseY,
                false,
                accent);
        this.button(
                context,
                this.panelX + 120,
                footerY,
                42,
                25,
                ">",
                scaledMouseX,
                scaledMouseY,
                false,
                accent);
        this.drawCenteredText(
                context,
                this.page + 1 + " / " + getPageCount(),
                this.panelX + 88,
                footerY + 9,
                -5524802);
        this.button(
                context,
                this.panelX + 420 - 174,
                footerY,
                76,
                25,
                "CANCEL",
                scaledMouseX,
                scaledMouseY,
                false,
                accent);
        this.button(
                context,
                this.panelX + 420 - 90,
                footerY,
                76,
                25,
                "APPLY",
                scaledMouseX,
                scaledMouseY,
                true,
                accent);
        context.getMatrices().popMatrix();
    }

    private void drawSkyCard(
            DrawContext context,
            SkyArts.Sky sky,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            int accent) {
        boolean selected = sky == this.selectedSky;
        boolean hovered = isInside(mouseX, mouseY, x, y, width, height);
        int background;
        if (selected) {
            background = -14405575;
        } else if (hovered) {
            background = -15262683;
        } else {
            background = -15657443;
        }

        DashboardRenderer.drawRoundedRect(context, x, y, width, height, 8, background);
        int previewX = x + 5;
        int previewY = y + 5;
        int previewWidth = width - 10;
        DashboardRenderer.drawRoundedRect(
                context, previewX, previewY, previewWidth, 55, 6, -16249838);
        context.enableScissor(previewX, previewY, previewX + previewWidth, previewY + 55);
        if (sky.hasTexture()) {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    sky.getTexture(),
                    previewX,
                    previewY,
                    sky.getAtlasU(),
                    sky.getAtlasV(previewWidth, 55),
                    previewWidth,
                    55,
                    1024,
                    sky.getRegionHeight(previewWidth, 55),
                    sky.atlasWidth(),
                    sky.atlasHeight());
        } else {
            this.drawCenteredText(context, "VANILLA SKY", x + width / 2, previewY + 24, -9208694);
        }

        context.disableScissor();
        int titleWidth = width - 38;
        String title = this.truncateText(sky.getDisplayName().toUpperCase(), titleWidth);
        int titleX = x + 8;
        int titleY = y + 66;
        this.drawText(context, title, titleX, titleY, selected ? -920585 : -5524802);
        int markX = x + width - 18;
        int markY = y + 65;
        DashboardRenderer.drawRoundedRect(
                context, markX, markY, 10, 10, 4, selected ? withAlpha(accent, 245) : -13617599);
        if (selected) {
            this.drawCheckMark(context, markX, markY);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        this.updatePosition();
        if (click.button() != 0) {
            return true;
        } else {
            double mouseX = click.x() / this.scale;
            double mouseY = click.y() / this.scale;
            if (!isInside(mouseX, mouseY, this.panelX + 420 - 37, this.panelY + 11, 24, 24)
                    && !isInside(
                            mouseX,
                            mouseY,
                            this.panelX + 420 - 174,
                            this.panelY + 276 - 39,
                            76,
                            25)) {
                if (isInside(
                        mouseX, mouseY, this.panelX + 420 - 90, this.panelY + 276 - 39, 76, 25)) {
                    this.setting.setValueFromString(this.selectedSky.name());
                    this.closeToParent();
                    return true;
                } else {
                    int footerY = this.panelY + 276 - 39;
                    if (isInside(mouseX, mouseY, this.panelX + 14, footerY, 42, 25)) {
                        this.page = Math.max(0, this.page - 1);
                        return true;
                    } else if (isInside(mouseX, mouseY, this.panelX + 120, footerY, 42, 25)) {
                        this.page = Math.min(getPageCount() - 1, this.page + 1);
                        return true;
                    } else {
                        SkyArts.Sky[] skies = getSkies();
                        int start = this.page * 4;

                        for (int i = start; i < Math.min(start + 4, skies.length); i++) {
                            int index = i - start;
                            if (isInside(
                                    mouseX,
                                    mouseY,
                                    this.panelX + 14 + index % 2 * 198,
                                    this.panelY + 52 + index / 2 * 88,
                                    190,
                                    80)) {
                                this.selectedSky = skies[i];
                                return true;
                            }
                        }

                        if (!isInside(mouseX, mouseY, this.panelX, this.panelY, 420, 276)) {
                            this.closeToParent();
                        }

                        return true;
                    }
                }
            } else {
                this.closeToParent();
                return true;
            }
        }
    }

    @Override
    public void close() {
        this.closeToParent();
    }

    private void updatePosition() {
        this.panelX = (this.getScaledWidth() - 420) / 2;
        this.panelY = Math.max(8, (this.getScaledHeight() - 276) / 2);
    }

    private int getScaledWidth() {
        return ScreenScaleCompat.getScaledWidth();
    }

    private int getScaledHeight() {
        return ScreenScaleCompat.getScaledHeight();
    }

    private void closeToParent() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    private void button(
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
        if (primary) {
            background = -14339782;
        } else if (hovered) {
            background = -14999510;
        } else {
            background = -15591648;
        }

        DashboardRenderer.drawRoundedRect(context, x, y, width, height, 7, background);
        int textX = x + width / 2;
        int textY = y + (height - 8) / 2;
        this.drawCenteredText(context, label, textX, textY, primary ? -723464 : -6182731);
    }

    private void drawCheckMark(DrawContext context, int x, int y) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(0.5F, 0.5F);

        try {
            int px = x * 2;
            int py = y * 2;
            context.fill(px + 4, py + 9, px + 7, py + 11, -526084);
            context.fill(px + 7, py + 11, px + 10, py + 14, -526084);
            context.fill(px + 10, py + 8, px + 13, py + 12, -526084);
            context.fill(px + 13, py + 5, px + 16, py + 9, -526084);
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawBoldText(context, this.textRenderer, text, x, y, color);
    }

    private void drawCenteredText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawCenteredBoldText(context, this.textRenderer, text, x, y, color);
    }

    private String truncateText(String text, int maxWidth) {
        return BoldText.truncateToWidth(this.textRenderer, text, maxWidth);
    }

    private static int lerpColor(int from, int to, float delta) {
        float t = Math.max(0.0F, Math.min(1.0F, delta));
        return Math.round((from >>> 24) * (1.0F - t) + (to >>> 24) * t) << 24
                | Math.round((from >> 16 & 0xFF) * (1.0F - t) + (to >> 16 & 0xFF) * t) << 16
                | Math.round((from >> 8 & 0xFF) * (1.0F - t) + (to >> 8 & 0xFF) * t) << 8
                | Math.round((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
    }

    private static int withAlpha(int color, int alpha) {
        int rgb = color & 16777215;
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }

    private static boolean isInside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    private static SkyArts.Sky[] getSkies() {
        return SkyArts.Sky.values();
    }

    private static int getPageCount() {
        return (getSkies().length + 3) / 4;
    }
}
