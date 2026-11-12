package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import odinclient.render.EntityColor;
import odinclient.setting.type.ColorSetting;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.ScreenScaleCompat;

import java.awt.Color;
import java.util.function.Consumer;

public final class ColorPickerScreen extends Screen {
    private static final int PANEL_WIDTH = 432;
    private static final int PANEL_HEIGHT = 290;
    private static final Identifier SATURATION_OVERLAY =
            Identifier.of("odinclient", "textures/gui/picker_saturation_overlay.png");
    private static final Identifier BRIGHTNESS_OVERLAY =
            Identifier.of("odinclient", "textures/gui/picker_brightness_overlay.png");
    private static final Identifier HUE_BAR =
            Identifier.of("odinclient", "textures/gui/picker_hue_bar.png");
    private static final Identifier OPACITY =
            Identifier.of("odinclient", "textures/gui/picker_opacity_overlay.png");
    private float guiScale = 1.0F;
    private static final int[] PRESETS;
    private final Screen parent;
    private final Consumer<EntityColor> onApply;
    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;
    private int panelX;
    private int panelY;
    private int dragTarget;

    private ColorPickerScreen(Screen parent, EntityColor initial, Consumer<EntityColor> onApply) {
        super(Text.literal("Color Picker"));
        this.parent = parent;
        this.onApply = onApply;
        EntityColor color = initial == null ? new EntityColor(155, 77, 255, 255) : initial;
        float[] hsb = Color.RGBtoHSB(color.r, color.g, color.b, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = color.a / 255.0F;
    }

    public static void open(Screen parent, ColorSetting setting) {
        if (setting != null) {
            EntityColor current = setting.getValue();
            openWithCallback(parent, current, setting::setValue);
        }
    }

    public static void openWithCallback(
            Screen parent, EntityColor initial, Consumer<EntityColor> onApply) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && onApply != null) {
            client.setScreen(new ColorPickerScreen(parent, initial, onApply));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.guiScale = ScreenScaleCompat.getScale(this.width);
        int scaledMouseX = Math.round(mouseX / this.guiScale);
        int scaledMouseY = Math.round(mouseY / this.guiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(this.guiScale, this.guiScale);
        this.updateLayout();
        context.fill(0, 0, this.getScaledWidth(), this.getScaledHeight(), -1107098359);
        this.drawPanel(context, this.panelX, this.panelY, 432, 290);
        this.drawLabel(context, "COLOR", this.panelX + 17, this.panelY + 16, -854792);
        this.drawLabel(
                context,
                "Pick saturation, brightness, hue and opacity.",
                this.panelX + 17,
                this.panelY + 31,
                -8551020);
        int pickerX = this.panelX + 17;
        int pickerY = this.panelY + 49;
        DashboardRenderer.drawRoundedRect(
                context, pickerX, pickerY, 220, 155, 7, hueToRgb(this.hue));
        this.drawOverlayTexture(
                context, SATURATION_OVERLAY, pickerX, pickerY, 220, 155, 880, 620, -1);
        this.drawOverlayTexture(
                context, BRIGHTNESS_OVERLAY, pickerX, pickerY, 220, 155, 880, 620, -1);
        DashboardRenderer.drawRing(
                context,
                pickerX + Math.round(this.saturation * 219.0F),
                pickerY + Math.round((1.0F - this.brightness) * 154.0F),
                5,
                2,
                -1,
                -16118251);
        int hueBarX = this.panelX + 247;
        this.drawOverlayTexture(context, HUE_BAR, hueBarX, pickerY, 18, 155, 72, 620, -1);
        DashboardRenderer.drawRoundedHighlight(
                context,
                hueBarX - 2,
                pickerY + Math.round(this.hue * 154.0F) - 1,
                22,
                3,
                2,
                -789257);
        int sideX = this.panelX + 284;
        this.drawLabel(context, "PREVIEW", sideX, this.panelY + 49, -7498333);
        DashboardRenderer.drawRoundedHighlight(
                context,
                sideX,
                this.panelY + 64,
                131,
                48,
                7,
                0xFF000000 | this.getArgb() & 16777215);
        this.drawLabel(context, "PRESETS", sideX, this.panelY + 128, -7498333);

        for (int i = 0; i < PRESETS.length; i++) {
            DashboardRenderer.drawRoundedHighlight(
                    context,
                    sideX + 1 + i % 5 * 27,
                    this.panelY + 145 + i / 5 * 29,
                    18,
                    18,
                    9,
                    PRESETS[i]);
        }

        this.drawLabel(context, "OPACITY", this.panelX + 17, this.panelY + 216, -7498333);
        this.drawLabelRight(
                context,
                Math.round(this.alpha * 100.0F) + "%",
                this.panelX + 265,
                this.panelY + 216,
                -3287588);
        DashboardRenderer.drawRoundedRect(
                context, this.panelX + 17, this.panelY + 232, 248, 11, 5, -2235927);
        this.drawOverlayTexture(
                context,
                OPACITY,
                this.panelX + 17,
                this.panelY + 232,
                248,
                11,
                992,
                44,
                0xFF000000 | this.getArgb() & 16777215);
        DashboardRenderer.drawRoundedHighlight(
                context,
                this.panelX + 17 + Math.round(this.alpha * 247.0F) - 2,
                this.panelY + 235,
                5,
                5,
                3,
                -1);
        this.button(
                context,
                this.panelX + 17,
                this.panelY + 290 - 35,
                96,
                22,
                "CANCEL",
                scaledMouseX,
                scaledMouseY,
                false);
        this.button(
                context,
                this.panelX + 432 - 113,
                this.panelY + 290 - 35,
                96,
                22,
                "APPLY",
                scaledMouseX,
                scaledMouseY,
                true);
        context.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        this.updateLayout();
        double mouseX = click.x() / this.guiScale;
        double mouseY = click.y() / this.guiScale;
        if (click.button() != 0) {
            return true;
        } else if (isInside(mouseX, mouseY, this.panelX + 17, this.panelY + 290 - 35, 96, 22)) {
            this.closeScreen();
            return true;
        } else if (isInside(
                mouseX, mouseY, this.panelX + 432 - 113, this.panelY + 290 - 35, 96, 22)) {
            this.onApply.accept(this.color());
            this.closeScreen();
            return true;
        } else if (isInside(mouseX, mouseY, this.panelX + 17, this.panelY + 49, 220, 155)) {
            this.dragTarget = 1;
            this.updateFromMouse(mouseX, mouseY);
            return true;
        } else if (isInside(mouseX, mouseY, this.panelX + 247, this.panelY + 49, 18, 155)) {
            this.dragTarget = 2;
            this.updateFromMouse(mouseX, mouseY);
            return true;
        } else if (isInside(mouseX, mouseY, this.panelX + 17, this.panelY + 232, 248, 11)) {
            this.dragTarget = 3;
            this.updateFromMouse(mouseX, mouseY);
            return true;
        } else {
            for (int i = 0; i < PRESETS.length; i++) {
                if (isInside(
                        mouseX,
                        mouseY,
                        this.panelX + 285 + i % 5 * 27,
                        this.panelY + 145 + i / 5 * 29,
                        18,
                        18)) {
                    int preset = PRESETS[i];
                    float[] hsb =
                            Color.RGBtoHSB(
                                    preset >> 16 & 0xFF, preset >> 8 & 0xFF, preset & 0xFF, null);
                    this.hue = hsb[0];
                    this.saturation = hsb[1];
                    this.brightness = hsb[2];
                    return true;
                }
            }

            if (!isInside(mouseX, mouseY, this.panelX, this.panelY, 432, 290)) {
                this.closeScreen();
            }

            return true;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.dragTarget != 0) {
            this.updateFromMouse(click.x() / this.guiScale, click.y() / this.guiScale);
        }

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        this.dragTarget = 0;
        return true;
    }

    @Override
    public void close() {
        this.closeScreen();
    }

    private void updateFromMouse(double mouseX, double mouseY) {
        if (this.dragTarget == 1) {
            this.saturation = clamp((float) ((mouseX - this.panelX - 17.0) / 220.0), 0.0F, 1.0F);
            this.brightness =
                    1.0F - clamp((float) ((mouseY - this.panelY - 49.0) / 155.0), 0.0F, 1.0F);
        } else if (this.dragTarget == 2) {
            this.hue = clamp((float) ((mouseY - this.panelY - 49.0) / 155.0), 0.0F, 1.0F);
        } else if (this.dragTarget == 3) {
            this.alpha = clamp((float) ((mouseX - this.panelX - 17.0) / 248.0), 0.0F, 1.0F);
        }
    }

    private EntityColor color() {
        int rgb = Color.HSBtoRGB(this.hue, this.saturation, this.brightness);
        return new EntityColor(
                rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Math.round(this.alpha * 255.0F));
    }

    private int getArgb() {
        return this.color().toArgb();
    }

    private void updateLayout() {
        this.panelX = (this.getScaledWidth() - 432) / 2;
        this.panelY = Math.max(8, (this.getScaledHeight() - 290) / 2);
    }

    private int getScaledWidth() {
        return ScreenScaleCompat.getScaledWidth();
    }

    private int getScaledHeight() {
        return ScreenScaleCompat.getScaledHeight();
    }

    private void closeScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    private void drawPanel(DrawContext context, int x, int y, int width, int height) {
        DashboardRenderer.drawRoundedHighlight(
                context, x + 2, y + 5, width, height, 12, 1962934272);
        DashboardRenderer.drawRoundedRect(context, x, y, width, height, 12, -66449899);
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
            boolean primary) {
        boolean hovered = isInside(mouseX, mouseY, x, y, width, height);
        int background;
        if (primary) {
            background = hovered ? -13418425 : -14207940;
        } else {
            background = hovered ? -14998226 : -15590878;
        }

        DashboardRenderer.drawRoundedRect(context, x, y, width, height, 7, background);
        int textColor = primary ? -657672 : -6051146;
        DashboardRenderer.drawCenteredBoldText(
                context, this.textRenderer, label, x + width / 2, y + (height - 8) / 2, textColor);
    }

    private void drawLabel(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawBoldText(context, this.textRenderer, text, x, y, color);
    }

    private void drawLabelRight(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawRightAlignedBoldText(context, this.textRenderer, text, x, y, color);
    }

    private static int hueToRgb(float hue) {
        return 0xFF000000 | Color.HSBtoRGB(hue, 1.0F, 1.0F) & 16777215;
    }

    private void drawOverlayTexture(
            DrawContext context,
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            int color) {
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                0.0F,
                0.0F,
                width,
                height,
                textureWidth,
                textureHeight,
                textureWidth,
                textureHeight,
                color);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isInside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    static {
        PRESETS =
                new int[] {
                    -50384, -680437, -340971, -12339138, -15157326, -14326805, -5745161, -1292135,
                    -7629919, -526084
                };
    }
}
