package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import odinclient.module.hud.ThemeModule;
import odinclient.ui.anim.AnimationUtil;
import odinclient.ui.draw.SdfFontRenderer;
import odinclient.ui.draw.SdfShapeRenderer;
import odinclient.ui.scale.ScreenScaleCompat;
import odinclient.util.ScannerExecutor;

import org.joml.Matrix3x2fStack;

public final class IntroSplashScreen extends Screen {
    public static final float DURATION_SECONDS = 3.0F;
    private static final String WORD_TOP = "DIN";
    private static final String WORD_BOTTOM = "LIENT";
    private static boolean hasShown;
    private static boolean fontsPreloaded;
    private static boolean isShowing;
    private static int idleTicks;
    private final Screen parent;
    private long lastFrameNanos;
    private float elapsedSeconds;
    private boolean finished;

    public IntroSplashScreen(Screen parent) {
        super(Text.literal("OdinClient"));
        this.parent = parent;
    }

    public static void tick(MinecraftClient client) {
        if (isShowing && !(client.currentScreen instanceof IntroSplashScreen)) {
            isShowing = false;
            ScannerExecutor.setPaused(false);
        }

        if (!hasShown) {
            boolean inWorld = client.world != null && client.player != null;
            if (!inWorld && !fontsPreloaded && client.currentScreen != null) {
                SdfShapeRenderer.initPipelines();
                SdfFontRenderer.preloadResources();
                fontsPreloaded = true;
            }

            if (inWorld && client.currentScreen == null && client.getOverlay() == null) {
                if (++idleTicks >= 10) {
                    hasShown = true;
                    isShowing = true;
                    ScannerExecutor.setPaused(true);
                    client.setScreen(new IntroSplashScreen(null));
                    if (!(client.currentScreen instanceof IntroSplashScreen)) {
                        isShowing = false;
                        ScannerExecutor.setPaused(false);
                    }
                }
            } else {
                idleTicks = 0;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == 256 || key == 32 || key == 257) {
            this.finish();
        }

        return true;
    }

    @Override
    public void close() {
        this.finish();
    }

    private float advanceElapsed() {
        long now = System.nanoTime();
        if (this.lastFrameNanos != 0L) {
            float delta = (float) (now - this.lastFrameNanos) / 1.0E9F;
            this.elapsedSeconds = this.elapsedSeconds + Math.min(0.08F, Math.max(0.0F, delta));
        }

        this.lastFrameNanos = now;
        return this.elapsedSeconds;
    }

    private void finish() {
        if (!this.finished) {
            this.finished = true;
            isShowing = false;
            ScannerExecutor.setPaused(false);
            MinecraftClient.getInstance().setScreen(new OdinGuiScreen(this.parent, true));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        float elapsed = this.advanceElapsed();
        if (elapsed >= 3.0F) {
            this.finish();
        } else {
            try (ScreenScaleCompat.ScaleScope scope = ScreenScaleCompat.enter(context)) {
                this.renderIntro(context, elapsed);
            }
        }
    }

    private void renderIntro(DrawContext context, float elapsed) {
        float width = ScreenScaleCompat.getScaledWidth();
        float height = ScreenScaleCompat.getScaledHeight();
        float centerX = width * 0.5F;
        float centerY = height * 0.5F;
        float backdropAlpha =
                AnimationUtil.lerp(
                        1.0F,
                        0.62F,
                        AnimationUtil.easeInOutCubic(AnimationUtil.progress(elapsed, 2.55F, 3.0F)));
        context.fill(
                0,
                0,
                (int) width + 1,
                (int) height + 1,
                AnimationUtil.multiplyAlpha(
                        -16777216,
                        backdropAlpha
                                * AnimationUtil.easeOutCubic(
                                        AnimationUtil.progress(elapsed, 0.0F, 0.18F))));
        int accent = ThemeModule.getAccentColorAt(0.2F);
        int accentAlt = ThemeModule.getAccentColorAt(0.8F);
        float fadeOut = AnimationUtil.easeInCubic(AnimationUtil.progress(elapsed, 2.55F, 3.0F));
        float fadeIn = 1.0F - fadeOut;
        float logoSize = Math.min(width, height) * 0.13F;
        float logoOffset = logoSize * 0.755F;
        float textSize = logoSize * 0.62F;
        float textScale = textSize / SdfFontRenderer.FontStyle.INTRO.capHeight;
        float topIndent = textSize * 1.62F;
        float topWidth =
                topIndent
                        + SdfFontRenderer.width(SdfFontRenderer.FontStyle.INTRO, "DIN") * textScale;
        float bottomIndent = textSize * 1.05F;
        float bottomWidth =
                bottomIndent
                        + topIndent
                        + SdfFontRenderer.width(SdfFontRenderer.FontStyle.INTRO, "LIENT")
                                * textScale;
        float textWidth = Math.max(topWidth, bottomWidth);
        float lineSpacing = textSize * 1.42F;
        float textLeft = centerX - textWidth * 0.5F;
        float topY = centerY - lineSpacing * 0.5F;
        float bottomY = centerY + lineSpacing * 0.5F;
        float leftTargetX = textLeft + textSize * 0.68F;
        float rightTargetX = textLeft + bottomIndent + textSize * 0.68F;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        float zoom = 1.0F + fadeOut * 0.08F;
        matrices.translate(centerX, centerY);
        matrices.scale(zoom, zoom);
        matrices.translate(-centerX, -centerY);
        float shadowAlpha =
                AnimationUtil.easeOutCubic(AnimationUtil.progress(elapsed, 0.05F, 0.9F)) * fadeIn;
        SdfShapeRenderer.drawShadow(
                context,
                centerX - logoSize * 1.9F,
                centerY - logoSize * 0.9F,
                logoSize * 3.8F,
                logoSize * 1.8F,
                logoSize * 0.9F,
                logoSize * 0.9F,
                AnimationUtil.multiplyAlpha(accent, 0.22F * shadowAlpha));
        float appear = AnimationUtil.easeOutCubic(AnimationUtil.progress(elapsed, 0.0F, 0.5F));
        float swap = AnimationUtil.easeInOutCubic(AnimationUtil.progress(elapsed, 0.5F, 1.25F));
        float settle = AnimationUtil.easeInOutCubic(AnimationUtil.progress(elapsed, 1.25F, 1.8F));
        float swapPulse = (float) Math.sin(3.141592653589793 * swap);
        float leftX = centerX - logoOffset;
        float rightX = centerX + logoOffset;
        float movingRightX = AnimationUtil.lerp(leftX, rightX, swap);
        float movingLeftX = AnimationUtil.lerp(rightX, leftX, swap);
        float inverseSwap = 1.0F - swap;
        float squash = 1.0F - swapPulse * 0.12F;
        float scale =
                AnimationUtil.lerp(logoSize, textSize, settle) * (0.86F + 0.14F * appear) * squash;
        float firstX = AnimationUtil.lerp(movingLeftX, leftTargetX, settle);
        float firstY = AnimationUtil.lerp(centerY, topY, settle);
        float secondX = AnimationUtil.lerp(movingRightX, rightTargetX, settle);
        float secondY = AnimationUtil.lerp(centerY, bottomY, settle);
        float glow = 0.1F + swapPulse * 0.22F;
        int glowColor =
                AnimationUtil.multiplyAlpha(
                        accentAlt, (0.45F + swapPulse * 0.35F) * fadeIn * appear);
        SdfShapeRenderer.drawLogoHalf(
                context, firstX, firstY, scale, inverseSwap, glow, appear, glowColor);
        SdfShapeRenderer.drawLogoHalf(
                context, secondX, secondY, scale, swap, glow, appear, glowColor);
        SdfShapeRenderer.drawLogoHalf(
                context,
                firstX,
                firstY,
                scale,
                inverseSwap,
                0.0F,
                appear,
                AnimationUtil.multiplyAlpha(accent, fadeIn));
        SdfShapeRenderer.drawLogoHalf(
                context,
                secondX,
                secondY,
                scale,
                swap,
                0.0F,
                appear,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(accent, accentAlt, swapPulse * 0.5F), fadeIn));
        float ringProgress = AnimationUtil.progress(elapsed, 0.8F, 1.45F);
        if (ringProgress > 0.0F && ringProgress < 1.0F) {
            float ringRadius = logoSize * (0.4F + 1.9F * AnimationUtil.easeOutCubic(ringProgress));
            SdfShapeRenderer.drawRing(
                    context,
                    centerX,
                    centerY,
                    ringRadius,
                    Math.max(0.6F, logoSize * 0.05F * (1.0F - ringProgress)),
                    AnimationUtil.multiplyAlpha(accentAlt, (1.0F - ringProgress) * 0.7F));
        }

        drawAnimatedWord(
                context,
                "DIN",
                textLeft + topIndent,
                topY,
                textScale,
                textSize,
                elapsed,
                1.6F,
                fadeIn);
        drawAnimatedWord(
                context,
                "LIENT",
                textLeft + bottomIndent + topIndent,
                bottomY,
                textScale,
                textSize,
                elapsed,
                1.75F,
                fadeIn);
        float sweepProgress = AnimationUtil.progress(elapsed, 2.2F, 2.65F);
        if (sweepProgress > 0.0F && sweepProgress < 1.0F) {
            float sweepX =
                    AnimationUtil.lerp(
                            textLeft - textSize,
                            textLeft + textWidth + textSize,
                            AnimationUtil.easeInOutCubic(sweepProgress));
            SdfShapeRenderer.drawShadow(
                    context,
                    sweepX - textSize * 0.4F,
                    topY - textSize,
                    textSize * 0.8F,
                    lineSpacing + textSize * 2.0F,
                    textSize * 0.4F,
                    textSize * 0.5F,
                    AnimationUtil.multiplyAlpha(
                            -1,
                            0.1F * (float) Math.sin(3.141592653589793 * sweepProgress) * fadeIn));
        }

        matrices.popMatrix();
    }

    private static void drawAnimatedWord(
            DrawContext context,
            String word,
            float startX,
            float y,
            float scale,
            float slide,
            float elapsed,
            float delay,
            float fade) {
        Matrix3x2fStack matrices = context.getMatrices();
        float x = startX;

        for (int i = 0; i < word.length(); i++) {
            String letter = word.substring(i, i + 1);
            float progress =
                    AnimationUtil.easeOutCubic(
                            AnimationUtil.progress(
                                    elapsed, delay + i * 0.075F, delay + i * 0.075F + 0.32F));
            float advance = SdfFontRenderer.width(SdfFontRenderer.FontStyle.INTRO, letter) * scale;
            if (progress > 0.0F) {
                matrices.pushMatrix();
                matrices.translate(x - (1.0F - progress) * slide * 0.5F, y);
                matrices.scale(scale, scale);
                float textY =
                        -SdfFontRenderer.FontStyle.INTRO.capTop
                                - SdfFontRenderer.FontStyle.INTRO.capHeight * 0.5F;
                SdfFontRenderer.drawTextAtTop(
                        context,
                        SdfFontRenderer.FontStyle.INTRO,
                        letter,
                        0.0F,
                        textY,
                        AnimationUtil.multiplyAlpha(-725249, progress * fade));
                matrices.popMatrix();
            }

            x += advance;
        }
    }
}
