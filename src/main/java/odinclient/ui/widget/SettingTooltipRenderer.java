package odinclient.ui.widget;

import net.minecraft.client.gui.DrawContext;

import odinclient.setting.Setting;
import odinclient.ui.anim.AnimationUtil;
import odinclient.ui.draw.SdfFontRenderer;
import odinclient.ui.draw.SdfShapeRenderer;

import org.joml.Matrix3x2fStack;

import java.util.List;

public final class SettingTooltipRenderer {
    private final TooltipLayout.TooltipFadeTimer fadeState = new TooltipLayout.TooltipFadeTimer();
    private Setting<?> lastSetting;
    private float lastWrapWidth;
    private List<String> titleLines = List.of();
    private List<String> descriptionLines = List.of();

    public void render(
            DrawContext context,
            Setting<?> setting,
            float mouseX,
            float mouseY,
            float screenWidth,
            float screenHeight,
            float scaleFactor,
            float alpha) {
        float fade = this.fadeState.getFadeProgress(setting, System.nanoTime()) * alpha;
        if (setting != null && !(fade <= 0.01F)) {
            float scale = Math.max(0.85F, scaleFactor);
            float width = screenWidth / scale;
            float height = screenHeight / scale;
            float wrapWidth = Math.min(220.0F, width - 26.0F);
            if (!(wrapWidth <= 0.0F)) {
                if (this.lastSetting != setting || this.lastWrapWidth != wrapWidth) {
                    this.lastSetting = setting;
                    this.lastWrapWidth = wrapWidth;
                    this.titleLines =
                            TooltipLayout.wrapText(
                                    setting.title,
                                    wrapWidth,
                                    s -> SdfFontRenderer.width(SdfFontRenderer.FontStyle.BOLD, s));
                    this.descriptionLines =
                            TooltipLayout.wrapText(
                                    setting.description,
                                    wrapWidth,
                                    s ->
                                            SdfFontRenderer.width(
                                                    SdfFontRenderer.FontStyle.BODY_SMALL, s));
                }

                float contentWidth = 0.0F;

                for (String title : this.titleLines) {
                    contentWidth =
                            Math.max(
                                    contentWidth,
                                    SdfFontRenderer.width(SdfFontRenderer.FontStyle.BOLD, title));
                }

                int bodyLines =
                        Math.max(
                                0,
                                Math.min(
                                        this.descriptionLines.size(),
                                        Math.min(
                                                6,
                                                (int)
                                                        ((height
                                                                        - 30.0F
                                                                        - this.titleLines.size()
                                                                                * 11.0F)
                                                                / 9.0F))));

                for (int i = 0; i < bodyLines; i++) {
                    contentWidth =
                            Math.max(
                                    contentWidth,
                                    SdfFontRenderer.width(
                                            SdfFontRenderer.FontStyle.BODY_SMALL,
                                            this.getBodyLine(i, bodyLines, wrapWidth)));
                }

                float titleHeight = 16.0F + this.titleLines.size() * 11.0F;
                float bodyHeight = bodyLines == 0 ? 0.0F : 4.0F + bodyLines * 9.0F;
                TooltipLayout.TooltipBounds bounds =
                        TooltipLayout.computeBounds(
                                mouseX / scale,
                                mouseY / scale,
                                contentWidth + 18.0F,
                                titleHeight + bodyHeight,
                                width,
                                height);
                Matrix3x2fStack matrices = context.getMatrices();
                matrices.pushMatrix();
                matrices.scale(scale, scale);
                SdfShapeRenderer.drawShadow(
                        context,
                        bounds.x(),
                        bounds.y() + 2.0F,
                        bounds.width(),
                        bounds.height(),
                        5.0F,
                        5.0F,
                        AnimationUtil.multiplyAlpha(1879048192, fade));
                SdfShapeRenderer.fill(
                        context,
                        bounds.x(),
                        bounds.y(),
                        bounds.width(),
                        bounds.height(),
                        5.0F,
                        AnimationUtil.multiplyAlpha(-183430116, fade));
                SdfShapeRenderer.drawOutline(
                        context,
                        bounds.x(),
                        bounds.y(),
                        bounds.width(),
                        bounds.height(),
                        5.0F,
                        0.65F,
                        AnimationUtil.multiplyAlpha(-13354424, fade));
                float y = bounds.y() + 8.0F;

                for (String title : this.titleLines) {
                    SdfFontRenderer.drawTextCenteredY(
                            context,
                            SdfFontRenderer.FontStyle.BOLD,
                            title,
                            bounds.x() + 9.0F,
                            y + 5.5F,
                            AnimationUtil.multiplyAlpha(-658693, fade));
                    y += 11.0F;
                }

                float bodyY = y + 4.0F;

                for (int i = 0; i < bodyLines; i++) {
                    String line = this.getBodyLine(i, bodyLines, wrapWidth);
                    SdfFontRenderer.drawTextCenteredY(
                            context,
                            SdfFontRenderer.FontStyle.BODY_SMALL,
                            line,
                            bounds.x() + 9.0F,
                            bodyY + 4.5F,
                            AnimationUtil.multiplyAlpha(-5393469, fade));
                    bodyY += 9.0F;
                }

                matrices.popMatrix();
            }
        }
    }

    private String getBodyLine(int index, int shown, float maxWidth) {
        String line = this.descriptionLines.get(index);
        return index == shown - 1 && shown < this.descriptionLines.size()
                ? SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BODY_SMALL, line + "...", maxWidth)
                : line;
    }
}
