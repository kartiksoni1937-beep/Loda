package odinclient.module.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.hud.HudLayoutEditor;

@Environment(EnvType.CLIENT)
public final class InventoryHud extends Module {
    private static final int SLOTS_PER_ROW = 9;
    private static final int ROWS = 3;
    private static final int SLOT_STRIDE = 19;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 6;
    private static final int TITLE_HEIGHT = 15;
    private static final int SECTION_GAP = 4;
    private static final int PANEL_WIDTH = 182;
    private static final int TITLE_COLOR = -723974;
    private static final int MUTED_TEXT_COLOR = -5789512;
    private static final int PANEL_INNER_COLOR = 1053983;
    private static final int SLOT_COLOR = 1646896;
    private final Setting<Boolean> showTitle;
    private final Setting<Boolean> showHotbar;
    private final Setting<Boolean> showEmptySlots;
    private final Setting<Boolean> background;
    private final Setting<Integer> positionX;
    private final Setting<Integer> positionY;
    private final Setting<Double> scale;
    private final Setting<Double> backgroundOpacity;

    public InventoryHud(Category category) {
        super(category, "inventory-hud", "Shows inventory items and their exact slot positions.");
        this.title = "Inventory HUD";
        this.showTitle =
                this.addBooleanSetting(
                        "show-title", "Shows the Inventory title and occupied-slot count.", true);
        this.showHotbar =
                this.addBooleanSetting(
                        "show-hotbar", "Adds the nine hotbar slots below the inventory.", false);
        this.showEmptySlots =
                this.addBooleanSetting(
                        "show-empty-slots",
                        "Shows empty cells so every slot position stays visible.",
                        true);
        this.background =
                this.addBooleanSetting(
                        "background", "Draws the smooth OdinClient panel behind the items.", true);
        this.positionX =
                this.addPercentSetting(
                        "position-x",
                        "Horizontal HUD position as a percentage of the screen.",
                        2,
                        0,
                        100);
        this.positionY =
                this.addPercentSetting(
                        "position-y",
                        "Vertical HUD position as a percentage of the screen.",
                        66,
                        0,
                        100);
        this.scale =
                this.settings
                        .getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("scale")
                                        .description("Changes the size of the Inventory HUD.")
                                        .defaultValue(0.75)
                                        .range(0.5, 1.5)
                                        .sliderRange(0.5, 1.5)
                                        .build());
        this.backgroundOpacity =
                this.settings
                        .getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("background-opacity")
                                        .description(
                                                "Controls the opacity of the panel and empty"
                                                    + " slots.")
                                        .defaultValue(0.82)
                                        .range(0.0, 1.0)
                                        .sliderRange(0.0, 1.0)
                                        .build());
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.shouldRender()) {
            DrawContext context = event.context;
            float scaleFactor = Math.max(0.5F, Math.min(1.5F, this.scale.getValue().floatValue()));
            int panelHeight = this.getPanelHeight();
            int virtualWidth = Math.max(182, (int) (event.scaledWidth / scaleFactor));
            int virtualHeight = Math.max(panelHeight, (int) (event.scaledHeight / scaleFactor));
            int x = Math.round((virtualWidth - 182) * this.positionX.getValue() / 100.0F);
            int y = Math.round((virtualHeight - panelHeight) * this.positionY.getValue() / 100.0F);
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(scaleFactor, scaleFactor);

            try {
                int rangeX = Math.max(1, virtualWidth - 182);
                int rangeY = Math.max(1, virtualHeight - panelHeight);
                HudLayoutEditor.registerElement(
                        context,
                        "inventory-hud",
                        "Inventory HUD",
                        x,
                        y,
                        182.0F,
                        panelHeight,
                        (dx, dy) -> {
                            this.positionX.setValue(
                                    Math.max(
                                            0,
                                            Math.min(
                                                    100,
                                                    Math.round(
                                                            this.positionX.getValue().intValue()
                                                                    + dx
                                                                            / scaleFactor
                                                                            / rangeX
                                                                            * 100.0F))));
                            this.positionY.setValue(
                                    Math.max(
                                            0,
                                            Math.min(
                                                    100,
                                                    Math.round(
                                                            this.positionY.getValue().intValue()
                                                                    + dy
                                                                            / scaleFactor
                                                                            / rangeY
                                                                            * 100.0F))));
                        });
                this.drawHud(context, x, y, panelHeight);
            } finally {
                context.getMatrices().popMatrix();
            }
        }
    }

    private void drawHud(DrawContext context, int x, int y, int panelHeight) {
        int opacity =
                Math.max(
                        0,
                        Math.min(
                                255,
                                Math.round(
                                        this.backgroundOpacity.getValue().floatValue() * 255.0F)));
        int accent = ThemeModule.getAccentColorAt(0.5F);
        if (this.background.getValue() && opacity > 0) {
            if (ThemeModule.isPanelStyle()) {
                DashboardRenderer.drawRoundedHighlight(
                        context, x, y, 182, panelHeight, 7, withAlpha(658965, opacity));
            } else {
                DashboardRenderer.drawRoundedHighlight(
                        context,
                        x,
                        y,
                        182,
                        panelHeight,
                        6,
                        withAlpha(accent, Math.min(110, opacity)));
                DashboardRenderer.drawRoundedHighlight(
                        context,
                        x + 1,
                        y + 1,
                        180,
                        panelHeight - 2,
                        5,
                        withAlpha(1053983, opacity));
            }
        }

        int contentY = y + 6;
        if (this.showTitle.getValue()) {
            DashboardRenderer.drawBoldSmallText(
                    context, this.mc.textRenderer, "INVENTORY", x + 6, contentY, -723974);
            String countText = Integer.toString(this.countOccupiedSlots());
            DashboardRenderer.drawBoldSmallText(
                    context,
                    this.mc.textRenderer,
                    countText,
                    x + 182 - 6 - DashboardRenderer.boldSmallWidth(this.mc.textRenderer, countText),
                    contentY,
                    withAlpha(accent, 240));
            int lineY = contentY + 10;
            context.fill(
                    x + 6,
                    lineY,
                    x + 182 - 6,
                    lineY + 1,
                    ThemeModule.isPanelStyle() ? withAlpha(2830652, 110) : withAlpha(accent, 52));
            contentY += 15;
        }

        this.drawMainInventory(context, x + 6, contentY, 9);
        if (this.showHotbar.getValue()) {
            int hotbarY = contentY + 57 + 4;
            context.fill(
                    x + 6,
                    hotbarY - 3,
                    x + 182 - 6,
                    hotbarY - 2,
                    ThemeModule.isPanelStyle() ? withAlpha(2830652, 95) : withAlpha(accent, 38));
            this.drawHotbar(context, x + 6, hotbarY);
        }
    }

    private void drawMainInventory(DrawContext context, int x, int y, int firstSlot) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = firstSlot + row * 9 + column;
                this.drawSlot(
                        context,
                        this.mc.player.getInventory().getStack(slot),
                        x + column * 19,
                        y + row * 19,
                        false);
            }
        }
    }

    private void drawHotbar(DrawContext context, int x, int y) {
        int selectedSlot = this.mc.player.getInventory().getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            this.drawSlot(
                    context,
                    this.mc.player.getInventory().getStack(i),
                    x + i * 19,
                    y,
                    i == selectedSlot);
        }
    }

    private void drawSlot(DrawContext context, ItemStack stack, int x, int y, boolean selected) {
        boolean empty = stack == null || stack.isEmpty();
        if (this.background.getValue() && (this.showEmptySlots.getValue() || !empty)) {
            int alpha =
                    Math.max(
                            0,
                            Math.min(
                                    255,
                                    Math.round(
                                            this.backgroundOpacity.getValue().floatValue()
                                                    * (selected ? 190.0F : 112.0F))));
            int color =
                    selected
                            ? ThemeModule.getAccentColorAt(0.65F)
                            : (ThemeModule.isPanelStyle() ? 1185824 : 1646896);
            DashboardRenderer.drawRoundedHighlight(
                    context, x, y, 18, 18, 3, withAlpha(color, alpha));
        }

        if (!empty) {
            context.drawItem(stack, x + 1, y + 1);
            context.drawStackOverlay(this.mc.textRenderer, stack, x + 1, y + 1);
        }
    }

    private int countOccupiedSlots() {
        int count = 0;
        int start = this.showHotbar.getValue() ? 0 : 9;

        for (int i = start; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty()) {
                count++;
            }
        }

        return count;
    }

    private int getPanelHeight() {
        int height = 68;
        if (this.showTitle.getValue()) {
            height += 15;
        }

        if (this.showHotbar.getValue()) {
            height += 23;
        }

        return height;
    }

    private boolean shouldRender() {
        return this.mc.player != null
                && this.mc.world != null
                && this.mc.currentScreen == null
                && this.mc.options != null
                && !this.mc.getDebugHud().shouldShowDebugHud();
    }

    private Setting<Boolean> addBooleanSetting(
            String name, String description, boolean defaultValue) {
        return this.settings
                .getGeneral()
                .add(
                        new BooleanSetting.Builder()
                                .name(name)
                                .description(description)
                                .defaultValue(defaultValue)
                                .build());
    }

    private Setting<Integer> addPercentSetting(
            String name, String description, int defaultValue, int min, int max) {
        return this.settings
                .getGeneral()
                .add(
                        new IntSetting.Builder()
                                .name(name)
                                .description(description)
                                .defaultValue(defaultValue)
                                .range(min, max)
                                .sliderRange(min, max)
                                .build());
    }

    private static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
    }
}
