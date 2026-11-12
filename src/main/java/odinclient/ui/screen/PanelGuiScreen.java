package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import odinclient.manager.ConfigProfileManager;
import odinclient.manager.ModuleManager;
import odinclient.manager.PanelLayoutStore;
import odinclient.module.Module;
import odinclient.module.gamble.GambleRigger;
import odinclient.module.hud.ThemeModule;
import odinclient.module.shortcut.ChatMacros;
import odinclient.render.EntityColor;
import odinclient.setting.HudPositionSettings;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BlockColorMapSetting;
import odinclient.setting.type.BlockListSetting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.EntityTypeListSetting;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.KeybindSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.draw.BoldText;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.ScreenScaleCompat;
import odinclient.ui.widget.GuiKeybind;
import odinclient.ui.widget.SettingTooltipRenderer;
import odinclient.util.input.KeyNames;

import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PanelGuiScreen extends Screen {
    private static final List<String> CATEGORIES =
            List.of("combat", "render", "misc", "donut", "client", "scamming");
    private static final int PANEL_TOP_OFFSET = 43;
    private static final int PANEL_GAP = 6;
    private static final int PANEL_HEADER_HEIGHT = 25;
    private static final int MODULE_ROW_HEIGHT = 22;
    private static final int[] ACCENT_PRESETS =
            new int[] {-6599169, -14447617, -15284326, -41846, -22997, -4669223};
    private final Screen parent;
    private final Map<Module, Boolean> expandedModules = new IdentityHashMap<>();
    private final Map<Module, Float> expandAnimations = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> settingToggleAnimations = new IdentityHashMap<>();
    private final Map<String, Float> labelToggleAnimations = new HashMap<>();
    private final Map<String, Float> scrollOffsets = new HashMap<>();
    private final Map<String, Float> maxScrolls = new HashMap<>();
    private final Map<String, PanelGuiScreen.Rect> panelBounds = new HashMap<>();
    private final Map<String, PanelLayoutStore.PanelPosition> panelPositions =
            PanelLayoutStore.load();
    private final List<String> panelOrder = new ArrayList<>(CATEGORIES);
    private final List<PanelGuiScreen.ModuleRow> moduleRows = new ArrayList<>();
    private final List<PanelGuiScreen.SettingRow> settingRows = new ArrayList<>();
    private final SettingTooltipRenderer tooltipRenderer = new SettingTooltipRenderer();
    private final List<PanelGuiScreen.ConfigRow> configRows = new ArrayList<>();
    private final List<PanelGuiScreen.Rect> accentSwatches = new ArrayList<>();
    private float guiScale = 1.0F;
    private long lastFrameNanos = System.nanoTime();
    private float frameDelta = 0.016666668F;
    private String searchQuery = "";
    private boolean searchFocused;
    private Setting<?> draggedSlider;
    private PanelGuiScreen.Rect draggedSliderBounds = PanelGuiScreen.Rect.EMPTY;
    private KeybindSetting listeningKeybind;
    private boolean listeningGuiKey;
    private Setting<?> editingSetting;
    private String editBuffer = "";
    private PanelGuiScreen.Overlay overlay = PanelGuiScreen.Overlay.NONE;
    private boolean newConfigDialogOpen;
    private String newConfigName = "";
    private int selectedConfigIndex = -1;
    private String status = "";
    private boolean statusSuccess;
    private long statusExpiry;
    private long resetConfirmExpiry;
    private PanelGuiScreen.Rect searchBounds = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect toolbarBounds = PanelGuiScreen.Rect.EMPTY;
    private String draggingPanel;
    private boolean draggingToolbar;
    private int dragOffsetX;
    private int dragOffsetY;
    private PanelGuiScreen.Rect themesButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect configsButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect guiKeyButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect closeButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect overlayCloseButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect dashboardStyleButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect panelsStyleButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect rainbowOption = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect animationsOption = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect msaaOption = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect customColorButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect newConfigButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect importButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect odinConfigButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect shareButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect resetButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect saveNewConfigButton = PanelGuiScreen.Rect.EMPTY;
    private PanelGuiScreen.Rect cancelNewConfigButton = PanelGuiScreen.Rect.EMPTY;

    public PanelGuiScreen(Screen parent) {
        super(Text.literal("OdinClient Panels"));
        this.parent = parent;

        for (String category : CATEGORIES) {
            this.scrollOffsets.put(category, 0.0F);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.draggingPanel != null || this.draggingToolbar) {
            PanelLayoutStore.save(this.panelPositions);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.guiScale = ScreenScaleCompat.getScale(this.width) * 0.8F;
        int scaledMouseX = Math.round(mouseX / this.guiScale);
        int scaledMouseY = Math.round(mouseY / this.guiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(this.guiScale, this.guiScale);
        this.updateAnimations();
        this.moduleRows.clear();
        this.settingRows.clear();
        int accent = ThemeModule.getAccentColorAt(0.5F);
        context.fill(0, 0, this.getScaledWidth(), this.getScaledHeight(), -1861941493);
        this.drawPanels(context, scaledMouseX, scaledMouseY, accent);
        this.drawToolbar(context, scaledMouseX, scaledMouseY, accent);
        if (this.overlay == PanelGuiScreen.Overlay.THEMES) {
            this.drawThemesOverlay(context, scaledMouseX, scaledMouseY, accent);
        }

        if (this.overlay == PanelGuiScreen.Overlay.CONFIGS) {
            this.drawConfigsOverlay(context, scaledMouseX, scaledMouseY, accent);
        }

        context.getMatrices().popMatrix();
        this.tooltipRenderer.render(
                context,
                this.getHoveredSetting(scaledMouseX, scaledMouseY),
                mouseX,
                mouseY,
                this.width,
                this.height,
                this.guiScale,
                1.0F);
    }

    private Setting<?> getHoveredSetting(int mouseX, int mouseY) {
        if (this.overlay == PanelGuiScreen.Overlay.NONE
                && !this.newConfigDialogOpen
                && this.draggedSlider == null
                && this.draggingPanel == null
                && !this.draggingToolbar
                && this.editingSetting == null
                && this.listeningKeybind == null
                && !this.listeningGuiKey
                && !this.toolbarBounds.contains(mouseX, mouseY)) {
            String panel = this.getPanelAt(mouseX, mouseY);

            for (PanelGuiScreen.SettingRow row : this.settingRows) {
                if (panel != null
                        && panel.equals(getCategoryName(row.module))
                        && row.row.contains(mouseX, mouseY)) {
                    return row.setting;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private int getScaledWidth() {
        return Math.round(ScreenScaleCompat.getScaledWidth() / 0.8F);
    }

    private int getScaledHeight() {
        return Math.round(ScreenScaleCompat.getScaledHeight() / 0.8F);
    }

    private void drawToolbar(DrawContext context, int mouseX, int mouseY, int accent) {
        int width = Math.min(525, this.getScaledWidth() - 24);
        int left = (this.getScaledWidth() - width) / 2;
        int top = 9;
        boolean compact = this.getScaledWidth() < 520;
        int height = compact ? 54 : 28;
        PanelLayoutStore.PanelPosition saved = this.panelPositions.get("toolbar");
        if (saved != null) {
            left = clampPosition(saved.x(), this.getScaledWidth() - width);
            top = clampPosition(saved.y(), this.getScaledHeight() - height);
        }

        this.toolbarBounds = new PanelGuiScreen.Rect(left, top, width, height);
        DashboardRenderer.drawRoundedHighlight(
                context, left + 1, top + 3, width, height, 9, alpha(-16777216, 72));
        DashboardRenderer.drawRoundedRect(context, left, top, width, height, 9, -200667885);
        this.drawText(context, "ODINCLIENT", left + 11, top + 10, -854792);
        int closeSize = 24;
        this.closeButton = new PanelGuiScreen.Rect(left + width - 28, top + 3, closeSize, 22);
        this.guiKeyButton = new PanelGuiScreen.Rect(this.closeButton.x - 72, top + 3, 65, 22);
        if (compact) {
            this.themesButton = new PanelGuiScreen.Rect(left + 10, top + 29, 61, 22);
            this.configsButton = new PanelGuiScreen.Rect(left + 78, top + 29, 62, 22);
            int searchX = left + 147;
            this.searchBounds =
                    new PanelGuiScreen.Rect(
                            searchX, top + 29, Math.max(32, left + width - 10 - searchX), 22);
        } else {
            this.configsButton = new PanelGuiScreen.Rect(this.guiKeyButton.x - 69, top + 3, 62, 22);
            this.themesButton = new PanelGuiScreen.Rect(this.configsButton.x - 68, top + 3, 61, 22);
            int searchLeft = left + 98;
            this.searchBounds =
                    new PanelGuiScreen.Rect(
                            searchLeft,
                            top + 3,
                            Math.max(90, this.themesButton.x - searchLeft - 8),
                            22);
        }

        this.drawButton(
                context,
                this.themesButton,
                "THEMES",
                mouseX,
                mouseY,
                this.overlay == PanelGuiScreen.Overlay.THEMES,
                accent);
        this.drawButton(
                context,
                this.configsButton,
                "CONFIGS",
                mouseX,
                mouseY,
                this.overlay == PanelGuiScreen.Overlay.CONFIGS,
                accent);
        String keyLabel =
                this.listeningGuiKey
                        ? "PRESS..."
                        : "KEY " + KeyNames.getKeyName(GuiKeybind.getKeyCode());
        this.drawButton(
                context, this.guiKeyButton, keyLabel, mouseX, mouseY, this.listeningGuiKey, accent);
        this.drawButton(context, this.closeButton, "X", mouseX, mouseY, false, accent);
        boolean searchHovered = this.searchBounds.contains(mouseX, mouseY);
        DashboardRenderer.drawRoundedRect(
                context,
                this.searchBounds.x,
                this.searchBounds.y,
                this.searchBounds.w,
                this.searchBounds.h,
                7,
                !searchHovered && !this.searchFocused ? -15723493 : -15394269);
        String searchText =
                this.searchQuery.isEmpty()
                        ? "Search modules..."
                        : this.searchQuery + (this.searchFocused && isCaretVisible() ? "_" : "");
        this.drawText(
                context,
                this.truncateText(searchText, this.searchBounds.w - 18),
                this.searchBounds.x + 9,
                this.searchBounds.y + 7,
                this.searchQuery.isEmpty() ? -9932672 : -1841428);
    }

    private void drawPanels(DrawContext context, int mouseX, int mouseY, int accent) {
        PanelGuiScreen.PanelLayout layout = this.computeLayout();
        int panelWidth = layout.panelWidth;
        int[] rowHeights = new int[(CATEGORIES.size() + layout.columns - 1) / layout.columns];
        List<List<Module>> categoryModules = new ArrayList<>(CATEGORIES.size());
        int[] contentHeights = new int[CATEGORIES.size()];

        for (int i = 0; i < CATEGORIES.size(); i++) {
            List<Module> modules = this.modules(CATEGORIES.get(i));
            categoryModules.add(modules);
            contentHeights[i] = this.getPanelContentHeight(modules);
            rowHeights[i / layout.columns] =
                    Math.max(
                            rowHeights[i / layout.columns],
                            Math.min(layout.maxPanelHeight, contentHeights[i]));
        }

        this.panelBounds.clear();

        for (String category : this.panelOrder) {
            int index = CATEGORIES.indexOf(category);
            int column = index % layout.columns;
            int rowIndex = index / layout.columns;
            int panelY = layout.startY;

            for (int r = 0; r < rowIndex; r++) {
                panelY += rowHeights[r] + 6;
            }

            int panelX = layout.startX + column * (panelWidth + 6);
            int panelHeight = Math.max(37, Math.min(layout.maxPanelHeight, contentHeights[index]));
            PanelLayoutStore.PanelPosition saved = this.panelPositions.get(category);
            if (saved != null) {
                panelX = clampPosition(saved.x(), this.getScaledWidth() - panelWidth);
                panelY = clampPosition(saved.y(), this.getScaledHeight() - 25);
            }

            this.panelBounds.put(
                    category, new PanelGuiScreen.Rect(panelX, panelY, panelWidth, panelHeight));
            int backgroundAlpha = 236;
            DashboardRenderer.drawRoundedHighlight(
                    context, panelX + 1, panelY + 4, panelWidth, panelHeight, 9, 1006632960);
            DashboardRenderer.drawRoundedRect(
                    context,
                    panelX,
                    panelY,
                    panelWidth,
                    panelHeight,
                    7,
                    alpha(-16118251, backgroundAlpha));
            DashboardRenderer.drawRoundedRect(
                    context,
                    panelX + 1,
                    panelY + 1,
                    panelWidth - 2,
                    25,
                    6,
                    alpha(-15656928, backgroundAlpha));
            drawCategoryIcon(context, category, panelX + 9, panelY + 8, -170073368);
            this.drawText(
                    context, category.toUpperCase(Locale.ROOT), panelX + 24, panelY + 8, -854792);
            List<Module> panelModules = categoryModules.get(index);
            this.drawRightAlignedText(
                    context,
                    Integer.toString(countActiveModules(panelModules)),
                    panelX + panelWidth - 9,
                    panelY + 8,
                    -7760993);
            int contentX = panelX + 5;
            int contentY = panelY + 25 + 3;
            int contentWidth = panelWidth - 10;
            int contentHeight = panelHeight - 25 - 6;
            float scroll = this.scrollOffsets.getOrDefault(category, 0.0F);
            int rowY = contentY - Math.round(scroll);
            context.enableScissor(
                    contentX, contentY, contentX + contentWidth, contentY + contentHeight);

            for (Module module : panelModules) {
                float expandProgress = this.expandAnimations.getOrDefault(module, 0.0F);
                int expandedHeight = expandProgress <= 0.01F ? 0 : this.getExpandedHeight(module);
                int rowHeight = 22 + Math.round(expandedHeight * expandProgress);
                if (rowY + rowHeight >= contentY && rowY <= contentY + contentHeight) {
                    this.drawModuleRow(
                            context,
                            module,
                            contentX,
                            rowY,
                            contentWidth,
                            expandProgress,
                            mouseX,
                            mouseY,
                            accent,
                            contentY,
                            contentHeight);
                }

                rowY += rowHeight + 2;
            }

            context.disableScissor();
            float maxScroll = Math.max(0, rowY + Math.round(scroll) - contentY - contentHeight);
            this.maxScrolls.put(category, maxScroll);
            this.scrollOffsets.put(category, clampFloat(scroll, 0.0F, maxScroll));
            if (maxScroll > 1.0F) {
                int thumbHeight =
                        Math.max(
                                18,
                                Math.round(
                                        contentHeight
                                                * contentHeight
                                                / (contentHeight + maxScroll)));
                int thumbY =
                        contentY + Math.round((contentHeight - thumbHeight) * (scroll / maxScroll));
                DashboardRenderer.drawRoundedRect(
                        context, panelX + panelWidth - 3, thumbY, 2, thumbHeight, 1, -11182743);
            }
        }
    }

    private void drawModuleRow(
            DrawContext context,
            Module module,
            int x,
            int y,
            int width,
            float expandProgress,
            int mouseX,
            int mouseY,
            int accent,
            int clipY,
            int clipHeight) {
        PanelGuiScreen.Rect bounds = new PanelGuiScreen.Rect(x, y, width, 22);
        boolean hovered = bounds.contains(mouseX, mouseY);
        int background =
                module.isActive()
                        ? blendColors(-15064536, accent, 0.18F)
                        : (hovered ? -15262428 : -15657187);
        this.drawRoundedRectSnapped(context, x, y, width, 22, 4, background);
        this.drawText(
                context,
                this.truncateText(module.title, width - 28),
                x + 7,
                y + 7,
                module.isActive() ? -1577239 : -5524290);
        drawChevron(context, x + width - 13, y + 11, expandProgress > 0.5F, -6576721);
        PanelGuiScreen.Rect visible =
                bounds.intersection(new PanelGuiScreen.Rect(x, clipY, width, clipHeight));
        if (!visible.empty()) {
            this.moduleRows.add(new PanelGuiScreen.ModuleRow(visible, module));
        }

        if (!(expandProgress <= 0.01F)) {
            int settingsY = y + 22;
            int settingsHeight = Math.round(this.getExpandedHeight(module) * expandProgress);
            int clipTop = Math.max(clipY, settingsY);
            int clipBottom = Math.min(clipY + clipHeight, settingsY + settingsHeight);
            if (clipBottom > clipTop) {
                context.enableScissor(x, clipTop, x + width, clipBottom);
                int rowY = settingsY + 2;

                for (Setting<?> setting : this.getVisibleSettings(module)) {
                    int rowHeight = getSettingRowHeight(setting);
                    this.drawSettingRow(
                            context,
                            module,
                            setting,
                            x + 4,
                            rowY,
                            width - 8,
                            rowHeight,
                            mouseX,
                            mouseY,
                            accent,
                            clipTop,
                            clipBottom);
                    rowY += rowHeight + 2;
                }

                context.disableScissor();
            }
        }
    }

    private void drawSettingRow(
            DrawContext context,
            Module module,
            Setting<?> setting,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            int accent,
            int clipY,
            int clipBottom) {
        PanelGuiScreen.Rect bounds = new PanelGuiScreen.Rect(x, y, width, height);
        boolean hovered = bounds.contains(mouseX, mouseY);
        this.drawRoundedRectSnapped(
                context, x, y, width, height, 4, hovered ? -14998998 : -15393757);
        boolean slider = setting instanceof IntSetting || setting instanceof NumberSetting;
        boolean toggle = setting instanceof BooleanSetting || setting instanceof ColorSetting;
        int controlWidth = toggle ? 20 : (slider ? 34 : Math.min(65, Math.max(40, width / 2)));
        PanelGuiScreen.Rect control =
                new PanelGuiScreen.Rect(
                        x + width - controlWidth - 5,
                        y + 4,
                        controlWidth,
                        Math.min(20, height - 8));
        int labelWidth = slider ? width - 48 : control.x - x - 10;
        String label = this.truncateText(setting.title, Math.max(25, labelWidth));
        this.drawText(context, label, x + 7, y + 7, -3682345);
        if (setting instanceof BooleanSetting booleanSetting) {
            boolean enabled = Boolean.TRUE.equals(booleanSetting.getValue());
            float progress = this.animateSettingToggle(booleanSetting, enabled);
            int toggleX = control.x + control.w - 18;
            int toggleY = y + (height - 8) / 2;
            this.drawRoundedRectSnapped(
                    context,
                    toggleX,
                    toggleY,
                    18,
                    8,
                    4,
                    blendColors(-13616829, accent, progress * 0.78F));
            drawToggleKnob(context, toggleX + 1 + progress * 10.0F, toggleY + 1, -920585);
        } else if (setting instanceof IntSetting intSetting) {
            this.drawRightAlignedText(
                    context,
                    Integer.toString(intSetting.getValue()),
                    x + width - 7,
                    y + 7,
                    -3287588);
            PanelGuiScreen.Rect intSlider =
                    new PanelGuiScreen.Rect(x + 7, y + height - 9, width - 14, 4);
            this.drawSlider(
                    context,
                    intSlider,
                    getSliderFraction(
                            intSetting.getValue().intValue(),
                            intSetting.sliderMin,
                            intSetting.sliderMax),
                    accent);
            control = intSlider;
        } else if (setting instanceof NumberSetting numberSetting) {
            this.drawRightAlignedText(
                    context,
                    formatDecimal(numberSetting.getValue(), getDecimalPlaces(numberSetting)),
                    x + width - 7,
                    y + 7,
                    -3287588);
            PanelGuiScreen.Rect numberSlider =
                    new PanelGuiScreen.Rect(x + 7, y + height - 9, width - 14, 4);
            this.drawSlider(
                    context,
                    numberSlider,
                    getSliderFraction(
                            numberSetting.getValue(),
                            numberSetting.sliderMin,
                            numberSetting.sliderMax),
                    accent);
            control = numberSlider;
        } else if (setting instanceof KeybindSetting keybindSetting) {
            String keyLabel =
                    keybindSetting == this.listeningKeybind
                            ? "PRESS..."
                            : KeyNames.getKeyName(keybindSetting.getValue());
            this.drawValueBox(
                    context, control, keyLabel, keybindSetting == this.listeningKeybind, accent);
        } else if (setting instanceof ColorSetting colorSetting) {
            EntityColor entityColor = colorSetting.getValue();
            int swatchColor =
                    entityColor == null ? accent : 0xFF000000 | entityColor.toArgb() & 16777215;
            DashboardRenderer.drawRoundedHighlight(
                    context,
                    control.x + control.w - 11,
                    y + (height - 8) / 2,
                    8,
                    8,
                    4,
                    swatchColor);
        } else if (setting instanceof EnumSetting enumSetting) {
            this.drawValueBox(
                    context, control, formatEnumName(enumSetting.getValue()), false, accent);
        } else if (setting instanceof BlockColorMapSetting blockColorMapSetting) {
            this.drawValueBox(
                    context,
                    control,
                    blockColorMapSetting.getValue().size() + " BLOCKS",
                    false,
                    accent);
        } else if (setting instanceof BlockListSetting blockListSetting) {
            this.drawValueBox(
                    context,
                    control,
                    blockListSetting.getValue().size() + " BLOCKS",
                    false,
                    accent);
        } else if (setting instanceof EntityTypeListSetting entityTypeListSetting) {
            this.drawValueBox(
                    context,
                    control,
                    entityTypeListSetting.getValue().size() + " ENTITIES",
                    false,
                    accent);
        } else {
            String valueText =
                    setting == this.editingSetting
                            ? this.editBuffer + (isCaretVisible() ? "_" : "")
                            : String.valueOf(setting.getValue());
            if ("macro-manager".equals(setting.name)
                    || "dropper-1".equals(setting.name)
                    || "dropper-2".equals(setting.name)) {
                valueText = "OPEN";
            }

            if (module instanceof GambleRigger gambleRigger
                    && GambleRigger.isSelectSetting(setting.name)) {
                valueText = gambleRigger.getSelectButtonLabel(setting.name);
            }

            this.drawValueBox(
                    context,
                    control,
                    this.truncateText(valueText, control.w - 10),
                    setting == this.editingSetting,
                    accent);
        }

        PanelGuiScreen.Rect visibleBounds =
                bounds.intersection(
                        new PanelGuiScreen.Rect(x, clipY, width, Math.max(0, clipBottom - clipY)));
        PanelGuiScreen.Rect visibleControl = control.intersection(visibleBounds);
        if (!visibleBounds.empty()) {
            this.settingRows.add(
                    new PanelGuiScreen.SettingRow(visibleBounds, visibleControl, module, setting));
        }
    }

    private void drawThemesOverlay(DrawContext context, int mouseX, int mouseY, int accent) {
        context.fill(0, 0, this.getScaledWidth(), this.getScaledHeight(), -1459617792);
        int width = Math.min(470, this.getScaledWidth() - 28);
        int height = Math.min(285, this.getScaledHeight() - 24);
        int left = (this.getScaledWidth() - width) / 2;
        int top = (this.getScaledHeight() - height) / 2;
        this.drawOverlayBackground(context, left, top, width, height);
        this.drawText(context, "THEMES & GUI", left + 18, top + 17, -788999);
        this.drawText(
                context,
                "Choose the menu layout and visual behavior.",
                left + 18,
                top + 33,
                -8551020);
        this.overlayCloseButton = new PanelGuiScreen.Rect(left + width - 37, top + 12, 24, 24);
        this.drawButton(context, this.overlayCloseButton, "X", mouseX, mouseY, false, accent);
        this.drawText(context, "GUI STYLE", left + 18, top + 62, -8024676);
        int optionWidth = (width - 43) / 2;
        this.dashboardStyleButton = new PanelGuiScreen.Rect(left + 18, top + 78, optionWidth, 44);
        this.panelsStyleButton =
                new PanelGuiScreen.Rect(
                        this.dashboardStyleButton.x + optionWidth + 7, top + 78, optionWidth, 44);
        this.drawStyleOption(
                context,
                this.dashboardStyleButton,
                "DASHBOARD",
                "Full layout",
                ThemeModule.getGuiStyle() == ThemeModule.GuiStyle.DASHBOARD,
                mouseX,
                mouseY,
                accent);
        this.drawStyleOption(
                context,
                this.panelsStyleButton,
                "PANELS",
                "Compact columns",
                ThemeModule.getGuiStyle() == ThemeModule.GuiStyle.PANELS,
                mouseX,
                mouseY,
                accent);
        ThemeModule theme = this.getThemeModule();
        this.drawText(context, "ACCENT", left + 18, top + 139, -8024676);
        this.accentSwatches.clear();

        for (int i = 0; i < ACCENT_PRESETS.length; i++) {
            PanelGuiScreen.Rect swatch =
                    new PanelGuiScreen.Rect(left + 18 + i * 34, top + 156, 24, 24);
            this.accentSwatches.add(swatch);
            int currentColor =
                    theme != null && theme.color.getValue() != null
                            ? theme.color.getValue().toArgb() & 16777215
                            : 0;
            boolean selected = currentColor == (ACCENT_PRESETS[i] & 16777215);
            if (selected) {
                DashboardRenderer.drawRoundedRect(
                        context, swatch.x - 2, swatch.y - 2, 28, 28, 9, -920585);
            }

            DashboardRenderer.drawRoundedRect(
                    context, swatch.x, swatch.y, swatch.w, swatch.h, 7, ACCENT_PRESETS[i]);
        }

        this.customColorButton =
                new PanelGuiScreen.Rect(left + 18 + ACCENT_PRESETS.length * 34, top + 156, 104, 24);
        this.drawButton(context, this.customColorButton, "CUSTOM", mouseX, mouseY, false, accent);
        if (theme != null && theme.color.getValue() != null) {
            DashboardRenderer.drawRoundedHighlight(
                    context,
                    this.customColorButton.x + 7,
                    this.customColorButton.y + 8,
                    8,
                    8,
                    4,
                    0xFF000000 | theme.color.getValue().toArgb() & 16777215);
        }

        int optionY = top + 199;
        int toggleWidth = (width - 50) / 3;
        this.rainbowOption = new PanelGuiScreen.Rect(left + 18, optionY, toggleWidth, 42);
        this.animationsOption =
                new PanelGuiScreen.Rect(
                        this.rainbowOption.x + toggleWidth + 7, optionY, toggleWidth, 42);
        this.msaaOption =
                new PanelGuiScreen.Rect(
                        this.animationsOption.x + toggleWidth + 7, optionY, toggleWidth, 42);
        this.drawToggleOption(
                context,
                this.rainbowOption,
                "RAINBOW",
                theme != null && Boolean.TRUE.equals(theme.rainbow.getValue()),
                mouseX,
                mouseY,
                accent);
        this.drawToggleOption(
                context,
                this.animationsOption,
                "ANIMATIONS",
                theme == null || Boolean.TRUE.equals(theme.animations.getValue()),
                mouseX,
                mouseY,
                accent);
        this.drawToggleOption(
                context,
                this.msaaOption,
                "MSAA",
                theme != null && Boolean.TRUE.equals(theme.msaa.getValue()),
                mouseX,
                mouseY,
                accent);
        this.drawText(context, "GUI KEY", left + 18, top + height - 26, -8024676);
        this.drawRightAlignedText(
                context,
                KeyNames.getKeyName(GuiKeybind.getKeyCode()),
                left + width - 18,
                top + height - 26,
                -3221794);
    }

    private void drawConfigsOverlay(DrawContext context, int mouseX, int mouseY, int accent) {
        context.fill(0, 0, this.getScaledWidth(), this.getScaledHeight(), -1459617792);
        int width = Math.min(510, this.getScaledWidth() - 28);
        int height = Math.min(344, this.getScaledHeight() - 24);
        int left = (this.getScaledWidth() - width) / 2;
        int top = (this.getScaledHeight() - height) / 2;
        this.drawOverlayBackground(context, left, top, width, height);
        this.drawText(context, "CONFIG LIBRARY", left + 18, top + 17, -788999);
        this.drawText(
                context,
                "Save, load and share complete OdinClient setups.",
                left + 18,
                top + 33,
                -8551020);
        this.overlayCloseButton = new PanelGuiScreen.Rect(left + width - 37, top + 12, 24, 24);
        this.drawButton(context, this.overlayCloseButton, "X", mouseX, mouseY, false, accent);
        this.configRows.clear();
        List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();
        int rowY = top + 61;

        for (int i = 0; i < Math.min(3, profiles.size()); i++) {
            ConfigProfileManager.Profile profile = profiles.get(i);
            PanelGuiScreen.Rect row = new PanelGuiScreen.Rect(left + 16, rowY, width - 32, 48);
            DashboardRenderer.drawRoundedRect(
                    context,
                    row.x,
                    row.y,
                    row.w,
                    row.h,
                    7,
                    i == this.selectedConfigIndex
                            ? blendColors(-15591648, accent, 0.18F)
                            : -15657442);
            this.drawText(
                    context,
                    this.truncateText(profile.name.toUpperCase(Locale.ROOT), row.w - 175),
                    row.x + 12,
                    row.y + 10,
                    -1512720);
            boolean active =
                    profile.name.equalsIgnoreCase(ConfigProfileManager.getActiveProfileName());
            this.drawText(
                    context,
                    active ? "ACTIVE" : "LOCAL CONFIG",
                    row.x + 12,
                    row.y + 27,
                    active ? accent : -9340535);
            PanelGuiScreen.Rect load =
                    new PanelGuiScreen.Rect(row.x + row.w - 126, row.y + 11, 34, 26);
            PanelGuiScreen.Rect save =
                    new PanelGuiScreen.Rect(row.x + row.w - 87, row.y + 11, 34, 26);
            PanelGuiScreen.Rect delete =
                    new PanelGuiScreen.Rect(row.x + row.w - 48, row.y + 11, 34, 26);
            this.drawButton(context, load, "L", mouseX, mouseY, false, accent);
            this.drawButton(context, save, "S", mouseX, mouseY, false, accent);
            this.drawButton(context, delete, "X", mouseX, mouseY, false, accent);
            this.configRows.add(new PanelGuiScreen.ConfigRow(row, load, save, delete, i));
            rowY += 55;
        }

        if (profiles.isEmpty()) {
            this.drawCenteredText(
                    context, "NO LOCAL CONFIGS YET", left + width / 2, top + 103, -9143157);
        }

        int buttonY = top + height - 75;
        int buttonWidth = (width - 46) / 5;
        this.newConfigButton = new PanelGuiScreen.Rect(left + 16, buttonY, buttonWidth, 27);
        this.importButton =
                new PanelGuiScreen.Rect(
                        this.newConfigButton.x + buttonWidth + 3, buttonY, buttonWidth, 27);
        this.odinConfigButton =
                new PanelGuiScreen.Rect(
                        this.importButton.x + buttonWidth + 3, buttonY, buttonWidth, 27);
        this.shareButton =
                new PanelGuiScreen.Rect(
                        this.odinConfigButton.x + buttonWidth + 3, buttonY, buttonWidth, 27);
        this.resetButton =
                new PanelGuiScreen.Rect(
                        this.shareButton.x + buttonWidth + 3, buttonY, buttonWidth, 27);
        this.drawButton(context, this.newConfigButton, "NEW", mouseX, mouseY, false, accent);
        this.drawButton(context, this.importButton, "IMPORT", mouseX, mouseY, false, accent);
        this.drawButton(context, this.odinConfigButton, "ODIN", mouseX, mouseY, false, accent);
        this.drawButton(context, this.shareButton, "SHARE", mouseX, mouseY, false, accent);
        this.drawButton(
                context,
                this.resetButton,
                System.currentTimeMillis() < this.resetConfirmExpiry ? "CONFIRM" : "RESET",
                mouseX,
                mouseY,
                false,
                accent);
        if (!this.status.isEmpty() && System.currentTimeMillis() < this.statusExpiry) {
            this.drawText(
                    context,
                    this.truncateText(this.status, width - 35),
                    left + 18,
                    top + height - 24,
                    this.statusSuccess ? -10296399 : -39288);
        }

        if (this.newConfigDialogOpen) {
            this.drawNewConfigDialog(context, left, top, width, height, mouseX, mouseY, accent);
        }
    }

    private void drawNewConfigDialog(
            DrawContext context,
            int left,
            int top,
            int width,
            int height,
            int mouseX,
            int mouseY,
            int accent) {
        context.fill(left, top, left + width, top + height, -1124073472);
        int dialogWidth = Math.min(340, width - 42);
        int dialogX = left + (width - dialogWidth) / 2;
        int dialogY = top + (height - 124) / 2;
        DashboardRenderer.drawRoundedRect(
                context, dialogX, dialogY, dialogWidth, 124, 10, -15657442);
        this.drawText(context, "NEW CONFIG", dialogX + 16, dialogY + 15, -854792);
        PanelGuiScreen.Rect field =
                new PanelGuiScreen.Rect(dialogX + 16, dialogY + 42, dialogWidth - 32, 28);
        DashboardRenderer.drawRoundedRect(
                context, field.x, field.y, field.w, field.h, 7, -16184045);
        String text =
                this.newConfigName.isEmpty()
                        ? "Config name..."
                        : this.newConfigName + (isCaretVisible() ? "_" : "");
        this.drawText(
                context,
                this.truncateText(text, field.w - 16),
                field.x + 8,
                field.y + 10,
                this.newConfigName.isEmpty() ? -10327431 : -1644306);
        this.cancelNewConfigButton =
                new PanelGuiScreen.Rect(dialogX + dialogWidth - 164, dialogY + 84, 70, 25);
        this.saveNewConfigButton =
                new PanelGuiScreen.Rect(dialogX + dialogWidth - 86, dialogY + 84, 70, 25);
        this.drawButton(
                context, this.cancelNewConfigButton, "CANCEL", mouseX, mouseY, false, accent);
        this.drawButton(context, this.saveNewConfigButton, "SAVE", mouseX, mouseY, true, accent);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x() / this.guiScale;
        double mouseY = click.y() / this.guiScale;
        int button = click.button();
        if (this.listeningKeybind != null && button >= 2 && button <= 7) {
            this.listeningKeybind.setValue(1000 + button);
            this.listeningKeybind = null;
            return true;
        } else if (this.listeningGuiKey && button >= 2 && button <= 7) {
            GuiKeybind.setting().setValue(1000 + button);
            this.listeningGuiKey = false;
            return true;
        } else if (this.overlay == PanelGuiScreen.Overlay.THEMES) {
            return this.handleThemesClick(mouseX, mouseY, button);
        } else if (this.overlay == PanelGuiScreen.Overlay.CONFIGS) {
            return this.handleConfigsClick(mouseX, mouseY, button);
        } else if (this.closeButton.contains(mouseX, mouseY) && button == 0) {
            this.close();
            return true;
        } else if (this.themesButton.contains(mouseX, mouseY) && button == 0) {
            this.overlay = PanelGuiScreen.Overlay.THEMES;
            this.searchFocused = false;
            return true;
        } else if (this.configsButton.contains(mouseX, mouseY) && button == 0) {
            this.overlay = PanelGuiScreen.Overlay.CONFIGS;
            this.searchFocused = false;
            return true;
        } else if (this.guiKeyButton.contains(mouseX, mouseY)) {
            if (button == 1) {
                GuiKeybind.setting().setValue(-1);
            }

            if (button == 0) {
                this.listeningGuiKey = true;
            }

            return true;
        } else if (this.searchBounds.contains(mouseX, mouseY) && button == 0) {
            this.searchFocused = true;
            return true;
        } else if (button == 0 && this.toolbarBounds.contains(mouseX, mouseY)) {
            this.draggingToolbar = true;
            this.dragOffsetX = (int) Math.round(mouseX - this.toolbarBounds.x);
            this.dragOffsetY = (int) Math.round(mouseY - this.toolbarBounds.y);
            return true;
        } else {
            this.searchFocused = false;
            if (this.editingSetting != null) {
                this.finishTextEdit(true);
            }

            String panel = this.getPanelAt(mouseX, mouseY);
            if (panel != null && button == 0) {
                PanelGuiScreen.Rect bounds = this.panelBounds.get(panel);
                if (bounds != null && mouseY < bounds.y + 25) {
                    this.draggingPanel = panel;
                    this.dragOffsetX = (int) Math.round(mouseX - bounds.x);
                    this.dragOffsetY = (int) Math.round(mouseY - bounds.y);
                    this.panelOrder.remove(panel);
                    this.panelOrder.add(panel);
                    return true;
                }
            }

            for (int i = this.settingRows.size() - 1; i >= 0; i--) {
                PanelGuiScreen.SettingRow settingRow = this.settingRows.get(i);
                if (panel != null
                        && panel.equals(getCategoryName(settingRow.module))
                        && settingRow.row.contains(mouseX, mouseY)) {
                    return this.handleSettingClick(settingRow, mouseX, button);
                }
            }

            for (int j = this.moduleRows.size() - 1; j >= 0; j--) {
                PanelGuiScreen.ModuleRow moduleRow = this.moduleRows.get(j);
                if (panel != null
                        && panel.equals(getCategoryName(moduleRow.module))
                        && moduleRow.row.contains(mouseX, mouseY)) {
                    if (button == 1) {
                        this.expandedModules.put(
                                moduleRow.module,
                                !this.expandedModules.getOrDefault(moduleRow.module, false));
                    } else if (button == 0) {
                        moduleRow.module.toggle();
                    }

                    return true;
                }
            }

            return true;
        }
    }

    private boolean handleSettingClick(PanelGuiScreen.SettingRow row, double mouseX, int button) {
        Setting<?> setting = row.setting;
        if (button == 0
                && row.module instanceof GambleRigger dropperRigger
                && ("dropper-1".equals(setting.name) || "dropper-2".equals(setting.name))) {
            DropperEditorScreen.open(this, dropperRigger, "dropper-1".equals(setting.name) ? 0 : 1);
        } else if (button == 0
                && row.module instanceof GambleRigger selectRigger
                && GambleRigger.isSelectSetting(setting.name)) {
            selectRigger.beginSelection(setting.name, this);
        } else if (button == 0
                && "macro-manager".equals(setting.name)
                && row.module instanceof ChatMacros chatMacros) {
            ChatMacroScreen.open(this, chatMacros);
        } else if (button == 0 && "edit-position".equals(setting.name)) {
            this.openPositionEditor(row.module);
        } else if (button == 0
                && row.module.name.equals("sky-arts")
                && setting instanceof EnumSetting skyEnum
                && (setting.name.equals("artwork") || setting.name.equals("sky"))) {
            ArtworkGalleryScreen.open(this, skyEnum);
        } else if (setting instanceof BooleanSetting booleanSetting && button == 0) {
            booleanSetting.setValue(!Boolean.TRUE.equals(booleanSetting.getValue()));
        } else if (!(setting instanceof IntSetting) && !(setting instanceof NumberSetting)) {
            if (setting instanceof KeybindSetting keybindSetting) {
                if (button == 1) {
                    keybindSetting.setValue(-1);
                }

                if (button == 0) {
                    this.listeningKeybind = keybindSetting;
                }
            } else if (setting instanceof ColorSetting colorSetting && button == 0) {
                ColorPickerScreen.open(this, colorSetting);
            } else if (setting instanceof BlockColorMapSetting blockColorMapSetting
                    && button == 0) {
                SelectorScreen.open(this, blockColorMapSetting);
            } else if (setting instanceof BlockListSetting blockListSetting && button == 0) {
                SelectorScreen.open(this, blockListSetting);
            } else if (setting instanceof EntityTypeListSetting entityTypeListSetting
                    && button == 0) {
                SelectorScreen.open(this, entityTypeListSetting);
            } else if (setting instanceof EnumSetting enumSetting) {
                cycleEnum(enumSetting, button == 1 ? -1 : 1);
            } else if (button == 0) {
                this.editingSetting = setting;
                this.editBuffer = String.valueOf(setting.getValue());
            }
        } else if (button == 0) {
            this.draggedSlider = setting;
            this.draggedSliderBounds = row.control;
            this.updateSliderValue(mouseX);
        }

        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mouseX = click.x() / this.guiScale;
        double mouseY = click.y() / this.guiScale;
        if (this.draggingToolbar) {
            this.panelPositions.put(
                    "toolbar",
                    new PanelLayoutStore.PanelPosition(
                            clampPosition(
                                    (int) Math.round(mouseX) - this.dragOffsetX,
                                    this.getScaledWidth() - this.toolbarBounds.w),
                            clampPosition(
                                    (int) Math.round(mouseY) - this.dragOffsetY,
                                    this.getScaledHeight() - this.toolbarBounds.h)));
        } else if (this.draggingPanel != null) {
            PanelGuiScreen.Rect bounds = this.panelBounds.get(this.draggingPanel);
            if (bounds != null) {
                this.panelPositions.put(
                        this.draggingPanel,
                        new PanelLayoutStore.PanelPosition(
                                clampPosition(
                                        (int) Math.round(mouseX) - this.dragOffsetX,
                                        this.getScaledWidth() - bounds.w),
                                clampPosition(
                                        (int) Math.round(mouseY) - this.dragOffsetY,
                                        this.getScaledHeight() - 25)));
            }
        } else if (this.draggedSlider != null) {
            this.updateSliderValue(mouseX);
        }

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.draggingPanel != null || this.draggingToolbar) {
            PanelLayoutStore.save(this.panelPositions);
        }

        this.draggingPanel = null;
        this.draggingToolbar = false;
        this.draggedSlider = null;
        return true;
    }

    private String getPanelAt(double mouseX, double mouseY) {
        for (int i = this.panelOrder.size() - 1; i >= 0; i--) {
            String name = this.panelOrder.get(i);
            PanelGuiScreen.Rect bounds = this.panelBounds.get(name);
            if (bounds != null && bounds.contains(mouseX, mouseY)) {
                return name;
            }
        }

        return null;
    }

    private static int clampPosition(int value, int max) {
        return Math.max(0, Math.min(value, Math.max(0, max)));
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        mouseX /= this.guiScale;
        mouseY /= this.guiScale;
        if (this.overlay != PanelGuiScreen.Overlay.NONE) {
            return true;
        } else if (this.toolbarBounds.contains(mouseX, mouseY)) {
            return true;
        } else {
            String panel = this.getPanelAt(mouseX, mouseY);
            if (panel != null) {
                float maxScroll = this.maxScrolls.getOrDefault(panel, 0.0F);
                float scroll =
                        this.scrollOffsets.getOrDefault(panel, 0.0F)
                                + (float) (-verticalAmount * 31.0);
                this.scrollOffsets.put(panel, clampFloat(scroll, 0.0F, maxScroll));
            }

            return true;
        }
    }

    private PanelGuiScreen.PanelLayout computeLayout() {
        int margin = 12;
        int top = this.getScaledWidth() < 520 ? 70 : 43;
        int columns = this.getScaledWidth() >= 780 ? 5 : (this.getScaledWidth() >= 480 ? 3 : 2);
        int rows = (CATEGORIES.size() + columns - 1) / columns;
        int availableWidth =
                Math.max(columns, this.getScaledWidth() - margin * 2 - 6 * (columns - 1));
        int availableHeight =
                Math.max(rows * 37, this.getScaledHeight() - top - 12 - 6 * (rows - 1));
        int panelWidth = Math.min(148, Math.max(1, availableWidth / columns));
        int panelHeight = Math.max(37, availableHeight / rows);
        int totalWidth = panelWidth * columns + 6 * (columns - 1);
        return new PanelGuiScreen.PanelLayout(
                (this.getScaledWidth() - totalWidth) / 2, top, columns, panelWidth, panelHeight);
    }

    private int getPanelContentHeight(List<Module> modules) {
        int height = 31;

        for (Module module : modules) {
            float expandProgress = this.expandAnimations.getOrDefault(module, 0.0F);
            height +=
                    22
                            + (expandProgress <= 0.01F
                                    ? 0
                                    : Math.round(this.getExpandedHeight(module) * expandProgress))
                            + 2;
        }

        return height + 2;
    }

    private static void drawCategoryIcon(
            DrawContext context, String category, int x, int y, int color) {
        switch (category) {
            case "combat":
                for (int m = 0; m < 6; m++) {
                    drawIconPixel(context, x + 9 - m, y + m, color);
                }

                drawIconPixel(context, x + 10, y - 1, color);
                drawIconPixel(context, x + 3, y + 4, color);
                drawIconPixel(context, x + 6, y + 7, color);
                drawIconPixel(context, x + 2, y + 8, color);
                break;
            case "render":
                for (int k = 0; k < 3; k++) {
                    drawIconPixel(context, x + k, y + 4 - k, color);
                    drawIconPixel(context, x + 8 - k, y + 2 + k, color);
                }

                for (int l = 0; l < 3; l++) {
                    drawIconPixel(context, x + l, y + 4 + l, color);
                    drawIconPixel(context, x + 8 - l, y + 6 - l, color);
                }

                DashboardRenderer.drawRoundedHighlight(context, x + 3, y + 3, 3, 3, 2, color);
                break;
            case "misc":
                for (int i = 0; i < 8; i++) {
                    drawIconPixel(context, x + i, y + i, color);
                }

                for (int j = 0; j < 4; j++) {
                    drawIconPixel(context, x + 7 - j, y + j, color);
                }
                break;
            case "donut":
                DashboardRenderer.drawRing(context, x + 5, y + 5, 5, 2, color, 0);
                break;
            case "scamming":
                drawIconPixel(context, x + 4, y, color);
                drawIconPixel(context, x + 2, y + 3, color);
                drawIconPixel(context, x + 6, y + 3, color);
                drawIconPixel(context, x + 4, y + 6, color);
                break;
            default:
                DashboardRenderer.drawRoundedHighlight(context, x + 1, y + 1, 8, 7, 2, color);
                context.fill(x + 3, y + 3, x + 7, y + 6, -15656928);
                drawIconPixel(context, x + 4, y + 9, color);
                drawIconPixel(context, x + 5, y + 9, color);
        }
    }

    private static void drawIconPixel(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 2, y + 2, color);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        int scancode = input.scancode();
        if (this.listeningGuiKey) {
            GuiKeybind.setting().setValue(isClearKey(key, scancode) ? -1 : key);
            this.listeningGuiKey = false;
            return true;
        } else if (this.listeningKeybind != null) {
            this.listeningKeybind.setValue(isClearKey(key, scancode) ? -1 : key);
            this.listeningKeybind = null;
            return true;
        } else if (this.newConfigDialogOpen) {
            if (key == 256) {
                this.newConfigDialogOpen = false;
                this.newConfigName = "";
            } else if (key == 257 || key == 335) {
                this.createConfig();
            } else if (key == 259 && !this.newConfigName.isEmpty()) {
                this.newConfigName =
                        this.newConfigName.substring(0, this.newConfigName.length() - 1);
            }

            return true;
        } else if (this.editingSetting == null) {
            if (this.searchFocused) {
                if (key == 256) {
                    this.searchFocused = false;
                } else if (key == 259 && !this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                }

                return true;
            } else {
                if (key == 256) {
                    if (this.overlay != PanelGuiScreen.Overlay.NONE) {
                        this.overlay = PanelGuiScreen.Overlay.NONE;
                    } else {
                        this.close();
                    }
                }

                return true;
            }
        } else {
            if (key == 257 || key == 335) {
                this.finishTextEdit(true);
            } else if (key == 256) {
                this.finishTextEdit(false);
            } else if (key == 259 && !this.editBuffer.isEmpty()) {
                this.editBuffer = this.editBuffer.substring(0, this.editBuffer.length() - 1);
            }

            return true;
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) {
            return true;
        } else {
            String typed = input.asString();
            if (this.newConfigDialogOpen && this.newConfigName.length() < 24) {
                this.newConfigName = limitLength(this.newConfigName + typed, 24);
            } else if (this.editingSetting != null && this.editBuffer.length() < 256) {
                this.editBuffer = limitLength(this.editBuffer + typed, 256);
            } else if (this.searchFocused && this.searchQuery.length() < 48) {
                this.searchQuery = limitLength(this.searchQuery + typed, 48);
            }

            return true;
        }
    }

    private boolean handleThemesClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        } else if (this.overlayCloseButton.contains(mouseX, mouseY)) {
            this.overlay = PanelGuiScreen.Overlay.NONE;
            return true;
        } else {
            ThemeModule theme = this.getThemeModule();
            if (theme == null) {
                return true;
            } else {
                if (this.dashboardStyleButton.contains(mouseX, mouseY)) {
                    theme.guiStyle.setValue(ThemeModule.GuiStyle.DASHBOARD);
                    MinecraftClient.getInstance().setScreen(new ClickGuiScreen(this.parent));
                } else if (this.panelsStyleButton.contains(mouseX, mouseY)) {
                    theme.guiStyle.setValue(ThemeModule.GuiStyle.PANELS);
                } else if (this.rainbowOption.contains(mouseX, mouseY)) {
                    theme.rainbow.setValue(!Boolean.TRUE.equals(theme.rainbow.getValue()));
                } else if (this.animationsOption.contains(mouseX, mouseY)) {
                    theme.animations.setValue(!Boolean.TRUE.equals(theme.animations.getValue()));
                } else if (this.msaaOption.contains(mouseX, mouseY)) {
                    theme.msaa.setValue(!Boolean.TRUE.equals(theme.msaa.getValue()));
                } else if (this.customColorButton.contains(mouseX, mouseY)) {
                    ColorPickerScreen.openWithCallback(
                            this,
                            theme.color.getValue(),
                            color -> {
                                theme.color.setValue(color);
                                theme.rainbow.setValue(false);
                            });
                } else {
                    for (int i = 0; i < this.accentSwatches.size(); i++) {
                        if (this.accentSwatches.get(i).contains(mouseX, mouseY)) {
                            int preset = ACCENT_PRESETS[i];
                            theme.color.setValue(
                                    new EntityColor(
                                            preset >> 16 & 0xFF,
                                            preset >> 8 & 0xFF,
                                            preset & 0xFF));
                            theme.rainbow.setValue(false);
                            break;
                        }
                    }
                }

                return true;
            }
        }
    }

    private boolean handleConfigsClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        } else if (this.newConfigDialogOpen) {
            if (this.cancelNewConfigButton.contains(mouseX, mouseY)) {
                this.newConfigDialogOpen = false;
                this.newConfigName = "";
            } else if (this.saveNewConfigButton.contains(mouseX, mouseY)) {
                this.createConfig();
            }

            return true;
        } else if (this.overlayCloseButton.contains(mouseX, mouseY)) {
            this.overlay = PanelGuiScreen.Overlay.NONE;
            return true;
        } else {
            for (PanelGuiScreen.ConfigRow configRow : this.configRows) {
                if (configRow.load.contains(mouseX, mouseY)) {
                    return this.showResult(ConfigProfileManager.loadProfile(configRow.index));
                }

                if (configRow.save.contains(mouseX, mouseY)) {
                    return this.showResult(ConfigProfileManager.updateProfile(configRow.index));
                }

                if (configRow.delete.contains(mouseX, mouseY)) {
                    if (this.selectedConfigIndex == configRow.index) {
                        this.selectedConfigIndex = -1;
                    }

                    return this.showResult(ConfigProfileManager.deleteProfile(configRow.index));
                }

                if (configRow.row.contains(mouseX, mouseY)) {
                    this.selectedConfigIndex = configRow.index;
                    return true;
                }
            }

            if (this.newConfigButton.contains(mouseX, mouseY)) {
                this.newConfigDialogOpen = true;
                this.newConfigName = "";
            } else if (this.importButton.contains(mouseX, mouseY)) {
                this.showResult(
                        ConfigProfileManager.importCode(this.client.keyboard.getClipboard()));
            } else if (this.odinConfigButton.contains(mouseX, mouseY)) {
                List<ConfigProfileManager.BundledConfig> bundled =
                        ConfigProfileManager.getBundledConfigs();
                this.showResult(
                        bundled.isEmpty()
                                ? ConfigProfileManager.Result.failure(
                                        "Bundled Odin config is unavailable")
                                : ConfigProfileManager.importCode(bundled.get(0).configJson));
            } else if (this.shareButton.contains(mouseX, mouseY)) {
                ConfigProfileManager.Result exported =
                        ConfigProfileManager.exportProfile(this.selectedConfigIndex);
                if (exported.success && exported.code != null) {
                    this.client.keyboard.setClipboard(exported.code);
                }

                this.showResult(exported);
            } else if (this.resetButton.contains(mouseX, mouseY)) {
                long now = System.currentTimeMillis();
                if (now < this.resetConfirmExpiry) {
                    this.resetConfirmExpiry = 0L;
                    this.showResult(ConfigProfileManager.resetToDefaults());
                } else {
                    this.resetConfirmExpiry = now + 5000L;
                    this.showResult(
                            ConfigProfileManager.Result.failure("Click reset again to confirm"));
                }
            }

            return true;
        }
    }

    private void createConfig() {
        ConfigProfileManager.Result result = ConfigProfileManager.saveCurrent(this.newConfigName);
        this.showResult(result);
        if (result.success) {
            this.newConfigDialogOpen = false;
            this.newConfigName = "";
            this.selectedConfigIndex = ConfigProfileManager.profiles().size() - 1;
        }
    }

    private boolean showResult(ConfigProfileManager.Result result) {
        this.status =
                result != null && result.message != null
                        ? result.message.toUpperCase(Locale.ROOT)
                        : "";
        this.statusSuccess = result != null && result.success;
        this.statusExpiry = System.currentTimeMillis() + 3800L;
        return true;
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        float delta =
                Math.min(0.05F, Math.max(0.001F, (float) (now - this.lastFrameNanos) / 1.0E9F));
        this.lastFrameNanos = now;
        this.frameDelta = delta;
        boolean animated = ThemeModule.areAnimationsEnabled();

        for (Module module : ModuleManager.get().getAll()) {
            float target = this.expandedModules.getOrDefault(module, false) ? 1.0F : 0.0F;
            float current = this.expandAnimations.getOrDefault(module, 0.0F);
            this.expandAnimations.put(
                    module, animated ? lerp(current, target, delta * 8.5F) : target);
        }
    }

    private float animateSettingToggle(Setting<?> setting, boolean enabled) {
        float target = enabled ? 1.0F : 0.0F;
        float current = this.settingToggleAnimations.getOrDefault(setting, target);
        float next =
                ThemeModule.areAnimationsEnabled()
                        ? lerp(current, target, this.frameDelta * 12.0F)
                        : target;
        this.settingToggleAnimations.put(setting, next);
        return next;
    }

    private float animateLabelToggle(String label, boolean enabled) {
        float target = enabled ? 1.0F : 0.0F;
        float current = this.labelToggleAnimations.getOrDefault(label, target);
        float next =
                ThemeModule.areAnimationsEnabled()
                        ? lerp(current, target, this.frameDelta * 12.0F)
                        : target;
        this.labelToggleAnimations.put(label, next);
        return next;
    }

    private List<Module> modules(String category) {
        String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();

        for (Module module : ModuleManager.get().getAll()) {
            if (!"theme".equals(module.name)
                    && category.equals(getCategoryName(module))
                    && (query.isEmpty()
                            || module.title.toLowerCase(Locale.ROOT).contains(query)
                            || module.name.toLowerCase(Locale.ROOT).contains(query))) {
                result.add(module);
            }
        }

        result.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        return result;
    }

    private static String getCategoryName(Module module) {
        String name = module.name == null ? "" : module.name.toLowerCase(Locale.ROOT);
        if ("scamming".equalsIgnoreCase(module.category.name)) {
            return "scamming";
        } else {
            return switch (name) {
                case "auto-totem",
                        "hover-totem",
                        "shield-breaker",
                        "triggerbot",
                        "aim-assist",
                        "mace-swap",
                        "crystal-optimizer",
                        "auto-inv-totem",
                        "safe-anchor",
                        "anchor-macro",
                        "auto-crystal" ->
                        "combat";
                case "freecam",
                        "fast-place",
                        "auto-tool",
                        "auto-mine",
                        "key-pearl",
                        "name-protect",
                        "block-notifier",
                        "odinclient-block-notifier",
                        "autolog",
                        "auto-firework",
                        "player-notify",
                        "weather-notifier",
                        "cord-snapper",
                        "skin-protect",
                        "sprint",
                        "zoom" ->
                        "misc";
                case "render-cycle",
                        "auto-relog",
                        "fake-stats",
                        "fake-roles",
                        "sus-chunk-finder",
                        "chunkfinder",
                        "player-chunk",
                        "player-debug-v2",
                        "antivanish-detection",
                        "region-map",
                        "key-homesetter",
                        "home-reset",
                        "key-rtp",
                        "amethyst-esp",
                        "staf-tracker" ->
                        "donut";
                case "name-tags",
                        "spawner-flagger",
                        "freelook",
                        "block-esp",
                        "block-outlines",
                        "item-glow",
                        "inventory-hud",
                        "player-esp",
                        "pearl-esp",
                        "pearl-trajectory",
                        "hole-esp",
                        "jump-circles",
                        "storage-esp",
                        "bedrock-hole-esp",
                        "block-entity-debug",
                        "light-finder",
                        "suspicious-esp",
                        "mob-esp",
                        "fullbright",
                        "swingspeed",
                        "spotify-hud",
                        "tool-size",
                        "sky-arts",
                        "china-head" ->
                        "render";
                case "hud", "discord-presence", "chat-macros", "theme" -> "client";
                default -> "misc";
            };
        }
    }

    private List<Setting<?>> getVisibleSettings(Module module) {
        List<Setting<?>> settings = new ArrayList<>();
        List<Setting<?>> keybinds = new ArrayList<>();

        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                if (setting.isVisible() && setting != module.keybind) {
                    if (setting instanceof KeybindSetting) {
                        keybinds.add(setting);
                    } else {
                        settings.add(setting);
                    }
                }
            }
        }

        settings.addAll(keybinds);
        if (module.keybind.isVisible()) {
            settings.add(module.keybind);
        }

        return HudPositionSettings.injectPositionEditor(settings, module);
    }

    private void openPositionEditor(Module module) {
        IntSetting xSetting = null;
        IntSetting ySetting = null;

        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                if (setting instanceof IntSetting intSetting) {
                    if ("x-position".equals(setting.name)) {
                        xSetting = intSetting;
                    }

                    if ("y-position".equals(setting.name)) {
                        ySetting = intSetting;
                    }
                }
            }
        }

        if (xSetting != null && ySetting != null) {
            MinecraftClient.getInstance()
                    .setScreen(new HudPositionEditorScreen(this, module, xSetting, ySetting));
        }
    }

    private int getExpandedHeight(Module module) {
        int height = 2;

        for (Setting<?> setting : this.getVisibleSettings(module)) {
            height += getSettingRowHeight(setting) + 2;
        }

        return height;
    }

    private static int getSettingRowHeight(Setting<?> setting) {
        return !(setting instanceof IntSetting) && !(setting instanceof NumberSetting) ? 28 : 36;
    }

    private void updateSliderValue(double mouseX) {
        if (this.draggedSlider != null && this.draggedSliderBounds.w > 0) {
            double fraction =
                    clampDouble(
                            (mouseX - this.draggedSliderBounds.x) / this.draggedSliderBounds.w,
                            0.0,
                            1.0);
            if (this.draggedSlider instanceof IntSetting intSetting) {
                intSetting.setValue(
                        (int)
                                Math.round(
                                        intSetting.sliderMin
                                                + fraction
                                                        * (intSetting.sliderMax
                                                                - intSetting.sliderMin)));
            } else if (this.draggedSlider instanceof NumberSetting numberSetting) {
                double raw =
                        numberSetting.sliderMin
                                + fraction * (numberSetting.sliderMax - numberSetting.sliderMin);
                double scale = Math.pow(10.0, getDecimalPlaces(numberSetting));
                numberSetting.setValue(Math.round(raw * scale) / scale);
            }
        }
    }

    private static void cycleEnum(EnumSetting setting, int step) {
        Enum current = (Enum) setting.getValue();
        Enum[] constants = (Enum[]) current.getDeclaringClass().getEnumConstants();
        setting.setValue(constants[Math.floorMod(current.ordinal() + step, constants.length)]);
    }

    private void finishTextEdit(boolean apply) {
        if (this.editingSetting != null && apply) {
            this.editingSetting.setValueFromString(this.editBuffer);
        }

        this.editingSetting = null;
        this.editBuffer = "";
    }

    private ThemeModule getThemeModule() {
        return ModuleManager.get().getByName("theme") instanceof ThemeModule theme ? theme : null;
    }

    private void drawOverlayBackground(DrawContext context, int x, int y, int width, int height) {
        DashboardRenderer.drawRoundedHighlight(
                context, x + 2, y + 5, width, height, 12, 1879048192);
        DashboardRenderer.drawRoundedRect(context, x, y, width, height, 12, -66449899);
    }

    private void drawButton(
            DrawContext context,
            PanelGuiScreen.Rect rect,
            String label,
            int mouseX,
            int mouseY,
            boolean active,
            int accent) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int background =
                active ? blendColors(-15328218, accent, 0.28F) : (hovered ? -15065303 : -15591906);
        DashboardRenderer.drawRoundedRect(context, rect.x, rect.y, rect.w, rect.h, 7, background);
        this.drawCenteredText(
                context,
                this.truncateText(label, rect.w - 8),
                rect.x + rect.w / 2,
                rect.y + (rect.h - 8) / 2,
                active ? -723464 : (hovered ? -1710100 : -7300954));
    }

    private void drawStyleOption(
            DrawContext context,
            PanelGuiScreen.Rect rect,
            String title,
            String description,
            boolean selected,
            int mouseX,
            int mouseY,
            int accent) {
        boolean hovered = rect.contains(mouseX, mouseY);
        DashboardRenderer.drawRoundedRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                8,
                selected
                        ? blendColors(-15591392, accent, 0.23F)
                        : (hovered ? -15196890 : -15657443));
        DashboardRenderer.drawRoundedRect(
                context, rect.x + 9, rect.y + 10, 5, 5, 3, selected ? accent : -11445911);
        this.drawText(context, title, rect.x + 22, rect.y + 8, selected ? -920585 : -5130301);
        this.drawText(context, description, rect.x + 22, rect.y + 24, -9208951);
    }

    private void drawToggleOption(
            DrawContext context,
            PanelGuiScreen.Rect rect,
            String label,
            boolean enabled,
            int mouseX,
            int mouseY,
            int accent) {
        DashboardRenderer.drawRoundedRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                7,
                rect.contains(mouseX, mouseY) ? -15196890 : -15657443);
        this.drawText(
                context,
                this.truncateText(label, rect.w - 32),
                rect.x + 9,
                rect.y + 17,
                enabled ? -1446928 : -8222056);
        float progress = this.animateLabelToggle(label, enabled);
        int toggleX = rect.x + rect.w - 24;
        int toggleY = rect.y + (rect.h - 8) / 2;
        this.drawRoundedRectSnapped(
                context,
                toggleX,
                toggleY,
                16,
                8,
                4,
                blendColors(-13486012, accent, progress * 0.78F));
        drawToggleKnob(context, toggleX + 1 + progress * 8.0F, toggleY + 1, -789257);
    }

    private void drawValueBox(
            DrawContext context,
            PanelGuiScreen.Rect rect,
            String text,
            boolean active,
            int accent) {
        DashboardRenderer.drawRoundedRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                6,
                active ? blendColors(-15723235, accent, 0.25F) : -15986408);
        this.drawCenteredText(
                context,
                this.truncateText(text, rect.w - 9),
                rect.x + rect.w / 2,
                rect.y + (rect.h - 8) / 2,
                active ? -789256 : -7169368);
    }

    private void drawSlider(
            DrawContext context, PanelGuiScreen.Rect rect, double fraction, int accent) {
        double clamped = clampDouble(fraction, 0.0, 1.0);
        this.drawRoundedRectSnapped(context, rect.x, rect.y, rect.w, rect.h, 2, -13617598);
        int fillWidth = (int) Math.round(rect.w * clamped);
        if (fillWidth > 1) {
            this.drawRoundedRectSnapped(context, rect.x, rect.y, fillWidth, rect.h, 2, accent);
        }

        int knobX = rect.x + (int) Math.round((rect.w - 1) * clamped) - 3;
        this.drawRoundedRectSnapped(context, knobX, rect.y - 1, 6, 6, 1, -1511950);
    }

    private void drawRoundedRectSnapped(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width > 0 && height > 0) {
            float scale = 1.6F;
            float snappedX = Math.round(x * scale) / scale;
            float snappedY = Math.round(y * scale) / scale;
            float snappedWidth = (Math.round((x + width) * scale) - Math.round(x * scale)) / scale;
            float snappedHeight =
                    (Math.round((y + height) * scale) - Math.round(y * scale)) / scale;
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(snappedX, snappedY);
            matrices.scale(snappedWidth / width, snappedHeight / height);
            DashboardRenderer.drawRoundedHighlight(context, 0, 0, width, height, radius, color);
            matrices.popMatrix();
        }
    }

    private static void drawToggleKnob(DrawContext context, float x, float y, int color) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        DashboardRenderer.drawRoundedHighlight(context, 0, 0, 6, 6, 3, color);
        matrices.popMatrix();
    }

    private static void drawChevron(
            DrawContext context, int x, int y, boolean collapsed, int color) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        float scale = 1.6F;
        matrices.translate(Math.round(x * scale) / scale, Math.round(y * scale) / scale);
        context.fill(-2, 0, 3, 1, color);
        if (!collapsed) {
            context.fill(0, -2, 1, 3, color);
        }

        matrices.popMatrix();
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawBoldText(context, this.textRenderer, text, x, y, color);
    }

    private void drawCenteredText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawCenteredBoldText(context, this.textRenderer, text, x, y, color);
    }

    private void drawRightAlignedText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawRightAlignedBoldText(context, this.textRenderer, text, x, y, color);
    }

    private String truncateText(String text, int maxWidth) {
        return BoldText.truncateToWidth(this.textRenderer, text == null ? "" : text, maxWidth);
    }

    private static int countActiveModules(List<Module> modules) {
        int count = 0;

        for (Module module : modules) {
            if (module.isActive()) {
                count++;
            }
        }

        return count;
    }

    private static String formatEnumName(Object value) {
        return value instanceof Enum enumValue
                ? enumValue.name().replace('_', ' ')
                : String.valueOf(value);
    }

    private static String formatDecimal(double value, int places) {
        String text = String.format(Locale.ROOT, "%." + places + "f", value);

        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }

        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static int getDecimalPlaces(NumberSetting setting) {
        return !"break-delay".equals(setting.name) && !"place-delay".equals(setting.name) ? 2 : 3;
    }

    private static double getSliderFraction(double value, double min, double max) {
        return max <= min ? 0.0 : (value - min) / (max - min);
    }

    private static boolean isClearKey(int key, int scancode) {
        return key == 256 || key == 261 || key == 259 || scancode == 83 || scancode == 339;
    }

    private static boolean isCaretVisible() {
        return (System.currentTimeMillis() / 450L & 1L) == 0L;
    }

    private static int alpha(int color, int opacity) {
        return Math.max(0, Math.min(255, opacity)) << 24 | color & 16777215;
    }

    private static int blendColors(int from, int to, float amount) {
        float t = clampFloat(amount, 0.0F, 1.0F);
        int a = Math.round((from >>> 24) * (1.0F - t) + (to >>> 24) * t);
        int r = Math.round((from >> 16 & 0xFF) * (1.0F - t) + (to >> 16 & 0xFF) * t);
        int g = Math.round((from >> 8 & 0xFF) * (1.0F - t) + (to >> 8 & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * clampFloat(t, 0.0F, 1.0F);
    }

    private static float easeOutCubic(float t) {
        float clamped = clampFloat(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - clamped) * (1.0F - clamped) * (1.0F - clamped);
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String limitLength(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record ConfigRow(
            PanelGuiScreen.Rect row,
            PanelGuiScreen.Rect load,
            PanelGuiScreen.Rect save,
            PanelGuiScreen.Rect delete,
            int index) {}

    private record ModuleRow(PanelGuiScreen.Rect row, Module module) {}

    private static enum Overlay {
        NONE,
        THEMES,
        CONFIGS;
    }

    private record PanelLayout(
            int startX, int startY, int columns, int panelWidth, int maxPanelHeight) {}

    private record Rect(int x, int y, int w, int h) {
        private static final PanelGuiScreen.Rect EMPTY = new PanelGuiScreen.Rect(0, 0, 0, 0);

        private boolean contains(double px, double py) {
            return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
        }

        private PanelGuiScreen.Rect intersection(PanelGuiScreen.Rect other) {
            int left = Math.max(this.x, other.x);
            int top = Math.max(this.y, other.y);
            int right = Math.min(this.x + this.w, other.x + other.w);
            int bottom = Math.min(this.y + this.h, other.y + other.h);
            return right > left && bottom > top
                    ? new PanelGuiScreen.Rect(left, top, right - left, bottom - top)
                    : EMPTY;
        }

        private boolean empty() {
            return this.w <= 0 || this.h <= 0;
        }
    }

    private record SettingRow(
            PanelGuiScreen.Rect row,
            PanelGuiScreen.Rect control,
            Module module,
            Setting<?> setting) {}
}
