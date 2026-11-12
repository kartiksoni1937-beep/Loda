package odinclient.module.hud.regionmap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;

import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.module.hud.ThemeModule;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.draw.BoldText;
import odinclient.ui.scale.GuiScaling;
import odinclient.ui.screen.ClickGuiScreen;

@Environment(EnvType.CLIENT)
public final class RegionMap extends Module {
    private static final int GRID_SIZE = 9;
    private static final double REGION_SIZE = 50000.0;
    private static final double WORLD_HALF_EXTENT = 225000.0;
    private static final int TEXT_COLOR = -1380363;
    private static final int MUTED_TEXT_COLOR = -7760733;
    private static final String[] REGION_NAMES =
            new String[] {"EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"};
    private static final int[] DEFAULT_REGION_COLORS =
            new int[] {-6304157, -16734621, -11555350, -13668678, -671166, -227325};
    private static final int[][] REGION_GRID =
            new int[][] {
                {82, 5},
                {100, 3},
                {101, 3},
                {102, 3},
                {103, 2},
                {104, 2},
                {105, 2},
                {106, 2},
                {91, 2},
                {83, 5},
                {44, 3},
                {75, 3},
                {42, 3},
                {41, 2},
                {40, 2},
                {39, 2},
                {38, 2},
                {92, 2},
                {84, 5},
                {45, 3},
                {14, 3},
                {13, 3},
                {12, 2},
                {11, 2},
                {10, 2},
                {37, 2},
                {93, 2},
                {85, 5},
                {46, 5},
                {74, 5},
                {3, 3},
                {2, 2},
                {1, 2},
                {25, 2},
                {36, 2},
                {94, 2},
                {86, 4},
                {47, 4},
                {72, 4},
                {71, 4},
                {5, 2},
                {4, 2},
                {24, 2},
                {35, 2},
                {95, 2},
                {87, 4},
                {51, 1},
                {17, 1},
                {9, 0},
                {8, 0},
                {7, 0},
                {23, 0},
                {34, 0},
                {96, 2},
                {88, 4},
                {54, 1},
                {18, 1},
                {61, 0},
                {62, 0},
                {21, 0},
                {22, 0},
                {33, 0},
                {97, 0},
                {89, 0},
                {26, 1},
                {27, 0},
                {28, 0},
                {29, 0},
                {30, 0},
                {59, 0},
                {32, 0},
                {98, 0},
                {90, 0},
                {107, 1},
                {108, 1},
                {109, 1},
                {110, 1},
                {111, 1},
                {112, 1},
                {113, 1},
                {99, 0}
            };
    private final SettingGroup regionColorsGroup = this.settings.addGroup("Region Colors");
    private final Setting<Integer> xPosition =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("x-position")
                                    .description(
                                            "Horizontal position of the Region Map in percent.")
                                    .defaultValue(2)
                                    .range(0, 100)
                                    .sliderRange(0, 100)
                                    .build());
    private final Setting<Integer> yPosition =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("y-position")
                                    .description("Vertical position of the Region Map in percent.")
                                    .defaultValue(18)
                                    .range(0, 100)
                                    .sliderRange(0, 100)
                                    .build());
    private final Setting<Double> scale =
            this.settings
                    .getGeneral()
                    .add(
                            new NumberSetting.Builder()
                                    .name("scale")
                                    .description(
                                            "Scales the complete Region Map, including all text and"
                                                + " coordinates.")
                                    .defaultValue(1.0)
                                    .range(0.5, 1.35)
                                    .sliderRange(0.5, 1.35)
                                    .build());
    private final Setting<Boolean> showCoordinates =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("show-coordinates")
                                    .description("Shows your current X and Z below the map.")
                                    .defaultValue(true)
                                    .build());
    private final Setting<EntityColor> euCentralColor =
            this.regionColorsGroup.add(
                    new ColorSetting.Builder()
                            .name("eu-central-color")
                            .description("Color used for EU Central regions.")
                            .defaultValue(new EntityColor(159, 206, 99))
                            .build());
    private final Setting<EntityColor> euWestColor =
            this.regionColorsGroup.add(
                    new ColorSetting.Builder()
                            .name("eu-west-color")
                            .description("Color used for EU West regions.")
                            .defaultValue(new EntityColor(0, 166, 99))
                            .build());
    private final Setting<EntityColor> naEastColor =
            this.regionColorsGroup.add(
                    new ColorSetting.Builder()
                            .name("na-east-color")
                            .description("Color used for NA East regions.")
                            .defaultValue(new EntityColor(79, 173, 234))
                            .build());
    private final Setting<EntityColor> naWestColor =
            this.regionColorsGroup.add(
                    new ColorSetting.Builder()
                            .name("na-west-color")
                            .description("Color used for NA West regions.")
                            .defaultValue(new EntityColor(47, 110, 186))
                            .build());
    private final Setting<EntityColor> asiaColor =
            this.regionColorsGroup.add(
                    new ColorSetting.Builder()
                            .name("asia-color")
                            .description("Color used for Asia regions.")
                            .defaultValue(new EntityColor(245, 194, 66))
                            .build());
    private final Setting<EntityColor> oceaniaColor =
            this.regionColorsGroup.add(
                    new ColorSetting.Builder()
                            .name("oceania-color")
                            .description("Color used for Oceania regions.")
                            .defaultValue(new EntityColor(252, 136, 3))
                            .build());
    private final Setting<Boolean> showRegionNumbers =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("region-numbers")
                                    .description("Shows the Donut region number in every map cell.")
                                    .defaultValue(true)
                                    .build());
    private final Setting<Double> backgroundOpacity =
            this.settings
                    .getGeneral()
                    .add(
                            new NumberSetting.Builder()
                                    .name("background-opacity")
                                    .description("Opacity of the Region Map panel.")
                                    .defaultValue(0.74)
                                    .range(0.25, 1.0)
                                    .sliderRange(0.25, 1.0)
                                    .build());

    public RegionMap(Category category) {
        super(category, "region-map", "Shows the DonutSMP region layout and your exact position.");
        this.title = "Region Map";
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.mc.player != null
                && this.mc.world != null
                && this.mc.textRenderer != null
                && !(this.mc.currentScreen instanceof ClickGuiScreen)
                && !this.mc.getDebugHud().shouldShowDebugHud()) {
            float scale = GuiScaling.getScale();
            int width = Math.max(1, (int) Math.floor(event.scaledWidth / scale));
            int height = Math.max(1, (int) Math.floor(event.scaledHeight / scale));
            DrawContext context = event.context;
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(scale, scale);

            try {
                this.drawPanel(context, width, height);
            } finally {
                context.getMatrices().popMatrix();
            }
        }
    }

    private void drawPanel(DrawContext context, int screenWidth, int screenHeight) {
        int extraHeight = Boolean.TRUE.equals(this.showCoordinates.getValue()) ? 28 : 17;
        int panelHeight = 167 + extraHeight;
        float scale = Math.round(this.scale.getValue().floatValue() * 20.0F) / 20.0F;
        int scaledWidth = Math.max(1, Math.round(156 * scale));
        int scaledHeight = Math.max(1, Math.round(panelHeight * scale));
        int x =
                Math.round(
                        Math.max(0, screenWidth - scaledWidth)
                                * this.xPosition.getValue()
                                / 100.0F);
        int y =
                Math.round(
                        Math.max(0, screenHeight - scaledHeight)
                                * this.yPosition.getValue()
                                / 100.0F);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        try {
            this.drawMapPanel(context, 0, 0, 16, 144, extraHeight, 156, panelHeight);
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private void drawMapPanel(
            DrawContext context,
            int x,
            int y,
            int cellSize,
            int gridHeight,
            int extraHeight,
            int width,
            int height) {
        int accentStart = ThemeModule.getAccentColorAt(0.18F);
        int accentEnd = ThemeModule.getAccentColorAt(0.82F);
        int backgroundAlpha = (int) Math.round(this.backgroundOpacity.getValue() * 220.0);
        this.fillRoundedRect(context, x, y, width, height, 7, withAlpha(2437950, 115));
        this.fillRoundedRect(
                context,
                x + 1,
                y + 1,
                width - 2,
                height - 2,
                6,
                withAlpha(857116, backgroundAlpha));
        this.fillRoundedRect(context, x + 6, y + 3, width - 12, 2, 1, withAlpha(accentStart, 230));
        int gridX = x + 6;
        int gridY = y + 20;
        int[] cell = this.getRegionCell(this.mc.player.getX(), this.mc.player.getZ());
        int playerIndex = isInsideGrid(cell[0], cell[1]) ? cell[1] * 9 + cell[0] : -1;
        String badgeText = playerIndex >= 0 ? "#" + REGION_GRID[playerIndex][0] : "OUT";
        int badgeWidth = Math.max(22, BoldText.width(this.mc.textRenderer, badgeText) + 8);
        BoldText.draw(context, this.mc.textRenderer, "REGION MAP", x + 8, y + 8, -1380363);
        this.fillRoundedRect(
                context,
                x + width - badgeWidth - 6,
                y + 6,
                badgeWidth,
                11,
                4,
                withAlpha(accentEnd, 44));
        BoldText.draw(
                context,
                this.mc.textRenderer,
                badgeText,
                x
                        + width
                        - badgeWidth
                        - 6
                        + (badgeWidth - BoldText.width(this.mc.textRenderer, badgeText)) / 2,
                y + 7,
                withAlpha(accentEnd, 245));

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                int cellX = gridX + col * cellSize;
                int cellY = gridY + row * cellSize;
                int regionColor = this.getRegionColor(REGION_GRID[index][1]);
                boolean isPlayerCell = index == playerIndex;
                context.fill(
                        cellX,
                        cellY,
                        cellX + cellSize - 1,
                        cellY + cellSize - 1,
                        withAlpha(regionColor, isPlayerCell ? 118 : 52));
                context.fill(
                        cellX + 1,
                        cellY + 1,
                        cellX + cellSize - 2,
                        cellY + cellSize - 2,
                        withAlpha(593944, isPlayerCell ? 70 : 112));
                if (isPlayerCell) {
                    this.drawCellOutline(context, cellX, cellY, cellSize - 1, accentEnd);
                }

                if (Boolean.TRUE.equals(this.showRegionNumbers.getValue())) {
                    String number = Integer.toString(REGION_GRID[index][0]);
                    float textScale =
                            cellSize <= 9
                                    ? 0.28F
                                    : (cellSize <= 11
                                            ? 0.35F
                                            : (cellSize <= 13
                                                    ? 0.42F
                                                    : (number.length() >= 3 ? 0.48F : 0.54F)));
                    this.drawScaledText(
                            context,
                            number,
                            cellX
                                    + (cellSize
                                                    - 1
                                                    - Math.max(
                                                            1,
                                                            Math.round(
                                                                    BoldText.width(
                                                                                    this.mc
                                                                                            .textRenderer,
                                                                                    number)
                                                                            * textScale)))
                                            / 2,
                            cellY + Math.max(2, (cellSize - 5) / 2),
                            textScale,
                            isPlayerCell ? -1380363 : withAlpha(-1380363, 188));
                }
            }
        }

        if (playerIndex >= 0) {
            this.drawPlayerDot(context, gridX, gridY, cellSize, cell[0], cell[1], accentEnd);
        }

        int textY = gridY + gridHeight + 4;
        String regionName =
                playerIndex >= 0 ? REGION_NAMES[REGION_GRID[playerIndex][1]] : "OUTSIDE MAP";
        if (Boolean.TRUE.equals(this.showCoordinates.getValue())) {
            String coordinates =
                    "X "
                            + (int) Math.floor(this.mc.player.getX())
                            + "   Z "
                            + (int) Math.floor(this.mc.player.getZ());
            BoldText.draw(context, this.mc.textRenderer, coordinates, x + 7, textY, -7760733);
            textY += 11;
        }

        int nameWidth = BoldText.width(this.mc.textRenderer, regionName);
        BoldText.draw(
                context,
                this.mc.textRenderer,
                regionName,
                x + (width - nameWidth) / 2,
                textY,
                withAlpha(accentEnd, 225));
    }

    private void drawPlayerDot(
            DrawContext context,
            int gridX,
            int gridY,
            int cellSize,
            int cellCol,
            int cellRow,
            int color) {
        double shiftedX = this.mc.player.getX() + 225000.0;
        double shiftedZ = this.mc.player.getZ() + 225000.0;
        double fracX = shiftedX / 50000.0 - Math.floor(shiftedX / 50000.0);
        double fracZ = shiftedZ / 50000.0 - Math.floor(shiftedZ / 50000.0);
        this.fillCircle(
                context,
                gridX
                        + cellCol * cellSize
                        + clampInt((int) Math.round(fracX * (cellSize - 2)), 1, cellSize - 2),
                gridY
                        + cellRow * cellSize
                        + clampInt((int) Math.round(fracZ * (cellSize - 2)), 1, cellSize - 2),
                1,
                withAlpha(color, 255));
    }

    private void drawCellOutline(DrawContext context, int x, int y, int size, int color) {
        int outlineColor = withAlpha(color, 238);
        context.fill(x, y, x + size, y + 1, outlineColor);
        context.fill(x, y + size - 1, x + size, y + size, outlineColor);
        context.fill(x, y, x + 1, y + size, outlineColor);
        context.fill(x + size - 1, y, x + size, y + size, outlineColor);
    }

    private int[] getRegionCell(double x, double z) {
        return new int[] {
            (int) Math.floor((x + 225000.0) / 50000.0), (int) Math.floor((z + 225000.0) / 50000.0)
        };
    }

    private static boolean isInsideGrid(int col, int row) {
        return col >= 0 && col < 9 && row >= 0 && row < 9;
    }

    private int getRegionColor(int region) {
        EntityColor color =
                switch (Math.max(0, Math.min(5, region))) {
                    case 0 -> this.euCentralColor.getValue();
                    case 1 -> this.euWestColor.getValue();
                    case 2 -> this.naEastColor.getValue();
                    case 3 -> this.naWestColor.getValue();
                    case 4 -> this.asiaColor.getValue();
                    default -> this.oceaniaColor.getValue();
                };
        return color == null
                ? DEFAULT_REGION_COLORS[
                        Math.max(0, Math.min(DEFAULT_REGION_COLORS.length - 1, region))]
                : color.toArgb();
    }

    private void drawScaledText(
            DrawContext context, String text, int x, int y, float scale, int color) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        BoldText.draw(context, this.mc.textRenderer, text, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    private void fillCircle(DrawContext context, int centerX, int centerY, int radius, int color) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(0.5F, 0.5F);
        int cx = centerX * 2;
        int cy = centerY * 2;
        int r = radius * 2;
        double radiusSq = (double) r * r;

        for (int row = cy - r; row < cy + r; row++) {
            double dy = row + 0.5 - cy;
            double halfWidth = Math.sqrt(Math.max(0.0, radiusSq - dy * dy));
            context.fill(
                    (int) Math.ceil(cx - halfWidth),
                    row,
                    (int) Math.floor(cx + halfWidth),
                    row + 1,
                    color);
        }

        context.getMatrices().popMatrix();
    }

    private void fillRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(0.5F, 0.5F);
            this.fillRoundedRectScaled(
                    context, x * 2, y * 2, width * 2, height * 2, radius * 2, color);
            context.getMatrices().popMatrix();
        }
    }

    private void fillRoundedRectScaled(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            context.fill(x, y, x + width, y + height, color);
        } else {
            context.fill(x, y + r, x + width, y + height - r, color);

            for (int i = 0; i < r; i++) {
                float dy = r - i - 0.5F;
                float inset = r - (float) Math.sqrt(r * r - dy * dy);
                int left = (int) Math.ceil(x + inset);
                int right = (int) Math.floor(x + width - inset);
                if (right > left) {
                    context.fill(left, y + i, right, y + i + 1, color);
                    context.fill(left, y + height - i - 1, right, y + height - i, color);
                }
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return clampInt(alpha, 0, 255) << 24 | color & 16777215;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
