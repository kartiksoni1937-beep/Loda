package odinclient.module.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.manager.ModuleManager;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.draw.BoldText;
import odinclient.ui.scale.GuiScaling;
import odinclient.ui.screen.ClickGuiScreen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class StaffTracker extends Module {
    private static final String STAFF_ICON = "\ue000";
    private static final String OWNER_NAME = "DrDonutt";
    private static final long NANOS_PER_SECOND = 1000000000L;
    private static final int COLOR_GREEN = 5635925;
    private static final int COLOR_PURPLE = 11818495;
    private static final int COLOR_GOLD = 16766026;
    private static final int COLOR_GOLD_DARK = 6635526;
    private static final int COLOR_GOLD_LIGHT = 16773280;
    private static final int COLOR_GOLD_OUTLINE = 2365186;
    private static final int COLOR_TEXT = -1644564;
    private static final int COLOR_TEXT_MUTED = -6906715;
    private final Setting<Integer> xPosition =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("x-position")
                                    .description(
                                            "Horizontal position of the Staf Tracker in percent.")
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
                                    .description(
                                            "Vertical position of the Staf Tracker in percent.")
                                    .defaultValue(36)
                                    .range(0, 100)
                                    .sliderRange(0, 100)
                                    .build());
    private final Setting<Double> scale =
            this.settings
                    .getGeneral()
                    .add(
                            new NumberSetting.Builder()
                                    .name("scale")
                                    .description("Scale of the Staf Tracker overlay.")
                                    .defaultValue(1.0)
                                    .range(0.7, 1.5)
                                    .sliderRange(0.7, 1.5)
                                    .build());
    private final Setting<Double> backgroundOpacity =
            this.settings
                    .getGeneral()
                    .add(
                            new NumberSetting.Builder()
                                    .name("background-opacity")
                                    .description("Opacity of the clean HUD background.")
                                    .defaultValue(0.72)
                                    .range(0.2, 1.0)
                                    .sliderRange(0.2, 1.0)
                                    .build());
    private final Setting<Boolean> showWhenEmpty =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("show-when-empty")
                                    .description(
                                            "Keep the compact status tile visible when no staff is"
                                                + " listed.")
                                    .defaultValue(true)
                                    .build());
    private List<StaffTracker.StaffEntry> cachedStaff = List.of();
    private Object lastNetworkHandler;
    private long nextRefreshNanos;

    public StaffTracker(Category category) {
        super(
                category,
                "staf-tracker",
                "Shows DonutSMP staff from the existing client-side player list.");
        this.title = "Staff Tracker";
    }

    @Override
    public void onActivate() {
        this.nextRefreshNanos = 0L;
    }

    @Override
    public void onDeactivate() {
        this.cachedStaff = List.of();
        this.lastNetworkHandler = null;
        this.nextRefreshNanos = 0L;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        ClientPlayNetworkHandler networkHandler = this.mc.getNetworkHandler();
        if (networkHandler != null && this.mc.world != null) {
            long now = System.nanoTime();
            if (networkHandler != this.lastNetworkHandler) {
                this.lastNetworkHandler = networkHandler;
                this.nextRefreshNanos = 0L;
            }

            if (now >= this.nextRefreshNanos) {
                try {
                    this.refreshStaff();
                } catch (RuntimeException | LinkageError ignored) {
                    this.cachedStaff = List.of();
                }

                this.nextRefreshNanos = now + NANOS_PER_SECOND;
            }

            if (this.mc.player != null
                    && this.mc.options != null
                    && this.mc.textRenderer != null
                    && !(this.mc.currentScreen instanceof ClickGuiScreen)
                    && !this.mc.getDebugHud().shouldShowDebugHud()
                    && (!this.cachedStaff.isEmpty() || this.showWhenEmpty.getValue())) {
                DrawContext context = event.context;
                float userScale = this.scale.getValue().floatValue();
                float scaleFactor = GuiScaling.getScale() * userScale;
                int width = Math.max(1, (int) Math.floor(event.scaledWidth / scaleFactor));
                int height = Math.max(1, (int) Math.floor(event.scaledHeight / scaleFactor));
                context.getMatrices().pushMatrix();
                context.getMatrices().scale(scaleFactor, scaleFactor);

                try {
                    this.drawPanel(context, width, height);
                } finally {
                    context.getMatrices().popMatrix();
                }
            }
        } else {
            this.cachedStaff = List.of();
            this.lastNetworkHandler = null;
            this.nextRefreshNanos = 0L;
        }
    }

    private void refreshStaff() {
        if (this.mc.getNetworkHandler() == null) {
            this.cachedStaff = List.of();
        } else {
            List<StaffTracker.StaffEntry> staff = new ArrayList<>();

            for (PlayerListEntry listEntry :
                    this.mc.getNetworkHandler().getListedPlayerListEntries()) {
                try {
                    String name = listEntry.getProfile().name();
                    if (name != null && !name.isBlank()) {
                        Text displayName = listEntry.getDisplayName();
                        boolean isOwner = "DrDonutt".equalsIgnoreCase(name);
                        int color =
                                isOwner
                                        ? 16766026
                                        : (displayName == null
                                                ? 0
                                                : this.findStaffColor(displayName));
                        if (color != 0) {
                            int sortRank = isOwner ? 0 : (isPurpleColor(color) ? 1 : 2);
                            staff.add(
                                    new StaffTracker.StaffEntry(
                                            name,
                                            listEntry.getSkinTextures(),
                                            color,
                                            sortRank,
                                            isOwner));
                        }
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
            }

            staff.sort(
                    Comparator.<StaffTracker.StaffEntry>comparingInt(e -> e.sortRank)
                            .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
            this.cachedStaff = List.copyOf(staff);
        }
    }

    private int findStaffColor(Text text) {
        return text.<Integer>visit(
                        (style, content) -> {
                            if (!hasStaffIcon(content)) {
                                return Optional.empty();
                            }

                            TextColor textColor = style.getColor();
                            if (textColor == null) {
                                return Optional.empty();
                            }

                            int rgb = textColor.getRgb() & 16777215;
                            return !isGreenColor(rgb) && !isPurpleColor(rgb)
                                    ? Optional.empty()
                                    : Optional.of(rgb);
                        },
                        Style.EMPTY)
                .orElse(0);
    }

    private static boolean hasStaffIcon(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        } else if (text.contains("\ue000")) {
            return true;
        } else {
            int index = 0;

            while (index < text.length()) {
                int codePoint = text.codePointAt(index);
                index += Character.charCount(codePoint);
                if (codePoint == 9733
                        || codePoint == 9734
                        || codePoint == 10022
                        || codePoint == 10023
                        || codePoint == 10026
                        || codePoint == 10029
                        || codePoint == 10038
                        || codePoint == 10039
                        || codePoint == 10040
                        || codePoint == 10041) {
                    return true;
                }
            }

            return false;
        }
    }

    private static boolean isGreenColor(int color) {
        if (color == 5635925 || color == 43520) {
            return true;
        }

        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        return green >= 130 && green >= red + 35 && green >= blue + 20;
    }

    private static boolean isPurpleColor(int color) {
        if (color == 11818495 || color == 16733695 || color == 11141290) {
            return true;
        }

        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        return red >= 110 && blue >= 120 && red >= green + 25 && blue >= green + 30;
    }

    private void drawPanel(DrawContext context, int screenWidth, int screenHeight) {
        int rows = Math.max(1, this.cachedStaff.size());
        String countText = Integer.toString(this.cachedStaff.size());
        int titleWidth = this.getTextWidth("ONLINE STAFF");
        int badgeWidth = Math.max(14, this.getTextWidth(countText) + 8);
        int panelWidth = Math.max(112, titleWidth + badgeWidth + 23);
        if (this.cachedStaff.isEmpty()) {
            panelWidth = Math.max(panelWidth, this.getTextWidth("NO STAFF ONLINE") + 27);
        } else {
            for (StaffTracker.StaffEntry staffEntry : this.cachedStaff) {
                panelWidth = Math.max(panelWidth, 42 + this.getTextWidth(staffEntry.name));
            }
        }

        int panelHeight = 20 + rows * 18;
        int x =
                Math.round(
                        Math.max(0, screenWidth - panelWidth) * this.xPosition.getValue() / 100.0F);
        int y =
                Math.round(
                        Math.max(0, screenHeight - panelHeight)
                                * this.yPosition.getValue()
                                / 100.0F);
        int accent = this.getAccentColor(0.0F);
        int backgroundAlpha = (int) Math.round(this.backgroundOpacity.getValue() * 215.0);
        this.drawRoundedRect(context, x, y, panelWidth, panelHeight, 6, withAlpha(4345951, 118));
        this.drawRoundedRect(
                context,
                x + 1,
                y + 1,
                panelWidth - 2,
                panelHeight - 2,
                5,
                withAlpha(856859, backgroundAlpha));
        this.drawRoundedRect(context, x + 5, y + 3, panelWidth - 10, 2, 1, withAlpha(accent, 245));
        this.drawText(
                context, "ONLINE STAFF", x + 8, y + 8, this.getTextAccentColor(0.12F, -1644564));
        int badgeX = x + panelWidth - badgeWidth - 5;
        this.drawRoundedRect(
                context,
                badgeX,
                y + 6,
                badgeWidth,
                11,
                3,
                withAlpha(this.getAccentColor(0.35F), 48));
        this.drawText(
                context,
                countText,
                badgeX + (badgeWidth - this.getTextWidth(countText)) / 2,
                y + 7,
                withAlpha(this.getAccentColor(0.45F), 235));
        context.fill(
                x + 6,
                y + 18,
                x + panelWidth - 6,
                y + 19,
                withAlpha(this.getAccentColor(0.6F), 48));
        int rowY = y + 20;
        if (this.cachedStaff.isEmpty()) {
            this.drawRowBackground(context, x, rowY, panelWidth, 0);
            this.drawRoundedRect(
                    context, x + 9, rowY + 7, 4, 4, 2, withAlpha(this.getAccentColor(0.82F), 205));
            this.drawText(
                    context,
                    "NO STAFF ONLINE",
                    x + 18,
                    rowY + 5,
                    this.getTextAccentColor(1.0F, -6906715));
        } else {
            int rowIndex = 0;

            for (StaffTracker.StaffEntry entry : this.cachedStaff) {
                this.drawRowBackground(context, x, rowY, panelWidth, rowIndex);
                this.drawRoundedRect(
                        context, x + 5, rowY + 4, 2, 9, 1, withAlpha(entry.color, 225));
                this.drawRoundedRect(
                        context, x + 9, rowY + 2, 14, 14, 3, withAlpha(entry.color, 58));
                PlayerSkinDrawer.draw(context, entry.skinTextures, x + 10, rowY + 3, 12);
                if (entry.isOwner) {
                    this.drawCrown(context, x + 10, rowY + 1);
                }

                this.drawText(
                        context,
                        entry.name,
                        x + 27,
                        rowY + 5,
                        entry.isOwner ? withAlpha(16766026, 255) : -1644564);
                this.drawRoundedRect(
                        context,
                        x + panelWidth - 12,
                        rowY + 7,
                        5,
                        5,
                        2,
                        withAlpha(entry.color, 220));
                rowY += 18;
                rowIndex++;
            }
        }
    }

    private void drawCrown(DrawContext context, int x, int y) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(0.5F, 0.5F);
        int originX = x * 2 - 1;
        int originY = y * 2 - 2;
        int[] xs = new int[] {0, 1, 4, 7, 10, 12, 14, 17, 20, 23, 24};
        int[] ys = new int[] {10, 4, 8, 2, 8, 0, 8, 2, 8, 4, 10};

        for (int i = 0; i < xs.length - 1; i++) {
            this.drawThickLine(
                    context,
                    originX + xs[i],
                    originY + ys[i] + 1,
                    originX + xs[i + 1],
                    originY + ys[i + 1] + 1,
                    3,
                    withAlpha(2365186, 235));
        }

        for (int j = 0; j < xs.length - 1; j++) {
            this.drawThickLine(
                    context,
                    originX + xs[j],
                    originY + ys[j],
                    originX + xs[j + 1],
                    originY + ys[j + 1],
                    2,
                    withAlpha(16766026, 255));
        }

        context.fill(originX, originY + 9, originX + 24, originY + 14, withAlpha(2365186, 235));
        context.fill(originX + 1, originY + 8, originX + 23, originY + 12, withAlpha(6635526, 255));
        context.fill(
                originX + 2, originY + 8, originX + 22, originY + 10, withAlpha(16766026, 255));
        context.fill(
                originX + 3, originY + 9, originX + 21, originY + 10, withAlpha(16773280, 230));
        context.fill(
                originX + 2, originY + 12, originX + 22, originY + 13, withAlpha(16766026, 245));
        this.drawCrownJewel(context, originX + 1, originY + 4, withAlpha(16773280, 255));
        this.drawCrownJewel(context, originX + 7, originY + 2, withAlpha(16773280, 255));
        this.drawCrownJewel(context, originX + 12, originY, withAlpha(16773280, 255));
        this.drawCrownJewel(context, originX + 17, originY + 2, withAlpha(16773280, 255));
        this.drawCrownJewel(context, originX + 23, originY + 4, withAlpha(16773280, 255));
        context.fill(originX + 6, originY + 10, originX + 8, originY + 12, -2474683);
        context.fill(originX + 11, originY + 10, originX + 13, originY + 12, -11555350);
        context.fill(originX + 16, originY + 10, originX + 18, originY + 12, -2474683);
        context.getMatrices().popMatrix();
    }

    private void drawThickLine(
            DrawContext context, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        int radius = Math.max(0, (thickness - 1) / 2);
        if (steps == 0) {
            context.fill(x1 - radius, y1 - radius, x1 + radius + 1, y1 + radius + 1, color);
        } else {
            for (int i = 0; i <= steps; i++) {
                int px = Math.round(x1 + dx * ((float) i / steps));
                int py = Math.round(y1 + dy * ((float) i / steps));
                context.fill(px - radius, py - radius, px + radius + 1, py + radius + 1, color);
            }
        }
    }

    private void drawCrownJewel(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 2, y + 2, color);
    }

    private void drawRowBackground(DrawContext context, int x, int y, int width, int index) {
        int alpha = index % 2 == 0 ? 24 : 18;
        this.drawRoundedRect(context, x + 4, y + 1, width - 8, 16, 4, withAlpha(8293016, alpha));
        this.drawRoundedRect(context, x + 5, y + 2, width - 10, 14, 3, withAlpha(1120288, 118));
    }

    private void drawBackground(DrawContext context, int x, int y, int width, int height) {
        int alpha = (int) Math.round(this.backgroundOpacity.getValue() * 205.0);
        this.drawRoundedRect(
                context,
                x,
                y,
                width,
                height,
                Math.max(2, Math.min(3, height / 3)),
                withAlpha(1120288, alpha));
    }

    private void drawRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(0.5F, 0.5F);
            this.drawRoundedRectScaled(
                    context, x * 2, y * 2, width * 2, height * 2, radius * 2, color);
            context.getMatrices().popMatrix();
        }
    }

    private void drawRoundedRectScaled(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int requestedRadius,
            int color) {
        int radius = Math.max(0, Math.min(requestedRadius, Math.min(width, height) / 2));
        if (radius == 0) {
            context.fill(x, y, x + width, y + height, color);
        } else {
            context.fill(x, y + radius, x + width, y + height - radius, color);

            for (int i = 0; i < radius; i++) {
                float dy = radius - i - 0.5F;
                float inset = radius - (float) Math.sqrt(radius * radius - dy * dy);
                this.drawRoundedRow(context, x, y + i, width, inset, color);
                this.drawRoundedRow(context, x, y + height - i - 1, width, inset, color);
            }
        }
    }

    private void drawRoundedRow(
            DrawContext context, int x, int y, int width, float inset, int color) {
        float left = x + inset;
        float right = x + width - inset;
        int startX = (int) Math.ceil(left);
        int endX = (int) Math.floor(right);
        if (endX > startX) {
            context.fill(startX, y, endX, y + 1, color);
        }

        int alpha = color >>> 24;
        float leftCoverage = startX - left;
        if (leftCoverage > 0.001F) {
            int leftAlpha = Math.max(1, Math.round(alpha * leftCoverage));
            context.fill(startX - 1, y, startX, y + 1, withAlpha(color, leftAlpha));
        }

        float rightCoverage = right - endX;
        if (rightCoverage > 0.001F) {
            context.fill(
                    endX,
                    y,
                    endX + 1,
                    y + 1,
                    withAlpha(color, Math.max(1, Math.round(alpha * rightCoverage))));
        }
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        BoldText.draw(context, this.mc.textRenderer, text, x, y, color);
    }

    private int getTextWidth(String text) {
        return Math.max(1, BoldText.width(this.mc.textRenderer, text));
    }

    private int getFallbackAccentColor() {
        return -7475216;
    }

    private int getAccentColor(float progress) {
        return this.isRainbowTheme()
                ? 0xFF000000 | lerpColor(5744895, 16014289, progress)
                : this.getFallbackAccentColor();
    }

    private int getTextAccentColor(float progress, int fallback) {
        return this.isRainbowTheme()
                ? 0xFF000000 | lerpColor(5744895, 16014289, progress)
                : fallback;
    }

    private boolean isRainbowTheme() {
        Module theme = ModuleManager.get().getByName("theme");
        return theme instanceof ThemeModule
                && Boolean.TRUE.equals(((ThemeModule) theme).rainbow.getValue());
    }

    private static int lerpColor(int from, int to, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        return Math.round((from >> 16 & 0xFF) * (1.0F - t) + (to >> 16 & 0xFF) * t) << 16
                | Math.round((from >> 8 & 0xFF) * (1.0F - t) + (to >> 8 & 0xFF) * t) << 8
                | Math.round((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
    }

    private static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
    }

    private static final class StaffEntry {
        private final String name;
        private final SkinTextures skinTextures;
        private final int color;
        private final int sortRank;
        private final boolean isOwner;

        private StaffEntry(
                String name, SkinTextures skinTextures, int color, int sortRank, boolean isOwner) {
            this.name = name;
            this.skinTextures = skinTextures;
            this.color = color;
            this.sortRank = sortRank;
            this.isOwner = isOwner;
        }
    }
}
