package odinclient.module.spotify;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import odinclient.ui.draw.BoldText;

import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
final class SpotifyCardRenderer {
    static final int CARD_HEIGHT = 62;
    static final int PAD = 9;
    static final int COVER_SIZE = 40;
    static final int CORNER_RADIUS = 9;
    static final int GLOW = 7;
    static final int BADGE_SIZE = 15;
    private static final int TEXT_LEFT = 58;
    private static final float LABEL_SCALE = 0.7F;
    private static final float TITLE_SCALE = 1.25F;
    private static final float ARTIST_SCALE = 0.9F;
    private static final float SOURCE_SCALE = 0.7F;
    private static final float TIME_SCALE = 0.7F;
    private static final int TITLE_COLOR = -592142;
    private static final int ARTIST_COLOR = -4670023;
    private static final int SOURCE_COLOR = -6709093;
    private static final int PAUSED_COLOR = -5722711;
    private static final int TIME_COLOR = -4012093;
    private static final int PROGRESS_TRACK_COLOR = 788529151;
    private static final int KNOB_COLOR = -1444353;
    private static final Identifier CIRCLE_TEXTURE =
            Identifier.of("odinclient", "textures/gui/radar_circle.png");
    private static final int SUPERSAMPLE = 4;
    private static final long FADE_IN_MS = 350L;
    private static final float[] BAR_SPEEDS = new float[] {5.1F, 6.6F, 4.2F};
    private static final float[] BAR_PHASES = new float[] {0.0F, 1.9F, 4.1F};
    private String currentTrackKey = "";
    private long trackChangedAt;
    private double smoothedPositionMs = -1.0;
    private long lastFrameNanos;
    private String textLayoutKey = "";
    private String titleText = "";
    private String artistText = "";
    private long elapsedSeconds = Long.MIN_VALUE;
    private long remainingSeconds = Long.MIN_VALUE;
    private String elapsedLabel = "0:00";
    private String remainingLabel = "-0:00";

    void render(
            DrawContext context,
            MinecraftClient client,
            SpotifyHudTextures textures,
            NowPlayingTrack track,
            SpotifyCardRenderer.CardLayout layout,
            int screenWidth,
            int screenHeight) {
        long now = System.currentTimeMillis();
        if (!track.key().equals(this.currentTrackKey)) {
            this.currentTrackKey = track.key();
            this.trackChangedAt = now;
            this.smoothedPositionMs = -1.0;
            this.textLayoutKey = "";
            this.elapsedSeconds = Long.MIN_VALUE;
            this.remainingSeconds = Long.MIN_VALUE;
        }

        float fade = clamp((float) (now - this.trackChangedAt) / 350.0F, 0.0F, 1.0F);
        float eased = fade * fade * (3.0F - 2.0F * fade);
        int cardWidth = layout.width;
        float scaledWidth = cardWidth * layout.scale;
        float scaledHeight = 62.0F * layout.scale;
        float x =
                layout.margin
                        + (float)
                                ((screenWidth - scaledWidth - 2 * layout.margin)
                                        * layout.xPercent
                                        / 100.0);
        float y =
                layout.margin
                        + (float)
                                ((screenHeight - scaledHeight - 2 * layout.margin)
                                        * layout.yPercent
                                        / 100.0);
        y += (1.0F - eased) * 4.0F * layout.scale;
        float popScale = 0.96F + 0.04F * eased;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + scaledWidth / 2.0F, y + scaledHeight / 2.0F);
        matrices.scale(layout.scale * popScale, layout.scale * popScale);
        matrices.translate(-cardWidth / 2.0F, -31.0F);
        this.drawCardBackground(context, cardWidth);
        this.drawCover(context, textures, track, layout);
        this.drawBadge(context, cardWidth, layout, eased);
        this.drawStatusRow(context, client, track, layout, now, eased);
        this.drawTrackInfo(context, client, track, cardWidth, eased);
        if (layout.showProgress) {
            this.drawProgressBar(context, client, track, layout, cardWidth, eased);
        }

        matrices.popMatrix();
        this.lastFrameNanos = System.nanoTime();
    }

    private void drawCardBackground(DrawContext context, int width) {
        context.drawTexturedQuad(
                SpotifyHudTextures.CARD_TEXTURE, -7, -7, width + 7, 69, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    private void drawCover(
            DrawContext context,
            SpotifyHudTextures textures,
            NowPlayingTrack track,
            SpotifyCardRenderer.CardLayout layout) {
        boolean coverLoaded = layout.showArt && textures.isCoverLoadedFor(track.key());
        context.drawTexturedQuad(
                coverLoaded
                        ? SpotifyHudTextures.COVER_TEXTURE
                        : SpotifyHudTextures.FALLBACK_COVER_TEXTURE,
                9,
                9,
                49,
                49,
                0.0F,
                1.0F,
                0.0F,
                1.0F);
    }

    private void drawBadge(
            DrawContext context, int width, SpotifyCardRenderer.CardLayout layout, float alpha) {
        float x = width - 9.0F - 15.0F;
        float y = 8.0F;
        drawCircle(context, x, y, 15.0F, applyAlpha(layout.accent, alpha));
        context.drawTexturedQuad(
                SpotifyHudTextures.BADGE_TEXTURE,
                (int) x,
                (int) y,
                (int) x + 15,
                (int) y + 15,
                0.0F,
                1.0F,
                0.0F,
                1.0F);
    }

    private void drawStatusRow(
            DrawContext context,
            MinecraftClient client,
            NowPlayingTrack track,
            SpotifyCardRenderer.CardLayout layout,
            long now,
            float alpha) {
        double seconds = now / 1000.0;

        for (int i = 0; i < 3; i++) {
            float barHeight =
                    track.playing()
                            ? 2.5F
                                    + 4.5F
                                            * (float)
                                                    Math.abs(
                                                            Math.sin(
                                                                    seconds * BAR_SPEEDS[i]
                                                                            + BAR_PHASES[i]))
                            : 2.0F;
            context.fill(
                    58 + i * 3,
                    17 - Math.round(barHeight * alpha),
                    58 + i * 3 + 2,
                    17,
                    applyAlpha(layout.accentBright, alpha));
        }

        String label = track.playing() ? "NOW PLAYING" : "PAUSED";
        int labelColor = track.playing() ? layout.accentBright : -5722711;
        drawScaledText(context, client, label, 69.0F, 10.5F, 0.7F, applyAlpha(labelColor, alpha));
    }

    private void drawTrackInfo(
            DrawContext context,
            MinecraftClient client,
            NowPlayingTrack track,
            int width,
            float alpha) {
        TextRenderer textRenderer = client.textRenderer;
        int titleWidth = width - 9 - 15 - 3 - 58;
        int artistWidth = width - 9 - 2 - 58;
        String layoutKey = track.key() + "\u0000" + width;
        if (!layoutKey.equals(this.textLayoutKey)) {
            this.textLayoutKey = layoutKey;
            this.titleText = truncateText(textRenderer, track.title(), titleWidth / 1.25F, "");
            String artist = track.artist().isBlank() ? "Spotify" : track.artist();
            this.artistText = truncateText(textRenderer, artist, artistWidth / 0.9F, "");
        }

        drawScaledText(
                context, client, this.titleText, 58.0F, 19.0F, 1.25F, applyAlpha(-592142, alpha));
        drawScaledText(
                context, client, this.artistText, 58.0F, 32.5F, 0.9F, applyAlpha(-4670023, alpha));
        String source = "Spotify";
        drawScaledText(context, client, source, 58.0F, 43.0F, 0.7F, applyAlpha(-6709093, alpha));
        int checkX = 58 + Math.round(BoldText.width(textRenderer, source) * 0.7F) + 3;
        context.drawTexturedQuad(
                SpotifyHudTextures.CHECK_TEXTURE,
                checkX,
                42,
                checkX + 7,
                49,
                0.0F,
                1.0F,
                0.0F,
                1.0F);
    }

    private void drawProgressBar(
            DrawContext context,
            MinecraftClient client,
            NowPlayingTrack track,
            SpotifyCardRenderer.CardLayout layout,
            int width,
            float alpha) {
        TextRenderer textRenderer = client.textRenderer;
        if (track.durationMs() <= 0L) {
            drawRoundedBar(context, 9.0F, 54, width - 9.0F, 57, applyAlpha(788529151, alpha));
        } else {
            long position = Math.min(track.durationMs(), Math.max(0L, track.estimatedPosition()));
            long nowNanos = System.nanoTime();
            double delta =
                    this.lastFrameNanos == 0L
                            ? 0.016
                            : Math.min(0.2, (nowNanos - this.lastFrameNanos) / 1.0E9);
            if (!(this.smoothedPositionMs < 0.0)
                    && !(Math.abs(position - this.smoothedPositionMs) > 2500.0)) {
                this.smoothedPositionMs =
                        this.smoothedPositionMs
                                + (position - this.smoothedPositionMs) * Math.min(1.0, delta * 6.0);
            } else {
                this.smoothedPositionMs = position;
            }

            long smoothed = (long) this.smoothedPositionMs;
            long elapsed = Math.max(0L, smoothed / 1000L);
            long remaining = Math.max(0L, (track.durationMs() - smoothed) / 1000L);
            if (elapsed != this.elapsedSeconds) {
                this.elapsedSeconds = elapsed;
                this.elapsedLabel = formatTime(smoothed);
            }

            if (remaining != this.remainingSeconds) {
                this.remainingSeconds = remaining;
                this.remainingLabel = "-" + formatTime(Math.max(0L, track.durationMs() - smoothed));
            }

            String elapsedText = this.elapsedLabel;
            String remainingText = this.remainingLabel;
            drawScaledText(
                    context, client, elapsedText, 9.0F, 52.5F, 0.7F, applyAlpha(-4012093, alpha));
            float remainingTextWidth = BoldText.width(textRenderer, remainingText) * 0.7F;
            drawScaledText(
                    context,
                    client,
                    remainingText,
                    width - 9 - remainingTextWidth,
                    52.5F,
                    0.7F,
                    applyAlpha(-4012093, alpha));
            int barLeft = 9 + Math.round(BoldText.width(textRenderer, elapsedText) * 0.7F) + 5;
            int barRight = width - 9 - Math.round(remainingTextWidth) - 5;
            if (barRight > barLeft + 6) {
                drawRoundedBar(context, barLeft, 54, barRight, 57, applyAlpha(788529151, alpha));
                float progressX =
                        barLeft
                                + (barRight - barLeft)
                                        * clamp(
                                                (float)
                                                        (this.smoothedPositionMs
                                                                / track.durationMs()),
                                                0.0F,
                                                1.0F);
                if (progressX > barLeft) {
                    drawRoundedBar(
                            context,
                            barLeft,
                            54,
                            Math.max(barLeft + 1.5F, progressX),
                            57,
                            applyAlpha(layout.accentBright, alpha));
                }

                drawCircle(
                        context,
                        Math.max(barLeft + 3.0F, Math.min(barRight - 3.0F, progressX)) - 3.0F,
                        52.0F,
                        6.0F,
                        applyAlpha(-1444353, alpha));
            }
        }
    }

    private static void drawRoundedBar(
            DrawContext context, float left, float top, float right, float bottom, int color) {
        if (!(right <= left) && !(bottom <= top) && color >>> 24 != 0) {
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.scale(0.25F, 0.25F);
            int x = Math.round(left * 4.0F);
            int y = Math.round(top * 4.0F);
            int width = Math.max(1, Math.round((right - left) * 4.0F));
            int height = Math.max(2, Math.round((bottom - top) * 4.0F));
            int radius = Math.min(height / 2, width / 2);
            if (radius > 0 && width > height) {
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        CIRCLE_TEXTURE,
                        x,
                        y,
                        0.0F,
                        0.0F,
                        radius,
                        height,
                        512,
                        1024,
                        1024,
                        1024,
                        color);
                context.fill(x + radius, y, x + width - radius, y + height, color);
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        CIRCLE_TEXTURE,
                        x + width - radius,
                        y,
                        512.0F,
                        0.0F,
                        radius,
                        height,
                        512,
                        1024,
                        1024,
                        1024,
                        color);
            } else {
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        CIRCLE_TEXTURE,
                        x,
                        y,
                        0.0F,
                        0.0F,
                        width,
                        height,
                        1024,
                        1024,
                        1024,
                        1024,
                        color);
            }

            matrices.popMatrix();
        }
    }

    private static void drawCircle(
            DrawContext context, float x, float y, float diameter, int color) {
        if (!(diameter <= 0.0F) && color >>> 24 != 0) {
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.scale(0.25F, 0.25F);
            int scaledX = Math.round(x * 4.0F);
            int scaledY = Math.round(y * 4.0F);
            int size = Math.max(2, Math.round(diameter * 4.0F));
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    CIRCLE_TEXTURE,
                    scaledX,
                    scaledY,
                    0.0F,
                    0.0F,
                    size,
                    size,
                    1024,
                    1024,
                    1024,
                    1024,
                    color);
            matrices.popMatrix();
        }
    }

    private static void drawScaledText(
            DrawContext context,
            MinecraftClient client,
            String text,
            float x,
            float y,
            float scale,
            int color) {
        if ((color >> 24 & 0xFF) >= 8) {
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            BoldText.draw(context, client.textRenderer, text, 0, 0, color);
            matrices.popMatrix();
        }
    }

    private static String truncateText(
            TextRenderer textRenderer, String text, float maxWidth, String prefix) {
        if (BoldText.width(textRenderer, prefix + text) <= maxWidth) {
            return text;
        } else {
            String ellipsis = "...";
            float available = maxWidth - BoldText.width(textRenderer, prefix + ellipsis);
            int length = text.length();

            while (length > 0
                    && BoldText.width(textRenderer, prefix + text.substring(0, length))
                            > available) {
                length--;
            }

            return length == 0 ? ellipsis : text.substring(0, length).stripTrailing() + ellipsis;
        }
    }

    private static String formatTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + ":" + (seconds < 10L ? "0" : "") + seconds;
    }

    private static int applyAlpha(int color, float alpha) {
        int colorAlpha = color >>> 24;
        if (colorAlpha == 0) {
            colorAlpha = 255;
        }

        colorAlpha = (int) (colorAlpha * alpha);
        return colorAlpha << 24 | color & 16777215;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    @Environment(EnvType.CLIENT)
    record CardLayout(
            int width,
            double xPercent,
            double yPercent,
            int margin,
            float scale,
            boolean showArt,
            boolean showProgress,
            int accent,
            int accentBright) {
        CardLayout(
                int width,
                double xPercent,
                double yPercent,
                int margin,
                float scale,
                boolean showArt,
                boolean showProgress,
                int accent,
                int accentBright) {
            this.width = width;
            this.xPercent = xPercent;
            this.yPercent = yPercent;
            this.margin = 0;
            this.scale = scale;
            this.showArt = showArt;
            this.showProgress = showProgress;
            this.accent = accent;
            this.accentBright = accentBright;
        }
    }
}
