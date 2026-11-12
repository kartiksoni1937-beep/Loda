package odinclient.module.hud.regionmap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

import odinclient.module.hud.ThemeModule;
import odinclient.ui.draw.BoldText;
import odinclient.ui.draw.SdfShapeRenderer;

import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.function.IntUnaryOperator;

public final class RegionMapHud {
    private static final float BASE_HEIGHT = 144.0F;
    private static final float CORNER_RADIUS = 4.0F;
    private static final float PANEL_WIDTH = 156.0F;

    private RegionMapHud() {}

    public static void render(
            DrawContext context,
            MinecraftClient client,
            int screenWidth,
            int screenHeight,
            int percentX,
            int percentY,
            float scale,
            boolean showCoords,
            boolean showNumbers,
            double opacity,
            IntUnaryOperator colorProvider) {
        float panelHeight = BASE_HEIGHT + (showCoords ? 51.0F : 40.0F);
        float offsetX = Math.max(0.0F, screenWidth - 156.0F * scale) * percentX / 100.0F;
        float offsetY = Math.max(0.0F, screenHeight - panelHeight * scale) * percentY / 100.0F;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(offsetX, offsetY);
        matrices.scale(scale, scale);

        try {
            int accent = ThemeModule.getAccentColorAt(0.82F);
            int backgroundAlpha = (int) Math.round(Math.max(0.0, Math.min(1.0, opacity)) * 220.0);
            if (!ThemeModule.isPanelStyle()) {
                SdfShapeRenderer.fill(context, 0.0F, 0.0F, 156.0F, panelHeight, 7.0F, 1932342078);
            }

            SdfShapeRenderer.fill(
                    context,
                    1.0F,
                    1.0F,
                    154.0F,
                    panelHeight - 2.0F,
                    6.0F,
                    backgroundAlpha << 24 | 658965);
            BoldText.draw(context, client.textRenderer, "Region Map", 8, 8, -1380363);
            boolean overworld = client.world.getRegistryKey().equals(World.OVERWORLD);
            RegionMapLayout.RegionRect currentRegion =
                    overworld
                            ? RegionMapLayout.findRegion(client.player.getX(), client.player.getZ())
                            : null;
            String badgeText = currentRegion == null ? "---" : "#" + currentRegion.number();

            int badgeWidth = Math.max(22, BoldText.width(client.textRenderer, badgeText) + 8);
            SdfShapeRenderer.fill(
                    context,
                    156.0F - badgeWidth - 6.0F,
                    6.0F,
                    badgeWidth,
                    11.0F,
                    4.0F,
                    withAlpha(accent, 108));
            BoldText.draw(
                    context, client.textRenderer, badgeText, 156 - badgeWidth - 2, 7, -1380363);

            for (RegionMapLayout.RegionRect region : RegionMapLayout.REGIONS) {
                float x = 6.0F + region.column() * 4.0F;
                float y = 20.0F + region.row() * 4.0F;
                float width = region.width() * 4.0F;
                float height = region.height() * 4.0F;
                boolean selected = region == currentRegion;
                int regionColor = getRegionColor(region.region(), colorProvider);
                int fillAlpha = selected ? 180 : 120;
                SdfShapeRenderer.fill(
                        context, x, y, width, height, 0.0F, withAlpha(regionColor, fillAlpha));
                float outlineWidth = selected ? 1.0F : 0.5F;
                int outlineColor = selected ? withAlpha(accent, 255) : -939524096;
                SdfShapeRenderer.drawOutline(
                        context, x, y, width, height, 0.0F, outlineWidth, outlineColor);
                if (showNumbers) {
                    String number = Integer.toString(region.number());
                    int numberWidth = BoldText.width(client.textRenderer, number);
                    float numberScale =
                            Math.min(
                                    0.54F,
                                    Math.min(
                                            (width - 1.0F) / Math.max(1, numberWidth),
                                            (height - 1.0F) / 9.0F));
                    matrices.pushMatrix();
                    matrices.translate(
                            x + (width - numberWidth * numberScale) * 0.5F,
                            y + (height - 9.0F * numberScale) * 0.5F);
                    matrices.scale(numberScale, numberScale);
                    int numberColor = selected ? -1 : -588582923;
                    BoldText.draw(context, client.textRenderer, number, 0, 0, numberColor);
                    matrices.popMatrix();
                }
            }

            if (currentRegion != null) {
                float playerX =
                        6.0F + (float) ((client.player.getX() + 225000.0) / 450000.0 * 144.0);
                float playerY =
                        20.0F + (float) ((client.player.getZ() + 225000.0) / 450000.0 * 144.0);
                double yaw = Math.toRadians(client.player.getYaw());
                SdfShapeRenderer.drawLines(
                        context,
                        List.of(
                                new SdfShapeRenderer.LineSegment(
                                        playerX,
                                        playerY,
                                        playerX - (float) Math.sin(yaw) * 5.0F,
                                        playerY + (float) Math.cos(yaw) * 5.0F,
                                        0.8F,
                                        -8039)));
                SdfShapeRenderer.fillCircle(context, playerX, playerY, 1.0F, -1);
            }

            int labelY = 168;
            if (showCoords) {
                String coords =
                        "X "
                                + (int) Math.floor(client.player.getX())
                                + "   Z "
                                + (int) Math.floor(client.player.getZ());
                BoldText.draw(context, client.textRenderer, coords, 7, 168, -7754819);
                labelY = 179;
            }

            String label;
            if (currentRegion == null) {
                label = overworld ? "Outside map" : "Overworld map";
            } else {
                label = currentRegion.region();
            }

            BoldText.draw(
                    context,
                    client.textRenderer,
                    label,
                    (156 - BoldText.width(client.textRenderer, label)) / 2,
                    labelY,
                    withAlpha(accent, 225));
        } catch (Throwable throwable) {
            matrices.popMatrix();
            throw throwable;
        }

        matrices.popMatrix();
    }

    private static int getRegionColor(String name, IntUnaryOperator colorProvider) {
        return switch (name) {
            case "EU Central" -> colorProvider.applyAsInt(0);
            case "EU West" -> colorProvider.applyAsInt(1);
            case "NA East" -> colorProvider.applyAsInt(2);
            case "NA West" -> colorProvider.applyAsInt(3);
            case "Asia" -> colorProvider.applyAsInt(4);
            case "Oceania" -> colorProvider.applyAsInt(5);
            default -> -2461482;
        };
    }

    private static int withAlpha(int color, int alpha) {
        return alpha << 24 | color & 16777215;
    }
}
