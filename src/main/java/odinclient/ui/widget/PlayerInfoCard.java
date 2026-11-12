package odinclient.ui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.Vec3d;

import odinclient.module.hud.ThemeModule;
import odinclient.module.privacy.NameProtect;
import odinclient.ui.anim.AnimationUtil;
import odinclient.ui.draw.SdfFontRenderer;
import odinclient.ui.draw.SdfShapeRenderer;

import org.joml.Matrix3x2fStack;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

public final class PlayerInfoCard {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ROOT);
    private static final int SAMPLE_COUNT = 48;
    private final float[] fpsSamples = new float[48];
    private int sampleHead;
    private long lastSampleTime;
    private float sampleShift;
    private float smoothedFps;
    private float smoothedBps;
    private float smoothedPing;
    private float graphScale = 0.25F;

    public void render(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float alpha,
            float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInfoCard.PlayerInfoSnapshot info = PlayerInfoCard.PlayerInfoSnapshot.read(client);
        this.recordFpsSample(info.fps);
        this.smoothedFps = AnimationUtil.smoothApproach(this.smoothedFps, info.fps, 6.0F, delta);
        this.smoothedBps =
                AnimationUtil.smoothApproach(this.smoothedBps, (float) info.bps, 8.0F, delta);
        this.smoothedPing = AnimationUtil.smoothApproach(this.smoothedPing, info.ping, 6.0F, delta);
        this.sampleShift = AnimationUtil.smoothApproach(this.sampleShift, 0.0F, 7.0F, delta);
        int accent = ThemeModule.getAccentColorAt(0.3F);
        SdfShapeRenderer.fill(
                context, x, y, width, height, 10.0F, AnimationUtil.multiplyAlpha(-16251379, alpha));
        SdfShapeRenderer.drawOutline(
                context,
                x,
                y,
                width,
                height,
                10.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(-14870999, alpha));
        SdfShapeRenderer.fillHorizontalGradient(
                context,
                x,
                y,
                width,
                30.0F,
                10.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 34), alpha),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 0), alpha));
        float iconSize = 22.0F;
        float iconX = x + 8.0F;
        float iconY = y + 8.0F;
        SdfShapeRenderer.drawShadow(
                context,
                iconX,
                iconY,
                iconSize,
                iconSize,
                6.0F,
                4.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 90), alpha));
        SdfShapeRenderer.fill(
                context,
                iconX - 1.0F,
                iconY - 1.0F,
                iconSize + 2.0F,
                iconSize + 2.0F,
                6.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-15659750, accent, 0.35F), alpha));
        if (client.player != null && alpha > 0.6F) {
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(iconX, iconY);
            matrices.scale(iconSize / 16.0F, iconSize / 16.0F);
            PlayerSkinDrawer.draw(context, client.player.getSkin(), 0, 0, 16);
            matrices.popMatrix();
        } else {
            SdfShapeRenderer.drawLogo(
                    context,
                    iconX + iconSize * 0.5F,
                    iconY + iconSize * 0.5F,
                    iconSize * 0.22F,
                    0.0F,
                    1.0F,
                    AnimationUtil.multiplyAlpha(accent, alpha));
        }

        float textX = iconX + iconSize + 7.0F;
        float textWidth = x + width - 8.0F - textX;
        float nameY = iconY + 4.5F;
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BOLD,
                SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BOLD, info.player, textWidth),
                textX,
                nameY,
                AnimationUtil.multiplyAlpha(-658693, alpha));
        float serverY = iconY + 13.5F;
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        "Playing on " + info.server,
                        textWidth),
                textX,
                serverY,
                AnimationUtil.multiplyAlpha(-6845010, alpha));
        this.drawPremiumBadge(context, textX, iconY + 18.5F, accent, alpha);
        float dividerY = y + 40.0F;
        SdfShapeRenderer.fill(
                context,
                x + 8.0F,
                dividerY,
                width - 16.0F,
                0.75F,
                0.0F,
                AnimationUtil.multiplyAlpha(-14870999, alpha));
        String[] labels = new String[] {"FPS", "BPS", "PING"};
        String[] values =
                new String[] {
                    String.valueOf(Math.round(this.smoothedFps)),
                    String.format(Locale.ROOT, "%.1f", this.smoothedBps),
                    Math.round(this.smoothedPing) + "ms"
                };
        float columnWidth = (width - 16.0F) / 3.0F;

        for (int i = 0; i < 3; i++) {
            float columnX = x + 8.0F + columnWidth * i + columnWidth * 0.5F;
            SdfFontRenderer.drawTextCentered(
                    context,
                    SdfFontRenderer.FontStyle.TINY,
                    labels[i],
                    columnX,
                    dividerY + 8.0F,
                    AnimationUtil.multiplyAlpha(-10529165, alpha));
            SdfFontRenderer.drawTextCentered(
                    context,
                    SdfFontRenderer.FontStyle.BOLD,
                    values[i],
                    columnX,
                    dividerY + 17.0F,
                    AnimationUtil.multiplyAlpha(-658693, alpha));
        }

        float footerHeight = 22.0F;
        float graphY = dividerY + 26.0F;
        float graphHeight = y + height - footerHeight - 12.0F - graphY;
        float maxSample = 0.0F;
        float[] samples = this.fpsSamples;
        int sampleLength = samples.length;

        for (int j = 0; j < sampleLength; j++) {
            maxSample = Math.max(maxSample, samples[j]);
        }

        this.graphScale =
                AnimationUtil.smoothApproach(
                        this.graphScale, Math.max(0.05F, maxSample * 1.25F), 3.0F, delta);
        if (graphHeight > 8.0F) {
            this.drawFpsGraph(context, x + 8.0F, graphY, width - 16.0F, graphHeight, accent, alpha);
        }

        float footerY = y + height - footerHeight - 6.0F;
        SdfShapeRenderer.fill(
                context,
                x + 6.0F,
                footerY,
                width - 12.0F,
                footerHeight,
                7.0F,
                AnimationUtil.multiplyAlpha(-15659750, alpha));
        LocalDateTime now = LocalDateTime.now();
        boolean night = now.getHour() >= 19 || now.getHour() < 7;
        int rawTimeColor = night ? AnimationUtil.lerpColor(accent, -1, 0.35F) : -14249;
        short timeColor = (short) rawTimeColor;
        SdfShapeRenderer.drawShadow(
                context,
                x + 12.0F,
                footerY + 5.0F,
                12.0F,
                12.0F,
                6.0F,
                3.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(timeColor, 70), alpha));
        String timeIcon = night ? "\ue017" : "\ue018";
        SdfFontRenderer.drawIcon(
                context,
                timeIcon,
                x + 18.0F,
                footerY + footerHeight * 0.5F,
                10.0F,
                AnimationUtil.multiplyAlpha(timeColor, alpha));
        float timeX = x + 29.0F;
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BOLD,
                TIME_FORMAT.format(now),
                timeX,
                footerY + 7.5F,
                AnimationUtil.multiplyAlpha(-658693, alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                DATE_FORMAT.format(now),
                timeX,
                footerY + 15.5F,
                AnimationUtil.multiplyAlpha(-6845010, alpha));
    }

    private void drawPremiumBadge(DrawContext context, float x, float y, int accent, float alpha) {
        String label = "PREMIUM";
        float badgeWidth = SdfFontRenderer.width(SdfFontRenderer.FontStyle.TINY, label) + 15.0F;
        SdfShapeRenderer.fillVerticalGradient(
                context,
                x,
                y,
                badgeWidth,
                8.5F,
                4.25F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 90), alpha),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 40), alpha));
        SdfShapeRenderer.drawOutline(
                context,
                x,
                y,
                badgeWidth,
                8.5F,
                4.25F,
                0.6F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 150), alpha));
        SdfFontRenderer.drawIcon(
                context,
                "\ue01c",
                x + 5.5F,
                y + 4.25F,
                5.5F,
                AnimationUtil.multiplyAlpha(-1, alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TINY,
                label,
                x + 10.0F,
                y + 4.25F,
                AnimationUtil.multiplyAlpha(-1, alpha));
    }

    private void recordFpsSample(int fps) {
        long time = System.currentTimeMillis();
        if (time - this.lastSampleTime >= 150L) {
            float sample = Math.max(0.06F, Math.min(1.0F, fps / 240.0F));
            if (this.lastSampleTime == 0L) {
                Arrays.fill(this.fpsSamples, sample);
            }

            this.fpsSamples[this.sampleHead] = sample;
            this.sampleHead = (this.sampleHead + 1) % 48;
            this.lastSampleTime = time;
            this.sampleShift = 1.0F;
        }
    }

    private void drawFpsGraph(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int accent,
            float alpha) {
        SdfShapeRenderer.fill(
                context,
                x,
                y,
                width,
                height,
                6.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 16), alpha));
        context.enableScissor(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.ceil(x + width),
                (int) Math.ceil(y + height));
        int columns = Math.max(8, Math.round(width / 1.5F));
        float step = width / (columns - 1);
        float previousY = Float.NaN;

        for (int i = 0; i < columns; i++) {
            float pointY =
                    y
                            + height
                            - 2.0F
                            - (height - 6.0F) * this.sampleGraph((float) i / (columns - 1));
            float pointX = x + i * step;
            SdfShapeRenderer.fillHorizontalGradient(
                    context,
                    pointX - step * 0.5F,
                    pointY,
                    step + 0.05F,
                    y + height - pointY,
                    0.0F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 120), alpha),
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 6), alpha));
            if (!Float.isNaN(previousY)) {
                SdfShapeRenderer.fillCircle(
                        context,
                        pointX - step * 0.5F,
                        (pointY + previousY) * 0.5F,
                        0.85F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(accent, -1, 0.35F), alpha));
            }

            SdfShapeRenderer.fillCircle(
                    context,
                    pointX,
                    pointY,
                    0.85F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.lerpColor(accent, -1, 0.35F), alpha));
            previousY = pointY;
        }

        context.disableScissor();
    }

    private float sampleGraph(float t) {
        float position = t * 45.0F + 1.0F - this.sampleShift;
        int index = (int) Math.floor(position);
        float fraction = position - index;
        float p0 = this.getSample(index - 1);
        float p1 = this.getSample(index);
        float p2 = this.getSample(index + 1);
        float p3 = this.getSample(index + 2);
        float fractionSquared = fraction * fraction;
        return Math.max(
                0.04F,
                Math.min(
                        1.0F,
                        0.5F
                                * (2.0F * p1
                                        + (p2 - p0) * fraction
                                        + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * fractionSquared
                                        + (-p0 + 3.0F * p1 - 3.0F * p2 + p3)
                                                * (fractionSquared * fraction))
                                / this.graphScale));
    }

    private float getSample(int offset) {
        int clamped = Math.max(0, Math.min(47, offset));
        return this.fpsSamples[(this.sampleHead + clamped) % 48];
    }

    private record PlayerInfoSnapshot(String player, String server, int fps, double bps, int ping) {
        static PlayerInfoCard.PlayerInfoSnapshot read(MinecraftClient client) {
            String playerName =
                    client.getGameProfile() == null ? "Player" : client.getGameProfile().name();
            String player = NameProtect.replaceNames(playerName);
            String server = "Main Menu";
            ServerInfo serverInfo = client.getCurrentServerEntry();
            if (serverInfo != null) {
                boolean useAddress =
                        serverInfo.name == null
                                || serverInfo.name.isBlank()
                                || "Minecraft Server".equalsIgnoreCase(serverInfo.name);
                server = useAddress ? serverInfo.address : serverInfo.name;
            } else if (client.isConnectedToLocalServer()) {
                server = "Singleplayer";
            }

            double bps = 0.0;
            int ping = 0;
            if (client.player != null) {
                Vec3d velocity = client.player.getVelocity();
                bps = Math.hypot(velocity.x, velocity.z) * 20.0;
                ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
                if (networkHandler != null) {
                    PlayerListEntry playerListEntry =
                            networkHandler.getPlayerListEntry(client.player.getGameProfile().id());
                    if (playerListEntry != null) {
                        ping = Math.max(0, playerListEntry.getLatency());
                    }
                }
            }

            return new PlayerInfoCard.PlayerInfoSnapshot(
                    player, server, Math.max(0, client.getCurrentFps()), bps, ping);
        }
    }
}
