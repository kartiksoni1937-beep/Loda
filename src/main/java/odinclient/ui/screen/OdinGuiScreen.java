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
import odinclient.ui.anim.AnimationUtil;
import odinclient.ui.draw.ItemIconAtlas;
import odinclient.ui.draw.SdfFontRenderer;
import odinclient.ui.draw.SdfShapeRenderer;
import odinclient.ui.hud.ModuleNotifications;
import odinclient.ui.layout.GuiPanelLayoutStore;
import odinclient.ui.scale.GuiScale;
import odinclient.ui.scale.ScreenScaleCompat;
import odinclient.ui.widget.GuiKeybind;
import odinclient.ui.widget.PlayerInfoCard;
import odinclient.ui.widget.SettingTooltipRenderer;
import odinclient.util.input.KeyNames;

import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.IntConsumer;

public final class OdinGuiScreen extends Screen {
    public static final float BACKDROP_ALPHA = 0.62F;
    static final int COLOR_BLACK = -16777216;
    static final int COLOR_SURFACE = -16251379;
    static final int COLOR_SURFACE_RAISED = -15659750;
    static final int COLOR_SURFACE_HOVER = -15265501;
    static final int COLOR_BORDER = -14870999;
    static final int COLOR_TEXT_PRIMARY = -658693;
    static final int COLOR_TEXT_SECONDARY = -6845010;
    static final int COLOR_TEXT_MUTED = -10529165;
    static final int COLOR_SUCCESS = -10755160;
    static final int COLOR_ERROR = -41081;
    private static final float W = 600.0F;
    private static final float H = 380.0F;
    private static final float PADDING = 10.0F;
    private static final float SIDEBAR_WIDTH = 150.0F;
    private static final float NAV_ROW_HEIGHT = 18.0F;
    private static final float CONTENT_X = 170.0F;
    private static final float CONTENT_WIDTH = 420.0F;
    private static final float CONTENT_TOP = 70.0F;
    private static final float MODULE_ROW_HEIGHT = 32.0F;
    private static final float ROW_SPACING = 5.0F;
    private static final float SETTINGS_PANEL_WIDTH = 184.0F;
    private static final String[] MODULE_CATEGORIES =
            new String[] {"combat", "render", "misc", "donut", "scamming"};
    private static final String[] FILTER_LABELS = new String[] {"All", "Enabled", "Disabled"};
    private static final String[] PANEL_CATEGORIES =
            new String[] {"combat", "render", "misc", "donut", "client", "scamming"};
    private static final float PANEL_WIDTH = 150.0F;
    private static final float DROPDOWN_WIDTH = 150.0F;
    private static final float PANEL_MARGIN = 8.0F;
    private static final float PANEL_HEADER_HEIGHT = 22.0F;
    private static final float PANEL_ROW_HEIGHT = 20.0F;
    private static final float PANEL_START_Y = 34.0F;
    private final Map<String, float[]> panelPositions = new HashMap<>();
    private final Map<String, Float> panelScrollOffsets = new HashMap<>();
    private final Set<String> hiddenPanels = new HashSet<>();
    private final Set<Module> expandedModules = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, float[]> panelScrollBounds = new HashMap<>();
    private String draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;
    private float layoutWidth;
    private float layoutHeight;
    private String openDropdown;
    private String lastOpenDropdown;
    private float[] dropdownBounds;
    private boolean renderingDropdown;
    private final List<float[]> layoutToggleRegions = new ArrayList<>();
    private float mouseScreenX;
    private float mouseScreenY;
    private final Screen parentScreen;
    private final boolean openedFromHud;
    private final PlayerInfoCard playerInfoCard = new PlayerInfoCard();
    private final Map<Object, Float> animations = new HashMap<>();
    private final List<OdinGuiScreen.ClickRegion> clickRegions = new ArrayList<>();
    private final List<float[]> scissorStack = new ArrayList<>();
    private long lastFrameNanos = System.nanoTime();
    private float deltaTime = 0.016666668F;
    private float elapsedTime;
    private float openProgress;
    private boolean closing;
    private float scale = 1.0F;
    private float layoutOffsetX;
    private float layoutOffsetY;
    private float mouseX;
    private float mouseY;
    private OdinGuiScreen.Tab currentTab = OdinGuiScreen.Tab.MODULES;
    private String selectedCategory = "combat";
    private String selectedNavKey = "combat";
    private float navIndicatorY = -1.0F;
    private float contentAnimStart;
    private int statusFilter;
    private float filterIndicatorX = -1.0F;
    private float filterIndicatorWidth;
    private float moduleScroll;
    private float moduleScrollTarget;
    private float moduleScrollMax;
    private Module inspectedModule;
    private Module lastInspectedModule;
    private float inspectorProgress;
    private float settingsScroll;
    private float settingsScrollTarget;
    private float settingsScrollMax;
    private float inspectorAnimStart;
    private String searchQuery = "";
    private boolean searchFocused;
    private boolean swallowSlashChar;
    private boolean swallowNextChar;
    private Setting<Integer> listeningKeybind;
    private Setting<?> editingSetting;
    private String editBuffer = "";
    private Setting<?> draggingSlider;
    private float sliderTrackX;
    private float sliderTrackWidth;
    private String hoverDescription;
    private Setting<?> hoveredSetting;
    private final SettingTooltipRenderer tooltipRenderer = new SettingTooltipRenderer();
    private boolean namingConfig;
    private String configNameInput = "";
    private int selectedConfigIndex = -1;
    private long resetConfirmDeadline;
    private String status = "";
    private boolean statusSuccess;
    private long statusExpiry;

    public OdinGuiScreen(Screen parent) {
        this(parent, false);
    }

    public OdinGuiScreen(Screen parent, boolean fromHud) {
        super(Text.literal("OdinClient"));
        this.parentScreen = parent;
        this.openedFromHud = fromHud;
        GuiPanelLayoutStore.PanelLayout layout = GuiPanelLayoutStore.loadLayout(PANEL_CATEGORIES);
        this.panelPositions.putAll(layout.positions());
        this.hiddenPanels.addAll(layout.hidden());
    }

    public boolean isCapturingInput() {
        return this.editingSetting != null
                || this.namingConfig
                || this.searchFocused
                || this.listeningKeybind != null;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void init() {
        this.lastFrameNanos = System.nanoTime();
    }

    @Override
    public void close() {
        this.savePanelLayout();
        this.closing = true;
        this.searchFocused = false;
        this.listeningKeybind = null;
        this.swallowNextChar = false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (MinecraftClient.getInstance().world != null) {
            this.applyBlur(context);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        long now = System.nanoTime();
        this.deltaTime =
                Math.min(0.05F, Math.max(5.0E-4F, (float) (now - this.lastFrameNanos) / 1.0E9F));
        this.lastFrameNanos = now;
        this.elapsedTime = this.elapsedTime + this.deltaTime;
        if (this.closing) {
            this.openProgress =
                    AnimationUtil.smoothApproach(this.openProgress, 0.0F, 16.0F, this.deltaTime);
            if (this.openProgress < 0.02F) {
                MinecraftClient.getInstance().setScreen(this.parentScreen);
                return;
            }
        } else {
            this.openProgress =
                    AnimationUtil.smoothApproach(
                            this.openProgress,
                            1.0F,
                            this.openedFromHud ? 7.0F : 9.0F,
                            this.deltaTime);
        }

        float screenWidth = this.width;
        float screenHeight = this.height;
        this.hoveredSetting = null;
        if (this.isPanelLayout()) {
            this.renderPanelLayout(context, mouseX, mouseY);
        } else {
            this.scale =
                    GuiScale.getBoundsFitScale(
                                    ScreenScaleCompat.getScaledWidth(),
                                    ScreenScaleCompat.getScaledHeight(),
                                    600.0F,
                                    380.0F)
                            * ScreenScaleCompat.getScale(this.width);
            this.layoutOffsetX = (screenWidth - 600.0F * this.scale) * 0.5F;
            this.layoutOffsetY = (screenHeight - 380.0F * this.scale) * 0.5F;
            this.mouseX = (mouseX - this.layoutOffsetX) / this.scale;
            this.mouseY = (mouseY - this.layoutOffsetY) / this.scale;
            this.mouseScreenX = mouseX;
            this.mouseScreenY = mouseY;
            float progress = AnimationUtil.clamp01(this.openProgress);
            context.fill(
                    0,
                    0,
                    this.width,
                    this.height,
                    AnimationUtil.multiplyAlpha(
                            -16777216, 0.62F * (this.openedFromHud ? 1.0F : progress)));
            this.clickRegions.clear();
            this.hoverDescription = null;
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            float windowScale =
                    this.closing
                            ? 0.96F + 0.04F * progress
                            : 0.93F + 0.07F * AnimationUtil.easeOutBack(progress);
            matrices.translate(
                    this.layoutOffsetX + 600.0F * this.scale * 0.5F,
                    this.layoutOffsetY + 380.0F * this.scale * 0.5F);
            matrices.scale(this.scale * windowScale, this.scale * windowScale);
            matrices.translate(-300.0F, -190.0F);
            this.drawWindowFrame(context, progress);
            this.drawSidebar(context, progress);
            float contentWidth =
                    420.0F - 192.0F * AnimationUtil.easeInOutCubic(this.inspectorProgress);
            switch (this.currentTab) {
                case MODULES:
                    this.drawModulesPage(context, contentWidth, progress);
                    break;
                case CONFIGS:
                    this.drawConfigsPage(context, contentWidth, progress);
                    break;
                case KEYBINDS:
                    this.drawKeybindsPage(context, contentWidth, progress);
            }

            this.drawModuleInspector(context, progress);
            this.drawStatusToast(context, progress);
            matrices.popMatrix();
            this.drawLayoutSwitcher(context, progress);
            ModuleNotifications.renderOverMenu(context, this.width, this.height);
            this.drawSettingTooltip(context, progress);
        }
    }

    private void drawWindowFrame(DrawContext context, float alpha) {
        int accentStart = ThemeModule.getAccentColorAt(0.0F);
        int accentEnd = ThemeModule.getAccentColorAt(1.0F);
        SdfShapeRenderer.drawShadow(
                context,
                -4.0F,
                2.0F,
                608.0F,
                388.0F,
                20.0F,
                18.0F,
                AnimationUtil.multiplyAlpha(-1342177280, alpha));
        SdfShapeRenderer.drawShadow(
                context,
                30.0F,
                350.0F,
                540.0F,
                40.0F,
                20.0F,
                26.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accentStart, 40), alpha));
        SdfShapeRenderer.fill(
                context,
                0.0F,
                0.0F,
                600.0F,
                380.0F,
                16.0F,
                AnimationUtil.multiplyAlpha(-16777216, alpha));
        float drift = (float) Math.sin(this.elapsedTime * 0.6F) * 3.0F;
        float logoX = 380.0F;
        float logoY = 212.8F + drift;
        SdfShapeRenderer.drawLogo(
                context,
                logoX,
                logoY,
                96.0F,
                0.4F,
                1.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accentStart, 22), alpha));
        SdfShapeRenderer.drawLogo(
                context,
                logoX,
                logoY,
                96.0F,
                0.0F,
                1.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accentEnd, 30), alpha));
        SdfShapeRenderer.fillCornerGradient(
                context,
                0.0F,
                0.0F,
                600.0F,
                380.0F,
                16.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accentStart, 14), alpha),
                0,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accentEnd, 18), alpha),
                0);
        SdfShapeRenderer.drawOutline(
                context,
                0.0F,
                0.0F,
                600.0F,
                380.0F,
                16.0F,
                1.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-14870999, accentStart, 0.25F), alpha));
    }

    private void drawSidebar(DrawContext context, float alpha) {
        float x = 10.0F;
        float y = 10.0F;
        float height = 360.0F;
        int accent = ThemeModule.getAccentColorAt(0.2F);
        SdfShapeRenderer.fill(
                context,
                x,
                y,
                150.0F,
                height,
                12.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(-16251379, 235), alpha));
        SdfShapeRenderer.drawOutline(
                context,
                x,
                y,
                150.0F,
                height,
                12.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(-14870999, alpha));
        float logoSize = 7.5F;
        SdfShapeRenderer.drawLogo(
                context,
                x + 12.0F + SdfShapeRenderer.getLogoWidth(logoSize) * 0.5F,
                y + 17.0F,
                logoSize,
                0.3F,
                1.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 110), alpha));
        SdfShapeRenderer.drawLogo(
                context,
                x + 12.0F + SdfShapeRenderer.getLogoWidth(logoSize) * 0.5F,
                y + 17.0F,
                logoSize,
                0.0F,
                1.0F,
                AnimationUtil.multiplyAlpha(accent, alpha));
        float textX = x + 16.0F + SdfShapeRenderer.getLogoWidth(logoSize);
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(textX, y + 17.0F);
        matrices.scale(0.72F, 0.72F);
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BRAND,
                "ODIN",
                0.0F,
                0.0F,
                AnimationUtil.multiplyAlpha(-658693, alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BRAND,
                "CLIENT",
                SdfFontRenderer.width(SdfFontRenderer.FontStyle.BRAND, "ODIN "),
                0.0F,
                AnimationUtil.multiplyAlpha(accent, alpha));
        matrices.popMatrix();
        float listTop = y + 48.0F;
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TINY,
                "MODULES",
                x + 12.0F,
                listTop - 6.0F,
                AnimationUtil.multiplyAlpha(-10529165, alpha));
        List<OdinGuiScreen.NavEntry> entries = new ArrayList<>();

        for (String category : MODULE_CATEGORIES) {
            entries.add(
                    new OdinGuiScreen.NavEntry(
                            category, title(category), getCategoryIcon(category), true));
        }

        float generalY = listTop + MODULE_CATEGORIES.length * 18.0F + 10.0F;
        entries.add(new OdinGuiScreen.NavEntry("client", "Client", "\ue004", true));
        entries.add(new OdinGuiScreen.NavEntry("configs", "Configs", "\ue006", false));
        entries.add(new OdinGuiScreen.NavEntry("keybinds", "Keybinds", "\ue007", false));
        float[] rowYs = new float[entries.size()];
        float cursorY = listTop;

        for (int n = 0; n < entries.size(); n++) {
            if (n == MODULE_CATEGORIES.length) {
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.TINY,
                        "GENERAL",
                        x + 12.0F,
                        generalY + 2.0F,
                        AnimationUtil.multiplyAlpha(-10529165, alpha));
                cursorY = generalY + 8.0F;
            }

            rowYs[n] = cursorY;
            cursorY += 18.0F;
        }

        int selectedIndex = 0;

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key.equals(this.selectedNavKey)) {
                selectedIndex = i;
            }
        }

        float targetY = rowYs[selectedIndex];
        this.navIndicatorY =
                this.navIndicatorY < 0.0F
                        ? targetY
                        : AnimationUtil.smoothApproach(
                                this.navIndicatorY, targetY, 18.0F, this.deltaTime);
        float stretch = Math.min(4.0F, Math.abs(targetY - this.navIndicatorY) * 0.15F);
        float rowX = x + 6.0F;
        float rowWidth = 138.0F;
        SdfShapeRenderer.drawShadow(
                context,
                rowX,
                this.navIndicatorY + 1.0F - stretch * 0.5F,
                rowWidth,
                16.0F + stretch,
                7.0F,
                6.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 60), alpha));
        SdfShapeRenderer.fillVerticalGradient(
                context,
                rowX,
                this.navIndicatorY + 1.0F - stretch * 0.5F,
                rowWidth,
                16.0F + stretch,
                7.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 120), alpha),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 34), alpha));
        SdfShapeRenderer.drawOutline(
                context,
                rowX,
                this.navIndicatorY + 1.0F - stretch * 0.5F,
                rowWidth,
                16.0F + stretch,
                7.0F,
                0.6F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 150), alpha));

        for (int index = 0; index < entries.size(); index++) {
            OdinGuiScreen.NavEntry entry = entries.get(index);
            float rowY = rowYs[index];
            boolean selected = entry.key.equals(this.selectedNavKey);
            boolean hovered = this.isHovered(rowX, rowY + 1.0F, rowWidth, 16.0F);
            float hoverProgress =
                    this.animate(
                            "nav-hover:" + entry.key, hovered && !selected ? 1.0F : 0.0F, 14.0F);
            float selectProgress =
                    this.animate("nav-select:" + entry.key, selected ? 1.0F : 0.0F, 12.0F);
            if (hoverProgress > 0.01F) {
                SdfShapeRenderer.fill(
                        context,
                        rowX,
                        rowY + 1.0F,
                        rowWidth,
                        16.0F,
                        7.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.withAlpha(-15265501, 255), hoverProgress * alpha));
            }

            float shift = hoverProgress * 1.5F + selectProgress * 1.0F;
            int textColor =
                    AnimationUtil.lerpColor(
                            AnimationUtil.lerpColor(-6845010, -658693, hoverProgress),
                            -1,
                            selectProgress);
            SdfFontRenderer.drawIcon(
                    context,
                    entry.icon,
                    x + 17.0F + shift,
                    rowY + 9.0F,
                    9.0F,
                    AnimationUtil.multiplyAlpha(textColor, alpha));
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BODY,
                    entry.title,
                    x + 28.0F + shift,
                    rowY + 9.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(
                                    AnimationUtil.lerpColor(-6845010, -658693, hoverProgress),
                                    -1,
                                    selectProgress),
                            alpha));
            if (entry.module) {
                String countText = String.valueOf(this.modules(entry.key, "", 0).size());
                float badgeWidth =
                        SdfFontRenderer.width(SdfFontRenderer.FontStyle.TINY, countText) + 7.0F;
                float badgeX = x + 150.0F - 12.0F - badgeWidth;
                SdfShapeRenderer.fill(
                        context,
                        badgeX,
                        rowY + 5.0F,
                        badgeWidth,
                        8.0F,
                        4.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(
                                        -15659750, 872415231, selectProgress * 0.4F),
                                alpha));
                SdfFontRenderer.drawTextCentered(
                        context,
                        SdfFontRenderer.FontStyle.TINY,
                        countText,
                        badgeX + badgeWidth * 0.5F,
                        rowY + 9.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(-10529165, -658693, selectProgress),
                                alpha));
            }

            String key = entry.key;
            this.addClickRegion(
                    rowX, rowY + 1.0F, rowWidth, 16.0F, button -> this.selectNavEntry(key));
        }

        float cardY = cursorY + 8.0F;
        float cardHeight = y + height - 6.0F - cardY;
        if (cardHeight > 60.0F) {
            this.playerInfoCard.render(
                    context, x + 6.0F, cardY, 138.0F, cardHeight, alpha, this.deltaTime);
        }
    }

    private void selectNavEntry(String key) {
        if (!key.equals(this.selectedNavKey)
                || this.currentTab == OdinGuiScreen.Tab.MODULES && !this.searchQuery.isEmpty()) {
            this.selectedNavKey = key;
            this.searchQuery = "";
            this.searchFocused = false;
            this.moduleScroll = this.moduleScrollTarget = 0.0F;
            this.contentAnimStart = this.elapsedTime;
            this.statusFilter = 0;
            switch (key) {
                case "configs":
                    this.currentTab = OdinGuiScreen.Tab.CONFIGS;
                    break;
                case "keybinds":
                    this.currentTab = OdinGuiScreen.Tab.KEYBINDS;
                    break;
                default:
                    this.currentTab = OdinGuiScreen.Tab.MODULES;
                    this.selectedCategory = key;
            }

            if (this.currentTab != OdinGuiScreen.Tab.MODULES) {
                this.inspectedModule = null;
            }
        }
    }

    private void drawModulesPage(DrawContext context, float width, float alpha) {
        boolean searching = !this.searchQuery.isBlank();
        List<Module> allModules =
                this.modules(searching ? null : this.selectedCategory, this.searchQuery, 0);
        List<Module> visible =
                this.modules(
                        searching ? null : this.selectedCategory,
                        this.searchQuery,
                        this.statusFilter);
        int enabledCount = 0;

        for (Module module : allModules) {
            if (module.isActive()) {
                enabledCount++;
            }
        }

        float fade = this.getContentFade();
        String titleText = searching ? "Search" : title(this.selectedCategory);
        String titleSuffix = searching ? " Results" : " Modules";
        this.drawPageHeader(
                context,
                titleText,
                titleSuffix,
                allModules.size()
                        + (allModules.size() == 1 ? " module" : " modules")
                        + "  \u00b7  "
                        + enabledCount
                        + " enabled",
                width,
                alpha,
                fade);
        this.drawStatusFilter(context, alpha);
        float top = 70.0F;
        float bottom = 370.0F;
        float contentHeight = visible.size() * 37.0F;
        this.moduleScrollMax = Math.max(0.0F, contentHeight - (bottom - top) + 4.0F);
        this.moduleScrollTarget = clampFloat(this.moduleScrollTarget, 0.0F, this.moduleScrollMax);
        this.moduleScroll =
                AnimationUtil.smoothApproach(
                        this.moduleScroll, this.moduleScrollTarget, 16.0F, this.deltaTime);
        this.pushScissor(context, 166.0F, top, width + 8.0F, bottom - top);
        float rowY = top - this.moduleScroll;

        for (int i = 0; i < visible.size(); i++) {
            Module row = visible.get(i);
            if (rowY + 32.0F >= top - 2.0F && rowY <= bottom + 2.0F) {
                float reveal =
                        AnimationUtil.easeOutCubic(
                                AnimationUtil.progress(
                                        this.elapsedTime - this.contentAnimStart,
                                        i * 0.028F,
                                        i * 0.028F + 0.34F));
                this.drawModuleRow(
                        context,
                        row,
                        170.0F,
                        rowY + (1.0F - reveal) * 10.0F,
                        width,
                        alpha * reveal);
            }

            rowY += 37.0F;
        }

        if (visible.isEmpty()) {
            SdfFontRenderer.drawIcon(
                    context,
                    "\ue008",
                    170.0F + width * 0.5F,
                    top + 60.0F,
                    18.0F,
                    AnimationUtil.multiplyAlpha(-10529165, alpha * fade));
            SdfFontRenderer.drawTextCentered(
                    context,
                    SdfFontRenderer.FontStyle.BODY,
                    "No modules match",
                    170.0F + width * 0.5F,
                    top + 82.0F,
                    AnimationUtil.multiplyAlpha(-6845010, alpha * fade));
        }

        this.popScissor(context);
        this.drawScrollFade(context, 166.0F, top, width + 8.0F, bottom - top, alpha);
        this.drawScrollbar(
                context,
                170.0F + width + 3.0F,
                top,
                bottom - top,
                this.moduleScroll,
                this.moduleScrollMax,
                alpha);
    }

    private void drawPageHeader(
            DrawContext context,
            String title,
            String suffix,
            String subtitle,
            float width,
            float alpha,
            float fade) {
        int accent = ThemeModule.getAccentColorAt(0.1F);
        float slide = (1.0F - fade) * 6.0F;
        float titleWidth = SdfFontRenderer.width(SdfFontRenderer.FontStyle.TITLE, title);
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TITLE,
                title,
                172.0F - slide,
                25.0F,
                AnimationUtil.multiplyAlpha(accent, alpha * fade));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TITLE,
                suffix,
                172.0F + titleWidth - slide,
                25.0F,
                AnimationUtil.multiplyAlpha(-658693, alpha * fade));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                subtitle,
                172.0F - slide * 0.5F,
                38.0F,
                AnimationUtil.multiplyAlpha(-6845010, alpha * fade));
        float headerWidth =
                172.0F
                        + titleWidth
                        + SdfFontRenderer.width(SdfFontRenderer.FontStyle.TITLE, suffix);
        this.drawSearchBar(context, 170.0F + width, 170.0F + width - headerWidth - 10.0F, alpha);
    }

    private void drawSearchBar(DrawContext context, float right, float maxWidth, float alpha) {
        float focus = this.animate("search-focus", this.searchFocused ? 1.0F : 0.0F, 12.0F);
        float barWidth =
                Math.min(
                        128.0F + 28.0F * AnimationUtil.easeOutCubic(focus),
                        Math.max(20.0F, maxWidth));
        boolean collapsed = barWidth < 64.0F;
        if (collapsed) {
            barWidth = 20.0F;
        }

        float barHeight = 20.0F;
        float barX = right - barWidth;
        float barY = 15.0F;
        int accent = ThemeModule.getAccentColorAt(0.5F);
        boolean hovered = this.isHovered(barX, barY, barWidth, barHeight);
        float hoverProgress = this.animate("search-hover", hovered ? 1.0F : 0.0F, 14.0F);
        if (focus > 0.01F) {
            SdfShapeRenderer.drawShadow(
                    context,
                    barX,
                    barY,
                    barWidth,
                    barHeight,
                    7.0F,
                    6.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, 70), focus * alpha));
        }

        SdfShapeRenderer.fill(
                context,
                barX,
                barY,
                barWidth,
                barHeight,
                7.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                -16251379, -15659750, Math.max(hoverProgress, focus)),
                        alpha));
        SdfShapeRenderer.drawOutline(
                context,
                barX,
                barY,
                barWidth,
                barHeight,
                7.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-14870999, accent, focus), alpha));
        SdfFontRenderer.drawIcon(
                context,
                "\ue008",
                barX + 10.0F,
                barY + barHeight * 0.5F,
                8.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-10529165, accent, focus), alpha));
        if (collapsed) {
            this.addClickRegion(
                    barX, barY, barWidth, barHeight, button -> this.searchFocused = true);
        } else {
            boolean empty = this.searchQuery.isEmpty();
            String text = empty ? "Cracked by bob" : this.searchQuery;
            String shown =
                    SdfFontRenderer.truncateToWidth(
                            SdfFontRenderer.FontStyle.BODY_SMALL, text, barWidth - 30.0F);
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BODY_SMALL,
                    shown,
                    barX + 19.0F,
                    barY + barHeight * 0.5F,
                    AnimationUtil.multiplyAlpha(empty ? -10529165 : -658693, alpha));
            if (this.searchFocused && isCaretVisible()) {
                float caretX =
                        barX
                                + 19.5F
                                + (empty
                                        ? 0.0F
                                        : SdfFontRenderer.width(
                                                SdfFontRenderer.FontStyle.BODY_SMALL, shown));
                SdfShapeRenderer.fill(
                        context,
                        caretX,
                        barY + 6.0F,
                        0.8F,
                        barHeight - 12.0F,
                        0.0F,
                        AnimationUtil.multiplyAlpha(accent, alpha));
            }

            if (empty) {
                float hintAlpha = 1.0F - focus;
                SdfShapeRenderer.fill(
                        context,
                        barX + barWidth - 17.0F,
                        barY + 5.0F,
                        11.0F,
                        10.0F,
                        3.0F,
                        AnimationUtil.multiplyAlpha(-15659750, alpha * hintAlpha));
                SdfShapeRenderer.drawOutline(
                        context,
                        barX + barWidth - 17.0F,
                        barY + 5.0F,
                        11.0F,
                        10.0F,
                        3.0F,
                        0.6F,
                        AnimationUtil.multiplyAlpha(-14870999, alpha * hintAlpha));
                SdfFontRenderer.drawTextCentered(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        "/",
                        barX + barWidth - 11.5F,
                        barY + barHeight * 0.5F,
                        AnimationUtil.multiplyAlpha(-6845010, alpha * hintAlpha));
            }

            if (!empty) {
                boolean clearHovered =
                        this.isHovered(barX + barWidth - 16.0F, barY + 3.0F, 13.0F, 14.0F);
                SdfFontRenderer.drawIcon(
                        context,
                        "\ue00b",
                        barX + barWidth - 9.5F,
                        barY + barHeight * 0.5F,
                        7.0F,
                        AnimationUtil.multiplyAlpha(clearHovered ? -658693 : -10529165, alpha));
                this.addClickRegion(
                        barX + barWidth - 16.0F,
                        barY + 3.0F,
                        13.0F,
                        14.0F,
                        button -> {
                            this.searchQuery = "";
                            this.contentAnimStart = this.elapsedTime;
                        });
            }

            this.addClickRegion(
                    barX, barY, barWidth, barHeight, button -> this.searchFocused = true);
        }
    }

    private void drawStatusFilter(DrawContext context, float alpha) {
        float left = 172.0F;
        float y = 48.0F;
        float height = 14.0F;
        int accent = ThemeModule.getAccentColorAt(0.4F);
        float[] xs = new float[FILTER_LABELS.length];
        float[] widths = new float[FILTER_LABELS.length];
        float cursorX = left;

        for (int i = 0; i < FILTER_LABELS.length; i++) {
            xs[i] = cursorX;
            widths[i] =
                    SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY_SMALL, FILTER_LABELS[i])
                            + 14.0F;
            cursorX += widths[i] + 4.0F;
        }

        SdfShapeRenderer.fill(
                context,
                left - 2.0F,
                y - 2.0F,
                cursorX - left,
                height + 4.0F,
                8.0F,
                AnimationUtil.multiplyAlpha(-16251379, alpha));
        SdfShapeRenderer.drawOutline(
                context,
                left - 2.0F,
                y - 2.0F,
                cursorX - left,
                height + 4.0F,
                8.0F,
                0.6F,
                AnimationUtil.multiplyAlpha(-14870999, alpha));
        this.filterIndicatorX =
                this.filterIndicatorX < 0.0F
                        ? xs[this.statusFilter]
                        : AnimationUtil.smoothApproach(
                                this.filterIndicatorX,
                                xs[this.statusFilter],
                                18.0F,
                                this.deltaTime);
        this.filterIndicatorWidth =
                this.filterIndicatorWidth <= 0.0F
                        ? widths[this.statusFilter]
                        : AnimationUtil.smoothApproach(
                                this.filterIndicatorWidth,
                                widths[this.statusFilter],
                                18.0F,
                                this.deltaTime);
        SdfShapeRenderer.fillVerticalGradient(
                context,
                this.filterIndicatorX,
                y,
                this.filterIndicatorWidth,
                height,
                6.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 150), alpha),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 80), alpha));

        for (int j = 0; j < FILTER_LABELS.length; j++) {
            boolean selected = j == this.statusFilter;
            float hoverProgress =
                    this.animate(
                            "filter:" + j,
                            this.isHovered(xs[j], y, widths[j], height) && !selected ? 1.0F : 0.0F,
                            14.0F);
            SdfFontRenderer.drawTextCentered(
                    context,
                    SdfFontRenderer.FontStyle.BODY_SMALL,
                    FILTER_LABELS[j],
                    xs[j] + widths[j] * 0.5F,
                    y + height * 0.5F,
                    AnimationUtil.multiplyAlpha(
                            selected
                                    ? -1
                                    : AnimationUtil.lerpColor(-6845010, -658693, hoverProgress),
                            alpha));
            int index = j;
            this.addClickRegion(
                    xs[j],
                    y,
                    widths[j],
                    height,
                    button -> {
                        if (this.statusFilter != index) {
                            this.statusFilter = index;
                            this.contentAnimStart = this.elapsedTime;
                            this.moduleScrollTarget = 0.0F;
                        }
                    });
        }
    }

    private void drawModuleRow(
            DrawContext context, Module module, float x, float y, float width, float alpha) {
        if (!(alpha <= 0.01F)) {
            int accent = ThemeModule.getAccentColorAt((y - 70.0F) / 310.0F);
            boolean active = module.isActive();
            boolean inspected = module == this.inspectedModule;
            boolean hovered = this.isHovered(x, y, width, 32.0F);
            float hoverProgress =
                    this.animate("row-hover:" + module.name, hovered ? 1.0F : 0.0F, 14.0F);
            float enabledProgress =
                    this.animate("row-on:" + module.name, active ? 1.0F : 0.0F, 10.0F);
            float inspectProgress =
                    this.animate("row-inspect:" + module.name, inspected ? 1.0F : 0.0F, 12.0F);
            if (hovered) {
                this.hoverDescription = module.description;
            }

            float rowY = y - hoverProgress;
            if (hoverProgress > 0.01F) {
                SdfShapeRenderer.drawShadow(
                        context,
                        x,
                        rowY + 2.0F,
                        width,
                        32.0F,
                        9.0F,
                        6.0F,
                        AnimationUtil.multiplyAlpha(-1879048192, hoverProgress * alpha));
            }

            SdfShapeRenderer.fill(
                    context,
                    x,
                    rowY,
                    width,
                    32.0F,
                    9.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(
                                    AnimationUtil.lerpColor(
                                            -16251379, -15265501, hoverProgress * 0.8F),
                                    200 + (int) (40.0F * hoverProgress)),
                            alpha));
            if (enabledProgress > 0.01F) {
                SdfShapeRenderer.fillVerticalGradient(
                        context,
                        x,
                        rowY,
                        width,
                        32.0F,
                        9.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.withAlpha(accent, 58), enabledProgress * alpha),
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.withAlpha(accent, 0), enabledProgress * alpha));
            }

            SdfShapeRenderer.drawOutline(
                    context,
                    x,
                    rowY,
                    width,
                    32.0F,
                    9.0F,
                    0.75F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(
                                    AnimationUtil.lerpColor(
                                            -14870999,
                                            AnimationUtil.withAlpha(accent, 200),
                                            enabledProgress * 0.45F),
                                    accent,
                                    inspectProgress),
                            alpha));
            if (enabledProgress > 0.01F) {
                float barHeight = 18.0F * AnimationUtil.easeOutBack(enabledProgress);
                SdfShapeRenderer.drawShadow(
                        context,
                        x + 1.5F,
                        rowY + (32.0F - barHeight) * 0.5F,
                        2.5F,
                        barHeight,
                        1.25F,
                        3.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.withAlpha(accent, 180), enabledProgress * alpha));
                SdfShapeRenderer.fill(
                        context,
                        x + 1.5F,
                        rowY + (32.0F - barHeight) * 0.5F,
                        2.5F,
                        barHeight,
                        1.25F,
                        AnimationUtil.multiplyAlpha(accent, enabledProgress * alpha));
            }

            float iconSize = 22.0F;
            float iconX = x + 7.0F;
            float iconY = rowY + (32.0F - iconSize) * 0.5F;
            SdfShapeRenderer.fill(
                    context,
                    iconX,
                    iconY,
                    iconSize,
                    iconSize,
                    6.5F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(
                                    -15659750,
                                    AnimationUtil.withAlpha(accent, 255),
                                    enabledProgress * 0.28F),
                            alpha));
            if (enabledProgress > 0.01F) {
                SdfShapeRenderer.drawOutline(
                        context,
                        iconX,
                        iconY,
                        iconSize,
                        iconSize,
                        6.5F,
                        0.7F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.withAlpha(accent, 170), enabledProgress * alpha));
            }

            drawModuleIcon(
                    context,
                    module,
                    iconX + iconSize * 0.5F,
                    iconY + iconSize * 0.5F,
                    19.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(
                                    AnimationUtil.lerpColor(-2699292, -1, hoverProgress),
                                    AnimationUtil.lerpColor(accent, -1, 0.45F),
                                    enabledProgress),
                            alpha));
            float textX = iconX + iconSize + 8.0F;
            float reserved = 62.0F;
            String keyName = KeyNames.getKeyName(module.keybind.getValue());
            boolean hasKey = module.keybind.getValue() != null && module.keybind.getValue() >= 0;
            float keyWidth =
                    hasKey
                            ? SdfFontRenderer.width(SdfFontRenderer.FontStyle.TINY, keyName) + 9.0F
                            : 0.0F;
            float textWidth = width - (textX - x) - reserved - (hasKey ? keyWidth + 6.0F : 0.0F);
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BOLD,
                    SdfFontRenderer.truncateToWidth(
                            SdfFontRenderer.FontStyle.BOLD, module.title, textWidth),
                    textX,
                    rowY + 11.5F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(
                                    AnimationUtil.lerpColor(-2501658, -658693, hoverProgress),
                                    -1,
                                    enabledProgress),
                            alpha));
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BODY_SMALL,
                    SdfFontRenderer.truncateToWidth(
                            SdfFontRenderer.FontStyle.BODY_SMALL, module.description, textWidth),
                    textX,
                    rowY + 21.5F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(
                                    -10529165, -6845010, 0.6F + hoverProgress * 0.4F),
                            alpha));
            float right = x + width - 8.0F;
            float toggleWidth = 22.0F;
            float toggleX = right - toggleWidth;
            float chevronX = toggleX - 14.0F;
            if (hasKey) {
                float keyX = chevronX - 10.0F - keyWidth;
                SdfShapeRenderer.fill(
                        context,
                        keyX,
                        rowY + 16.0F - 5.0F,
                        keyWidth,
                        10.0F,
                        3.5F,
                        AnimationUtil.multiplyAlpha(-15659750, alpha));
                SdfShapeRenderer.drawOutline(
                        context,
                        keyX,
                        rowY + 16.0F - 5.0F,
                        keyWidth,
                        10.0F,
                        3.5F,
                        0.6F,
                        AnimationUtil.multiplyAlpha(-14870999, alpha));
                SdfFontRenderer.drawTextCentered(
                        context,
                        SdfFontRenderer.FontStyle.TINY,
                        keyName,
                        keyX + keyWidth * 0.5F,
                        rowY + 16.0F,
                        AnimationUtil.multiplyAlpha(-6845010, alpha));
            }

            boolean chevronHovered =
                    this.isHovered(chevronX - 7.0F, rowY + 16.0F - 7.0F, 14.0F, 14.0F);
            float chevronHover =
                    this.animate(
                            "chevron-hover:" + module.name, chevronHovered ? 1.0F : 0.0F, 16.0F);
            if (chevronHover > 0.01F) {
                SdfShapeRenderer.fillCircle(
                        context,
                        chevronX,
                        rowY + 16.0F,
                        7.0F,
                        AnimationUtil.multiplyAlpha(-15265501, chevronHover * alpha));
            }

            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(chevronX + chevronHover * 0.8F, rowY + 16.0F);
            matrices.rotate(3.1415927F * AnimationUtil.easeInOutCubic(inspectProgress));
            SdfFontRenderer.drawIcon(
                    context,
                    "\ue009",
                    0.0F,
                    0.0F,
                    8.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(
                                    AnimationUtil.lerpColor(-10529165, -658693, chevronHover),
                                    accent,
                                    inspectProgress),
                            alpha));
            matrices.popMatrix();
            this.drawToggle(
                    context, "module:" + module.name, toggleX, rowY + 16.0F - 6.0F, active, alpha);
            this.addClickRegion(
                    x,
                    rowY,
                    width,
                    32.0F,
                    button -> {
                        if (button == 1) {
                            this.toggleInspector(module);
                        } else if (button == 0) {
                            module.toggle();
                        } else if (button == 2) {
                            this.listeningKeybind = module.keybind;
                        }
                    });
            this.addClickRegion(
                    chevronX - 8.0F,
                    rowY + 4.0F,
                    16.0F,
                    24.0F,
                    button -> this.toggleInspector(module));
        }
    }

    private void toggleInspector(Module module) {
        if (this.inspectedModule == module) {
            this.inspectedModule = null;
        } else {
            this.inspectedModule = module;
            this.settingsScroll = this.settingsScrollTarget = 0.0F;
            this.inspectorAnimStart = this.elapsedTime;
            this.editingSetting = null;
        }
    }

    private void drawModuleInspector(DrawContext context, float alpha) {
        this.inspectorProgress =
                AnimationUtil.smoothApproach(
                        this.inspectorProgress,
                        this.inspectedModule != null ? 1.0F : 0.0F,
                        this.inspectedModule != null ? 11.0F : 15.0F,
                        this.deltaTime);
        if (this.inspectedModule != null) {
            this.lastInspectedModule = this.inspectedModule;
        }

        Module module = this.lastInspectedModule;
        if (module != null && !(this.inspectorProgress < 0.01F)) {
            float eased = AnimationUtil.easeOutCubic(this.inspectorProgress);
            float x = 406.0F + (1.0F - eased) * 24.0F;
            float y = 10.0F;
            float height = 360.0F;
            float panelAlpha = alpha * eased;
            int accent = ThemeModule.getAccentColorAt(0.6F);
            float intro =
                    AnimationUtil.easeOutCubic(
                            AnimationUtil.progress(
                                    this.elapsedTime - this.inspectorAnimStart, 0.0F, 0.3F));
            SdfShapeRenderer.drawShadow(
                    context,
                    x - 2.0F,
                    y + 2.0F,
                    188.0F,
                    height,
                    12.0F,
                    10.0F,
                    AnimationUtil.multiplyAlpha(-1073741824, panelAlpha));
            SdfShapeRenderer.fill(
                    context,
                    x,
                    y,
                    184.0F,
                    height,
                    12.0F,
                    AnimationUtil.multiplyAlpha(-16251379, panelAlpha));
            SdfShapeRenderer.fillHorizontalGradient(
                    context,
                    x,
                    y,
                    184.0F,
                    70.0F,
                    12.0F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 40), panelAlpha),
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 0), panelAlpha));
            SdfShapeRenderer.drawOutline(
                    context,
                    x,
                    y,
                    184.0F,
                    height,
                    12.0F,
                    0.75F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-14870999, accent, 0.35F), panelAlpha));
            float contentAlpha = panelAlpha * intro;
            float slide = (1.0F - intro) * 6.0F;
            float iconSize = 24.0F;
            SdfShapeRenderer.fill(
                    context,
                    x + 10.0F,
                    y + 10.0F + slide,
                    iconSize,
                    iconSize,
                    7.0F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 60), contentAlpha));
            SdfShapeRenderer.drawOutline(
                    context,
                    x + 10.0F,
                    y + 10.0F + slide,
                    iconSize,
                    iconSize,
                    7.0F,
                    0.7F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, 170), contentAlpha));
            drawModuleIcon(
                    context,
                    module,
                    x + 10.0F + iconSize * 0.5F,
                    y + 10.0F + iconSize * 0.5F + slide,
                    20.0F,
                    AnimationUtil.multiplyAlpha(-1, contentAlpha));
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BOLD,
                    SdfFontRenderer.truncateToWidth(
                            SdfFontRenderer.FontStyle.BOLD, module.title, 120.0F),
                    x + 40.0F,
                    y + 17.0F + slide,
                    AnimationUtil.multiplyAlpha(-658693, contentAlpha));
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.TINY,
                    module.isActive() ? "ENABLED" : "DISABLED",
                    x + 40.0F,
                    y + 27.0F + slide,
                    AnimationUtil.multiplyAlpha(
                            module.isActive() ? accent : -10529165, contentAlpha));
            boolean closeHovered = this.isHovered(x + 184.0F - 22.0F, y + 10.0F, 13.0F, 13.0F);
            float closeHover = this.animate("panel-close", closeHovered ? 1.0F : 0.0F, 16.0F);
            SdfShapeRenderer.fillCircle(
                    context,
                    x + 184.0F - 15.5F,
                    y + 16.5F,
                    6.5F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-15659750, -15265501, closeHover), panelAlpha));
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x + 184.0F - 15.5F, y + 16.5F);
            matrices.rotate(closeHover * 3.1415927F * 0.5F);
            SdfFontRenderer.drawIcon(
                    context,
                    "\ue00b",
                    0.0F,
                    0.0F,
                    7.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-6845010, -658693, closeHover), panelAlpha));
            matrices.popMatrix();
            this.addClickRegion(
                    x + 184.0F - 22.0F,
                    y + 10.0F,
                    13.0F,
                    13.0F,
                    button -> this.inspectedModule = null);
            List<String> descriptionLines =
                    wrapText(SdfFontRenderer.FontStyle.BODY_SMALL, module.description, 164.0F, 3);
            float lineY = y + 44.0F + slide;

            for (String line : descriptionLines) {
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        line,
                        x + 10.0F,
                        lineY,
                        AnimationUtil.multiplyAlpha(-6845010, contentAlpha));
                lineY += 8.5F;
            }

            float enabledY = lineY + 4.0F;
            float rowX = x + 8.0F;
            float rowWidth = 168.0F;
            this.drawInspectorRowBackground(context, rowX, enabledY, rowWidth, 20.0F, contentAlpha);
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BODY,
                    "Enabled",
                    rowX + 8.0F,
                    enabledY + 10.0F,
                    AnimationUtil.multiplyAlpha(-658693, contentAlpha));
            this.drawToggle(
                    context,
                    "panel-enabled:" + module.name,
                    rowX + rowWidth - 30.0F,
                    enabledY + 4.0F,
                    module.isActive(),
                    contentAlpha);
            this.addClickRegion(rowX, enabledY, rowWidth, 20.0F, button -> module.toggle());
            float settingsLabelY = enabledY + 30.0F;
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.TINY,
                    "SETTINGS",
                    rowX + 2.0F,
                    settingsLabelY - 3.0F,
                    AnimationUtil.multiplyAlpha(-10529165, contentAlpha));
            float listTop = settingsLabelY + 3.0F;
            float listBottom = y + height - 26.0F;
            List<Setting<?>> settings = getSortedSettings(module);
            float totalHeight = 0.0F;

            for (Setting<?> setting : settings) {
                totalHeight += getSettingHeight(setting) + 3.0F;
            }

            this.settingsScrollMax = Math.max(0.0F, totalHeight - (listBottom - listTop));
            this.settingsScrollTarget =
                    clampFloat(this.settingsScrollTarget, 0.0F, this.settingsScrollMax);
            this.settingsScroll =
                    AnimationUtil.smoothApproach(
                            this.settingsScroll, this.settingsScrollTarget, 16.0F, this.deltaTime);
            this.pushScissor(context, x + 2.0F, listTop, 180.0F, listBottom - listTop);
            float settingY = listTop - this.settingsScroll;
            int i = 0;

            for (Setting<?> entry : settings) {
                float settingHeight = getSettingHeight(entry);
                if (settingY + settingHeight >= listTop - 2.0F && settingY <= listBottom + 2.0F) {
                    float reveal =
                            AnimationUtil.easeOutCubic(
                                    AnimationUtil.progress(
                                            this.elapsedTime - this.inspectorAnimStart,
                                            0.08F + i * 0.025F,
                                            0.38F + i * 0.025F));
                    this.drawSettingRow(
                            context,
                            module,
                            entry,
                            rowX,
                            settingY + (1.0F - reveal) * 8.0F,
                            rowWidth,
                            settingHeight,
                            panelAlpha * reveal);
                }

                settingY += settingHeight + 3.0F;
                i++;
            }

            if (settings.isEmpty()) {
                SdfFontRenderer.drawTextCentered(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        "No extra settings",
                        x + 92.0F,
                        listTop + 18.0F,
                        AnimationUtil.multiplyAlpha(-10529165, contentAlpha));
            }

            this.popScissor(context);
            this.drawScrollFade(
                    context, x + 2.0F, listTop, 180.0F, listBottom - listTop, panelAlpha);
            this.drawScrollbar(
                    context,
                    x + 184.0F - 5.0F,
                    listTop,
                    listBottom - listTop,
                    this.settingsScroll,
                    this.settingsScrollMax,
                    panelAlpha);
            String footer =
                    this.hoverDescription != null && this.isHovered(x, y, 184.0F, height)
                            ? this.hoverDescription
                            : "Right-click a module to open its settings";
            SdfShapeRenderer.fill(
                    context,
                    x + 8.0F,
                    y + height - 21.0F,
                    168.0F,
                    0.6F,
                    0.0F,
                    AnimationUtil.multiplyAlpha(-14870999, panelAlpha));
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BODY_SMALL,
                    SdfFontRenderer.truncateToWidth(
                            SdfFontRenderer.FontStyle.BODY_SMALL, footer, 164.0F),
                    x + 10.0F,
                    y + height - 11.0F,
                    AnimationUtil.multiplyAlpha(-10529165, panelAlpha));
        }
    }

    private void drawInspectorRowBackground(
            DrawContext context, float x, float y, float width, float height, float alpha) {
        boolean hovered = this.isHovered(x, y, width, height);
        float hoverProgress =
                this.animate(
                        "panel-row:" + (int) x + ":" + (int) (y * 4.0F),
                        hovered ? 1.0F : 0.0F,
                        16.0F);
        SdfShapeRenderer.fill(
                context,
                x,
                y,
                width,
                height,
                6.5F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-15659750, -15265501, hoverProgress), alpha));
    }

    private void drawSettingTooltip(DrawContext context, float alpha) {
        boolean suppressed =
                this.closing
                        || this.namingConfig
                        || this.draggingPanel != null
                        || this.draggingSlider != null
                        || this.editingSetting != null
                        || this.listeningKeybind != null;

        for (float[] region : this.layoutToggleRegions) {
            if (this.isRawMouseInside(region[0], region[1], region[2], region[3])) {
                suppressed = true;
            }
        }

        if (this.openDropdown != null && this.dropdownBounds != null) {
            float[] bounds = this.dropdownBounds;
            if (this.mouseX >= bounds[0]
                    && this.mouseY >= bounds[1]
                    && this.mouseX < bounds[0] + bounds[2]
                    && this.mouseY < bounds[1] + bounds[3]) {
                suppressed = true;
            }
        }

        this.tooltipRenderer.render(
                context,
                suppressed ? null : this.hoveredSetting,
                this.mouseScreenX,
                this.mouseScreenY,
                this.width,
                this.height,
                this.scale,
                alpha);
    }

    private void drawSettingRow(
            DrawContext context,
            Module module,
            Setting<?> setting,
            float x,
            float y,
            float width,
            float height,
            float alpha) {
        if (!(alpha <= 0.01F)) {
            int accent = ThemeModule.getAccentColorAt(0.7F);
            boolean hovered = this.isHovered(x, y, width, height);
            float hoverProgress =
                    this.animate(
                            "setting-hover:" + System.identityHashCode(setting),
                            hovered ? 1.0F : 0.0F,
                            16.0F);
            if (hovered) {
                this.hoverDescription = setting.description;
                this.hoveredSetting = setting;
            }

            SdfShapeRenderer.fill(
                    context,
                    x,
                    y,
                    width,
                    height,
                    6.5F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-15659750, -15265501, hoverProgress), alpha));
            boolean slider = setting instanceof IntSetting || setting instanceof NumberSetting;
            float labelY = slider ? y + 9.0F : y + height * 0.5F;
            float right = x + width - 6.0F;
            float labelWidth = width - 16.0F - (slider ? 30.0F : 62.0F);
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BODY,
                    SdfFontRenderer.truncateToWidth(
                            SdfFontRenderer.FontStyle.BODY, setting.title, labelWidth),
                    x + 8.0F,
                    labelY,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-2501658, -658693, hoverProgress), alpha));
            if (setting instanceof BooleanSetting booleanSetting) {
                this.drawToggle(
                        context,
                        setting,
                        right - 22.0F,
                        y + height * 0.5F - 6.0F,
                        Boolean.TRUE.equals(booleanSetting.getValue()),
                        alpha);
                this.addClickRegion(
                        x,
                        y,
                        width,
                        height,
                        button -> {
                            if (button == 0) {
                                booleanSetting.setValue(
                                        !Boolean.TRUE.equals(booleanSetting.getValue()));
                            }
                        });
            } else if (setting instanceof IntSetting intSetting) {
                this.drawSlider(
                        context,
                        setting,
                        x + 8.0F,
                        y + 20.0F,
                        width - 16.0F,
                        normalizeSliderValue(
                                intSetting.getValue().intValue(),
                                intSetting.sliderMin,
                                intSetting.sliderMax),
                        Integer.toString(intSetting.getValue()),
                        right,
                        labelY,
                        alpha);
            } else if (setting instanceof NumberSetting numberSetting) {
                this.drawSlider(
                        context,
                        setting,
                        x + 8.0F,
                        y + 20.0F,
                        width - 16.0F,
                        normalizeSliderValue(
                                numberSetting.getValue(),
                                numberSetting.sliderMin,
                                numberSetting.sliderMax),
                        formatNumber(numberSetting.getValue(), getDecimalPlaces(numberSetting)),
                        right,
                        labelY,
                        alpha);
            } else if (setting instanceof KeybindSetting keybindSetting) {
                this.drawKeybindButton(context, keybindSetting, right, y + height * 0.5F, alpha);
                this.addClickRegion(
                        x,
                        y,
                        width,
                        height,
                        button -> {
                            if (button == 1) {
                                keybindSetting.setValue(-1);
                            } else if (button == 0) {
                                this.listeningKeybind = keybindSetting;
                            }
                        });
            } else if (setting instanceof ColorSetting colorSetting) {
                EntityColor color = colorSetting.getValue();
                int swatch = color == null ? accent : 0xFF000000 | color.toArgb() & 16777215;
                SdfShapeRenderer.drawShadow(
                        context,
                        right - 16.0F,
                        y + height * 0.5F - 5.0F,
                        16.0F,
                        10.0F,
                        5.0F,
                        3.0F,
                        AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(swatch, 110), alpha));
                SdfShapeRenderer.fill(
                        context,
                        right - 16.0F,
                        y + height * 0.5F - 5.0F,
                        16.0F,
                        10.0F,
                        5.0F,
                        AnimationUtil.multiplyAlpha(swatch, alpha));
                SdfShapeRenderer.drawOutline(
                        context,
                        right - 16.0F,
                        y + height * 0.5F - 5.0F,
                        16.0F,
                        10.0F,
                        5.0F,
                        0.7F,
                        AnimationUtil.multiplyAlpha(1090519039, alpha));
                this.addClickRegion(
                        x,
                        y,
                        width,
                        height,
                        button -> {
                            if (button == 0) {
                                ColorPickerScreen.open(this, colorSetting);
                            }
                        });
            } else if (setting instanceof EnumSetting enumSetting) {
                this.drawEnumSelector(context, enumSetting, right, y + height * 0.5F, alpha);
                this.addClickRegion(
                        x,
                        y,
                        width,
                        height,
                        button -> {
                            if (button != 0
                                    || !module.name.equals("sky-arts")
                                    || !setting.name.equals("artwork")
                                            && !setting.name.equals("sky")) {
                                cycleEnum(enumSetting, button == 1 ? -1 : 1);
                                this.animations.put(
                                        "choice-bump:" + System.identityHashCode(enumSetting),
                                        button == 1 ? -1.0F : 1.0F);
                            } else {
                                ArtworkGalleryScreen.open(this, enumSetting);
                            }
                        });
            } else if (setting instanceof BlockColorMapSetting blockColors) {
                this.drawValueButton(
                        context,
                        blockColors.getValue().size() + " blocks",
                        right,
                        y + height * 0.5F,
                        false,
                        alpha);
                this.addClickRegion(
                        x, y, width, height, button -> SelectorScreen.open(this, blockColors));
            } else if (setting instanceof BlockListSetting blockList) {
                this.drawValueButton(
                        context,
                        blockList.getValue().size() + " blocks",
                        right,
                        y + height * 0.5F,
                        false,
                        alpha);
                this.addClickRegion(
                        x, y, width, height, button -> SelectorScreen.open(this, blockList));
            } else if (setting instanceof EntityTypeListSetting entityList) {
                this.drawValueButton(
                        context,
                        entityList.getValue().size() + " entities",
                        right,
                        y + height * 0.5F,
                        false,
                        alpha);
                this.addClickRegion(
                        x, y, width, height, button -> SelectorScreen.open(this, entityList));
            } else if (module instanceof GambleRigger gambleRigger
                    && ("dropper-1".equals(setting.name) || "dropper-2".equals(setting.name))) {
                this.drawValueButton(context, "Open", right, y + height * 0.5F, true, alpha);
                this.addClickRegion(
                        x,
                        y,
                        width,
                        height,
                        button -> {
                            if (button == 0) {
                                DropperEditorScreen.open(
                                        this,
                                        gambleRigger,
                                        "dropper-1".equals(setting.name) ? 0 : 1);
                            }
                        });
            } else if (module instanceof GambleRigger rigger
                    && GambleRigger.isSelectSetting(setting.name)) {
                this.drawValueButton(
                        context,
                        rigger.getSelectButtonLabel(setting.name),
                        right,
                        y + height * 0.5F,
                        true,
                        alpha);
                this.addClickRegion(
                        x,
                        y,
                        width,
                        height,
                        button -> {
                            if (button == 0) {
                                rigger.beginSelection(setting.name, this);
                            }
                        });
            } else if ("macro-manager".equals(setting.name)
                    && module instanceof ChatMacros chatMacros) {
                this.drawValueButton(context, "Open", right, y + height * 0.5F, true, alpha);
                this.addClickRegion(
                        x, y, width, height, button -> ChatMacroScreen.open(this, chatMacros));
            } else if ("edit-position".equals(setting.name)) {
                this.drawValueButton(context, "Edit", right, y + height * 0.5F, true, alpha);
                this.addClickRegion(x, y, width, height, button -> this.openPositionEditor(module));
            } else {
                boolean editing = setting == this.editingSetting;
                String text = editing ? this.editBuffer : String.valueOf(setting.getValue());
                this.drawValueButton(
                        context,
                        SdfFontRenderer.truncateToWidth(
                                        SdfFontRenderer.FontStyle.BODY_SMALL, text, 64.0F)
                                + (editing && isCaretVisible() ? "|" : ""),
                        right,
                        y + height * 0.5F,
                        editing,
                        alpha);
                this.addClickRegion(
                        x,
                        y,
                        width,
                        height,
                        button -> {
                            if (button == 0) {
                                this.editingSetting = setting;
                                this.editBuffer = String.valueOf(setting.getValue());
                            }
                        });
            }
        }
    }

    private void drawSlider(
            DrawContext context,
            Setting<?> setting,
            float x,
            float y,
            float width,
            double value,
            String label,
            float labelRight,
            float labelY,
            float alpha) {
        int accent = ThemeModule.getAccentColorAt(0.8F);
        boolean dragging = this.draggingSlider == setting;
        boolean hovered = this.isHovered(x - 3.0F, y - 5.0F, width + 6.0F, 10.0F);
        float focus =
                this.animate(
                        "slider-focus:" + System.identityHashCode(setting),
                        !dragging && !hovered ? 0.0F : 1.0F,
                        16.0F);
        float fill =
                this.animate(
                        "slider-value:" + System.identityHashCode(setting),
                        (float) clampDouble(value, 0.0, 1.0),
                        dragging ? 40.0F : 14.0F);
        float trackHeight = 3.0F + focus;
        SdfShapeRenderer.fill(
                context,
                x,
                y - trackHeight * 0.5F,
                width,
                trackHeight,
                trackHeight * 0.5F,
                AnimationUtil.multiplyAlpha(-14410701, alpha));
        if (fill > 0.001F) {
            SdfShapeRenderer.fillVerticalGradient(
                    context,
                    x,
                    y - trackHeight * 0.5F,
                    Math.max(trackHeight, width * fill),
                    trackHeight,
                    trackHeight * 0.5F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(accent, -16777216, 0.25F), alpha),
                    AnimationUtil.multiplyAlpha(accent, alpha));
        }

        float knobX = x + width * fill;
        float knobRadius = 3.2F + focus * 1.2F;
        SdfShapeRenderer.drawShadow(
                context,
                knobX - knobRadius,
                y - knobRadius,
                knobRadius * 2.0F,
                knobRadius * 2.0F,
                knobRadius,
                3.0F + focus * 2.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 150), alpha));
        SdfShapeRenderer.fillCircle(
                context, knobX, y, knobRadius, AnimationUtil.multiplyAlpha(-1, alpha));
        float labelWidth =
                SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY_SMALL, label) + 8.0F;
        SdfShapeRenderer.fill(
                context,
                labelRight - labelWidth,
                labelY - 5.0F,
                labelWidth,
                10.0F,
                5.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                -16251379, AnimationUtil.withAlpha(accent, 255), 0.25F * focus),
                        alpha));
        SdfFontRenderer.drawTextCentered(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                label,
                labelRight - labelWidth * 0.5F,
                labelY,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-6845010, -658693, focus), alpha));
        this.addClickRegion(
                x - 3.0F,
                y - 12.0F,
                width + 6.0F,
                18.0F,
                button -> {
                    if (button == 0) {
                        this.draggingSlider = setting;
                        this.sliderTrackX = x;
                        this.sliderTrackWidth = width;
                        this.updateSliderFromMouse(this.mouseX);
                    }
                });
    }

    private void drawToggle(
            DrawContext context, Object id, float x, float y, boolean on, float alpha) {
        int accent = ThemeModule.getAccentColorAt(0.5F);
        float progress = this.animate("toggle:" + id, on ? 1.0F : 0.0F, 11.0F);
        float knobProgress =
                on
                        ? AnimationUtil.easeOutBack(progress)
                        : 1.0F - AnimationUtil.easeOutBack(1.0F - progress);
        float trackWidth = 22.0F;
        float trackHeight = 12.0F;
        if (progress > 0.01F) {
            SdfShapeRenderer.drawShadow(
                    context,
                    x,
                    y,
                    trackWidth,
                    trackHeight,
                    trackHeight * 0.5F,
                    4.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, 120), progress * alpha));
        }

        SdfShapeRenderer.fill(
                context,
                x,
                y,
                trackWidth,
                trackHeight,
                trackHeight * 0.5F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-14476496, accent, progress), alpha));
        SdfShapeRenderer.drawOutline(
                context,
                x,
                y,
                trackWidth,
                trackHeight,
                trackHeight * 0.5F,
                0.6F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                -13753024, AnimationUtil.lerpColor(accent, -1, 0.3F), progress),
                        alpha));
        float travel = trackWidth - trackHeight;
        float squash = (float) Math.sin(3.141592653589793 * AnimationUtil.clamp01(progress)) * 3.0F;
        float knobX = x + 2.0F + travel * knobProgress - squash * (on ? 1.0F : 0.0F) * 0.5F;
        SdfShapeRenderer.fill(
                context,
                knobX,
                y + 2.0F,
                trackHeight - 4.0F + squash * 0.5F,
                trackHeight - 4.0F,
                (trackHeight - 4.0F) * 0.5F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-7437149, -1, progress), alpha));
    }

    private void drawKeybindButton(
            DrawContext context, Setting<Integer> setting, float x, float y, float alpha) {
        boolean listening = this.listeningKeybind == setting;
        Integer keyCode = setting.getValue();
        String label =
                listening
                        ? "Press a key"
                        : (keyCode != null && keyCode >= 0 ? KeyNames.getKeyName(keyCode) : "None");
        this.drawValueButton(context, label, x, y, listening, alpha);
    }

    private void drawEnumSelector(
            DrawContext context, EnumSetting<?> setting, float right, float y, float alpha) {
        String label = formatEnumName(setting.getValue());
        String bumpKey = "choice-bump:" + System.identityHashCode(setting);
        float bump = this.animations.getOrDefault(bumpKey, 0.0F);
        this.animations.put(
                bumpKey, AnimationUtil.smoothApproach(bump, 0.0F, 14.0F, this.deltaTime));
        float width =
                Math.min(
                        84.0F,
                        SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY_SMALL, label) + 22.0F);
        float left = right - width;
        SdfShapeRenderer.fill(
                context,
                left,
                y - 6.0F,
                width,
                12.0F,
                5.0F,
                AnimationUtil.multiplyAlpha(-16251379, alpha));
        SdfShapeRenderer.drawOutline(
                context,
                left,
                y - 6.0F,
                width,
                12.0F,
                5.0F,
                0.6F,
                AnimationUtil.multiplyAlpha(-14870999, alpha));
        this.pushScissor(context, left + 8.0F, y - 6.0F, width - 16.0F, 12.0F);
        SdfFontRenderer.drawTextCentered(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BODY_SMALL, label, width - 18.0F),
                left + width * 0.5F + bump * 6.0F,
                y,
                AnimationUtil.multiplyAlpha(-658693, alpha * (1.0F - Math.abs(bump) * 0.8F)));
        this.popScissor(context);
        SdfFontRenderer.drawIcon(
                context,
                "\ue009",
                left + width - 5.5F,
                y,
                5.5F,
                AnimationUtil.multiplyAlpha(-10529165, alpha));
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(left + 5.5F, y);
        matrices.rotate(3.1415927F);
        SdfFontRenderer.drawIcon(
                context, "\ue009", 0.0F, 0.0F, 5.5F, AnimationUtil.multiplyAlpha(-10529165, alpha));
        matrices.popMatrix();
    }

    private void drawValueButton(
            DrawContext context, String text, float right, float y, boolean active, float alpha) {
        int accent = ThemeModule.getAccentColorAt(0.6F);
        float pulse = active ? 0.5F + 0.5F * (float) Math.sin(this.elapsedTime * 6.0F) : 0.0F;
        float width = SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY_SMALL, text) + 12.0F;
        float left = right - width;
        if (active) {
            SdfShapeRenderer.drawShadow(
                    context,
                    left,
                    y - 6.0F,
                    width,
                    12.0F,
                    5.0F,
                    4.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, (int) (60.0F + 60.0F * pulse)), alpha));
        }

        SdfShapeRenderer.fill(
                context,
                left,
                y - 6.0F,
                width,
                12.0F,
                5.0F,
                AnimationUtil.multiplyAlpha(
                        active ? AnimationUtil.lerpColor(-16251379, accent, 0.35F) : -16251379,
                        alpha));
        SdfShapeRenderer.drawOutline(
                context,
                left,
                y - 6.0F,
                width,
                12.0F,
                5.0F,
                0.6F,
                AnimationUtil.multiplyAlpha(active ? accent : -14870999, alpha));
        SdfFontRenderer.drawTextCentered(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                text,
                left + width * 0.5F,
                y,
                AnimationUtil.multiplyAlpha(active ? -1 : -6845010, alpha));
    }

    private void drawConfigsPage(DrawContext context, float width, float alpha) {
        float fade = this.getContentFade();
        int accent = ThemeModule.getAccentColorAt(0.3F);
        List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();
        this.drawSimpleHeader(
                context,
                "Config",
                " Library",
                profiles.size() + " of 3 slots used  \u00b7  save, load and share setups",
                alpha,
                fade);
        float y = 52.0F;

        for (int slot = 0; slot < 3; slot++) {
            float reveal =
                    AnimationUtil.easeOutCubic(
                            AnimationUtil.progress(
                                    this.elapsedTime - this.contentAnimStart,
                                    slot * 0.05F,
                                    slot * 0.05F + 0.35F));
            float slotAlpha = alpha * reveal;
            float slotY = y + (1.0F - reveal) * 10.0F;
            float slotHeight = 44.0F;
            if (slot >= profiles.size()) {
                SdfShapeRenderer.drawOutline(
                        context,
                        170.0F,
                        slotY,
                        width,
                        slotHeight,
                        10.0F,
                        0.75F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.withAlpha(-14870999, 200), slotAlpha));
                SdfFontRenderer.drawIcon(
                        context,
                        "\ue014",
                        190.0F,
                        slotY + slotHeight * 0.5F,
                        10.0F,
                        AnimationUtil.multiplyAlpha(-10529165, slotAlpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BODY,
                        "Empty slot",
                        204.0F,
                        slotY + slotHeight * 0.5F - 4.0F,
                        AnimationUtil.multiplyAlpha(-6845010, slotAlpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        "Save your current setup here",
                        204.0F,
                        slotY + slotHeight * 0.5F + 6.0F,
                        AnimationUtil.multiplyAlpha(-10529165, slotAlpha));
                boolean hovered = this.isHovered(170.0F, slotY, width, slotHeight);
                float hoverProgress =
                        this.animate("config-empty:" + slot, hovered ? 1.0F : 0.0F, 14.0F);
                if (hoverProgress > 0.01F) {
                    SdfShapeRenderer.fill(
                            context,
                            170.0F,
                            slotY,
                            width,
                            slotHeight,
                            10.0F,
                            AnimationUtil.multiplyAlpha(
                                    AnimationUtil.withAlpha(-15265501, 160),
                                    hoverProgress * slotAlpha));
                }

                this.addClickRegion(
                        170.0F,
                        slotY,
                        width,
                        slotHeight,
                        button -> {
                            this.namingConfig = true;
                            this.configNameInput = "";
                        });
            } else {
                ConfigProfileManager.Profile profile = profiles.get(slot);
                boolean active =
                        profile.name.equalsIgnoreCase(ConfigProfileManager.getActiveProfileName());
                boolean selected = slot == this.selectedConfigIndex;
                boolean profileHovered = this.isHovered(170.0F, slotY, width, slotHeight);
                float profileHover =
                        this.animate("config-hover:" + slot, profileHovered ? 1.0F : 0.0F, 14.0F);
                float selectProgress =
                        this.animate("config-select:" + slot, selected ? 1.0F : 0.0F, 12.0F);
                SdfShapeRenderer.fill(
                        context,
                        170.0F,
                        slotY,
                        width,
                        slotHeight,
                        10.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(-16251379, -15265501, profileHover),
                                slotAlpha));
                if (active) {
                    SdfShapeRenderer.fillVerticalGradient(
                            context,
                            170.0F,
                            slotY,
                            width,
                            slotHeight,
                            10.0F,
                            AnimationUtil.multiplyAlpha(
                                    AnimationUtil.withAlpha(accent, 60), slotAlpha),
                            AnimationUtil.multiplyAlpha(
                                    AnimationUtil.withAlpha(accent, 0), slotAlpha));
                }

                SdfShapeRenderer.drawOutline(
                        context,
                        170.0F,
                        slotY,
                        width,
                        slotHeight,
                        10.0F,
                        0.75F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(
                                        -14870999,
                                        accent,
                                        Math.max(selectProgress, active ? 0.4F : 0.0F)),
                                slotAlpha));
                SdfShapeRenderer.fill(
                        context,
                        178.0F,
                        slotY + 9.0F,
                        26.0F,
                        26.0F,
                        7.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(
                                        -15659750,
                                        AnimationUtil.withAlpha(accent, 255),
                                        active ? 0.3F : 0.0F),
                                slotAlpha));
                SdfFontRenderer.drawIcon(
                        context,
                        "\ue006",
                        191.0F,
                        slotY + 22.0F,
                        12.0F,
                        AnimationUtil.multiplyAlpha(active ? -1 : -6845010, slotAlpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BOLD,
                        SdfFontRenderer.truncateToWidth(
                                SdfFontRenderer.FontStyle.BOLD, profile.name, width - 170.0F),
                        212.0F,
                        slotY + 17.0F,
                        AnimationUtil.multiplyAlpha(-658693, slotAlpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.TINY,
                        active ? "ACTIVE CONFIG" : "LOCAL CONFIG",
                        212.0F,
                        slotY + 28.0F,
                        AnimationUtil.multiplyAlpha(active ? accent : -10529165, slotAlpha));
                int index = slot;
                this.addClickRegion(
                        170.0F,
                        slotY,
                        width,
                        slotHeight,
                        button -> this.selectedConfigIndex = index);
                float buttonRight = 170.0F + width - 10.0F;
                buttonRight =
                        this.drawIconButton(
                                context,
                                "\ue00f",
                                "Delete this config",
                                buttonRight,
                                slotY + slotHeight * 0.5F,
                                -41081,
                                slotAlpha,
                                () -> {
                                    if (this.selectedConfigIndex == index) {
                                        this.selectedConfigIndex = -1;
                                    }

                                    this.showResult(ConfigProfileManager.deleteProfile(index));
                                });
                buttonRight =
                        this.drawIconButton(
                                context,
                                "\ue00e",
                                "Overwrite with current settings",
                                buttonRight - 4.0F,
                                slotY + slotHeight * 0.5F,
                                -658693,
                                slotAlpha,
                                () -> this.showResult(ConfigProfileManager.updateProfile(index)));
                this.drawLabeledIconButton(
                        context,
                        "\ue011",
                        "Load",
                        "Apply this config",
                        buttonRight - 6.0F,
                        slotY + slotHeight * 0.5F,
                        accent,
                        slotAlpha,
                        () -> this.showResult(ConfigProfileManager.loadProfile(index)));
            }

            y += 50.0F;
        }

        float actionsY = y + 8.0F;
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TINY,
                "ACTIONS",
                172.0F,
                actionsY,
                AnimationUtil.multiplyAlpha(-10529165, alpha * fade));
        String[] actionLabels =
                new String[] {
                    "New",
                    "Import",
                    "Odin preset",
                    "Share",
                    System.currentTimeMillis() < this.resetConfirmDeadline
                            ? "Confirm reset"
                            : "Reset"
                };
        String[] actionIcons = new String[] {"\ue014", "\ue010", "\ue00d", "\ue012", "\ue013"};
        Runnable[] actions =
                new Runnable[] {
                    () -> {
                        this.namingConfig = true;
                        this.configNameInput = "";
                    },
                    () ->
                            this.showResult(
                                    ConfigProfileManager.importCode(
                                            this.client.keyboard.getClipboard())),
                    () -> {
                        List<ConfigProfileManager.BundledConfig> bundled =
                                ConfigProfileManager.getBundledConfigs();
                        this.showResult(
                                bundled.isEmpty()
                                        ? ConfigProfileManager.Result.failure(
                                                "Bundled Odin config is unavailable")
                                        : ConfigProfileManager.importCode(
                                                bundled.get(0).configJson));
                    },
                    () -> {
                        ConfigProfileManager.Result exported =
                                ConfigProfileManager.exportProfile(this.selectedConfigIndex);
                        if (exported.success && exported.code != null) {
                            this.client.keyboard.setClipboard(exported.code);
                        }

                        this.showResult(exported);
                    },
                    () -> {
                        long now = System.currentTimeMillis();
                        if (now < this.resetConfirmDeadline) {
                            this.resetConfirmDeadline = 0L;
                            this.showResult(ConfigProfileManager.resetToDefaults());
                        } else {
                            this.resetConfirmDeadline = now + 5000L;
                            this.showResult(
                                    ConfigProfileManager.Result.failure(
                                            "Click reset again to confirm"));
                        }
                    }
                };
        float buttonWidth = (width - 20.0F) / 5.0F;

        for (int i = 0; i < actionLabels.length; i++) {
            float buttonX = 170.0F + i * (buttonWidth + 5.0F);
            float buttonY = actionsY + 8.0F;
            float buttonReveal =
                    AnimationUtil.easeOutCubic(
                            AnimationUtil.progress(
                                    this.elapsedTime - this.contentAnimStart,
                                    0.15F + i * 0.04F,
                                    0.5F + i * 0.04F));
            this.drawActionButton(
                    context,
                    "config-action:" + i,
                    actionIcons[i],
                    actionLabels[i],
                    buttonX,
                    buttonY + (1.0F - buttonReveal) * 6.0F,
                    buttonWidth,
                    30.0F,
                    i == 4 && System.currentTimeMillis() < this.resetConfirmDeadline,
                    alpha * buttonReveal,
                    actions[i]);
        }

        String hint =
                this.hoverDescription != null
                        ? this.hoverDescription
                        : "Import reads a config code from your clipboard; Share copies the"
                              + " selected config.";
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                SdfFontRenderer.truncateToWidth(SdfFontRenderer.FontStyle.BODY_SMALL, hint, 410.0F),
                172.0F,
                actionsY + 50.0F,
                AnimationUtil.multiplyAlpha(
                        this.hoverDescription != null ? -6845010 : -10529165, alpha * fade));
        if (this.namingConfig) {
            this.drawConfigNamePrompt(context, alpha);
        }
    }

    private void drawConfigNamePrompt(DrawContext context, float alpha) {
        float intro = this.animate("config-naming", 1.0F, 12.0F);
        int accent = ThemeModule.getAccentColorAt(0.5F);
        float screenWidth = this.isPanelLayout() ? this.layoutWidth : 600.0F;
        float screenHeight = this.isPanelLayout() ? this.layoutHeight : 380.0F;
        this.addClickRegion(0.0F, 0.0F, screenWidth, screenHeight, button -> {});
        SdfShapeRenderer.fill(
                context,
                0.0F,
                0.0F,
                screenWidth,
                screenHeight,
                this.isPanelLayout() ? 0.0F : 16.0F,
                AnimationUtil.multiplyAlpha(-1342177280, intro * alpha));
        float boxWidth = 230.0F;
        float boxHeight = 96.0F;
        float boxX = (screenWidth - boxWidth) * 0.5F;
        float boxY =
                (screenHeight - boxHeight) * 0.5F
                        + (1.0F - AnimationUtil.easeOutBack(intro)) * 12.0F;
        SdfShapeRenderer.drawShadow(
                context,
                boxX,
                boxY + 3.0F,
                boxWidth,
                boxHeight,
                12.0F,
                12.0F,
                AnimationUtil.multiplyAlpha(-805306368, intro * alpha));
        SdfShapeRenderer.fill(
                context,
                boxX,
                boxY,
                boxWidth,
                boxHeight,
                12.0F,
                AnimationUtil.multiplyAlpha(-16251379, intro * alpha));
        SdfShapeRenderer.drawOutline(
                context,
                boxX,
                boxY,
                boxWidth,
                boxHeight,
                12.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-14870999, accent, 0.5F), intro * alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BOLD,
                "New config",
                boxX + 12.0F,
                boxY + 15.0F,
                AnimationUtil.multiplyAlpha(-658693, intro * alpha));
        SdfShapeRenderer.fill(
                context,
                boxX + 12.0F,
                boxY + 28.0F,
                boxWidth - 24.0F,
                22.0F,
                7.0F,
                AnimationUtil.multiplyAlpha(-16777216, intro * alpha));
        SdfShapeRenderer.drawOutline(
                context,
                boxX + 12.0F,
                boxY + 28.0F,
                boxWidth - 24.0F,
                22.0F,
                7.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(accent, intro * alpha));
        boolean empty = this.configNameInput.isEmpty();
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY,
                empty ? "Config name" : this.configNameInput + (isCaretVisible() ? "|" : ""),
                boxX + 20.0F,
                boxY + 39.0F,
                AnimationUtil.multiplyAlpha(empty ? -10529165 : -658693, intro * alpha));
        this.drawActionButton(
                context,
                "naming-cancel",
                "\ue00b",
                "Cancel",
                boxX + 12.0F,
                boxY + 60.0F,
                (boxWidth - 30.0F) * 0.5F,
                24.0F,
                false,
                intro * alpha,
                () -> {
                    this.namingConfig = false;
                    this.configNameInput = "";
                });
        this.drawActionButton(
                context,
                "naming-save",
                "\ue015",
                "Save",
                boxX + 18.0F + (boxWidth - 30.0F) * 0.5F,
                boxY + 60.0F,
                (boxWidth - 30.0F) * 0.5F,
                24.0F,
                true,
                intro * alpha,
                this::submitConfigName);
        if (!this.status.isEmpty() && System.currentTimeMillis() < this.statusExpiry) {
            SdfFontRenderer.drawTextCentered(
                    context,
                    SdfFontRenderer.FontStyle.BODY_SMALL,
                    this.status,
                    boxX + boxWidth * 0.5F,
                    boxY + boxHeight + 10.0F,
                    AnimationUtil.multiplyAlpha(
                            this.statusSuccess ? -10755160 : -41081, intro * alpha));
        }
    }

    private float drawIconButton(
            DrawContext context,
            String icon,
            String tooltip,
            float right,
            float centerY,
            int color,
            float alpha,
            Runnable action) {
        float size = 20.0F;
        float left = right - size;
        float top = centerY - size * 0.5F;
        boolean hovered = this.isHovered(left, top, size, size);
        float hoverProgress =
                this.animate(
                        "icon-button:" + icon + ":" + (int) (centerY * 4.0F),
                        hovered ? 1.0F : 0.0F,
                        16.0F);
        if (hovered) {
            this.hoverDescription = tooltip;
        }

        SdfShapeRenderer.fill(
                context,
                left,
                top,
                size,
                size,
                6.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                -15659750,
                                AnimationUtil.withAlpha(color, 255),
                                hoverProgress * 0.22F),
                        alpha));
        SdfShapeRenderer.drawOutline(
                context,
                left,
                top,
                size,
                size,
                6.0F,
                0.6F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-14870999, color, hoverProgress * 0.7F), alpha));
        SdfFontRenderer.drawIcon(
                context,
                icon,
                left + size * 0.5F,
                top + size * 0.5F - hoverProgress * 0.6F,
                9.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-6845010, color, 0.35F + hoverProgress * 0.65F),
                        alpha));
        this.addClickRegion(
                left,
                top,
                size,
                size,
                button -> {
                    if (button == 0) {
                        action.run();
                    }
                });
        return left;
    }

    private void drawActionButton(
            DrawContext context,
            String id,
            String icon,
            String label,
            float x,
            float y,
            float width,
            float height,
            boolean highlighted,
            float alpha,
            Runnable action) {
        int accent = ThemeModule.getAccentColorAt(0.5F);
        boolean hovered = this.isHovered(x, y, width, height);
        float hoverProgress = this.animate("action:" + id, hovered ? 1.0F : 0.0F, 16.0F);
        float press = this.animate("action-press:" + id, 0.0F, 10.0F);
        float inset = press * 1.2F;
        if (highlighted || hoverProgress > 0.01F) {
            SdfShapeRenderer.drawShadow(
                    context,
                    x,
                    y + 2.0F,
                    width,
                    height,
                    8.0F,
                    6.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, highlighted ? 90 : 50),
                            alpha * Math.max(highlighted ? 1.0F : 0.0F, hoverProgress)));
        }

        if (highlighted) {
            SdfShapeRenderer.fillVerticalGradient(
                    context,
                    x + inset,
                    y + inset,
                    width - inset * 2.0F,
                    height - inset * 2.0F,
                    8.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(accent, -1, hoverProgress * 0.12F), alpha),
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(accent, -16777216, 0.3F), alpha));
        } else {
            SdfShapeRenderer.fill(
                    context,
                    x + inset,
                    y + inset,
                    width - inset * 2.0F,
                    height - inset * 2.0F,
                    8.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-16251379, -15265501, hoverProgress), alpha));
            SdfShapeRenderer.drawOutline(
                    context,
                    x + inset,
                    y + inset,
                    width - inset * 2.0F,
                    height - inset * 2.0F,
                    8.0F,
                    0.7F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-14870999, accent, hoverProgress * 0.6F),
                            alpha));
        }

        float contentWidth =
                SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY_SMALL, label) + 12.0F;
        float contentX = x + (width - contentWidth) * 0.5F;
        SdfFontRenderer.drawIcon(
                context,
                icon,
                contentX + 4.0F,
                y + height * 0.5F,
                8.0F,
                AnimationUtil.multiplyAlpha(
                        highlighted ? -1 : AnimationUtil.lerpColor(-6845010, accent, hoverProgress),
                        alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BODY_SMALL, label, width - 18.0F),
                contentX + 11.0F,
                y + height * 0.5F,
                AnimationUtil.multiplyAlpha(
                        highlighted
                                ? -1
                                : AnimationUtil.lerpColor(-6845010, -658693, hoverProgress),
                        alpha));
        this.addClickRegion(
                x,
                y,
                width,
                height,
                button -> {
                    if (button == 0) {
                        this.animations.put("action-press:" + id, 1.0F);
                        action.run();
                    }
                });
    }

    private void drawKeybindsPage(DrawContext context, float width, float alpha) {
        float fade = this.getContentFade();
        List<Module> modules = this.modules(null, this.searchQuery, 0);
        int boundCount = 0;

        for (Module module : modules) {
            if (module.keybind.getValue() != null && module.keybind.getValue() >= 0) {
                boundCount++;
            }
        }

        this.drawPageHeader(
                context,
                "Key",
                "binds",
                boundCount
                        + " of "
                        + modules.size()
                        + " modules bound  \u00b7  right-click to clear",
                width,
                alpha,
                fade);
        float headerY = 50.0F;
        int accent = ThemeModule.getAccentColorAt(0.2F);
        SdfShapeRenderer.fillVerticalGradient(
                context,
                170.0F,
                headerY,
                width,
                24.0F,
                8.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 60), alpha * fade),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 10), alpha * fade));
        SdfShapeRenderer.drawOutline(
                context,
                170.0F,
                headerY,
                width,
                24.0F,
                8.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 140), alpha * fade));
        SdfFontRenderer.drawIcon(
                context,
                "\ue007",
                184.0F,
                headerY + 12.0F,
                10.0F,
                AnimationUtil.multiplyAlpha(-1, alpha * fade));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BOLD,
                "Open menu",
                196.0F,
                headerY + 12.0F,
                AnimationUtil.multiplyAlpha(-658693, alpha * fade));
        this.drawKeybindButton(
                context,
                GuiKeybind.setting(),
                170.0F + width - 8.0F,
                headerY + 12.0F,
                alpha * fade);
        this.addClickRegion(
                170.0F,
                headerY,
                width,
                24.0F,
                button -> {
                    if (button == 1) {
                        GuiKeybind.setting().setValue(48);
                    } else if (button == 0) {
                        this.listeningKeybind = GuiKeybind.setting();
                    }
                });
        float listTop = headerY + 30.0F;
        float listBottom = 370.0F;
        float rowHeight = 22.0F;
        this.moduleScrollMax =
                Math.max(0.0F, modules.size() * (rowHeight + 3.0F) - (listBottom - listTop) + 4.0F);
        this.moduleScrollTarget = clampFloat(this.moduleScrollTarget, 0.0F, this.moduleScrollMax);
        this.moduleScroll =
                AnimationUtil.smoothApproach(
                        this.moduleScroll, this.moduleScrollTarget, 16.0F, this.deltaTime);
        this.pushScissor(context, 166.0F, listTop, width + 8.0F, listBottom - listTop);
        float rowY = listTop - this.moduleScroll;

        for (int i = 0; i < modules.size(); i++) {
            Module entry = modules.get(i);
            if (rowY + rowHeight >= listTop && rowY <= listBottom) {
                float reveal =
                        AnimationUtil.easeOutCubic(
                                AnimationUtil.progress(
                                        this.elapsedTime - this.contentAnimStart,
                                        i * 0.02F,
                                        i * 0.02F + 0.3F));
                float rowAlpha = alpha * reveal;
                float y = rowY + (1.0F - reveal) * 8.0F;
                boolean hovered = this.isHovered(170.0F, y, width, rowHeight);
                float hoverProgress =
                        this.animate("key-row:" + entry.name, hovered ? 1.0F : 0.0F, 16.0F);
                SdfShapeRenderer.fill(
                        context,
                        170.0F,
                        y,
                        width,
                        rowHeight,
                        7.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(-16251379, -15265501, hoverProgress),
                                rowAlpha));
                drawModuleIcon(
                        context,
                        entry,
                        183.0F,
                        y + rowHeight * 0.5F,
                        14.0F,
                        AnimationUtil.multiplyAlpha(
                                entry.isActive() ? accent : -6845010, rowAlpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BODY,
                        entry.title,
                        195.0F,
                        y + rowHeight * 0.5F,
                        AnimationUtil.multiplyAlpha(-658693, rowAlpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        title(getCategoryKey(entry)),
                        200.0F + SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY, entry.title),
                        y + rowHeight * 0.5F,
                        AnimationUtil.multiplyAlpha(-10529165, rowAlpha));
                this.drawKeybindButton(
                        context,
                        entry.keybind,
                        170.0F + width - 8.0F,
                        y + rowHeight * 0.5F,
                        rowAlpha);
                this.addClickRegion(
                        170.0F,
                        y,
                        width,
                        rowHeight,
                        button -> {
                            if (button == 1) {
                                entry.keybind.setValue(-1);
                            } else if (button == 0) {
                                this.listeningKeybind = entry.keybind;
                            }
                        });
            }

            rowY += rowHeight + 3.0F;
        }

        this.popScissor(context);
        this.drawScrollFade(context, 166.0F, listTop, width + 8.0F, listBottom - listTop, alpha);
        this.drawScrollbar(
                context,
                170.0F + width + 3.0F,
                listTop,
                listBottom - listTop,
                this.moduleScroll,
                this.moduleScrollMax,
                alpha);
    }

    private void drawSimpleHeader(
            DrawContext context,
            String title,
            String suffix,
            String subtitle,
            float alpha,
            float fade) {
        int accent = ThemeModule.getAccentColorAt(0.1F);
        float slide = (1.0F - fade) * 6.0F;
        float titleWidth = SdfFontRenderer.width(SdfFontRenderer.FontStyle.TITLE, title);
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TITLE,
                title,
                172.0F - slide,
                25.0F,
                AnimationUtil.multiplyAlpha(accent, alpha * fade));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TITLE,
                suffix,
                172.0F + titleWidth - slide,
                25.0F,
                AnimationUtil.multiplyAlpha(-658693, alpha * fade));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                subtitle,
                172.0F - slide * 0.5F,
                38.0F,
                AnimationUtil.multiplyAlpha(-6845010, alpha * fade));
    }

    private void drawStatusToast(DrawContext context, float alpha) {
        long now = System.currentTimeMillis();
        float target = !this.status.isEmpty() && now < this.statusExpiry ? 1.0F : 0.0F;
        float progress = this.animate("status", target, 10.0F);
        if (!(progress < 0.01F)) {
            int color = this.statusSuccess ? -10755160 : -41081;
            float width =
                    SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY_SMALL, this.status)
                            + 30.0F;
            float x = 170.0F + (420.0F - width) * 0.5F;
            float y = 346.0F + (1.0F - AnimationUtil.easeOutBack(progress)) * 14.0F;
            SdfShapeRenderer.drawShadow(
                    context,
                    x,
                    y + 2.0F,
                    width,
                    20.0F,
                    10.0F,
                    8.0F,
                    AnimationUtil.multiplyAlpha(-1073741824, progress * alpha));
            SdfShapeRenderer.fill(
                    context,
                    x,
                    y,
                    width,
                    20.0F,
                    10.0F,
                    AnimationUtil.multiplyAlpha(-15659750, progress * alpha));
            SdfShapeRenderer.drawOutline(
                    context,
                    x,
                    y,
                    width,
                    20.0F,
                    10.0F,
                    0.75F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(color, 180), progress * alpha));
            SdfFontRenderer.drawIcon(
                    context,
                    this.statusSuccess ? "\ue015" : "\ue00b",
                    x + 11.0F,
                    y + 10.0F,
                    8.0F,
                    AnimationUtil.multiplyAlpha(color, progress * alpha));
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BODY_SMALL,
                    this.status,
                    x + 20.0F,
                    y + 10.0F,
                    AnimationUtil.multiplyAlpha(-658693, progress * alpha));
        }
    }

    private void drawScrollFade(
            DrawContext context, float x, float y, float width, float height, float alpha) {
        SdfShapeRenderer.fillHorizontalGradient(
                context,
                x,
                y - 0.5F,
                width,
                8.0F,
                0.0F,
                AnimationUtil.multiplyAlpha(-16777216, alpha),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(-16777216, 0), alpha));
        SdfShapeRenderer.fillHorizontalGradient(
                context,
                x,
                y + height - 10.0F,
                width,
                10.5F,
                0.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(-16777216, 0), alpha),
                AnimationUtil.multiplyAlpha(-16777216, alpha));
    }

    private void drawScrollbar(
            DrawContext context,
            float x,
            float y,
            float height,
            float scroll,
            float maxScroll,
            float alpha) {
        float visibility =
                this.animate("scrollbar:" + (int) x, maxScroll > 1.0F ? 1.0F : 0.0F, 10.0F);
        if (!(visibility < 0.01F) && !(maxScroll <= 0.0F)) {
            float thumbHeight = Math.max(20.0F, height * height / (height + maxScroll));
            float thumbY = y + (height - thumbHeight) * (scroll / maxScroll);
            SdfShapeRenderer.fill(
                    context,
                    x,
                    y,
                    2.0F,
                    height,
                    1.0F,
                    AnimationUtil.multiplyAlpha(352321535, alpha * visibility));
            SdfShapeRenderer.fill(
                    context,
                    x,
                    thumbY,
                    2.0F,
                    thumbHeight,
                    1.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(ThemeModule.getAccentColorAt(0.5F), 170),
                            alpha * visibility));
        }
    }

    private float getContentFade() {
        return AnimationUtil.easeOutCubic(
                AnimationUtil.progress(this.elapsedTime - this.contentAnimStart, 0.0F, 0.35F));
    }

    private boolean isPanelLayout() {
        return ThemeModule.getGuiStyle() == ThemeModule.GuiStyle.PANELS;
    }

    private static void setLayout(ThemeModule.GuiStyle style) {
        if (ModuleManager.get().getByName("theme") instanceof ThemeModule theme) {
            theme.guiStyle.setValue(style);
        }
    }

    private void renderPanelLayout(DrawContext context, int mouseX, int mouseY) {
        float screenWidth = this.width;
        float screenHeight = this.height;
        long visiblePanels =
                Arrays.stream(PANEL_CATEGORIES)
                        .filter(name -> !this.hiddenPanels.contains(name))
                        .count();
        float requiredWidth = 166.0F + (float) Math.max(5L, visiblePanels) * 158.0F;
        this.scale =
                GuiScale.getWidthFitScale(ScreenScaleCompat.getScaledWidth(), requiredWidth)
                        * ScreenScaleCompat.getScale(this.width);
        this.layoutOffsetX = 0.0F;
        this.layoutOffsetY = 0.0F;
        this.layoutWidth = screenWidth / this.scale;
        this.layoutHeight = screenHeight / this.scale;
        this.mouseX = mouseX / this.scale;
        this.mouseY = mouseY / this.scale;
        this.mouseScreenX = mouseX;
        this.mouseScreenY = mouseY;
        float progress = AnimationUtil.clamp01(this.openProgress);
        context.fill(
                0,
                0,
                this.width,
                this.height,
                AnimationUtil.multiplyAlpha(-16777216, 0.42F * progress));
        this.clickRegions.clear();
        this.hoverDescription = null;
        this.panelScrollBounds.clear();
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(this.scale, this.scale);
        this.drawPanelSearchBar(context, progress);
        this.drawPanelHub(context, 8.0F, 34.0F, progress);

        for (int i = 0; i < PANEL_CATEGORIES.length; i++) {
            String category = PANEL_CATEGORIES[i];
            int index = i;
            float[] position =
                    this.panelPositions.computeIfAbsent(
                            category, name -> new float[] {166.0F + index * 158.0F, 34.0F});
            float shown =
                    this.animate(
                            "panel-shown:" + category,
                            this.hiddenPanels.contains(category) ? 0.0F : 1.0F,
                            12.0F);
            if (!(shown < 0.01F)) {
                float reveal = AnimationUtil.clamp01(progress * 1.4F - i * 0.08F);
                float eased = AnimationUtil.easeOutBack(reveal);
                this.drawCategoryPanel(
                        context,
                        category,
                        position[0],
                        position[1] - (1.0F - eased) * 14.0F,
                        progress * shown * reveal);
            }
        }

        this.drawDropdown(context, progress);
        if (this.namingConfig) {
            this.drawConfigNamePrompt(context, progress);
        }

        matrices.popMatrix();
        this.drawLayoutSwitcher(context, progress);
        ModuleNotifications.renderOverMenu(context, this.width, this.height);
        this.drawSettingTooltip(context, progress);
    }

    private void drawPanelSearchBar(DrawContext context, float alpha) {
        float focus = this.animate("search-focus", this.searchFocused ? 1.0F : 0.0F, 12.0F);
        float width = 170.0F + 50.0F * AnimationUtil.easeOutCubic(focus);
        float height = 18.0F;
        float x = (this.layoutWidth - width) * 0.5F;
        float y = 8.0F;
        int accent = ThemeModule.getAccentColorAt(0.5F);
        boolean hovered = this.isHovered(x, y, width, height);
        float hoverProgress = this.animate("search-hover", hovered ? 1.0F : 0.0F, 14.0F);
        if (focus > 0.01F) {
            SdfShapeRenderer.drawShadow(
                    context,
                    x,
                    y,
                    width,
                    height,
                    7.0F,
                    6.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, 70), focus * alpha));
        }

        SdfShapeRenderer.fill(
                context,
                x,
                y,
                width,
                height,
                7.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                -16251379, -15659750, Math.max(hoverProgress, focus)),
                        alpha));
        SdfShapeRenderer.drawOutline(
                context,
                x,
                y,
                width,
                height,
                7.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-14870999, accent, focus), alpha));
        SdfFontRenderer.drawIcon(
                context,
                "\ue008",
                x + 10.0F,
                y + height * 0.5F,
                8.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-10529165, accent, focus), alpha));
        boolean empty = this.searchQuery.isEmpty();
        String shown =
                SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        empty ? "Cracked by bob" : this.searchQuery,
                        width - 34.0F);
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                shown,
                x + 19.0F,
                y + height * 0.5F,
                AnimationUtil.multiplyAlpha(empty ? -10529165 : -658693, alpha));
        if (this.searchFocused && isCaretVisible()) {
            SdfShapeRenderer.fill(
                    context,
                    x
                            + 19.5F
                            + (empty
                                    ? 0.0F
                                    : SdfFontRenderer.width(
                                            SdfFontRenderer.FontStyle.BODY_SMALL, shown)),
                    y + 5.0F,
                    0.8F,
                    height - 10.0F,
                    0.0F,
                    AnimationUtil.multiplyAlpha(accent, alpha));
        }

        this.addClickRegion(x, y, width, height, button -> this.searchFocused = true);
        if (empty) {
            float hintAlpha = 1.0F - focus;
            SdfShapeRenderer.fill(
                    context,
                    x + width - 16.0F,
                    y + 4.0F,
                    11.0F,
                    10.0F,
                    3.0F,
                    AnimationUtil.multiplyAlpha(-15659750, alpha * hintAlpha));
            SdfFontRenderer.drawTextCentered(
                    context,
                    SdfFontRenderer.FontStyle.BODY_SMALL,
                    "/",
                    x + width - 10.5F,
                    y + height * 0.5F,
                    AnimationUtil.multiplyAlpha(-6845010, alpha * hintAlpha));
        } else {
            SdfFontRenderer.drawIcon(
                    context,
                    "\ue00b",
                    x + width - 9.0F,
                    y + height * 0.5F,
                    7.0F,
                    AnimationUtil.multiplyAlpha(-6845010, alpha));
            this.addClickRegion(
                    x + width - 16.0F, y + 2.0F, 14.0F, 14.0F, button -> this.searchQuery = "");
        }
    }

    private void drawPanelHub(DrawContext context, float x, float y, float alpha) {
        int accent = ThemeModule.getAccentColorAt(0.2F);
        float listHeight = PANEL_CATEGORIES.length * 18.0F + 54.0F;
        float cardHeight = 128.0F;
        float height =
                Math.min(
                        46.0F + listHeight + 22.0F + cardHeight + 8.0F,
                        this.layoutHeight - y - 8.0F);
        x -= (1.0F - AnimationUtil.easeOutCubic(alpha)) * 12.0F;
        SdfShapeRenderer.drawShadow(
                context,
                x,
                y + 2.0F,
                150.0F,
                height,
                11.0F,
                8.0F,
                AnimationUtil.multiplyAlpha(-1879048192, alpha));
        SdfShapeRenderer.fill(
                context,
                x,
                y,
                150.0F,
                height,
                11.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(-16777216, 240), alpha));
        SdfShapeRenderer.drawOutline(
                context,
                x,
                y,
                150.0F,
                height,
                11.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-14870999, accent, 0.3F), alpha));
        float logoSize = 7.5F;
        float logoX = x + 12.0F + SdfShapeRenderer.getLogoWidth(logoSize) * 0.5F;
        SdfShapeRenderer.drawLogo(
                context,
                logoX,
                y + 17.0F,
                logoSize,
                0.3F,
                1.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 110), alpha));
        SdfShapeRenderer.drawLogo(
                context,
                logoX,
                y + 17.0F,
                logoSize,
                0.0F,
                1.0F,
                AnimationUtil.multiplyAlpha(accent, alpha));
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + 16.0F + SdfShapeRenderer.getLogoWidth(logoSize), y + 17.0F);
        matrices.scale(0.72F, 0.72F);
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BRAND,
                "ODIN",
                0.0F,
                0.0F,
                AnimationUtil.multiplyAlpha(-658693, alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BRAND,
                "CLIENT",
                SdfFontRenderer.width(SdfFontRenderer.FontStyle.BRAND, "ODIN "),
                0.0F,
                AnimationUtil.multiplyAlpha(accent, alpha));
        matrices.popMatrix();
        float rowY = y + 42.0F;
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TINY,
                "PANELS",
                x + 12.0F,
                rowY - 5.0F,
                AnimationUtil.multiplyAlpha(-10529165, alpha));

        for (String category : PANEL_CATEGORIES) {
            boolean visible = !this.hiddenPanels.contains(category);
            this.drawHubEntry(
                    context,
                    x,
                    rowY,
                    getCategoryIcon(category),
                    title(category),
                    visible,
                    true,
                    alpha,
                    () -> {
                        if (!this.hiddenPanels.remove(category)) {
                            this.hiddenPanels.add(category);
                        }

                        this.savePanelLayout();
                    });
            rowY += 18.0F;
        }

        rowY += 10.0F;
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.TINY,
                "GENERAL",
                x + 12.0F,
                rowY - 5.0F,
                AnimationUtil.multiplyAlpha(-10529165, alpha));
        this.drawHubEntry(
                context,
                x,
                rowY,
                "\ue006",
                "Configs",
                "configs".equals(this.openDropdown),
                false,
                alpha,
                () -> this.toggleDropdown("configs"));
        this.drawHubEntry(
                context,
                x,
                rowY + 18.0F,
                "\ue007",
                "Keybinds",
                "keybinds".equals(this.openDropdown),
                false,
                alpha,
                () -> this.toggleDropdown("keybinds"));
        rowY += 44.0F;
        float cardRemaining = y + height - 6.0F - rowY;
        if (cardRemaining > 60.0F) {
            this.playerInfoCard.render(
                    context, x + 6.0F, rowY, 138.0F, cardRemaining, alpha, this.deltaTime);
        }
    }

    private void drawHubEntry(
            DrawContext context,
            float x,
            float y,
            String icon,
            String label,
            boolean on,
            boolean toggleStyle,
            float alpha,
            Runnable action) {
        int accent = ThemeModule.getAccentColorAt(0.3F);
        float rowX = x + 6.0F;
        float rowWidth = 138.0F;
        boolean hovered = this.isHovered(rowX, y + 1.0F, rowWidth, 16.0F);
        float hoverProgress = this.animate("hub-hover:" + label, hovered ? 1.0F : 0.0F, 14.0F);
        float onProgress = this.animate("hub-on:" + label, on ? 1.0F : 0.0F, 12.0F);
        if (hoverProgress > 0.01F) {
            SdfShapeRenderer.fill(
                    context,
                    rowX,
                    y + 1.0F,
                    rowWidth,
                    16.0F,
                    6.0F,
                    AnimationUtil.multiplyAlpha(-15265501, hoverProgress * alpha));
        }

        int textColor =
                AnimationUtil.lerpColor(-6845010, -658693, Math.max(hoverProgress, onProgress));
        SdfFontRenderer.drawIcon(
                context,
                icon,
                x + 17.0F,
                y + 9.0F,
                9.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                textColor, accent, toggleStyle ? onProgress * 0.6F : 0.0F),
                        alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY,
                label,
                x + 28.0F,
                y + 9.0F,
                AnimationUtil.multiplyAlpha(textColor, alpha));
        if (toggleStyle) {
            float switchX = x + 150.0F - 30.0F;
            float switchY = y + 9.0F - 4.0F;
            SdfShapeRenderer.fill(
                    context,
                    switchX,
                    switchY,
                    16.0F,
                    8.0F,
                    4.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-14476496, accent, onProgress), alpha));
            SdfShapeRenderer.fillCircle(
                    context,
                    switchX + 4.0F + 8.0F * AnimationUtil.easeOutBack(onProgress),
                    switchY + 4.0F,
                    2.8F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-7437149, -1, onProgress), alpha));
        } else {
            SdfFontRenderer.drawIcon(
                    context,
                    "\ue009",
                    x + 150.0F - 16.0F + hoverProgress * 1.5F,
                    y + 9.0F,
                    6.0F,
                    AnimationUtil.multiplyAlpha(-10529165, alpha));
        }

        this.addClickRegion(
                rowX,
                y + 1.0F,
                rowWidth,
                16.0F,
                button -> {
                    if (button == 0) {
                        action.run();
                    }
                });
    }

    private void toggleDropdown(String name) {
        this.openDropdown = name.equals(this.openDropdown) ? null : name;
        this.namingConfig = false;
    }

    private void drawCategoryPanel(
            DrawContext context, String category, float x, float y, float alpha) {
        if (!(alpha < 0.01F)) {
            int accent = ThemeModule.getAccentColorAt(0.3F);
            boolean searching = !this.searchQuery.isBlank();
            List<Module> all = this.modules(category, "", 0);
            List<Module> shown = searching ? this.modules(category, this.searchQuery, 0) : all;
            int enabledCount = 0;

            for (Module module : all) {
                if (module.isActive()) {
                    enabledCount++;
                }
            }

            float contentHeight = 4.0F;

            for (Module shownModule : shown) {
                contentHeight += 20.0F + this.getExpandedHeight(shownModule);
            }

            contentHeight += shown.isEmpty() ? 22.0F : 2.0F;
            float maxBody = Math.max(40.0F, this.layoutHeight - y - 22.0F - 8.0F);
            float bodyHeight = Math.min(contentHeight, maxBody);
            float panelHeight = 22.0F + bodyHeight;
            float maxScroll = Math.max(0.0F, contentHeight - bodyHeight);
            float scroll =
                    clampFloat(
                            this.panelScrollOffsets.getOrDefault(category, 0.0F), 0.0F, maxScroll);
            this.panelScrollOffsets.put(category, scroll);
            float smoothScroll = this.animate("panel-scroll:" + category, scroll, 16.0F);
            this.panelScrollBounds.put(
                    category, new float[] {x, y, 150.0F, panelHeight, maxScroll});
            if (this.mouseX >= x
                    && this.mouseY >= y
                    && this.mouseX < x + 150.0F
                    && this.mouseY < y + panelHeight) {
                this.hoveredSetting = null;
            }

            boolean dragging = category.equals(this.draggingPanel);
            float lift = this.animate("panel-lift:" + category, dragging ? 1.0F : 0.0F, 14.0F);
            SdfShapeRenderer.drawShadow(
                    context,
                    x,
                    y + 2.0F + lift * 2.0F,
                    150.0F,
                    panelHeight,
                    10.0F,
                    7.0F + lift * 6.0F,
                    AnimationUtil.multiplyAlpha(-1879048192, alpha));
            SdfShapeRenderer.fill(
                    context,
                    x,
                    y,
                    150.0F,
                    panelHeight,
                    10.0F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(-16777216, 238), alpha));
            SdfShapeRenderer.drawOutline(
                    context,
                    x,
                    y,
                    150.0F,
                    panelHeight,
                    10.0F,
                    0.75F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-14870999, accent, 0.25F + lift * 0.5F),
                            alpha));
            boolean headerHovered = this.isHovered(x, y, 150.0F, 22.0F);
            float headerHover =
                    this.animate("panel-head:" + category, headerHovered ? 1.0F : 0.0F, 14.0F);
            SdfShapeRenderer.fillVerticalGradient(
                    context,
                    x + 1.0F,
                    y + 1.0F,
                    148.0F,
                    21.0F,
                    9.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, 70 + (int) (30.0F * headerHover)),
                            alpha),
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 12), alpha));
            SdfFontRenderer.drawIcon(
                    context,
                    getCategoryIcon(category),
                    x + 12.0F,
                    y + 11.0F,
                    9.0F,
                    AnimationUtil.multiplyAlpha(-1, alpha));
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BOLD,
                    title(category),
                    x + 23.0F,
                    y + 11.0F,
                    AnimationUtil.multiplyAlpha(-658693, alpha));
            SdfFontRenderer.drawTextRightAligned(
                    context,
                    SdfFontRenderer.FontStyle.TINY,
                    enabledCount + "/" + all.size(),
                    x + 150.0F - 9.0F,
                    y + 11.0F,
                    AnimationUtil.multiplyAlpha(-6845010, alpha));
            this.addClickRegion(
                    x,
                    y,
                    150.0F,
                    22.0F,
                    button -> {
                        if (button == 0) {
                            this.draggingPanel = category;
                            this.dragOffsetX = this.mouseX - x;
                            this.dragOffsetY = this.mouseY - y;
                        }
                    });
            this.pushScissor(context, x, y + 22.0F, 150.0F, bodyHeight);
            float rowY = y + 22.0F + 3.0F - smoothScroll;
            int i = 0;

            for (Module entry : shown) {
                float reveal =
                        AnimationUtil.easeOutCubic(
                                AnimationUtil.progress(
                                        this.elapsedTime - this.contentAnimStart,
                                        i * 0.02F,
                                        i * 0.02F + 0.3F));
                this.drawPanelModuleRow(context, entry, x, rowY, alpha * reveal);
                rowY += 20.0F + this.getExpandedHeight(entry);
                i++;
            }

            if (shown.isEmpty()) {
                SdfFontRenderer.drawTextCentered(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        searching ? "No matches" : "No modules yet",
                        x + 75.0F,
                        y + 22.0F + 12.0F,
                        AnimationUtil.multiplyAlpha(-10529165, alpha));
            }

            this.popScissor(context);
            if (maxScroll > 0.0F) {
                float trackHeight = bodyHeight - 6.0F;
                float thumbHeight = Math.max(14.0F, trackHeight * bodyHeight / contentHeight);
                SdfShapeRenderer.fill(
                        context,
                        x + 150.0F - 3.5F,
                        y + 22.0F + 3.0F + (trackHeight - thumbHeight) * (smoothScroll / maxScroll),
                        2.0F,
                        thumbHeight,
                        1.0F,
                        AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 160), alpha));
            }
        }
    }

    private float getExpandedHeight(Module module) {
        float progress = this.animations.getOrDefault("expand:" + module.name, 0.0F);
        if (progress <= 0.001F) {
            return 0.0F;
        } else {
            float height = 4.0F;

            for (Setting<?> setting : getSortedSettings(module)) {
                height += getSettingHeight(setting) + 3.0F;
            }

            return height * AnimationUtil.easeInOutCubic(progress);
        }
    }

    private void drawPanelModuleRow(
            DrawContext context, Module module, float x, float y, float alpha) {
        int accent = ThemeModule.getAccentColorAt(0.4F);
        boolean active = module.isActive();
        this.animate(
                "expand:" + module.name,
                this.expandedModules.contains(module) ? 1.0F : 0.0F,
                11.0F);
        float rowX = x + 3.0F;
        float rowWidth = 144.0F;
        float rowHeight = 18.0F;
        boolean hovered = this.isHovered(rowX, y, rowWidth, rowHeight);
        float hoverProgress =
                this.animate("prow-hover:" + module.name, hovered ? 1.0F : 0.0F, 16.0F);
        float onProgress = this.animate("prow-on:" + module.name, active ? 1.0F : 0.0F, 12.0F);
        if (hovered) {
            this.hoverDescription = module.description;
        }

        if (hoverProgress > 0.01F) {
            SdfShapeRenderer.fill(
                    context,
                    rowX,
                    y,
                    rowWidth,
                    rowHeight,
                    5.0F,
                    AnimationUtil.multiplyAlpha(-15265501, hoverProgress * alpha));
        }

        if (onProgress > 0.01F) {
            SdfShapeRenderer.fillVerticalGradient(
                    context,
                    rowX,
                    y,
                    Math.max(rowHeight, rowWidth * AnimationUtil.easeOutCubic(onProgress)),
                    rowHeight,
                    5.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, 210), onProgress * alpha),
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(accent, 120), onProgress * alpha));
        }

        float right = rowX + rowWidth - 4.0F;
        String keyName = KeyNames.getKeyName(module.keybind.getValue());
        boolean hasKey = module.keybind.getValue() != null && module.keybind.getValue() >= 0;
        float keyWidth =
                hasKey
                        ? SdfFontRenderer.width(SdfFontRenderer.FontStyle.TINY, keyName) + 8.0F
                        : 0.0F;
        float titleWidth = rowWidth - 26.0F - (hasKey ? keyWidth + 4.0F : 0.0F);
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY,
                SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BODY, module.title, titleWidth),
                rowX + 9.0F,
                y + rowHeight * 0.5F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                AnimationUtil.lerpColor(-6845010, -658693, hoverProgress),
                                -1,
                                onProgress),
                        alpha));
        if (hasKey) {
            float keyX = right - 12.0F - keyWidth;
            SdfShapeRenderer.fill(
                    context,
                    keyX,
                    y + rowHeight * 0.5F - 5.0F,
                    keyWidth,
                    10.0F,
                    3.5F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(-16777216, 120), alpha));
            SdfFontRenderer.drawTextCentered(
                    context,
                    SdfFontRenderer.FontStyle.TINY,
                    keyName,
                    keyX + keyWidth * 0.5F,
                    y + rowHeight * 0.5F,
                    AnimationUtil.multiplyAlpha(-6845010, alpha));
        }

        float dotsX = right - 4.0F;
        boolean dotsHovered = this.isHovered(dotsX - 6.0F, y, 12.0F, rowHeight);
        float dotsHover =
                this.animate("prow-dots:" + module.name, dotsHovered ? 1.0F : 0.0F, 16.0F);
        float expandProgress = this.animations.getOrDefault("expand:" + module.name, 0.0F);
        if (dotsHover > 0.01F) {
            SdfShapeRenderer.fillCircle(
                    context,
                    dotsX,
                    y + rowHeight * 0.5F,
                    5.5F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(-1, 30), dotsHover * alpha));
        }

        for (int i = -1; i <= 1; i++) {
            float spread = 2.6F * (1.0F - expandProgress);
            SdfShapeRenderer.fillCircle(
                    context,
                    dotsX + i * 2.2F * expandProgress,
                    y + rowHeight * 0.5F + i * spread,
                    0.95F,
                    AnimationUtil.multiplyAlpha(
                            -1, alpha * (0.6F + 0.4F * Math.max(dotsHover, onProgress))));
        }

        this.addClickRegion(
                rowX,
                y,
                rowWidth,
                rowHeight,
                button -> {
                    if (button == 0) {
                        module.toggle();
                    } else if (button == 1) {
                        this.toggleExpanded(module);
                    } else if (button == 2) {
                        this.listeningKeybind = module.keybind;
                    }
                });
        this.addClickRegion(
                dotsX - 6.0F, y, 12.0F, rowHeight, button -> this.toggleExpanded(module));
        float expandedHeight = this.getExpandedHeight(module);
        if (!(expandedHeight < 0.5F)) {
            float bodyY = y + 20.0F;
            this.pushScissor(context, x, bodyY, 150.0F, expandedHeight);
            float settingX = x + 5.0F;
            float settingWidth = 140.0F;
            SdfShapeRenderer.fill(
                    context,
                    x + 3.0F,
                    bodyY - 1.0F,
                    144.0F,
                    expandedHeight - 1.0F,
                    6.0F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(-16251379, 230), alpha));
            float settingY = bodyY + 2.0F;

            for (Setting<?> setting : getSortedSettings(module)) {
                float settingHeight = getSettingHeight(setting);
                this.drawSettingRow(
                        context,
                        module,
                        setting,
                        settingX,
                        settingY,
                        settingWidth,
                        settingHeight,
                        alpha);
                settingY += settingHeight + 3.0F;
            }

            this.popScissor(context);
        }
    }

    private void toggleExpanded(Module module) {
        if (!this.expandedModules.remove(module)) {
            this.expandedModules.add(module);
        }

        this.editingSetting = null;
    }

    private void drawDropdown(DrawContext context, float alpha) {
        float progress = this.animate("dropdown", this.openDropdown == null ? 0.0F : 1.0F, 13.0F);
        if (this.openDropdown != null) {
            this.lastOpenDropdown = this.openDropdown;
        }

        if (!(progress < 0.01F) && this.lastOpenDropdown != null) {
            this.renderingDropdown = true;
            this.hoverDescription = null;
            boolean configs = "configs".equals(this.lastOpenDropdown);
            float width = configs ? 250.0F : 215.0F;
            float height = configs ? 196.0F : 230.0F;
            float x = 166.0F;
            float y = Math.min(this.layoutHeight - height - 8.0F, 94.0F);
            float eased = AnimationUtil.easeOutCubic(progress);
            float panelAlpha = alpha * progress;
            int accent = ThemeModule.getAccentColorAt(0.4F);
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(1.0F, 0.86F + 0.14F * eased);
            matrices.translate(-x, -y - (1.0F - eased) * 10.0F);
            this.addClickRegion(x, y, width, height, button -> {});
            SdfShapeRenderer.drawShadow(
                    context,
                    x,
                    y + 3.0F,
                    width,
                    height,
                    11.0F,
                    10.0F,
                    AnimationUtil.multiplyAlpha(-1342177280, panelAlpha));
            SdfShapeRenderer.fill(
                    context,
                    x,
                    y,
                    width,
                    height,
                    11.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.withAlpha(-16777216, 246), panelAlpha));
            SdfShapeRenderer.fillHorizontalGradient(
                    context,
                    x,
                    y,
                    width,
                    46.0F,
                    11.0F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 45), panelAlpha),
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 0), panelAlpha));
            SdfShapeRenderer.drawOutline(
                    context,
                    x,
                    y,
                    width,
                    height,
                    11.0F,
                    0.75F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-14870999, accent, 0.4F), panelAlpha));
            SdfFontRenderer.drawIcon(
                    context,
                    configs ? "\ue006" : "\ue007",
                    x + 16.0F,
                    y + 15.0F,
                    10.0F,
                    AnimationUtil.multiplyAlpha(-1, panelAlpha));
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BOLD,
                    configs ? "Configs" : "Keybinds",
                    x + 27.0F,
                    y + 15.0F,
                    AnimationUtil.multiplyAlpha(-658693, panelAlpha));
            boolean closeHovered = this.isHovered(x + width - 24.0F, y + 6.0F, 18.0F, 18.0F);
            float closeHover = this.animate("dropdown-close", closeHovered ? 1.0F : 0.0F, 16.0F);
            SdfShapeRenderer.fillCircle(
                    context,
                    x + width - 15.0F,
                    y + 15.0F,
                    6.5F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-15659750, -15265501, closeHover), panelAlpha));
            SdfFontRenderer.drawIcon(
                    context,
                    "\ue00b",
                    x + width - 15.0F,
                    y + 15.0F,
                    7.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-6845010, -658693, closeHover), panelAlpha));
            this.addClickRegion(
                    x + width - 24.0F, y + 6.0F, 18.0F, 18.0F, button -> this.openDropdown = null);
            if (configs) {
                this.drawConfigsDropdown(context, x, y + 30.0F, width, height - 30.0F, panelAlpha);
            } else {
                this.drawKeybindsDropdown(context, x, y + 30.0F, width, height - 30.0F, panelAlpha);
            }

            this.dropdownBounds = new float[] {x, y, width, height};
            this.renderingDropdown = false;
            matrices.popMatrix();
        }
    }

    private void drawConfigsDropdown(
            DrawContext context, float x, float y, float width, float height, float alpha) {
        int accent = ThemeModule.getAccentColorAt(0.3F);
        List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();
        float slotY = y + 2.0F;

        for (int slot = 0; slot < 3; slot++) {
            float slotHeight = 26.0F;
            float slotX = x + 8.0F;
            float slotWidth = width - 16.0F;
            if (slot >= profiles.size()) {
                boolean emptyHovered = this.isHovered(slotX, slotY, slotWidth, slotHeight);
                float emptyHover =
                        this.animate("dd-empty:" + slot, emptyHovered ? 1.0F : 0.0F, 14.0F);
                SdfShapeRenderer.drawOutline(
                        context,
                        slotX,
                        slotY,
                        slotWidth,
                        slotHeight,
                        7.0F,
                        0.7F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.withAlpha(-14870999, 210), alpha));
                if (emptyHover > 0.01F) {
                    SdfShapeRenderer.fill(
                            context,
                            slotX,
                            slotY,
                            slotWidth,
                            slotHeight,
                            7.0F,
                            AnimationUtil.multiplyAlpha(-15265501, emptyHover * alpha));
                }

                SdfFontRenderer.drawIcon(
                        context,
                        "\ue014",
                        slotX + 14.0F,
                        slotY + slotHeight * 0.5F,
                        9.0F,
                        AnimationUtil.multiplyAlpha(-10529165, alpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        "Save current setup",
                        slotX + 26.0F,
                        slotY + slotHeight * 0.5F,
                        AnimationUtil.multiplyAlpha(-6845010, alpha));
                this.addClickRegion(
                        slotX,
                        slotY,
                        slotWidth,
                        slotHeight,
                        button -> {
                            this.namingConfig = true;
                            this.configNameInput = "";
                        });
            } else {
                ConfigProfileManager.Profile profile = profiles.get(slot);
                boolean active =
                        profile.name.equalsIgnoreCase(ConfigProfileManager.getActiveProfileName());
                boolean hovered = this.isHovered(slotX, slotY, slotWidth, slotHeight);
                float hoverProgress = this.animate("dd-row:" + slot, hovered ? 1.0F : 0.0F, 14.0F);
                SdfShapeRenderer.fill(
                        context,
                        slotX,
                        slotY,
                        slotWidth,
                        slotHeight,
                        7.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(-16251379, -15265501, hoverProgress),
                                alpha));
                if (active) {
                    SdfShapeRenderer.fillVerticalGradient(
                            context,
                            slotX,
                            slotY,
                            slotWidth,
                            slotHeight,
                            7.0F,
                            AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 70), alpha),
                            AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 0), alpha));
                }

                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BODY,
                        SdfFontRenderer.truncateToWidth(
                                SdfFontRenderer.FontStyle.BODY, profile.name, slotWidth - 118.0F),
                        slotX + 9.0F,
                        slotY + 10.0F,
                        AnimationUtil.multiplyAlpha(-658693, alpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.MICRO,
                        active ? "ACTIVE" : "SAVED",
                        slotX + 9.0F,
                        slotY + 19.0F,
                        AnimationUtil.multiplyAlpha(active ? accent : -10529165, alpha));
                int index = slot;
                this.addClickRegion(
                        slotX,
                        slotY,
                        slotWidth,
                        slotHeight,
                        button -> this.selectedConfigIndex = index);
                float buttonRight = slotX + slotWidth - 6.0F;
                buttonRight =
                        this.drawSmallIconButton(
                                context,
                                "\ue00f",
                                "Delete this config",
                                buttonRight,
                                slotY + slotHeight * 0.5F,
                                -41081,
                                alpha,
                                () -> this.showResult(ConfigProfileManager.deleteProfile(index)));
                buttonRight =
                        this.drawSmallIconButton(
                                context,
                                "\ue00e",
                                "Overwrite with current settings",
                                buttonRight - 3.0F,
                                slotY + slotHeight * 0.5F,
                                -658693,
                                alpha,
                                () -> this.showResult(ConfigProfileManager.updateProfile(index)));
                buttonRight =
                        this.drawLabeledIconButton(
                                context,
                                "\ue011",
                                "Load",
                                "Apply this config",
                                buttonRight - 4.0F,
                                slotY + slotHeight * 0.5F,
                                accent,
                                alpha,
                                () -> this.showResult(ConfigProfileManager.loadProfile(index)));
            }

            slotY += 30.0F;
        }

        float actionY = y + height - 26.0F;
        String[] actionIcons = new String[] {"\ue014", "\ue010", "\ue00d", "\ue012", "\ue013"};
        String[] actionTips =
                new String[] {
                    "New config",
                    "Import from clipboard",
                    "Bundled Odin config",
                    "Share selected",
                    System.currentTimeMillis() < this.resetConfirmDeadline
                            ? "Click again to reset"
                            : "Reset to defaults"
                };
        Runnable[] actions =
                new Runnable[] {
                    () -> {
                        this.namingConfig = true;
                        this.configNameInput = "";
                    },
                    () ->
                            this.showResult(
                                    ConfigProfileManager.importCode(
                                            this.client.keyboard.getClipboard())),
                    () -> {
                        List<ConfigProfileManager.BundledConfig> bundled =
                                ConfigProfileManager.getBundledConfigs();
                        this.showResult(
                                bundled.isEmpty()
                                        ? ConfigProfileManager.Result.failure(
                                                "Bundled Odin config is unavailable")
                                        : ConfigProfileManager.importCode(
                                                bundled.get(0).configJson));
                    },
                    () -> {
                        ConfigProfileManager.Result exported =
                                ConfigProfileManager.exportProfile(this.selectedConfigIndex);
                        if (exported.success && exported.code != null) {
                            this.client.keyboard.setClipboard(exported.code);
                        }

                        this.showResult(exported);
                    },
                    () -> {
                        long now = System.currentTimeMillis();
                        if (now < this.resetConfirmDeadline) {
                            this.resetConfirmDeadline = 0L;
                            this.showResult(ConfigProfileManager.resetToDefaults());
                        } else {
                            this.resetConfirmDeadline = now + 5000L;
                            this.showResult(
                                    ConfigProfileManager.Result.failure(
                                            "Click reset again to confirm"));
                        }
                    }
                };
        float buttonWidth = (width - 16.0F - 16.0F) / 5.0F;

        for (int i = 0; i < actionIcons.length; i++) {
            float buttonX = x + 8.0F + i * (buttonWidth + 4.0F);
            boolean buttonHovered = this.isHovered(buttonX, actionY, buttonWidth, 20.0F);
            float buttonHover = this.animate("dd-action:" + i, buttonHovered ? 1.0F : 0.0F, 16.0F);
            if (buttonHovered) {
                this.hoverDescription = actionTips[i];
            }

            SdfShapeRenderer.fill(
                    context,
                    buttonX,
                    actionY,
                    buttonWidth,
                    20.0F,
                    6.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-16251379, -15265501, buttonHover), alpha));
            SdfShapeRenderer.drawOutline(
                    context,
                    buttonX,
                    actionY,
                    buttonWidth,
                    20.0F,
                    6.0F,
                    0.6F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-14870999, accent, buttonHover * 0.7F), alpha));
            SdfFontRenderer.drawIcon(
                    context,
                    actionIcons[i],
                    buttonX + buttonWidth * 0.5F,
                    actionY + 10.0F - buttonHover * 0.5F,
                    9.0F,
                    AnimationUtil.multiplyAlpha(
                            AnimationUtil.lerpColor(-6845010, -658693, buttonHover), alpha));
            Runnable action = actions[i];
            this.addClickRegion(
                    buttonX,
                    actionY,
                    buttonWidth,
                    20.0F,
                    button -> {
                        if (button == 0) {
                            action.run();
                        }
                    });
        }

        boolean statusVisible =
                !this.status.isEmpty() && System.currentTimeMillis() < this.statusExpiry;
        String message = statusVisible ? this.status : this.hoverDescription;
        if (message != null && !message.isEmpty()) {
            SdfFontRenderer.drawTextCenteredY(
                    context,
                    SdfFontRenderer.FontStyle.BODY_SMALL,
                    SdfFontRenderer.truncateToWidth(
                            SdfFontRenderer.FontStyle.BODY_SMALL, message, width - 20.0F),
                    x + 9.0F,
                    actionY - 9.0F,
                    AnimationUtil.multiplyAlpha(
                            statusVisible ? (this.statusSuccess ? -10755160 : -41081) : -6845010,
                            alpha));
        }
    }

    private void drawKeybindsDropdown(
            DrawContext context, float x, float y, float width, float height, float alpha) {
        int accent = ThemeModule.getAccentColorAt(0.3F);
        float rowX = x + 8.0F;
        float rowWidth = width - 16.0F;
        SdfShapeRenderer.fillVerticalGradient(
                context,
                rowX,
                y,
                rowWidth,
                22.0F,
                7.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 70), alpha),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 10), alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY,
                "Open menu",
                rowX + 9.0F,
                y + 11.0F,
                AnimationUtil.multiplyAlpha(-658693, alpha));
        this.drawKeybindButton(
                context, GuiKeybind.setting(), rowX + rowWidth - 6.0F, y + 11.0F, alpha);
        this.addClickRegion(
                rowX,
                y,
                rowWidth,
                22.0F,
                button -> {
                    if (button == 1) {
                        GuiKeybind.setting().setValue(48);
                    } else {
                        this.listeningKeybind = GuiKeybind.setting();
                    }
                });
        float listTop = y + 27.0F;
        float listBottom = y + height - 6.0F;
        List<Module> modules = this.modules(null, this.searchQuery, 0);
        float rowHeight = 18.0F;
        float contentHeight = modules.size() * (rowHeight + 2.0F);
        float maxScroll = Math.max(0.0F, contentHeight - (listBottom - listTop));
        this.panelScrollOffsets.put(
                "keybinds-dropdown",
                clampFloat(
                        this.panelScrollOffsets.getOrDefault("keybinds-dropdown", 0.0F),
                        0.0F,
                        maxScroll));
        this.panelScrollBounds.put(
                "keybinds-dropdown",
                new float[] {x, listTop, width, listBottom - listTop, maxScroll});
        float scroll =
                this.animate(
                        "dd-scroll",
                        this.panelScrollOffsets.getOrDefault("keybinds-dropdown", 0.0F),
                        16.0F);
        this.pushScissor(context, x + 2.0F, listTop, width - 4.0F, listBottom - listTop);
        float rowY = listTop - scroll;

        for (Module module : modules) {
            if (rowY + rowHeight >= listTop - 2.0F && rowY <= listBottom + 2.0F) {
                boolean bound = module.keybind.getValue() != null && module.keybind.getValue() >= 0;
                boolean hovered = this.isHovered(rowX, rowY, rowWidth, rowHeight);
                float hoverProgress =
                        this.animate("dd-key:" + module.name, hovered ? 1.0F : 0.0F, 16.0F);
                SdfShapeRenderer.fill(
                        context,
                        rowX,
                        rowY,
                        rowWidth,
                        rowHeight,
                        6.0F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(-16251379, -15265501, hoverProgress),
                                alpha));
                drawModuleIcon(
                        context,
                        module,
                        rowX + 11.0F,
                        rowY + rowHeight * 0.5F,
                        12.0F,
                        AnimationUtil.multiplyAlpha(bound ? -1 : -4344370, alpha));
                SdfFontRenderer.drawTextCenteredY(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        SdfFontRenderer.truncateToWidth(
                                SdfFontRenderer.FontStyle.BODY_SMALL,
                                module.title,
                                rowWidth - 80.0F),
                        rowX + 20.0F,
                        rowY + rowHeight * 0.5F,
                        AnimationUtil.multiplyAlpha(
                                AnimationUtil.lerpColor(
                                        -6845010,
                                        -658693,
                                        Math.max(hoverProgress, bound ? 1.0F : 0.0F)),
                                alpha));
                this.drawKeybindButton(
                        context,
                        module.keybind,
                        rowX + rowWidth - 5.0F,
                        rowY + rowHeight * 0.5F,
                        alpha);
                this.addClickRegion(
                        rowX,
                        rowY,
                        rowWidth,
                        rowHeight,
                        button -> {
                            if (button == 1) {
                                module.keybind.setValue(-1);
                            } else {
                                this.listeningKeybind = module.keybind;
                            }
                        });
            }

            rowY += rowHeight + 2.0F;
        }

        this.popScissor(context);
        if (maxScroll > 0.0F) {
            float trackHeight = listBottom - listTop;
            float thumbHeight = Math.max(16.0F, trackHeight * trackHeight / contentHeight);
            SdfShapeRenderer.fill(
                    context,
                    x + width - 5.0F,
                    listTop + (trackHeight - thumbHeight) * (scroll / maxScroll),
                    2.0F,
                    thumbHeight,
                    1.0F,
                    AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 150), alpha));
        }
    }

    private float drawLabeledIconButton(
            DrawContext context,
            String icon,
            String label,
            String tooltip,
            float right,
            float centerY,
            int color,
            float alpha,
            Runnable action) {
        float width = SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY_SMALL, label) + 24.0F;
        float height = 18.0F;
        float left = right - width;
        float top = centerY - height * 0.5F;
        boolean hovered = this.isHovered(left, top, width, height);
        float hoverProgress =
                this.animate("dd-load:" + (int) (centerY * 4.0F), hovered ? 1.0F : 0.0F, 16.0F);
        if (hovered) {
            this.hoverDescription = tooltip;
        }

        SdfShapeRenderer.fillVerticalGradient(
                context,
                left,
                top,
                width,
                height,
                6.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.withAlpha(color, 210 + (int) (45.0F * hoverProgress)), alpha),
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.withAlpha(color, 130 + (int) (60.0F * hoverProgress)),
                        alpha));
        SdfFontRenderer.drawIcon(
                context,
                icon,
                left + 10.0F,
                top + height * 0.5F,
                8.0F,
                AnimationUtil.multiplyAlpha(-1, alpha));
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                label,
                left + 17.0F,
                top + height * 0.5F,
                AnimationUtil.multiplyAlpha(-1, alpha));
        this.addClickRegion(
                left,
                top,
                width,
                height,
                button -> {
                    if (button == 0) {
                        action.run();
                    }
                });
        return left;
    }

    private float drawSmallIconButton(
            DrawContext context,
            String icon,
            String tooltip,
            float right,
            float centerY,
            int color,
            float alpha,
            Runnable action) {
        float size = 17.0F;
        float left = right - size;
        float top = centerY - size * 0.5F;
        boolean hovered = this.isHovered(left, top, size, size);
        float hoverProgress =
                this.animate(
                        "dd-icon:" + icon + ":" + (int) (centerY * 4.0F),
                        hovered ? 1.0F : 0.0F,
                        16.0F);
        if (hovered) {
            this.hoverDescription = tooltip;
        }

        SdfShapeRenderer.fill(
                context,
                left,
                top,
                size,
                size,
                5.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                -15659750,
                                AnimationUtil.withAlpha(color, 255),
                                hoverProgress * 0.22F),
                        alpha));
        SdfFontRenderer.drawIcon(
                context,
                icon,
                left + size * 0.5F,
                top + size * 0.5F - hoverProgress * 0.5F,
                8.0F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-6845010, color, 0.4F + hoverProgress * 0.6F),
                        alpha));
        this.addClickRegion(
                left,
                top,
                size,
                size,
                button -> {
                    if (button == 0) {
                        action.run();
                    }
                });
        return left;
    }

    private void drawLayoutSwitcher(DrawContext context, float alpha) {
        float drawScale = this.isPanelLayout() ? this.scale : this.scale * 0.9F;
        float viewWidth = this.width / drawScale;
        float viewHeight = this.height / drawScale;
        int accent = ThemeModule.getAccentColorAt(0.5F);
        String[] names = new String[] {"Quick Panels", "Control Center"};
        String[] icons = new String[] {"\ue01f", "\ue01e"};
        float height = 24.0F;
        float[] widths = new float[] {34.0F, 34.0F};
        float totalWidth = 6.0F + widths[0] + widths[1];
        float left = (viewWidth - totalWidth) * 0.5F;
        float top = viewHeight - height - 8.0F;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(drawScale, drawScale);
        float slide = (1.0F - AnimationUtil.easeOutCubic(alpha)) * 10.0F;
        SdfShapeRenderer.drawShadow(
                context,
                left,
                top + 2.0F + slide,
                totalWidth,
                height,
                11.0F,
                7.0F,
                AnimationUtil.multiplyAlpha(-1728053248, alpha));
        SdfShapeRenderer.fill(
                context,
                left,
                top + slide,
                totalWidth,
                height,
                11.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(-16777216, 242), alpha));
        SdfShapeRenderer.drawOutline(
                context,
                left,
                top + slide,
                totalWidth,
                height,
                11.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(-14870999, accent, 0.3F), alpha));
        boolean panels = this.isPanelLayout();
        float pillTarget = left + 3.0F + (panels ? 0.0F : widths[0]);
        float pillX = this.animate("layout-pill-x", pillTarget, 16.0F);
        float pillWidth = this.animate("layout-pill-w", widths[panels ? 0 : 1], 16.0F);
        SdfShapeRenderer.fillVerticalGradient(
                context,
                pillX,
                top + 3.0F + slide,
                pillWidth,
                height - 6.0F,
                8.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 190), alpha),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 110), alpha));
        this.layoutToggleRegions.clear();
        float cursorX = left + 3.0F;

        for (int i = 0; i < 2; i++) {
            boolean active = panels == (i == 0);
            boolean hovered =
                    this.isRawMouseInside(
                            cursorX * drawScale,
                            (top + slide) * drawScale,
                            widths[i] * drawScale,
                            height * drawScale);
            float hoverProgress =
                    this.animate("layout-hover:" + i, hovered && !active ? 1.0F : 0.0F, 16.0F);
            int iconColor = active ? -1 : AnimationUtil.lerpColor(-6845010, -658693, hoverProgress);
            SdfFontRenderer.drawIcon(
                    context,
                    icons[i],
                    cursorX + widths[i] * 0.5F,
                    top + height * 0.5F + slide,
                    11.0F,
                    AnimationUtil.multiplyAlpha(iconColor, alpha));
            if (hovered) {
                this.hoverDescription = names[i];
            }

            if (hoverProgress > 0.01F) {
                float tipWidth =
                        SdfFontRenderer.width(SdfFontRenderer.FontStyle.BODY_SMALL, names[i])
                                + 12.0F;
                float tipX = cursorX + widths[i] * 0.5F - tipWidth * 0.5F;
                float tipY = top + slide - 18.0F;
                SdfShapeRenderer.fill(
                        context,
                        tipX,
                        tipY,
                        tipWidth,
                        15.0F,
                        6.0F,
                        AnimationUtil.multiplyAlpha(-16777216, hoverProgress * alpha));
                SdfShapeRenderer.drawOutline(
                        context,
                        tipX,
                        tipY,
                        tipWidth,
                        15.0F,
                        6.0F,
                        0.6F,
                        AnimationUtil.multiplyAlpha(-14870999, hoverProgress * alpha));
                SdfFontRenderer.drawTextCentered(
                        context,
                        SdfFontRenderer.FontStyle.BODY_SMALL,
                        names[i],
                        tipX + tipWidth * 0.5F,
                        tipY + 7.5F,
                        AnimationUtil.multiplyAlpha(-658693, hoverProgress * alpha));
            }

            this.layoutToggleRegions.add(
                    new float[] {
                        cursorX * drawScale,
                        (top + slide) * drawScale,
                        widths[i] * drawScale,
                        height * drawScale,
                        i
                    });
            cursorX += widths[i];
        }

        matrices.popMatrix();
    }

    private boolean isRawMouseInside(float x, float y, float width, float height) {
        float mx = this.mouseScreenX;
        float my = this.mouseScreenY;
        return mx >= x && my >= y && mx < x + width && my < y + height;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.closing) {
            return true;
        } else {
            float localX = (float) ((click.x() - this.layoutOffsetX) / this.scale);
            float localY = (float) ((click.y() - this.layoutOffsetY) / this.scale);
            int button = click.button();
            float rawX = (float) click.x();
            float rawY = (float) click.y();

            for (float[] region : this.layoutToggleRegions) {
                if (rawX >= region[0]
                        && rawY >= region[1]
                        && rawX < region[0] + region[2]
                        && rawY < region[1] + region[3]) {
                    if (button == 0) {
                        setLayout(
                                region[4] == 0.0F
                                        ? ThemeModule.GuiStyle.PANELS
                                        : ThemeModule.GuiStyle.DASHBOARD);
                        this.inspectedModule = null;
                        this.openDropdown = null;
                        this.contentAnimStart = this.elapsedTime;
                    }

                    return true;
                }
            }

            if (this.listeningKeybind != null) {
                if (button >= 2 && button <= 7) {
                    this.listeningKeybind.setValue(1000 + button);
                    this.listeningKeybind = null;
                    return true;
                }

                this.listeningKeybind = null;
            }

            boolean wasEditing = this.editingSetting != null;
            boolean wasSearching = this.searchFocused;
            this.searchFocused = false;
            if (wasEditing) {
                this.finishEditing(true);
            }

            for (int i = this.clickRegions.size() - 1; i >= 0; i--) {
                OdinGuiScreen.ClickRegion clickRegion = this.clickRegions.get(i);
                if (clickRegion.contains(localX, localY)) {
                    clickRegion.action.accept(button);
                    return true;
                }
            }

            if (this.isPanelLayout() && this.openDropdown != null) {
                this.openDropdown = null;
            }

            if (!this.isPanelLayout()
                    && !wasSearching
                    && !wasEditing
                    && (localX < 0.0F || localY < 0.0F || localX > 600.0F || localY > 380.0F)) {
                this.close();
            }

            return true;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        float localX = (float) ((click.x() - this.layoutOffsetX) / this.scale);
        float localY = (float) ((click.y() - this.layoutOffsetY) / this.scale);
        if (this.draggingPanel != null) {
            float[] position = this.panelPositions.get(this.draggingPanel);
            if (position != null) {
                position[0] =
                        clampFloat(
                                localX - this.dragOffsetX,
                                0.0F,
                                Math.max(0.0F, this.layoutWidth - 150.0F));
                position[1] =
                        clampFloat(
                                localY - this.dragOffsetY,
                                0.0F,
                                Math.max(0.0F, this.layoutHeight - 22.0F));
            }
        } else if (this.draggingSlider != null) {
            this.updateSliderFromMouse(localX);
        }

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        this.draggingSlider = null;
        if (this.draggingPanel != null) {
            this.savePanelLayout();
        }

        this.draggingPanel = null;
        return true;
    }

    private void savePanelLayout() {
        GuiPanelLayoutStore.saveLayout(PANEL_CATEGORIES, this.panelPositions, this.hiddenPanels);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float localX = (float) ((mouseX - this.layoutOffsetX) / this.scale);
        float localY = (float) ((mouseY - this.layoutOffsetY) / this.scale);
        float delta = (float) (-verticalAmount * 34.0);
        if (this.isPanelLayout()) {
            for (Entry<String, float[]> entry : this.panelScrollBounds.entrySet()) {
                float[] bounds = entry.getValue();
                if (localX >= bounds[0]
                        && localX < bounds[0] + bounds[2]
                        && localY >= bounds[1]
                        && localY < bounds[1] + bounds[3]) {
                    float offset =
                            this.panelScrollOffsets.getOrDefault(entry.getKey(), 0.0F) + delta;
                    this.panelScrollOffsets.put(
                            entry.getKey(), clampFloat(offset, 0.0F, bounds[4]));
                }
            }

            return true;
        } else {
            float inspectorLeft = 406.0F;
            if (this.inspectedModule != null && localX >= inspectorLeft) {
                this.settingsScrollTarget =
                        clampFloat(this.settingsScrollTarget + delta, 0.0F, this.settingsScrollMax);
            } else if (localX >= 170.0F && localY >= 70.0F) {
                this.moduleScrollTarget =
                        clampFloat(this.moduleScrollTarget + delta, 0.0F, this.moduleScrollMax);
            }

            return true;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        int scancode = input.scancode();
        if (this.listeningKeybind != null) {
            this.listeningKeybind.setValue(isUnbindKey(key, scancode) ? -1 : key);
            this.listeningKeybind = null;
            this.searchFocused = false;
            this.swallowNextChar = true;
            return true;
        } else {
            this.swallowNextChar = false;
            if (this.namingConfig) {
                if (key == 256) {
                    this.namingConfig = false;
                    this.configNameInput = "";
                } else if (key == 257 || key == 335) {
                    this.submitConfigName();
                } else if (key == 259 && !this.configNameInput.isEmpty()) {
                    this.configNameInput =
                            this.configNameInput.substring(0, this.configNameInput.length() - 1);
                }

                return true;
            } else if (this.editingSetting != null) {
                if (key == 257 || key == 335) {
                    this.finishEditing(true);
                } else if (key == 256) {
                    this.finishEditing(false);
                } else if (key == 259 && !this.editBuffer.isEmpty()) {
                    this.editBuffer = this.editBuffer.substring(0, this.editBuffer.length() - 1);
                }

                return true;
            } else if (!this.searchFocused) {
                if (key == 256) {
                    if (this.openDropdown != null) {
                        this.openDropdown = null;
                    } else if (this.inspectedModule != null) {
                        this.inspectedModule = null;
                    } else {
                        this.close();
                    }

                    return true;
                } else if (key == 47 && this.currentTab != OdinGuiScreen.Tab.CONFIGS) {
                    this.searchFocused = true;
                    this.swallowSlashChar = true;
                    return true;
                } else if (key == 70 && (input.modifiers() & 2) != 0) {
                    this.searchFocused = true;
                    return true;
                } else {
                    return true;
                }
            } else {
                if (key == 256 || key == 257) {
                    this.searchFocused = false;
                } else if (key == 259 && !this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                    this.contentAnimStart = this.elapsedTime;
                }

                return true;
            }
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) {
            return true;
        } else if (this.swallowNextChar) {
            this.swallowNextChar = false;
            return true;
        } else if (this.listeningKeybind != null) {
            return true;
        } else {
            String typed = input.asString();
            if (this.swallowSlashChar) {
                this.swallowSlashChar = false;
                if ("/".equals(typed)) {
                    return true;
                }
            }

            if (this.namingConfig) {
                if (this.configNameInput.length() < 24) {
                    this.configNameInput = truncate(this.configNameInput + typed, 24);
                }
            } else if (this.editingSetting != null) {
                if (this.editBuffer.length() < 256) {
                    this.editBuffer = truncate(this.editBuffer + typed, 256);
                }
            } else if (this.searchFocused
                    || this.currentTab != OdinGuiScreen.Tab.CONFIGS
                            && !typed.isBlank()
                            && this.listeningKeybind == null) {
                this.searchFocused = true;
                if (this.searchQuery.length() < 48) {
                    this.searchQuery = truncate(this.searchQuery + typed, 48);
                    this.contentAnimStart = this.elapsedTime;
                    this.moduleScrollTarget = 0.0F;
                }
            }

            return true;
        }
    }

    private void updateSliderFromMouse(float mouseX) {
        if (this.draggingSlider != null && !(this.sliderTrackWidth <= 0.0F)) {
            double ratio =
                    clampDouble((mouseX - this.sliderTrackX) / this.sliderTrackWidth, 0.0, 1.0);
            if (this.draggingSlider instanceof IntSetting intSetting) {
                intSetting.setValue(
                        (int)
                                Math.round(
                                        intSetting.sliderMin
                                                + ratio
                                                        * (intSetting.sliderMax
                                                                - intSetting.sliderMin)));
            } else if (this.draggingSlider instanceof NumberSetting numberSetting) {
                double raw =
                        numberSetting.sliderMin
                                + ratio * (numberSetting.sliderMax - numberSetting.sliderMin);
                double scale = Math.pow(10.0, getDecimalPlaces(numberSetting));
                numberSetting.setValue(Math.round(raw * scale) / scale);
            }
        }
    }

    private void finishEditing(boolean commit) {
        if (this.editingSetting != null && commit) {
            this.editingSetting.setValueFromString(this.editBuffer);
        }

        this.editingSetting = null;
        this.editBuffer = "";
    }

    private void submitConfigName() {
        ConfigProfileManager.Result result = ConfigProfileManager.saveCurrent(this.configNameInput);
        this.showResult(result);
        if (result.success) {
            this.namingConfig = false;
            this.configNameInput = "";
            this.animations.put("config-naming", 0.0F);
            this.selectedConfigIndex = ConfigProfileManager.profiles().size() - 1;
        }
    }

    private void showResult(ConfigProfileManager.Result result) {
        this.status = result != null && result.message != null ? result.message : "";
        this.statusSuccess = result != null && result.success;
        this.statusExpiry = System.currentTimeMillis() + 3800L;
        if (result != null && result.message != null && !result.message.isBlank()) {
            ModuleNotifications.pushCustomNotification(
                    "\ue006", "Configs", result.message, result.success);
        }
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

    private void addClickRegion(float x, float y, float width, float height, IntConsumer action) {
        float[] clip =
                this.scissorStack.isEmpty()
                        ? null
                        : this.scissorStack.get(this.scissorStack.size() - 1);
        if (clip != null) {
            float left = Math.max(x, clip[0]);
            float top = Math.max(y, clip[1]);
            float right = Math.min(x + width, clip[0] + clip[2]);
            float bottom = Math.min(y + height, clip[1] + clip[3]);
            if (right <= left || bottom <= top) {
                return;
            }

            x = left;
            y = top;
            width = right - left;
            height = bottom - top;
        }

        this.clickRegions.add(new OdinGuiScreen.ClickRegion(x, y, width, height, action));
    }

    private boolean isHovered(float x, float y, float width, float height) {
        if (this.closing) {
            return false;
        } else if (this.dropdownBounds != null
                && !this.renderingDropdown
                && !this.namingConfig
                && this.openDropdown != null
                && this.mouseX >= this.dropdownBounds[0]
                && this.mouseY >= this.dropdownBounds[1]
                && this.mouseX < this.dropdownBounds[0] + this.dropdownBounds[2]
                && this.mouseY < this.dropdownBounds[1] + this.dropdownBounds[3]) {
            return false;
        } else if (!(this.mouseX < x)
                && !(this.mouseY < y)
                && !(this.mouseX >= x + width)
                && !(this.mouseY >= y + height)) {
            for (float[] clip : this.scissorStack) {
                if (this.mouseX < clip[0]
                        || this.mouseY < clip[1]
                        || this.mouseX >= clip[0] + clip[2]
                        || this.mouseY >= clip[1] + clip[3]) {
                    return false;
                }
            }

            if (this.namingConfig
                    && (this.currentTab == OdinGuiScreen.Tab.CONFIGS || this.isPanelLayout())) {
                float boxX = ((this.isPanelLayout() ? this.layoutWidth : 600.0F) - 230.0F) * 0.5F;
                float boxY = ((this.isPanelLayout() ? this.layoutHeight : 380.0F) - 96.0F) * 0.5F;
                boolean mouseInside =
                        this.mouseX >= boxX
                                && this.mouseX <= boxX + 230.0F
                                && this.mouseY >= boxY
                                && this.mouseY <= boxY + 96.0F;
                boolean regionInside =
                        x >= boxX
                                && y >= boxY
                                && x + width <= boxX + 230.0F
                                && y + height <= boxY + 96.0F + 20.0F;
                if (mouseInside != regionInside) {
                    return false;
                }
            }

            return this.inspectedModule == null
                    || !(this.inspectorProgress > 0.5F)
                    || !(x < 406.0F)
                    || !(this.mouseX >= 406.0F);
        } else {
            return false;
        }
    }

    private float animate(Object key, float target, float speed) {
        float current = this.animations.getOrDefault(key, target);
        float next = AnimationUtil.smoothApproach(current, target, speed, this.deltaTime);
        this.animations.put(key, next);
        return next;
    }

    private void pushScissor(DrawContext context, float x, float y, float width, float height) {
        this.scissorStack.add(new float[] {x, y, width, height});
        context.enableScissor(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.ceil(x + width),
                (int) Math.ceil(y + height));
    }

    private void popScissor(DrawContext context) {
        if (!this.scissorStack.isEmpty()) {
            this.scissorStack.remove(this.scissorStack.size() - 1);
        }

        context.disableScissor();
    }

    private List<Module> modules(String category, String query, int statusFilter) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();

        for (Module module : ModuleManager.get().getAll()) {
            if (!"theme".equals(module.name)
                    && (category == null || category.equals(getCategoryKey(module)))
                    && (needle.isEmpty()
                            || module.title.toLowerCase(Locale.ROOT).contains(needle)
                            || module.name.toLowerCase(Locale.ROOT).contains(needle)
                            || module.description != null
                                    && module.description.toLowerCase(Locale.ROOT).contains(needle))
                    && (statusFilter != 1 || module.isActive())
                    && (statusFilter != 2 || !module.isActive())) {
                result.add(module);
            }
        }

        result.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        return result;
    }

    static String getCategoryKey(Module module) {
        if (module.category != null && "scamming".equalsIgnoreCase(module.category.name)) {
            return "scamming";
        } else {
            String name = module.name == null ? "" : module.name.toLowerCase(Locale.ROOT);

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
                        "odinclient-freecam",
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
                        "sky-arts" ->
                        "render";
                case "hud", "discord-presence", "chat-macros", "theme" -> "client";
                default -> "misc";
            };
        }
    }

    static String title(String category) {
        return switch (category) {
            case "combat" -> "Combat";
            case "render" -> "Render";
            case "misc" -> "Misc";
            case "donut" -> "Donut";
            case "client" -> "Client";
            case "scamming" -> "Scamming";
            default -> category;
        };
    }

    static String getCategoryIcon(String category) {
        return switch (category) {
            case "combat" -> "\ue000";
            case "render" -> "\ue001";
            case "donut" -> "\ue003";
            case "client" -> "\ue004";
            case "scamming" -> "\ue00d";
            default -> "\ue002";
        };
    }

    public static String getModuleIconName(Module module) {
        String name = module.name == null ? "" : module.name.toLowerCase(Locale.ROOT);

        return switch (name) {
            case "auto-totem", "hover-totem", "auto-inv-totem" -> "totem_of_undying";
            case "shield-breaker" -> "iron_axe";
            case "triggerbot", "aim-assist" -> "target";
            case "mace-swap" -> "mace";
            case "crystal-optimizer", "auto-crystal" -> "end_crystal";
            case "safe-anchor", "anchor-macro" -> "respawn_anchor";
            case "freecam", "odinclient-freecam", "zoom" -> "spyglass";
            case "freelook" -> "ender_eye";
            case "swingspeed", "render-cycle", "auto-relog" -> "clock";
            case "autolog" -> "oak_door";
            case "auto-firework" -> "firework_rocket";
            case "antivanish-detection" -> "ender_eye";
            case "sprint" -> "sugar";
            case "fast-place" -> "redstone_torch";
            case "auto-tool", "auto-mine" -> "diamond_pickaxe";
            case "tool-size" -> "diamond_sword";
            case "key-homesetter", "home-reset" -> "red_bed";
            case "key-rtp" -> "chorus_fruit";
            case "key-pearl", "pearl-esp", "pearl-trajectory" -> "ender_pearl";
            case "skin-protect", "player-esp" -> "player_head";
            case "name-tags", "name-protect", "fake-roles" -> "name_tag";
            case "block-esp" -> "diamond_ore";
            case "block-outlines" -> "glass";
            case "item-glow" -> "glow_ink_sac";
            case "inventory-hud", "storage-esp" -> "chest";
            case "bedrock-hole-esp" -> "bedrock";
            case "hole-esp" -> "ladder";
            case "jump-circles" -> "feather";
            case "fullbright" -> "glowstone";
            case "sky-arts" -> "painting";
            case "block-entity-debug" -> "structure_block";
            case "light-finder" -> "torch";
            case "suspicious-esp" -> "suspicious_sand";
            case "mob-esp" -> "zombie_spawn_egg";
            case "spawner-flagger" -> "spawner";
            case "block-notifier", "odinclient-block-notifier", "player-notify" -> "bell";
            case "weather-notifier" -> "lightning_rod";
            case "amethyst-esp" -> "amethyst_cluster";
            case "region-map" -> "filled_map";
            case "sus-chunk-finder" -> "recovery_compass";
            case "chunkfinder" -> "map";
            case "player-chunk", "player-debug-v2" -> "compass";
            case "hud" -> "item_frame";
            case "discord-presence" -> "discord";
            case "spotify-hud" -> "music_disc";
            case "fake-stats" -> "book";
            case "fake-pay" -> "emerald";
            case "fake-spawner" -> "spawner";
            case "fake-elytra" -> "elytra";
            case "cord-snapper" -> "shears";
            case "chat-macros" -> "writable_book";
            case "staf-tracker" -> "enchanted_book";
            default -> "unknown_block";
        };
    }

    public static void drawModuleIcon(
            DrawContext context, Module module, float x, float y, float size, int color) {
        drawIcon(context, getModuleIconName(module), x, y, size, color);
    }

    public static void drawIcon(
            DrawContext context, String name, float x, float y, float size, int color) {
        if (name != null && name.length() == 1 && name.charAt(0) >= '\ue000') {
            SdfFontRenderer.drawIcon(context, name, x, y, size * 0.8F, color);
        } else if (!ItemIconAtlas.drawIcon(context, name, x, y, size, color)) {
            String fallback = "discord".equals(name) ? "\ue021" : "\ue022";
            SdfFontRenderer.drawIcon(context, fallback, x, y, size * 0.85F, color);
        }
    }

    private static List<Setting<?>> getSortedSettings(Module module) {
        List<Setting<?>> normal = new ArrayList<>();
        List<Setting<?>> keybinds = new ArrayList<>();
        List<Setting<?>> activation = new ArrayList<>();

        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                if (setting.isVisible() && setting != module.keybind) {
                    if (setting instanceof KeybindSetting) {
                        if (!setting.name.startsWith("activate-")
                                && !setting.name.startsWith("activation-")) {
                            keybinds.add(setting);
                        } else {
                            activation.add(setting);
                        }
                    } else {
                        normal.add(setting);
                    }
                }
            }
        }

        List<Setting<?>> result = HudPositionSettings.injectPositionEditor(normal, module);
        result.addAll(keybinds);
        result.addAll(activation);
        if (module.keybind.isVisible()) {
            result.add(module.keybind);
        }

        return result;
    }

    private static float getSettingHeight(Setting<?> setting) {
        return !(setting instanceof IntSetting) && !(setting instanceof NumberSetting)
                ? 20.0F
                : 30.0F;
    }

    private static List<String> wrapText(
            SdfFontRenderer.FontStyle style, String text, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            StringBuilder current = new StringBuilder();

            for (String word : text.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!(SdfFontRenderer.width(style, candidate) <= maxWidth) && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                    if (lines.size() == maxLines) {
                        break;
                    }
                } else {
                    current = new StringBuilder(candidate);
                }
            }

            if (lines.size() < maxLines && !current.isEmpty()) {
                lines.add(current.toString());
            }

            if (lines.size() == maxLines) {
                lines.set(
                        maxLines - 1,
                        SdfFontRenderer.truncateToWidth(style, lines.get(maxLines - 1), maxWidth));
            }

            return lines;
        } else {
            return lines;
        }
    }

    private static void cycleEnum(EnumSetting setting, int step) {
        Enum current = (Enum) setting.getValue();
        Enum[] constants = (Enum[]) current.getDeclaringClass().getEnumConstants();
        setting.setValue(constants[Math.floorMod(current.ordinal() + step, constants.length)]);
    }

    private static String formatEnumName(Object value) {
        if (value instanceof Enum enumValue) {
            String name = enumValue.name().replace('_', ' ').toLowerCase(Locale.ROOT);
            return name.isEmpty()
                    ? name
                    : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        } else {
            return String.valueOf(value);
        }
    }

    private static String formatNumber(double value, int decimals) {
        String text = String.format(Locale.ROOT, "%." + decimals + "f", value);

        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }

        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static int getDecimalPlaces(NumberSetting setting) {
        return !"break-delay".equals(setting.name) && !"place-delay".equals(setting.name) ? 2 : 3;
    }

    private static double normalizeSliderValue(double value, double min, double max) {
        return max <= min ? 0.0 : (value - min) / (max - min);
    }

    private static boolean isUnbindKey(int key, int scancode) {
        return key == 256 || key == 261 || key == 259 || scancode == 83 || scancode == 339;
    }

    private static boolean isCaretVisible() {
        return (System.currentTimeMillis() / 450L & 1L) == 0L;
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record ClickRegion(float x, float y, float w, float h, IntConsumer action) {
        boolean contains(float px, float py) {
            return px >= this.x && py >= this.y && px < this.x + this.w && py < this.y + this.h;
        }
    }

    private record NavEntry(String key, String title, String icon, boolean module) {}

    private static enum Tab {
        MODULES,
        CONFIGS,
        KEYBINDS;
    }
}
