package odinclient.module.spotify;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

@Environment(EnvType.CLIENT)
final class SpotifyHudTextures {
    static final Identifier CARD_TEXTURE = id("spotify_hud_card");
    static final Identifier COVER_TEXTURE = id("spotify_hud_cover");
    static final Identifier FALLBACK_COVER_TEXTURE = id("spotify_hud_cover_fallback");
    static final Identifier BADGE_TEXTURE = id("spotify_hud_badge");
    static final Identifier CHECK_TEXTURE = id("spotify_hud_check");
    static final Identifier KNOB_TEXTURE = id("spotify_hud_knob");
    private static final int SUPERSAMPLE = 4;
    private static final int COVER_SIZE = 176;
    private String cardKey = "";
    private String badgeKey = "";
    private String fallbackKey = "";
    private String loadedCoverKey = "";
    private boolean hasCover;
    private String lastCoverKey = "";
    private int lastCoverSize = -1;

    private static Identifier id(String path) {
        return Identifier.of("odinclient", path);
    }

    boolean isCoverLoadedFor(String key) {
        return this.hasCover && this.loadedCoverKey.equals(key);
    }

    void updateCardTexture(
            MinecraftClient client,
            int width,
            int height,
            int radius,
            int shadow,
            int baseAlpha,
            int tint) {
        String cacheKey =
                width + "x" + height + ":" + radius + ":" + shadow + ":" + baseAlpha + ":" + tint;
        if (!cacheKey.equals(this.cardKey)) {
            this.cardKey = cacheKey;
            int imageWidth = (width + 2 * shadow) * 4;
            int imageHeight = (height + 2 * shadow) * 4;
            NativeImage image = new NativeImage(imageWidth, imageHeight, true);
            float halfWidth = width / 2.0F;
            float halfHeight = height / 2.0F;
            int tintR = tint >> 16 & 0xFF;
            int tintG = tint >> 8 & 0xFF;
            int tintB = tint & 0xFF;

            for (int py = 0; py < imageHeight; py++) {
                for (int px = 0; px < imageWidth; px++) {
                    float localY = (py + 0.5F) / 4.0F - shadow;
                    float distance =
                            roundedBoxDistance(
                                    (px + 0.5F) / 4.0F - shadow - halfWidth,
                                    localY - halfHeight,
                                    halfWidth,
                                    halfHeight,
                                    radius);
                    float edgeCoverage = coverage(distance, 0.35F);
                    int alpha = 0;
                    int red = 0;
                    int green = 0;
                    int blue = 0;
                    if (distance < shadow) {
                        alpha =
                                (int)
                                        (88.0
                                                * Math.pow(
                                                        1.0F - Math.max(0.0F, distance) / shadow,
                                                        2.3));
                    }

                    if (edgeCoverage > 0.0F) {
                        float gradient = clamp(localY / height, 0.0F, 1.0F);
                        float topR = lerp(17.0F, 8.0F, gradient);
                        float topG = lerp(25.0F, 12.0F, gradient);
                        float topB = lerp(20.0F, 10.0F, gradient);
                        float highlight = clamp(1.0F - localY / 1.9F, 0.0F, 1.0F) * 0.14F;
                        float litR = topR + (255.0F - topR) * highlight;
                        float litG = topG + (255.0F - topG) * highlight;
                        float litB = topB + (255.0F - topB) * highlight;
                        float tintAmount = clamp(1.0F - -distance / 1.25F, 0.0F, 1.0F) * 0.45F;
                        float mixedR = lerp(litR, tintR, tintAmount);
                        float mixedG = lerp(litG, tintG, tintAmount);
                        float mixedB = lerp(litB, tintB, tintAmount);
                        int fillAlpha = (int) Math.min(255.0F, baseAlpha + 35.0F * tintAmount);
                        red = (int) lerp(0, mixedR, edgeCoverage);
                        green = (int) lerp(0, mixedG, edgeCoverage);
                        blue = (int) lerp(0, mixedB, edgeCoverage);
                        alpha = (int) Math.max(alpha, fillAlpha * edgeCoverage);
                    }

                    image.setColorArgb(px, py, packArgb(alpha, red, green, blue));
                }
            }

            registerImage(client, CARD_TEXTURE, image);
        }
    }

    void updateBadgeTextures(MinecraftClient client, int badgeColor, int knobColor) {
        String cacheKey = badgeColor + ":" + knobColor;
        if (!cacheKey.equals(this.badgeKey)) {
            this.badgeKey = cacheKey;
            registerSmoothImage(client, BADGE_TEXTURE, createBadgeImage(badgeColor));
            registerImage(client, CHECK_TEXTURE, createCheckImage(badgeColor));
            registerImage(client, KNOB_TEXTURE, createKnobImage(knobColor));
        }
    }

    private static NativeImage createBadgeImage(int color) {
        NativeImage image = new NativeImage(192, 192, true);
        float center = 192 / 2.0F;
        float radius = 192 * 0.468F;

        for (int y = 0; y < 192; y++) {
            for (int x = 0; x < 192; x++) {
                float dx = x + 0.5F - center;
                float dy = y + 0.5F - center;
                float circleCoverage =
                        coverage((float) Math.sqrt(dx * dx + dy * dy) - radius, 1.15F);
                if (circleCoverage <= 0.0F) {
                    image.setColorArgb(x, y, 0);
                } else {
                    image.setColorArgb(
                            x,
                            y,
                            packArgb(
                                    (int)
                                            (255.0F
                                                    * circleCoverage
                                                    * soundWaveCoverage(dx, dy, radius * 0.86F)),
                                    5,
                                    15,
                                    9));
                }
            }
        }

        return image;
    }

    private static float soundWaveCoverage(float x, float y, float radius) {
        float[][] waves = {{-0.31F, 0.62F, 0.075F}, {0.0F, 0.54F, 0.068F}, {0.29F, 0.45F, 0.06F}};
        float result = 0.0F;

        for (float[] wave : waves) {
            float halfWidth = wave[1] * radius;
            float thickness = wave[2] * radius;
            float arcRadius = 1.35F * radius;
            float centerY = wave[0] * radius + arcRadius;
            float distance;
            if (Math.abs(x) <= halfWidth) {
                distance =
                        Math.abs(
                                (float) Math.sqrt(x * x + (y - centerY) * (y - centerY))
                                        - arcRadius);
            } else {
                float endY =
                        centerY - (float) Math.sqrt(arcRadius * arcRadius - halfWidth * halfWidth);
                float dx = Math.abs(x) - halfWidth;
                float dy = y - endY;
                distance = (float) Math.sqrt(dx * dx + dy * dy);
            }

            result = Math.max(result, coverage(distance - thickness, 1.0F));
        }

        return result;
    }

    private static NativeImage createCheckImage(int color) {
        NativeImage image = new NativeImage(56, 56, true);
        float center = 56 / 2.0F;
        float radius = 56 * 0.46F;
        float startX = 56 * 0.28F;
        float startY = 56 * 0.52F;
        float midX = 56 * 0.44F;
        float midY = 56 * 0.68F;
        float endX = 56 * 0.74F;
        float endY = 56 * 0.34F;
        float strokeWidth = 56 * 0.075F;

        for (int y = 0; y < 56; y++) {
            for (int x = 0; x < 56; x++) {
                float px = x + 0.5F;
                float py = y + 0.5F;
                float circleCoverage =
                        coverage((float) Math.hypot(px - center, py - center) - radius, 1.0F);
                if (circleCoverage <= 0.0F) {
                    image.setColorArgb(x, y, 0);
                } else {
                    float checkCoverage =
                            coverage(
                                    Math.min(
                                                    segmentDistance(
                                                            px, py, startX, startY, midX, midY),
                                                    segmentDistance(px, py, midX, midY, endX, endY))
                                            - strokeWidth,
                                    1.0F);
                    image.setColorArgb(
                            x,
                            y,
                            packArgb(
                                    (int) (255.0F * circleCoverage),
                                    lerpInt(color >> 16 & 0xFF, 245, checkCoverage),
                                    lerpInt(color >> 8 & 0xFF, 250, checkCoverage),
                                    lerpInt(color & 0xFF, 246, checkCoverage)));
                }
            }
        }

        return image;
    }

    private static NativeImage createKnobImage(int color) {
        NativeImage image = new NativeImage(48, 48, true);
        float center = 48 / 2.0F;
        float innerRadius = 48 * 0.3F;
        float outerRadius = 48 * 0.48F;

        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                float distance = (float) Math.hypot(x + 0.5F - center, y + 0.5F - center);
                float fill = coverage(distance - innerRadius, 1.0F);
                float glow = clamp(1.0F - distance / outerRadius, 0.0F, 1.0F);
                image.setColorArgb(
                        x,
                        y,
                        packArgb(
                                (int) Math.max(255.0F * fill, 90.0F * glow * glow),
                                color >> 16 & 0xFF,
                                color >> 8 & 0xFF,
                                color & 0xFF));
            }
        }

        return image;
    }

    void updateFallbackCover(MinecraftClient client, int color) {
        String cacheKey = String.valueOf(color);
        if (!cacheKey.equals(this.fallbackKey)) {
            this.fallbackKey = cacheKey;
            NativeImage image = new NativeImage(176, 176, true);
            float half = 88.0F;
            float cornerRadius = 22.0F;
            float discRadius = 45.76F;

            for (int y = 0; y < 176; y++) {
                for (int x = 0; x < 176; x++) {
                    float px = x + 0.5F;
                    float py = y + 0.5F;
                    float boxCoverage =
                            coverage(
                                    roundedBoxDistance(
                                            px - half, py - half, half, half, cornerRadius),
                                    1.0F);
                    if (boxCoverage <= 0.0F) {
                        image.setColorArgb(x, y, 0);
                    } else {
                        float fraction = py / 176.0F;
                        float red = lerp(26.0F, 16.0F, fraction);
                        float green = lerp(34.0F, 22.0F, fraction);
                        float blue = lerp(28.0F, 18.0F, fraction);
                        float discCoverage =
                                coverage(
                                        (float) Math.hypot(px - half, py - half) - discRadius,
                                        1.0F);
                        if (discCoverage > 0.0F) {
                            float discR = lerp(red, color >> 16 & 0xFF, discCoverage);
                            float discG = lerp(green, color >> 8 & 0xFF, discCoverage);
                            float discB = lerp(blue, color & 0xFF, discCoverage);
                            float waveCoverage =
                                    soundWaveCoverage(px - half, py - half, discRadius);
                            red = lerp(discR, 10.0F, waveCoverage);
                            green = lerp(discG, 18.0F, waveCoverage);
                            blue = lerp(discB, 12.0F, waveCoverage);
                        }

                        image.setColorArgb(
                                x,
                                y,
                                packArgb(
                                        (int) (255.0F * boxCoverage),
                                        (int) red,
                                        (int) green,
                                        (int) blue));
                    }
                }
            }

            registerImage(client, FALLBACK_COVER_TEXTURE, image);
        }
    }

    void updateCover(MinecraftClient client, String key, byte[] data) {
        int size = data == null ? 0 : data.length;

        if (!key.equals(this.lastCoverKey) || size != this.lastCoverSize) {
            this.lastCoverKey = key;
            this.lastCoverSize = size;
            if (size == 0) {
                this.hasCover = false;
                this.loadedCoverKey = "";
            } else {
                try (NativeImage source = readImage(data)) {
                    registerImage(client, COVER_TEXTURE, createRoundedCover(source));
                    this.loadedCoverKey = key;
                    this.hasCover = true;
                } catch (Exception e) {
                    this.hasCover = false;
                    this.loadedCoverKey = "";
                }
            }
        }
    }

    private static NativeImage readImage(byte[] data) throws IOException {
        try {
            return NativeImage.read(data);
        } catch (Exception e) {
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(data));
            if (buffered == null) {
                throw new IOException("Unsupported image data");
            } else {
                NativeImage image =
                        new NativeImage(buffered.getWidth(), buffered.getHeight(), true);

                for (int y = 0; y < buffered.getHeight(); y++) {
                    for (int x = 0; x < buffered.getWidth(); x++) {
                        image.setColorArgb(x, y, buffered.getRGB(x, y) | 0xFF000000);
                    }
                }

                return image;
            }
        }
    }

    private static NativeImage createRoundedCover(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int top = 0;
        int bottom = height - 1;
        int left = 0;
        int right = width - 1;
        int maxRowTrim = height / 4;
        int maxColumnTrim = width / 4;

        while (top < maxRowTrim && isRowDark(source, top, width)) {
            top++;
        }

        while (height - 1 - bottom < maxRowTrim && isRowDark(source, bottom, width)) {
            bottom--;
        }

        while (left < maxColumnTrim && isColumnDark(source, left, height)) {
            left++;
        }

        while (width - 1 - right < maxColumnTrim && isColumnDark(source, right, height)) {
            right--;
        }

        int cropWidth = Math.max(8, right - left + 1);
        int cropHeight = Math.max(8, bottom - top + 1);
        int side = Math.min(cropWidth, cropHeight);
        float originX = left + (cropWidth - side) / 2.0F;
        float originY = top + (cropHeight - side) / 2.0F;
        NativeImage result = new NativeImage(176, 176, true);
        float half = 88.0F;
        float cornerRadius = 22.0F;

        for (int y = 0; y < 176; y++) {
            for (int x = 0; x < 176; x++) {
                float distance =
                        roundedBoxDistance(
                                x + 0.5F - half, y + 0.5F - half, half, half, cornerRadius);
                float edgeCoverage = coverage(distance, 1.0F);
                if (edgeCoverage <= 0.0F) {
                    result.setColorArgb(x, y, 0);
                } else {
                    int sampled =
                            sampleBilinear(
                                    source,
                                    originX + (x + 0.5F) / 176.0F * side,
                                    originY + (y + 0.5F) / 176.0F * side,
                                    width,
                                    height);
                    float shade = clamp(1.0F - -distance / 3.52F, 0.0F, 1.0F) * 0.25F;
                    result.setColorArgb(
                            x,
                            y,
                            packArgb(
                                    (int) (255.0F * edgeCoverage),
                                    (int) ((sampled >> 16 & 0xFF) * (1.0F - shade)),
                                    (int) ((sampled >> 8 & 0xFF) * (1.0F - shade)),
                                    (int) ((sampled & 0xFF) * (1.0F - shade))));
                }
            }
        }

        return result;
    }

    private static boolean isRowDark(NativeImage image, int y, int width) {
        int step = Math.max(1, width / 24);

        for (int x = 0; x < width; x += step) {
            if (maxChannel(image.getColorArgb(x, y)) > 16) {
                return false;
            }
        }

        return true;
    }

    private static boolean isColumnDark(NativeImage image, int x, int height) {
        int step = Math.max(1, height / 24);

        for (int y = 0; y < height; y += step) {
            if (maxChannel(image.getColorArgb(x, y)) > 16) {
                return false;
            }
        }

        return true;
    }

    private static int maxChannel(int color) {
        return Math.max(color >> 16 & 0xFF, Math.max(color >> 8 & 0xFF, color & 0xFF));
    }

    private static int sampleBilinear(NativeImage image, float u, float v, int width, int height) {
        float fx = clamp(u - 0.5F, 0.0F, width - 1.001F);
        float fy = clamp(v - 0.5F, 0.0F, height - 1.001F);
        int x0 = (int) fx;
        int y0 = (int) fy;
        int x1 = Math.min(x0 + 1, width - 1);
        int y1 = Math.min(y0 + 1, height - 1);
        float tx = fx - x0;
        float ty = fy - y0;
        int topLeft = image.getColorArgb(x0, y0);
        int topRight = image.getColorArgb(x1, y0);
        int bottomLeft = image.getColorArgb(x0, y1);
        int bottomRight = image.getColorArgb(x1, y1);
        return packArgb(
                255,
                lerpChannel(
                        lerpChannel(topLeft >> 16 & 0xFF, topRight >> 16 & 0xFF, tx),
                        lerpChannel(bottomLeft >> 16 & 0xFF, bottomRight >> 16 & 0xFF, tx),
                        ty),
                lerpChannel(
                        lerpChannel(topLeft >> 8 & 0xFF, topRight >> 8 & 0xFF, tx),
                        lerpChannel(bottomLeft >> 8 & 0xFF, bottomRight >> 8 & 0xFF, tx),
                        ty),
                lerpChannel(
                        lerpChannel(topLeft & 0xFF, topRight & 0xFF, tx),
                        lerpChannel(bottomLeft & 0xFF, bottomRight & 0xFF, tx),
                        ty));
    }

    private static void registerTexture(
            MinecraftClient client, Identifier id, NativeImageBackedTexture texture) {
        client.getTextureManager().registerTexture(id, texture);
    }

    private static void registerImage(MinecraftClient client, Identifier id, NativeImage image) {
        registerTexture(
                client,
                id,
                new NativeImageBackedTexture(() -> "OdinClient Spotify HUD " + id, image));
    }

    private static void registerSmoothImage(
            MinecraftClient client, Identifier id, NativeImage image) {
        registerTexture(
                client,
                id,
                new SpotifyHudTextures.SmoothTexture(
                        () -> "OdinClient Spotify HUD smooth " + id, image));
    }

    private static float roundedBoxDistance(
            float x, float y, float halfWidth, float halfHeight, float radius) {
        float qx = Math.abs(x) - halfWidth + radius;
        float qy = Math.abs(y) - halfHeight + radius;
        float outside = (float) Math.hypot(Math.max(qx, 0.0F), Math.max(qy, 0.0F));
        return Math.min(Math.max(qx, qy), 0.0F) + outside - radius;
    }

    private static float segmentDistance(
            float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax;
        float aby = by - ay;
        float t = clamp(((px - ax) * abx + (py - ay) * aby) / (abx * abx + aby * aby), 0.0F, 1.0F);
        return (float) Math.hypot(px - (ax + t * abx), py - (ay + t * aby));
    }

    private static float coverage(float distance, float softness) {
        return clamp(0.5F - distance / softness, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static int lerpInt(int from, int to, float t) {
        return (int) (from + (to - from) * t);
    }

    private static int lerpChannel(int from, int to, float t) {
        return (int) (from + (to - from) * t);
    }

    private static int packArgb(int alpha, int red, int green, int blue) {
        return Math.max(0, Math.min(255, alpha)) << 24
                | Math.max(0, Math.min(255, red)) << 16
                | Math.max(0, Math.min(255, green)) << 8
                | Math.max(0, Math.min(255, blue));
    }

    @Environment(EnvType.CLIENT)
    private static final class SmoothTexture extends NativeImageBackedTexture {
        private SmoothTexture(Supplier<String> nameSupplier, NativeImage image) {
            super(nameSupplier, image);
            this.sampler = RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR);
        }
    }
}
