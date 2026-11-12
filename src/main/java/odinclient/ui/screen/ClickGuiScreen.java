package odinclient.ui.screen;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import odinclient.manager.ConfigProfileManager;
import odinclient.manager.ModuleManager;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.module.gamble.GambleRigger;
import odinclient.module.hud.ThemeModule;
import odinclient.module.privacy.NameProtect;
import odinclient.module.shortcut.ChatMacros;
import odinclient.render.EntityColor;
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
import odinclient.setting.type.MapSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.setting.type.StringListSetting;
import odinclient.setting.type.StringSetting;
import odinclient.ui.draw.CircleRenderer;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.ScreenScaleCompat;
import odinclient.ui.widget.GuiKeybind;
import odinclient.ui.widget.SettingTooltipRenderer;

import org.joml.Matrix3x2fStack;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class ClickGuiScreen extends Screen {
    private static int accentColor = -5745161;
    private static int accentBorderColor = -9623335;
    private static int accentHighlightColor = -7643914;
    private static final int TEXT_PRIMARY = -789256;
    private static final int TEXT_SECONDARY = -6182984;
    private static final int TEXT_MUTED = -9866875;
    private static final int COLOR_SUCCESS = -12392796;
    private static final int COLOR_DANGER = -41096;
    private static final int COLOR_WARNING = -18355;
    private static final int PANEL_BORDER_FILL = -234222314;
    private static final int SECTION_FILL = -233761503;
    private static final int ROW_FILL = -350938585;
    private static final int ROW_HOVER_FILL = -266657488;
    private static final int PANEL_BORDER = -1774500775;
    private static final int SUBTLE_BORDER = 1882471762;
    private static final int CARD_HOVER_FILL = -653982192;
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 36;
    private static final int MAX_SIDEBAR_WIDTH = 150;
    private static final int MAX_COMPACT_RIGHT_WIDTH = 172;
    private static final int MAX_RIGHT_WIDTH = 238;
    private static final Identifier DONUT_CATEGORY_TEXTURE =
            Identifier.of("odinclient", "textures/gui/donut_category.png");
    private static final Identifier BRAND_LOGO_56 =
            Identifier.of("odinclient", "textures/gui/odin_brand_logo_56.png");
    private static final Identifier BRAND_LOGO_48 =
            Identifier.of("odinclient", "textures/gui/odin_brand_logo_48.png");
    private static final Identifier PICKER_SATURATION_TEXTURE =
            Identifier.of("odinclient", "textures/gui/picker_saturation_overlay.png");
    private static final Identifier PICKER_BRIGHTNESS_TEXTURE =
            Identifier.of("odinclient", "textures/gui/picker_brightness_overlay.png");
    private static final Identifier PICKER_HUE_TEXTURE =
            Identifier.of("odinclient", "textures/gui/picker_hue_bar.png");
    private static final Identifier PICKER_OPACITY_TEXTURE =
            Identifier.of("odinclient", "textures/gui/picker_opacity_overlay.png");
    private static final Identifier GLOW_TEXTURE =
            Identifier.of("odinclient", "textures/gui/soft_accent_glow.png");
    private static final Identifier FOOTER_HOME_ICON =
            Identifier.of("odinclient", "textures/gui/footer_home.png");
    private static final Identifier FOOTER_CONFIGS_ICON =
            Identifier.of("odinclient", "textures/gui/footer_configs.png");
    private static final Identifier FOOTER_THEMES_ICON =
            Identifier.of("odinclient", "textures/gui/footer_themes.png");
    private static final Identifier CONFIG_SAVE_ICON =
            Identifier.of("odinclient", "textures/gui/config_save.png");
    private static final Identifier CONFIG_NEW_ICON =
            Identifier.of("odinclient", "textures/gui/config_plus.png");
    private static final Identifier CONFIG_IMPORT_ICON =
            Identifier.of("odinclient", "textures/gui/config_import.png");
    private static final Identifier CONFIG_SHARE_ICON =
            Identifier.of("odinclient", "textures/gui/config_share.png");
    private static final Identifier CONFIG_RESET_ICON =
            Identifier.of("odinclient", "textures/gui/config_reload.png");
    private static final Identifier CONFIG_LOAD_ICON =
            Identifier.of("odinclient", "textures/gui/config_reload.png");
    private static final Identifier CONFIG_DELETE_ICON =
            Identifier.of("odinclient", "textures/gui/config_delete.png");
    private static final Identifier CONFIG_MORE_ICON =
            Identifier.of("odinclient", "textures/gui/config_more.png");
    private static final Identifier CONFIG_INFO_ICON =
            Identifier.of("odinclient", "textures/gui/config_info.png");
    private static final int[] PICKER_PRESET_COLORS =
            new int[] {
                -50384, -680437, -340971, -12339138, -15157326, -14326805, -5745161, -1292135,
                -7629919, -526084
            };
    private static final String[] THEME_PRESET_NAMES =
            new String[] {"ODIN PURPLE", "MIDNIGHT", "EMERALD", "CRIMSON", "SUNSET", "AURORA"};
    private static final String[] THEME_PRESET_DESCRIPTIONS =
            new String[] {
                "Signature violet",
                "Deep electric blue",
                "Clean jade green",
                "Bold ruby red",
                "Warm amber glow",
                "Animated gradient"
            };
    private static final int[] THEME_PRESET_COLORS =
            new int[] {-5745161, -14447617, -12392796, -45715, -680437, -7643914};
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy - h:mm a", Locale.ENGLISH);
    private final Screen parent;
    private float guiScale = 1.0F;
    private final Map<Object, Float> hoverAnimations = new HashMap<>();
    private final Map<Module, Float> moduleActiveAnimations = new IdentityHashMap<>();
    private final Map<Object, Float> toggleAnimations = new IdentityHashMap<>();
    private final Map<String, Float> categoryProgressAnimations = new HashMap<>();
    private final Map<String, ItemStack> itemStackCache = new HashMap<>();
    private final Map<String, List<Module>> modulesByCategory = new HashMap<>();
    private final Map<Module, Map<Integer, ClickGuiScreen.CardText>> cardTextCache =
            new IdentityHashMap<>();
    private final List<ClickGuiScreen.CategoryHit> categoryHits = new ArrayList<>();
    private final List<ClickGuiScreen.ModuleCardHit> moduleCardHits = new ArrayList<>();
    private final List<ClickGuiScreen.SettingRowHit> settingRowHits = new ArrayList<>();
    private final List<ClickGuiScreen.SelectorItemHit> selectorItemHits = new ArrayList<>();
    private List<Module> allModules = List.of();
    private List<Category> categories = List.of();
    private List<Module> searchResults = List.of();
    private String lastSearchQuery = "";
    private int lastModuleCount = -1;
    private Module cachedSettingsModule;
    private int cachedSettingsHash;
    private List<ClickGuiScreen.SettingEntry> cachedSettings = List.of();
    private String selectedCategory;
    private Module selectedModule;
    private String searchText = "";
    private boolean searchFocused;
    private float openProgress;
    private float detailProgress;
    private float moduleScrollAnimated;
    private float moduleScrollTarget;
    private float moduleScrollMax;
    private float settingScrollAnimated;
    private float settingScrollTarget;
    private float settingScrollMax;
    private long lastFrameNanos = System.nanoTime();
    private float frameDelta = 0.016666668F;
    private final float[] fpsHistory = new float[48];
    private int fpsHistoryIndex;
    private long lastFpsSampleTime;
    private float fpsGraphShift;
    private ClickGuiScreen.Rect searchRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect closeRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect homeTabRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect configsTabRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect themesTabRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect enableAllRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect disableAllRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect prevCategoryRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect nextCategoryRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect backRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect detailPanelRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect guiKeybindRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect moduleGridRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect settingsListRect = ClickGuiScreen.Rect.EMPTY;
    private boolean configsTabOpen;
    private boolean themesTabOpen;
    private boolean browsingPresets;
    private int selectedProfileIndex = -1;
    private boolean newConfigDialogOpen;
    private String newConfigName = "";
    private String statusMessage = "";
    private boolean statusSuccess = true;
    private long statusExpireTime;
    private long resetConfirmExpireTime;
    private ClickGuiScreen.Rect myConfigsTabRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect browseTabRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect saveConfigRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect newConfigRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect importConfigRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect shareConfigRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect resetConfigRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect dialogCancelRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect dialogSaveRect = ClickGuiScreen.Rect.EMPTY;
    private final List<ClickGuiScreen.ProfileRowHit> profileRowHits = new ArrayList<>();
    private final List<ClickGuiScreen.PresetRowHit> presetRowHits = new ArrayList<>();
    private final List<ClickGuiScreen.ThemePresetHit> themePresetHits = new ArrayList<>();
    private ClickGuiScreen.Rect customAccentRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect rainbowToggleRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect animationsToggleRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect msaaToggleRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect dashboardModeRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect panelsModeRect = ClickGuiScreen.Rect.EMPTY;
    private final SettingTooltipRenderer tooltipRenderer = new SettingTooltipRenderer();
    private Setting<?> draggedSetting;
    private ClickGuiScreen.Rect draggedSliderRect = ClickGuiScreen.Rect.EMPTY;
    private Setting<?> editingSetting;
    private String editBuffer = "";
    private KeybindSetting listeningKeybind;
    private ClickGuiScreen.SelectorMode selectorMode = ClickGuiScreen.SelectorMode.NONE;
    private BlockColorMapSetting coloredBlocksSetting;
    private BlockListSetting blockListSetting;
    private EntityTypeListSetting entityListSetting;
    private String selectorSearchText = "";
    private boolean selectorShowSelectedOnly;
    private float selectorScrollAnimated;
    private float selectorScrollTarget;
    private float selectorScrollMax;
    private float selectorProgress;
    private List<Object> selectorEntries = List.of();
    private boolean selectorDirty;
    private Map<Block, EntityColor> pendingBlockColors;
    private List<Block> pendingBlocks;
    private List<EntityType<?>> pendingEntities;
    private Map<EntityType<?>, EntityColor> pendingEntityColors;
    private ClickGuiScreen.Rect selectorDialogRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorSearchRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorAllTabRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorSelectedTabRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorFilterRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorCloseRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorClearRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorCancelRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorDoneRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect selectorGridRect = ClickGuiScreen.Rect.EMPTY;
    private ColorSetting pickerColorSetting;
    private BlockColorMapSetting pickerBlockColorsSetting;
    private Block pickerBlock;
    private EntityTypeListSetting pickerEntitySetting;
    private EntityType<?> pickerEntityType;
    private EntityColor pickerDirectColor;
    private EntityColor pickerOriginalColor;
    private float pickerHue;
    private float pickerSaturation;
    private float pickerBrightness;
    private float pickerOpacity;
    private int pickerDragTarget;
    private float pickerProgress;
    private ClickGuiScreen.Rect pickerDialogRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect pickerAreaRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect pickerHueRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect pickerOpacityRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect pickerCancelRect = ClickGuiScreen.Rect.EMPTY;
    private ClickGuiScreen.Rect pickerApplyRect = ClickGuiScreen.Rect.EMPTY;
    private final ClickGuiScreen.Rect[] pickerPresetRects =
            new ClickGuiScreen.Rect[PICKER_PRESET_COLORS.length];

    public ClickGuiScreen() {
        this(null);
    }

    public ClickGuiScreen(Screen parent) {
        super(Text.literal("OdinClient"));
        this.parent = parent;
        this.ensureValidCategory();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {}

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    @Override
    public void removed() {
        this.endTextEdit(true);
        this.selectorMode = ClickGuiScreen.SelectorMode.NONE;
        this.cancelColorPicker();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.guiScale = ScreenScaleCompat.getScale(this.width);
        int scaledMouseX = Math.round(mouseX / this.guiScale);
        int scaledMouseY = Math.round(mouseY / this.guiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(this.guiScale, this.guiScale);
        this.updateAccentColor();
        this.updateAnimations();
        this.ensureValidCategory();
        ClickGuiScreen.PanelLayout layout = this.computeLayout();
        this.categoryHits.clear();
        this.moduleCardHits.clear();
        this.settingRowHits.clear();
        this.guiKeybindRect = ClickGuiScreen.Rect.EMPTY;
        context.fill(
                0,
                0,
                this.getScreenWidth(),
                this.getScreenHeight(),
                applyAlpha(-1207696117, this.openProgress));
        this.drawGlow(context, layout.x, layout.y, layout.w, layout.h, 10, accentColor, 0.34F);
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                layout.x,
                layout.y,
                layout.w,
                layout.h,
                10,
                applyAlpha(-234222314, this.openProgress),
                applyAlpha(-1774500775, this.openProgress));
        this.drawHeader(context, layout, scaledMouseX, scaledMouseY);
        this.drawSidebar(context, layout, scaledMouseX, scaledMouseY);
        if (this.configsTabOpen) {
            this.drawConfigsTab(context, layout, scaledMouseX, scaledMouseY);
        } else if (this.themesTabOpen) {
            this.drawThemesTab(context, layout, scaledMouseX, scaledMouseY);
        } else {
            this.drawModuleGrid(context, layout, scaledMouseX, scaledMouseY);
            this.drawRightPanel(context, layout, scaledMouseX, scaledMouseY);
        }

        this.drawFooter(context, layout, scaledMouseX, scaledMouseY);
        if (this.newConfigDialogOpen) {
            this.drawNewConfigDialog(context, layout, scaledMouseX, scaledMouseY);
        }

        if (this.selectorMode != ClickGuiScreen.SelectorMode.NONE) {
            this.drawSelectorDialog(context, scaledMouseX, scaledMouseY);
        }

        if (this.isColorPickerOpen()) {
            this.drawColorPicker(context, scaledMouseX, scaledMouseY);
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
                this.openProgress);
    }

    private Setting<?> getHoveredSetting(int mouseX, int mouseY) {
        if (this.selectedModule != null
                && !this.configsTabOpen
                && !this.themesTabOpen
                && !this.newConfigDialogOpen
                && this.selectorMode == ClickGuiScreen.SelectorMode.NONE
                && !this.isColorPickerOpen()
                && this.draggedSetting == null
                && this.editingSetting == null
                && this.listeningKeybind == null
                && this.settingsListRect.contains(mouseX, mouseY)) {
            for (ClickGuiScreen.SettingRowHit hit : this.settingRowHits) {
                if (hit.row.contains(mouseX, mouseY)) {
                    return hit.setting;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private int getScreenWidth() {
        return ScreenScaleCompat.getScaledWidth();
    }

    private int getScreenHeight() {
        return ScreenScaleCompat.getScaledHeight();
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        this.frameDelta =
                Math.min(0.05F, Math.max(0.001F, (float) (now - this.lastFrameNanos) / 1.0E9F));
        this.lastFrameNanos = now;
        if (!ThemeModule.areAnimationsEnabled()) {
            this.openProgress = 1.0F;
            this.detailProgress = this.selectedModule == null ? 0.0F : 1.0F;
            this.pickerProgress = this.isColorPickerOpen() ? 1.0F : 0.0F;
            this.selectorProgress =
                    this.selectorMode == ClickGuiScreen.SelectorMode.NONE ? 0.0F : 1.0F;
            this.moduleScrollAnimated = this.moduleScrollTarget;
            this.settingScrollAnimated = this.settingScrollTarget;
            this.selectorScrollAnimated = this.selectorScrollTarget;
            this.fpsGraphShift = 0.0F;
        } else {
            this.openProgress = smoothTowards(this.openProgress, 1.0F, 10.0F, this.frameDelta);
            this.detailProgress =
                    smoothTowards(
                            this.detailProgress,
                            this.selectedModule == null ? 0.0F : 1.0F,
                            12.0F,
                            this.frameDelta);
            this.pickerProgress =
                    smoothTowards(
                            this.pickerProgress,
                            this.isColorPickerOpen() ? 1.0F : 0.0F,
                            14.0F,
                            this.frameDelta);
            this.selectorProgress =
                    smoothTowards(
                            this.selectorProgress,
                            this.selectorMode == ClickGuiScreen.SelectorMode.NONE ? 0.0F : 1.0F,
                            14.0F,
                            this.frameDelta);
            this.moduleScrollAnimated =
                    smoothTowards(
                            this.moduleScrollAnimated,
                            this.moduleScrollTarget,
                            14.0F,
                            this.frameDelta);
            this.settingScrollAnimated =
                    smoothTowards(
                            this.settingScrollAnimated,
                            this.settingScrollTarget,
                            14.0F,
                            this.frameDelta);
            this.selectorScrollAnimated =
                    smoothTowards(
                            this.selectorScrollAnimated,
                            this.selectorScrollTarget,
                            14.0F,
                            this.frameDelta);
            this.fpsGraphShift = smoothTowards(this.fpsGraphShift, 0.0F, 13.0F, this.frameDelta);
        }
    }

    private ClickGuiScreen.PanelLayout computeLayout() {
        int width = Math.max(620, Math.min(956, Math.round(this.getScreenWidth() * 0.78F)));
        int height = Math.max(360, Math.min(526, Math.round(this.getScreenHeight() * 0.8F)));
        width = Math.min(width, this.getScreenWidth() - 12);
        height = Math.min(height, this.getScreenHeight() - 12);
        int x = (this.getScreenWidth() - width) / 2;
        int y =
                (this.getScreenHeight() - height) / 2
                        + Math.round((1.0F - easeOutCubic(this.openProgress)) * 12.0F);
        int sidebarWidth = Math.max(110, Math.min(150, Math.round(width * 0.16F)));
        int compactRightWidth = Math.max(150, Math.min(172, Math.round(width * 0.2F)));
        int wideRightWidth = Math.max(196, Math.min(238, Math.round(width * 0.29F)));
        int rightWidth =
                Math.round(
                        compactRightWidth
                                + (wideRightWidth - compactRightWidth)
                                        * easeOutCubic(this.detailProgress));
        return new ClickGuiScreen.PanelLayout(x, y, width, height, sidebarWidth, rightWidth);
    }

    private void drawHeader(
            DrawContext context, ClickGuiScreen.PanelLayout layout, int mouseX, int mouseY) {
        DashboardRenderer.drawRoundedRect(
                context,
                layout.x + 1,
                layout.y + 1,
                layout.w - 2,
                50,
                9,
                applyAlpha(-234024931, this.openProgress));
        drawHorizontalGradient(
                context,
                layout.x + 1,
                layout.y + 50 - 2,
                layout.w - 2,
                2,
                applyAlpha(accentColor & 16777215, this.openProgress),
                applyAlpha(accentColor, this.openProgress));
        int logoX = layout.x + 18;
        int logoY = layout.y + 11;
        this.drawBrandLogo(context, logoX, logoY, 28);
        this.drawText(context, "ODINCLIENT", layout.x + 55, layout.y + 18, -789256);
        int searchX = layout.x + layout.sidebarW + 68;
        int searchWidth =
                Math.max(150, Math.min(286, layout.w - layout.sidebarW - layout.rightW - 126));
        this.searchRect = new ClickGuiScreen.Rect(searchX, layout.y + 11, searchWidth, 28);
        boolean searchHovered = this.searchRect.contains(mouseX, mouseY);
        float searchHover = this.animateHover("search", searchHovered || this.searchFocused);
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                this.searchRect.x,
                this.searchRect.y,
                this.searchRect.w,
                this.searchRect.h,
                6,
                applyAlpha(
                        lerpColor(-15723745, lerpColor(-15723745, accentColor, 0.08F), searchHover),
                        this.openProgress),
                applyAlpha(lerpColor(-1774500775, accentColor, searchHover), this.openProgress));
        this.drawText(
                context,
                "Q",
                this.searchRect.x + 10,
                this.searchRect.y + 9,
                searchHover > 0.5F ? accentColor : -6182984);
        String searchLabel =
                this.configsTabOpen
                        ? "Manage config profiles..."
                        : (this.themesTabOpen
                                ? "Customize colors and visuals..."
                                : (this.searchText.isEmpty()
                                        ? (this.searchFocused ? "" : "Search modules...")
                                        : this.searchText));
        if (!this.configsTabOpen
                && !this.themesTabOpen
                && this.searchFocused
                && isCursorVisible()) {
            searchLabel = searchLabel + "_";
        }

        this.drawText(
                context,
                this.ellipsize(searchLabel, this.searchRect.w - 42),
                this.searchRect.x + 27,
                this.searchRect.y + 9,
                !this.configsTabOpen && !this.themesTabOpen && !this.searchText.isEmpty()
                        ? -789256
                        : -6182984);
        DashboardRenderer.drawRoundedRect(
                context,
                this.searchRect.x + this.searchRect.w - 24,
                this.searchRect.y + 5,
                18,
                18,
                4,
                applyAlpha(-15000019, this.openProgress));
        this.drawCenteredText(
                context,
                "/",
                this.searchRect.x + this.searchRect.w - 15,
                this.searchRect.y + 10,
                -9866875);
        int profileX = layout.x + layout.w - layout.rightW + 6;
        int profileWidth = layout.rightW - 16;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                profileX,
                layout.y + 7,
                profileWidth,
                36,
                7,
                applyAlpha(-15723745, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        this.drawBrandLogo(context, profileX + 7, layout.y + 13, 24);
        this.drawText(context, "OdinClient", profileX + 38, layout.y + 14, -789256);
        this.drawText(context, "PREMIUM", profileX + 38, layout.y + 27, accentColor);
        this.closeRect =
                new ClickGuiScreen.Rect(profileX + profileWidth - 25, layout.y + 15, 18, 18);
        boolean closeHovered = this.closeRect.contains(mouseX, mouseY);
        DashboardRenderer.drawRoundedRect(
                context,
                this.closeRect.x,
                this.closeRect.y,
                this.closeRect.w,
                this.closeRect.h,
                4,
                applyAlpha(closeHovered ? -12970203 : -15131607, this.openProgress));
        this.drawCenteredText(
                context,
                "X",
                this.closeRect.x + 9,
                this.closeRect.y + 5,
                closeHovered ? -41096 : -6182984);
    }

    private void drawSidebar(
            DrawContext context, ClickGuiScreen.PanelLayout layout, int mouseX, int mouseY) {
        int x = layout.x + 8;
        int y = layout.y + 50 + 9;
        int width = layout.sidebarW - 15;
        int profileCardHeight = 245;
        int availableHeight = layout.h - 50 - 36 - profileCardHeight - 27;
        List<Category> categoryList = this.getCategories();
        int rowHeight =
                Math.max(24, Math.min(34, availableHeight / Math.max(1, categoryList.size())));

        for (Category category : categoryList) {
            boolean selected = category.name.equalsIgnoreCase(this.selectedCategory);
            ClickGuiScreen.Rect rect = new ClickGuiScreen.Rect(x, y, width, rowHeight - 3);
            boolean hovered = rect.contains(mouseX, mouseY);
            float hover = this.animateHover("cat:" + category.name, hovered || selected);
            int fill = lerpColor(-350938585, -266657488, hover * 0.3F);
            this.drawGlowRect(
                    context,
                    rect.x,
                    rect.y,
                    rect.w,
                    rect.h,
                    7,
                    fill,
                    1882471762,
                    accentColor,
                    hover * (selected ? 0.82F : 0.38F));
            if (!this.drawCategoryIcon(
                    context,
                    category.name,
                    category.icon,
                    rect.x + 9,
                    rect.y + Math.max(2, (rect.h - 16) / 2))) {
                this.drawCenteredText(
                        context,
                        title(category.name).substring(0, 1),
                        rect.x + 17,
                        rect.y + (rect.h - 8) / 2,
                        selected ? accentColor : -6182984);
            }

            this.drawText(
                    context,
                    this.ellipsize(title(category.name), rect.w - 38),
                    rect.x + 32,
                    rect.y + (rect.h - 8) / 2,
                    selected ? -789256 : -2698016);
            this.categoryHits.add(new ClickGuiScreen.CategoryHit(rect, category.name));
            y += rowHeight;
        }

        int cardY = y + 6;
        int bottom = layout.y + layout.h - 36 - 8;
        this.drawSidebarProfileCard(context, x, cardY, width, Math.max(112, bottom - cardY));
    }

    private void drawSidebarProfileCard(DrawContext context, int x, int y, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName =
                client.getGameProfile() == null ? "Player" : client.getGameProfile().name();
        playerName = NameProtect.replaceNames(playerName);
        String location = "Main Menu";
        ServerInfo serverEntry = client.getCurrentServerEntry();
        if (serverEntry != null) {
            boolean unnamedServer =
                    serverEntry.name == null
                            || serverEntry.name.isBlank()
                            || "Minecraft Server".equalsIgnoreCase(serverEntry.name);
            location = unnamedServer ? serverEntry.address : serverEntry.name;
        } else if (client.isConnectedToLocalServer()) {
            location = "Singleplayer";
        }

        int fps = Math.max(0, client.getCurrentFps());
        double speed = 0.0;
        int ping = 0;
        if (client.player != null) {
            Vec3d velocity = client.player.getVelocity();
            speed = Math.hypot(velocity.x, velocity.z) * 20.0;
            ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
            if (networkHandler != null) {
                PlayerListEntry playerListEntry =
                        networkHandler.getPlayerListEntry(client.player.getGameProfile().id());
                if (playerListEntry != null) {
                    ping = Math.max(0, playerListEntry.getLatency());
                }
            }
        }

        this.sampleFps(fps);
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                width,
                height,
                7,
                applyAlpha(-267316192, this.openProgress),
                applyAlpha(lerpColor(1882471762, accentColor, 0.2F), this.openProgress));
        int avatarX = x + 9;
        int avatarY = y + 10;
        int avatarSize = height < 155 ? 24 : 28;
        DashboardRenderer.drawRoundedRect(
                context,
                avatarX - 1,
                avatarY - 1,
                avatarSize + 2,
                avatarSize + 2,
                6,
                applyAlpha(lerpColor(-15460319, accentColor, 0.16F), this.openProgress));
        if (client.player != null) {
            PlayerSkinDrawer.draw(context, client.player.getSkin(), avatarX, avatarY, avatarSize);
        } else {
            this.drawCenteredText(
                    context,
                    "O",
                    avatarX + avatarSize / 2,
                    avatarY + avatarSize / 2 - 4,
                    accentColor);
        }

        int textX = avatarX + avatarSize + 7;
        int textWidth = Math.max(24, x + width - 8 - textX);
        this.drawTextFitted(context, playerName, textX, avatarY, textWidth, -789256);
        this.drawSmallText(context, "Playing on", textX, avatarY + 13, -6182984);
        this.drawSmallText(
                context, this.ellipsize(location, textWidth), textX, avatarY + 23, -789256);
        if (height >= 140) {
            int badgeY = avatarY + 35;
            this.drawPremiumBadge(context, textX, badgeY, textWidth);
        }

        int statsY = y + (height < 155 ? 45 : 59);
        context.fill(
                x + 9,
                statsY,
                x + width - 9,
                statsY + 1,
                applyAlpha(1714699602, this.openProgress));
        int statWidth = (width - 18) / 3;
        String[] labels = new String[] {"FPS", "BPS", "PING"};
        String[] values =
                new String[] {
                    String.valueOf(fps),
                    String.format(Locale.ROOT, "%.1f", speed),
                    String.valueOf(ping)
                };

        for (int i = 0; i < 3; i++) {
            int centerX = x + 9 + statWidth * i + statWidth / 2;
            this.drawSmallTextCentered(context, labels[i], centerX, statsY + 9, -6182984);
            this.drawCenteredText(context, values[i], centerX, statsY + 22, -789256);
        }

        int graphTop = statsY + 38;
        if (height >= 140) {
            context.fill(
                    x + 9,
                    graphTop,
                    x + width - 9,
                    graphTop + 1,
                    applyAlpha(1144274258, this.openProgress));
            int graphY = graphTop + 6;
            int clockY = y + height - 38;
            int graphHeight = clockY - graphY - 6;
            if (graphHeight >= 10) {
                this.drawFpsGraph(context, x + 9, graphY, width - 18, graphHeight);
            }
        }

        int timeY = y + height - 38;
        int timeHeight = 29;
        int timeFill = lerpColor(-15592162, accentColor, 0.08F);
        DashboardRenderer.drawRoundedRect(
                context,
                x + 8,
                timeY,
                width - 16,
                timeHeight,
                5,
                applyAlpha(timeFill, this.openProgress));
        LocalDateTime now = LocalDateTime.now();
        boolean night = now.getHour() >= 22 || now.getHour() < 7;
        this.drawDayNightIcon(context, x + 15, timeY + 8, night, timeFill);
        this.drawSmallText(context, TIME_FORMAT.format(now), x + 32, timeY + 5, -789256);
        this.drawSmallText(context, DATE_FORMAT.format(now), x + 32, timeY + 16, -6182984);
    }

    private void sampleFps(int fps) {
        long now = System.currentTimeMillis();
        if (now - this.lastFpsSampleTime >= 120L) {
            float sample = clamp(fps / 240.0F, 0.05F, 1.0F);
            if (this.lastFpsSampleTime == 0L) {
                for (int i = 0; i < this.fpsHistory.length; i++) {
                    this.fpsHistory[i] = sample;
                }
            }

            this.fpsHistory[this.fpsHistoryIndex] = sample;
            this.fpsHistoryIndex = (this.fpsHistoryIndex + 1) % this.fpsHistory.length;
            this.lastFpsSampleTime = now;
            this.fpsGraphShift = 1.0F;
        }
    }

    private void drawFpsGraph(DrawContext context, int x, int y, int width, int height) {
        DashboardRenderer.drawRoundedRect(
                context,
                x,
                y,
                width,
                height,
                4,
                applyAlpha(alpha(accentColor, 25), this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context,
                x + 1,
                y + height - 7,
                width - 2,
                6,
                3,
                applyAlpha(alpha(accentColor, 45), this.openProgress));
        int innerWidth = Math.max(1, width - 4);
        int sampleCount = Math.min(this.fpsHistory.length - 1, Math.max(4, innerWidth / 2));
        int startIndex =
                Math.floorMod(this.fpsHistoryIndex - sampleCount - 1, this.fpsHistory.length);
        int scale = 4;
        int pixelWidth = innerWidth * scale;
        int baseline = (height - 2) * scale;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + 2, y);
        matrices.scale(1.0F / scale, 1.0F / scale);

        try {
            for (int px = 0; px < pixelWidth; px++) {
                float t = pixelWidth <= 1 ? 0.0F : (float) px / (pixelWidth - 1);
                float position = 1.0F - this.fpsGraphShift + t * (sampleCount - 1);
                int index = Math.max(0, Math.min(sampleCount - 1, (int) Math.floor(position)));
                float frac = clamp(position - index, 0.0F, 1.0F);
                float p0 = this.getFpsSample(startIndex, sampleCount, index - 1);
                float p1 = this.getFpsSample(startIndex, sampleCount, index);
                float p2 = this.getFpsSample(startIndex, sampleCount, index + 1);
                float p3 = this.getFpsSample(startIndex, sampleCount, index + 2);
                float value = clamp(catmullRom(p0, p1, p2, p3, frac), 0.05F, 1.0F);
                int top = baseline - Math.max(8, Math.round((height - 6) * scale * value));
                int lineColor =
                        applyAlpha(
                                lerpColor(accentColor, accentHighlightColor, value),
                                this.openProgress);
                context.fillGradient(
                        px,
                        top,
                        px + 1,
                        baseline,
                        applyAlpha(alpha(accentColor, 176), this.openProgress),
                        applyAlpha(alpha(accentColor, 24), this.openProgress));
                context.fillGradient(
                        px,
                        top,
                        px + 1,
                        top + 7,
                        applyAlpha(alpha(accentColor, 112), this.openProgress),
                        accentColor & 16777215);
                context.fill(px, top, px + 1, top + 2, lineColor);
            }
        } finally {
            matrices.popMatrix();
        }
    }

    private float getFpsSample(int start, int count, int offset) {
        int clamped = Math.max(0, Math.min(count, offset));
        return this.fpsHistory[(start + clamped) % this.fpsHistory.length];
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float tc = clamp(t, 0.0F, 1.0F);
        float t2 = tc * tc;
        float t3 = t2 * tc;
        return 0.5F
                * (2.0F * p1
                        + (p2 - p0) * tc
                        + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
                        + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
    }

    private void drawPremiumBadge(DrawContext context, int x, int y, int maxWidth) {
        String text = "PREMIUM";
        int textWidth = DashboardRenderer.microWidth(this.textRenderer, text);
        int dotSize = 3;
        int gap = 3;
        int contentWidth = dotSize + gap + textWidth;
        int badgeWidth = Math.max(22, Math.min(maxWidth, contentWidth + 6));
        int badgeHeight = 9;
        int dotX = x + Math.max(3, (badgeWidth - contentWidth) / 2);
        int dotY = y + 3;
        DashboardRenderer.drawRoundedRect(
                context,
                x,
                y,
                badgeWidth,
                badgeHeight,
                5,
                applyAlpha(
                        alpha(lerpColor(-350938585, accentColor, 0.24F), 230), this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context,
                x + 2,
                y + 1,
                badgeWidth - 4,
                2,
                1,
                applyAlpha(
                        alpha(lerpColor(-350938585, accentColor, 0.16F), 36), this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context,
                dotX - 1,
                dotY - 1,
                dotSize + 2,
                dotSize + 2,
                3,
                applyAlpha(alpha(accentColor, 32), this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context,
                dotX,
                dotY,
                dotSize,
                dotSize,
                2,
                applyAlpha(lerpColor(accentColor, -789256, 0.12F), this.openProgress));
        DashboardRenderer.drawMicroText(
                context,
                this.textRenderer,
                text,
                dotX + dotSize + gap,
                y,
                applyAlpha(lerpColor(accentColor, -789256, 0.58F), this.openProgress));
    }

    private void drawDayNightIcon(
            DrawContext context, int x, int y, boolean night, int cutoutColor) {
        int color = night ? accentColor : -14249;
        if (night) {
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(0.5F, 0.5F);

            try {
                DashboardRenderer.drawRoundedRect(
                        context, 0, 0, 20, 20, 10, applyAlpha(color, this.openProgress));
                DashboardRenderer.drawRoundedRect(
                        context, 8, -2, 16, 16, 8, applyAlpha(cutoutColor, this.openProgress));
            } finally {
                matrices.popMatrix();
            }
        } else {
            DashboardRenderer.drawRoundedRect(
                    context, x + 1, y + 1, 9, 9, 5, applyAlpha(color, this.openProgress));
        }
    }

    private void drawModuleGrid(
            DrawContext context, ClickGuiScreen.PanelLayout layout, int mouseX, int mouseY) {
        int x = layout.x + layout.sidebarW + 6;
        int y = layout.y + 50 + 8;
        int width = layout.w - layout.sidebarW - layout.rightW - 12;
        int height = layout.h - 50 - 36 - 15;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                width,
                height,
                8,
                applyAlpha(-921956582, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        this.drawBottomGlow(context, x + 2, y + height - 33, width - 4, 30);
        String heading =
                this.searchText.isBlank() ? title(this.selectedCategory) : "Search Results";
        this.drawText(context, heading, x + 14, y + 12, -789256);
        this.drawText(
                context,
                this.searchText.isBlank()
                        ? getCategoryDescription(this.selectedCategory)
                        : "Matching modules across every category.",
                x + 14,
                y + 26,
                -6182984);
        List<Module> modules = this.getVisibleModules();
        int enabledCount = countEnabled(modules);
        this.drawRightAlignedText(
                context,
                enabledCount + " / " + modules.size() + " ENABLED",
                x + width - 14,
                y + 14,
                enabledCount == modules.size() && !modules.isEmpty() ? -12392796 : accentColor);
        int gridX = x + 12;
        int gridY = y + 45;
        int gridWidth = width - 24;
        int gridHeight = height - 57;
        this.moduleGridRect = new ClickGuiScreen.Rect(gridX, gridY, gridWidth, gridHeight);
        int columns = 3;
        int gap = 8;
        int cardWidth = (gridWidth - gap * (columns - 1)) / columns;
        int cardHeight = 74;
        int rows = (modules.size() + columns - 1) / columns;
        this.moduleScrollMax = Math.max(0, rows * (cardHeight + gap) - gap - gridHeight);
        this.moduleScrollTarget = clamp(this.moduleScrollTarget, 0.0F, this.moduleScrollMax);
        int margin = 12;
        int clipLeft = Math.max(x + 1, gridX - margin);
        int clipTop = Math.max(y + 1, gridY - margin);
        int clipRight = Math.min(x + width - 1, gridX + gridWidth + margin);
        int clipBottom = Math.min(y + height - 1, gridY + gridHeight + margin);
        context.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

        for (int i = 0; i < modules.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int cardX = gridX + column * (cardWidth + gap);
            int cardY = gridY + row * (cardHeight + gap) - Math.round(this.moduleScrollAnimated);
            if (cardY + cardHeight >= gridY && cardY <= gridY + gridHeight) {
                this.drawModuleCard(
                        context,
                        modules.get(i),
                        cardX,
                        cardY,
                        cardWidth,
                        cardHeight,
                        mouseX,
                        mouseY);
            }
        }

        context.disableScissor();
        if (this.moduleScrollMax > 1.0F) {
            this.drawScrollbar(
                    context,
                    gridX + gridWidth - 2,
                    gridY,
                    gridHeight,
                    this.moduleScrollAnimated,
                    this.moduleScrollMax,
                    rows * (cardHeight + gap));
        }
    }

    private void drawModuleCard(
            DrawContext context,
            Module module,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY) {
        ClickGuiScreen.Rect card = new ClickGuiScreen.Rect(x, y, width, height);
        ClickGuiScreen.Rect toggle = new ClickGuiScreen.Rect(x + width - 27, y + 13, 16, 8);
        boolean hovered =
                card.contains(mouseX, mouseY)
                        && this.selectorMode == ClickGuiScreen.SelectorMode.NONE
                        && !this.isColorPickerOpen();
        float hover = this.animateHover(module, hovered || module == this.selectedModule);
        float active = this.animateModuleActive(module, 10.0F);
        int fill = lerpColor(-350938585, -266657488, hover * 0.42F);
        float glow =
                Math.max(active * 0.62F, hover * (module == this.selectedModule ? 0.7F : 0.28F));
        this.drawGlowRect(context, x, y, width, height, 7, fill, -1774500775, accentColor, glow);
        ClickGuiScreen.CardText text = this.getCardText(module, width);
        DashboardRenderer.drawRoundedRect(
                context,
                x + 8,
                y + 9,
                23,
                23,
                6,
                applyAlpha(lerpColor(-15065813, accentColor, active * 0.07F), this.openProgress));
        ItemStack icon = this.getModuleIcon(module);
        if (!icon.isEmpty()) {
            context.drawItem(icon, x + 11, y + 12);
        } else {
            this.drawCenteredText(
                    context,
                    text.displayTitle.substring(0, 1),
                    x + 19,
                    y + 16,
                    active > 0.5F ? accentColor : -6182984);
        }

        this.drawText(context, text.title, x + 37, y + 10, -789256);

        for (int i = 0; i < text.description.size(); i++) {
            this.drawSmallText(context, text.description.get(i), x + 37, y + 23 + i * 8, -6182984);
        }

        context.fill(
                x + 8, y + 48, x + width - 8, y + 49, applyAlpha(1429090884, this.openProgress));
        this.drawSmallText(
                context,
                module.isActive() ? "Active" : "Disabled",
                x + 9,
                y + 58,
                module.isActive() ? -12392796 : -9866875);
        this.drawToggleSwitch(
                context,
                toggle,
                module,
                module.isActive() ? 1.0F : 0.0F,
                toggle.contains(mouseX, mouseY));
        ClickGuiScreen.Rect settings =
                new ClickGuiScreen.Rect(x + width - 27, y + height - 22, 18, 16);
        this.drawGlowRect(
                context,
                settings.x,
                settings.y,
                settings.w,
                settings.h,
                4,
                -15328987,
                1882471762,
                accentColor,
                hovered ? 0.3F : 0.0F);
        this.drawCenteredText(
                context,
                "...",
                settings.x + settings.w / 2,
                settings.y + 2,
                hovered ? -789256 : -6182984);
        this.moduleCardHits.add(new ClickGuiScreen.ModuleCardHit(card, toggle, settings, module));
    }

    private void drawRightPanel(
            DrawContext context, ClickGuiScreen.PanelLayout layout, int mouseX, int mouseY) {
        int x = layout.x + layout.w - layout.rightW + 5;
        int y = layout.y + 50 + 8;
        int width = layout.rightW - 13;
        int height = layout.h - 50 - 36 - 15;
        if (this.selectedModule != null) {
            this.prevCategoryRect = ClickGuiScreen.Rect.EMPTY;
            this.nextCategoryRect = ClickGuiScreen.Rect.EMPTY;
            this.drawModuleDetailPanel(context, x, y, width, height, mouseX, mouseY);
        } else {
            this.detailPanelRect = ClickGuiScreen.Rect.EMPTY;
            this.drawCategoryPanel(context, x, y, width, height, mouseX, mouseY);
        }
    }

    private void drawConfigsTab(
            DrawContext context, ClickGuiScreen.PanelLayout layout, int mouseX, int mouseY) {
        int x = layout.x + layout.sidebarW + 6;
        int y = layout.y + 50 + 8;
        int width = layout.w - layout.sidebarW - 12;
        int height = layout.h - 50 - 36 - 15;
        this.profileRowHits.clear();
        this.presetRowHits.clear();
        int gap = 8;
        int sideWidth = Math.max(154, Math.min(176, Math.round(width * 0.25F)));
        int mainWidth = width - sideWidth - gap;
        int sideX = x + mainWidth + gap;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                mainWidth,
                height,
                8,
                applyAlpha(-653520869, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                sideX,
                y,
                sideWidth,
                height,
                8,
                applyAlpha(-653520869, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        this.drawBottomGlow(context, x + 2, y + height - 31, mainWidth - 4, 28);
        this.drawText(context, "CONFIG LIBRARY", x + 14, y + 13, -789256);
        this.drawText(
                context,
                "Save, load and share complete OdinClient setups.",
                x + 14,
                y + 28,
                -6182984);
        List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();
        this.drawRightAlignedText(
                context, profiles.size() + " / 3 CONFIGS", x + mainWidth - 14, y + 14, accentColor);
        this.myConfigsTabRect = new ClickGuiScreen.Rect(x + 14, y + 43, 76, 26);
        this.browseTabRect = new ClickGuiScreen.Rect(x + 96, y + 43, 58, 26);
        this.drawConfigSubTab(
                context,
                this.myConfigsTabRect,
                "MY CONFIGS",
                !this.browsingPresets,
                mouseX,
                mouseY);
        this.drawConfigSubTab(
                context, this.browseTabRect, "BROWSE", this.browsingPresets, mouseX, mouseY);
        context.fill(
                x + 13,
                y + 69,
                x + mainWidth - 13,
                y + 70,
                applyAlpha(1161051474, this.openProgress));
        int listX = x + 10;
        int listY = y + 79;
        int listWidth = mainWidth - 20;
        int listHeight = height - 89;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                listX,
                listY,
                listWidth,
                listHeight,
                7,
                applyAlpha(-1441720799, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        this.drawText(
                context,
                this.browsingPresets ? "AVAILABLE PRESETS" : "LOCAL PROFILES",
                listX + 11,
                listY + 10,
                -6182984);
        if (this.browsingPresets) {
            this.drawPresetList(context, listX, listY, listWidth, listHeight, mouseX, mouseY);
        } else {
            this.drawLocalProfiles(
                    context, profiles, listX, listY, listWidth, listHeight, mouseX, mouseY);
        }

        this.drawQuickActions(context, sideX, y, sideWidth, height, mouseX, mouseY);
        if (!this.statusMessage.isBlank() && System.currentTimeMillis() < this.statusExpireTime) {
            this.drawCenteredText(
                    context,
                    this.ellipsize(this.statusMessage, listWidth - 28),
                    listX + listWidth / 2,
                    listY + listHeight - 17,
                    this.statusSuccess ? accentColor : -41096);
        }
    }

    private void drawThemesTab(
            DrawContext context, ClickGuiScreen.PanelLayout layout, int mouseX, int mouseY) {
        int x = layout.x + layout.sidebarW + 6;
        int y = layout.y + 50 + 8;
        int width = layout.w - layout.sidebarW - 12;
        int height = layout.h - 50 - 36 - 15;
        int gap = 8;
        int previewWidth = Math.max(170, Math.min(218, Math.round(width * 0.29F)));
        int mainWidth = width - previewWidth - gap;
        boolean compact = height < 330;
        ThemeModule theme = this.getThemeModule();
        this.themePresetHits.clear();
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                mainWidth,
                height,
                8,
                applyAlpha(-871493091, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x + mainWidth + gap,
                y,
                previewWidth,
                height,
                8,
                applyAlpha(-871493091, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        this.drawText(context, "THEME STUDIO", x + 14, y + 13, -789256);
        this.drawText(
                context,
                "Personalize OdinClient with a live, instant preview.",
                x + 14,
                y + 28,
                -6182984);
        this.drawRightAlignedText(context, "LIVE", x + mainWidth - 14, y + 14, -12392796);
        if (theme == null) {
            this.drawCenteredText(
                    context, "THEME SERVICE IS UNAVAILABLE", x + mainWidth / 2, y + 92, -41096);
        } else {
            int presetsY = y + (compact ? 43 : 49);
            int cardGap = compact ? 5 : 7;
            int cardHeight = compact ? 37 : 58;
            int cardWidth = (mainWidth - 28 - cardGap * 2) / 3;
            int currentColor = toArgb(theme.color.getValue());
            boolean rainbow = Boolean.TRUE.equals(theme.rainbow.getValue());

            for (int i = 0; i < THEME_PRESET_NAMES.length; i++) {
                int column = i % 3;
                int row = i / 3;
                ClickGuiScreen.Rect rect =
                        new ClickGuiScreen.Rect(
                                x + 14 + column * (cardWidth + cardGap),
                                presetsY + row * (cardHeight + cardGap),
                                cardWidth,
                                cardHeight);
                boolean selected =
                        i == THEME_PRESET_NAMES.length - 1
                                ? rainbow
                                : !rainbow
                                        && (currentColor & 16777215)
                                                == (THEME_PRESET_COLORS[i] & 16777215);
                this.drawThemePresetCard(context, rect, i, selected, mouseX, mouseY, compact);
                this.themePresetHits.add(new ClickGuiScreen.ThemePresetHit(rect, i));
            }

            int accentY = presetsY + cardHeight * 2 + cardGap + (compact ? 7 : 12);
            int fullWidth = mainWidth - 28;
            this.customAccentRect =
                    new ClickGuiScreen.Rect(x + 14, accentY, fullWidth, compact ? 30 : 40);
            this.drawAccentButton(context, theme, this.customAccentRect, mouseX, mouseY, compact);
            int toggleY = this.customAccentRect.y + this.customAccentRect.h + (compact ? 5 : 7);
            int toggleGap = compact ? 5 : 7;
            int toggleWidth = (fullWidth - toggleGap * 2) / 3;
            int toggleHeight = compact ? 29 : 39;
            this.rainbowToggleRect =
                    new ClickGuiScreen.Rect(x + 14, toggleY, toggleWidth, toggleHeight);
            this.animationsToggleRect =
                    new ClickGuiScreen.Rect(
                            this.rainbowToggleRect.x + toggleWidth + toggleGap,
                            toggleY,
                            toggleWidth,
                            toggleHeight);
            this.msaaToggleRect =
                    new ClickGuiScreen.Rect(
                            this.animationsToggleRect.x + toggleWidth + toggleGap,
                            toggleY,
                            toggleWidth,
                            toggleHeight);
            this.drawThemeToggleCard(
                    context,
                    this.rainbowToggleRect,
                    "RAINBOW",
                    "Flowing accent",
                    Boolean.TRUE.equals(theme.rainbow.getValue()),
                    mouseX,
                    mouseY,
                    compact);
            this.drawThemeToggleCard(
                    context,
                    this.animationsToggleRect,
                    "ANIMATIONS",
                    "Smooth motion",
                    Boolean.TRUE.equals(theme.animations.getValue()),
                    mouseX,
                    mouseY,
                    compact);
            this.drawThemeToggleCard(
                    context,
                    this.msaaToggleRect,
                    "MSAA",
                    "Smooth tracers",
                    Boolean.TRUE.equals(theme.msaa.getValue()),
                    mouseX,
                    mouseY,
                    compact);
            this.drawThemePreview(
                    context,
                    theme,
                    x + mainWidth + gap,
                    y,
                    previewWidth,
                    height,
                    mouseX,
                    mouseY,
                    compact);
        }
    }

    private void drawThemePresetCard(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            int index,
            boolean selected,
            int mouseX,
            int mouseY,
            boolean compact) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = this.animateHover("theme-preset:" + index, hovered || selected);
        int presetColor = THEME_PRESET_COLORS[index];
        int fill = lerpColor(-350938585, -266657488, hover * 0.3F);
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                7,
                fill,
                1882471762,
                presetColor,
                hover * (selected ? 0.68F : 0.26F));
        this.drawText(
                context,
                this.ellipsize(THEME_PRESET_NAMES[index], rect.w - 16),
                rect.x + 8,
                rect.y + (compact ? 18 : 21),
                selected || hovered ? -789256 : -1973526);
        if (!compact) {
            this.drawText(
                    context,
                    this.ellipsize(THEME_PRESET_DESCRIPTIONS[index], rect.w - 16),
                    rect.x + 8,
                    rect.y + 36,
                    -6182984);
        }

        if (selected) {
            DashboardRenderer.drawRoundedRect(
                    context,
                    rect.x + rect.w - 13,
                    rect.y + 7,
                    5,
                    5,
                    3,
                    applyAlpha(presetColor, this.openProgress));
        }
    }

    private void drawAccentButton(
            DrawContext context,
            ThemeModule theme,
            ClickGuiScreen.Rect rect,
            int mouseX,
            int mouseY,
            boolean compact) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int color = toArgb(theme.color.getValue());
        float hover = this.animateHover("theme-accent", hovered);
        int fill = lerpColor(-350938585, -266657488, hover * 0.38F);
        this.drawGlowRect(
                context, rect.x, rect.y, rect.w, rect.h, 7, fill, 1882471762, color, hover * 0.48F);
        DashboardRenderer.drawRoundedRect(
                context,
                rect.x + 9,
                rect.y + 8,
                compact ? 14 : 24,
                rect.h - 16,
                compact ? 5 : 7,
                applyAlpha(lerpColor(-266657488, color, 0.42F), this.openProgress));
        this.drawText(
                context,
                "CUSTOM ACCENT",
                rect.x + (compact ? 30 : 42),
                rect.y + (compact ? 11 : 9),
                -789256);
        if (!compact) {
            this.drawText(
                    context, "Open the smooth color studio", rect.x + 42, rect.y + 23, -6182984);
        }

        this.drawRightAlignedText(
                context,
                "EDIT",
                rect.x + rect.w - 12,
                rect.y + (rect.h - 8) / 2,
                hovered ? color : -6182984);
    }

    private void drawThemeToggleCard(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String title,
            String description,
            boolean enabled,
            int mouseX,
            int mouseY,
            boolean compact) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = this.animateHover("theme-toggle:" + title, hovered || enabled);
        int fill = lerpColor(-350938585, -266657488, hover * 0.32F);
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                7,
                fill,
                1882471762,
                accentColor,
                hover * (enabled ? 0.62F : 0.25F));
        this.drawText(
                context,
                this.ellipsize(title, rect.w - 35),
                rect.x + 8,
                rect.y + (compact ? 10 : 8),
                enabled ? -789256 : -6182984);
        if (!compact) {
            this.drawText(
                    context,
                    this.ellipsize(description, rect.w - 16),
                    rect.x + 8,
                    rect.y + 23,
                    -9866875);
        }

        ClickGuiScreen.Rect toggle =
                new ClickGuiScreen.Rect(rect.x + rect.w - 23, rect.y + 8, 15, 8);
        this.drawToggleSwitch(context, toggle, title, enabled ? 1.0F : 0.0F, hovered);
    }

    private void drawThemePreview(
            DrawContext context,
            ThemeModule theme,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean compact) {
        int accent = accentColor;
        this.drawText(context, "LIVE PREVIEW", x + 13, y + 13, -789256);
        this.drawText(
                context,
                Boolean.TRUE.equals(theme.rainbow.getValue()) ? "AURORA ACTIVE" : "CUSTOM THEME",
                x + 13,
                y + 28,
                accent);
        int innerX = x + 11;
        int innerY = y + 48;
        int innerWidth = width - 22;
        int previewHeight = Math.max(94, Math.min(150, height - 116));
        this.drawGlowRect(
                context,
                innerX,
                innerY,
                innerWidth,
                previewHeight,
                8,
                -15657438,
                1882471762,
                accent,
                0.32F);
        DashboardRenderer.drawRoundedRect(
                context,
                innerX + 8,
                innerY + 9,
                innerWidth - 16,
                18,
                6,
                applyAlpha(lerpColor(-15262679, accent, 0.18F), this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context, innerX + 14, innerY + 15, 6, 6, 3, applyAlpha(accent, this.openProgress));
        this.drawText(context, "OdinClient", innerX + 27, innerY + 14, -789256);
        int cardY = innerY + 36;
        this.drawGlowRect(
                context,
                innerX + 8,
                cardY,
                innerWidth - 16,
                compact ? 35 : 45,
                7,
                -350938585,
                1882471762,
                accent,
                0.56F);
        DashboardRenderer.drawRoundedRect(
                context,
                innerX + 16,
                cardY + 10,
                compact ? 14 : 20,
                compact ? 14 : 20,
                6,
                applyAlpha(lerpColor(-15065040, accent, 0.28F), this.openProgress));
        this.drawText(context, "MODULE CARD", innerX + (compact ? 37 : 44), cardY + 9, -789256);
        if (!compact) {
            this.drawText(context, "Smooth and focused", innerX + 44, cardY + 24, -6182984);
        }

        ClickGuiScreen.Rect toggle =
                new ClickGuiScreen.Rect(innerX + innerWidth - 38, cardY + 10, 16, 8);
        this.drawToggleSwitch(context, toggle, "theme-live-preview", 1.0F, false);
        int gradientY = innerY + previewHeight - 25;
        drawHorizontalGradient(
                context,
                innerX + 9,
                gradientY,
                innerWidth - 18,
                12,
                applyAlpha(lerpColor(1054242, accent & 16777215, 0.7F), this.openProgress),
                applyAlpha(accent, this.openProgress));
        int textY = innerY + previewHeight + 13;
        this.drawText(context, "APPLIES INSTANTLY", x + 13, textY, -12392796);
        this.drawText(context, "Your selection is saved with", x + 13, textY + 18, -6182984);
        this.drawText(context, "the rest of your client settings.", x + 13, textY + 31, -6182984);
        int buttonY = y + height - 43;
        int buttonWidth = (width - 28) / 2;
        this.dashboardModeRect = new ClickGuiScreen.Rect(x + 11, buttonY, buttonWidth, 30);
        this.panelsModeRect =
                new ClickGuiScreen.Rect(
                        this.dashboardModeRect.x + buttonWidth + 6, buttonY, buttonWidth, 30);
        this.drawLayoutModeButton(
                context,
                this.dashboardModeRect,
                "DASHBOARD",
                theme.guiStyle.getValue() == ThemeModule.GuiStyle.DASHBOARD,
                mouseX,
                mouseY);
        this.drawLayoutModeButton(
                context,
                this.panelsModeRect,
                "PANELS",
                theme.guiStyle.getValue() == ThemeModule.GuiStyle.PANELS,
                mouseX,
                mouseY);
    }

    private void drawLayoutModeButton(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String label,
            boolean selected,
            int mouseX,
            int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill =
                selected
                        ? lerpColor(-266657488, accentColor, 0.22F)
                        : (hovered ? -266657488 : -350938585);
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                7,
                fill,
                1882471762,
                accentColor,
                selected ? 0.62F : (hovered ? 0.24F : 0.0F));
        this.drawCenteredText(
                context, label, rect.x + rect.w / 2, rect.y + 11, selected ? -789256 : -6182984);
    }

    private ThemeModule getThemeModule() {
        return ModuleManager.get().getByName("theme") instanceof ThemeModule theme ? theme : null;
    }

    private KeybindSetting getGuiKeybindSetting() {
        return GuiKeybind.setting();
    }

    private void updateAccentColor() {
        ThemeModule theme = this.getThemeModule();
        if (theme != null && theme.color.getValue() != null) {
            boolean animated = ThemeModule.areAnimationsEnabled();
            int target;
            if (Boolean.TRUE.equals(theme.rainbow.getValue())) {
                float phase =
                        animated
                                ? (float)
                                        ((Math.sin(System.currentTimeMillis() / 950.0) + 1.0) * 0.5)
                                : 0.5F;
                target = ThemeModule.getAccentColorAt(phase);
            } else {
                target = toArgb(theme.color.getValue());
            }

            accentColor =
                    animated && !(this.openProgress < 0.001F)
                            ? lerpColor(
                                    accentColor, target, clamp(this.frameDelta * 7.5F, 0.0F, 1.0F))
                            : target;
            accentBorderColor = lerpColor(-15592162, accentColor, 0.68F);
            accentHighlightColor = lerpColor(accentColor, -789256, 0.18F);
        }
    }

    private void applyThemePreset(int index) {
        ThemeModule theme = this.getThemeModule();
        if (theme != null && index >= 0 && index < THEME_PRESET_COLORS.length) {
            int rgb = THEME_PRESET_COLORS[index];
            theme.color.setValue(
                    new EntityColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255));
            theme.rainbow.setValue(index == THEME_PRESET_COLORS.length - 1);
            this.updateAccentColor();
        }
    }

    private void drawConfigSubTab(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String label,
            boolean selected,
            int mouseX,
            int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        this.drawText(
                context,
                label,
                rect.x + 6,
                rect.y + 8,
                selected ? accentColor : (hovered ? -789256 : -6182984));
    }

    private void drawLocalProfiles(
            DrawContext context,
            List<ConfigProfileManager.Profile> profiles,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY) {
        if (profiles.isEmpty()) {
            this.drawCenteredText(context, "NO CONFIGS SAVED YET", x + width / 2, y + 72, -6182984);
            this.drawCenteredText(
                    context,
                    "Create your first local profile with New Config.",
                    x + width / 2,
                    y + 89,
                    -9866875);
        } else {
            int rowY = y + 28;

            for (int i = 0; i < profiles.size(); i++) {
                ConfigProfileManager.Profile profile = profiles.get(i);
                ClickGuiScreen.Rect row = new ClickGuiScreen.Rect(x + 9, rowY, width - 18, 58);
                boolean active =
                        profile.name.equalsIgnoreCase(ConfigProfileManager.getActiveProfileName());
                boolean selected = i == this.selectedProfileIndex;
                boolean hovered = row.contains(mouseX, mouseY);
                int accent = accentColor;
                int fill = hovered ? lerpColor(-350938585, -266657488, 0.45F) : -350938585;
                if (!active && !selected) {
                    DashboardRenderer.drawBorderedRoundedRect(
                            context,
                            row.x,
                            row.y,
                            row.w,
                            row.h,
                            6,
                            applyAlpha(fill, this.openProgress),
                            applyAlpha(1882471762, this.openProgress));
                } else {
                    this.drawGlowRect(
                            context,
                            row.x,
                            row.y,
                            row.w,
                            row.h,
                            6,
                            fill,
                            1882471762,
                            accent,
                            active ? 0.68F : 0.58F);
                }

                String name = this.ellipsize(profile.name.toUpperCase(Locale.ROOT), row.w - 160);
                this.drawText(context, name, row.x + 13, row.y + 12, -789256);
                if (active) {
                    int badgeX = row.x + 19 + DashboardRenderer.width(this.textRenderer, name);
                    ClickGuiScreen.Rect badge = new ClickGuiScreen.Rect(badgeX, row.y + 8, 42, 17);
                    DashboardRenderer.drawRoundedRect(
                            context,
                            badge.x,
                            badge.y,
                            badge.w,
                            badge.h,
                            8,
                            applyAlpha(
                                    lerpColor(-266657488, accentColor, 0.28F), this.openProgress));
                    this.drawCenteredText(
                            context, "ACTIVE", badge.x + badge.w / 2, badge.y + 5, accentColor);
                }

                this.drawText(
                        context,
                        formatLastModified(profile.updated),
                        row.x + 13,
                        row.y + 34,
                        -6182984);
                ClickGuiScreen.Rect load =
                        new ClickGuiScreen.Rect(row.x + row.w - 91, row.y + 17, 25, 25);
                ClickGuiScreen.Rect delete =
                        new ClickGuiScreen.Rect(row.x + row.w - 61, row.y + 17, 25, 25);
                ClickGuiScreen.Rect more =
                        new ClickGuiScreen.Rect(row.x + row.w - 31, row.y + 17, 23, 25);
                this.drawIconButton(
                        context,
                        load,
                        active ? CONFIG_LOAD_ICON : CONFIG_IMPORT_ICON,
                        accentColor,
                        load.contains(mouseX, mouseY));
                this.drawIconButton(
                        context,
                        delete,
                        CONFIG_DELETE_ICON,
                        -41096,
                        delete.contains(mouseX, mouseY));
                this.drawIconButton(
                        context,
                        more,
                        CONFIG_MORE_ICON,
                        accentColor,
                        more.contains(mouseX, mouseY));
                this.profileRowHits.add(new ClickGuiScreen.ProfileRowHit(row, load, delete, i));
                rowY += 65;
            }
        }
    }

    private static String formatLastModified(long millis) {
        if (millis <= 0L) {
            return "Last modified: Unknown";
        } else {
            LocalDateTime dateTime =
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            return "Last modified: " + DATE_TIME_FORMAT.format(dateTime);
        }
    }

    private void drawPresetList(
            DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
        List<ConfigProfileManager.BundledConfig> configs = ConfigProfileManager.getBundledConfigs();
        if (configs.isEmpty()) {
            DashboardRenderer.drawBorderedRoundedRect(
                    context,
                    x + 18,
                    y + 49,
                    width - 36,
                    62,
                    7,
                    applyAlpha(-1458366686, this.openProgress),
                    applyAlpha(1882471762, this.openProgress));
            this.drawCenteredText(
                    context, "NO BUILT-IN CONFIGS YET", x + width / 2, y + 68, -789256);
            this.drawCenteredText(
                    context, "Offline presets will appear here.", x + width / 2, y + 86, -6182984);
        } else {
            int rowY = y + 28;

            for (int i = 0; i < configs.size(); i++) {
                ConfigProfileManager.BundledConfig config = configs.get(i);
                ClickGuiScreen.Rect row = new ClickGuiScreen.Rect(x + 9, rowY, width - 18, 46);
                ClickGuiScreen.Rect apply =
                        new ClickGuiScreen.Rect(row.x + row.w - 58, row.y + 13, 50, 20);
                DashboardRenderer.drawBorderedRoundedRect(
                        context,
                        row.x,
                        row.y,
                        row.w,
                        row.h,
                        6,
                        applyAlpha(
                                row.contains(mouseX, mouseY) ? -266657488 : -350938585,
                                this.openProgress),
                        applyAlpha(1882471762, this.openProgress));
                this.drawText(
                        context,
                        this.ellipsize(config.name.toUpperCase(Locale.ROOT), row.w - 84),
                        row.x + 11,
                        row.y + 9,
                        -789256);
                this.drawText(
                        context,
                        this.ellipsize(config.description, row.w - 84),
                        row.x + 11,
                        row.y + 25,
                        -6182984);
                this.drawDialogButton(
                        context,
                        apply,
                        "APPLY",
                        accentColor,
                        apply.contains(mouseX, mouseY),
                        false);
                this.presetRowHits.add(new ClickGuiScreen.PresetRowHit(row, apply, i));
                rowY += 53;
            }
        }
    }

    private void drawQuickActions(
            DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
        this.drawText(context, "QUICK ACTIONS", x + 13, y + 14, -789256);
        boolean compact = height < 310;
        int buttonHeight = compact ? 26 : 36;
        int buttonsY = y + (compact ? 34 : 39);
        int step = compact ? 31 : 45;
        this.saveConfigRect = new ClickGuiScreen.Rect(x + 11, buttonsY, width - 22, buttonHeight);
        this.newConfigRect =
                new ClickGuiScreen.Rect(x + 11, buttonsY + step, width - 22, buttonHeight);
        this.importConfigRect =
                new ClickGuiScreen.Rect(x + 11, buttonsY + step * 2, width - 22, buttonHeight);
        this.shareConfigRect =
                new ClickGuiScreen.Rect(x + 11, buttonsY + step * 3, width - 22, buttonHeight);
        this.drawQuickActionButton(
                context,
                this.saveConfigRect,
                CONFIG_SAVE_ICON,
                "SAVE CURRENT",
                accentColor,
                this.saveConfigRect.contains(mouseX, mouseY),
                true);
        this.drawQuickActionButton(
                context,
                this.newConfigRect,
                CONFIG_NEW_ICON,
                "NEW CONFIG",
                accentColor,
                this.newConfigRect.contains(mouseX, mouseY),
                false);
        this.drawQuickActionButton(
                context,
                this.importConfigRect,
                CONFIG_IMPORT_ICON,
                "IMPORT",
                accentColor,
                this.importConfigRect.contains(mouseX, mouseY),
                false);
        this.drawQuickActionButton(
                context,
                this.shareConfigRect,
                CONFIG_SHARE_ICON,
                "SHARE",
                accentColor,
                this.shareConfigRect.contains(mouseX, mouseY),
                false);
        boolean confirmingReset = System.currentTimeMillis() < this.resetConfirmExpireTime;
        this.resetConfigRect =
                new ClickGuiScreen.Rect(x + 11, buttonsY + step * 4, width - 22, buttonHeight);
        this.drawQuickActionButton(
                context,
                this.resetConfigRect,
                CONFIG_RESET_ICON,
                confirmingReset ? "CONFIRM RESET" : "RESET DEFAULTS",
                -18355,
                this.resetConfigRect.contains(mouseX, mouseY),
                false);
        int noteHeight = compact ? 35 : 48;
        ClickGuiScreen.Rect note =
                new ClickGuiScreen.Rect(
                        x + 11, y + height - noteHeight - 10, width - 22, noteHeight);
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                note.x,
                note.y,
                note.w,
                note.h,
                6,
                applyAlpha(-1441457626, this.openProgress),
                applyAlpha(1345600850, this.openProgress));
        this.drawIcon(
                context, CONFIG_INFO_ICON, note.x + 8, note.y + 9, compact ? 11 : 13, -6182984);
        List<String> lines =
                this.wrapText(
                        "Configs include every module, setting and keybind.",
                        Math.max(30, note.w - 35),
                        2);
        int textY = note.y + (compact ? 8 : 11);

        for (int i = 0; i < lines.size(); i++) {
            this.drawSmallText(context, lines.get(i), note.x + 25, textY + i * 10, -6182984);
        }
    }

    private void drawQuickActionButton(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            Identifier icon,
            String label,
            int color,
            boolean hovered,
            boolean primary) {
        int fill =
                primary
                        ? lerpColor(-15394266, accentColor, hovered ? 0.18F : 0.11F)
                        : (hovered ? lerpColor(-15262679, color, 0.14F) : -15394266);
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                6,
                fill,
                1882471762,
                color,
                primary ? (hovered ? 0.62F : 0.42F) : (hovered ? 0.34F : 0.0F));
        int iconSize = rect.h < 30 ? 11 : 14;
        int iconY = rect.y + (rect.h - iconSize) / 2;
        this.drawIcon(
                context,
                icon,
                rect.x + 12,
                iconY,
                iconSize,
                !primary && !hovered ? -6182984 : -789256);
        this.drawText(
                context,
                label,
                rect.x + 34,
                rect.y + (rect.h - 8) / 2,
                !primary && !hovered ? -6182984 : -789256);
    }

    private void drawIconButton(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            Identifier icon,
            int color,
            boolean hovered) {
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                6,
                -15328473,
                1882471762,
                color,
                hovered ? 0.34F : 0.0F);
        int iconSize = 12;
        this.drawIcon(
                context,
                icon,
                rect.x + (rect.w - iconSize) / 2,
                rect.y + (rect.h - iconSize) / 2,
                iconSize,
                hovered ? -789256 : -6182984);
    }

    private void drawIcon(DrawContext context, Identifier icon, int x, int y, int size, int color) {
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                icon,
                x,
                y,
                0.0F,
                0.0F,
                size,
                size,
                128,
                128,
                128,
                128,
                applyAlpha(color, this.openProgress));
    }

    private void drawDialogButton(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String label,
            int color,
            boolean hovered,
            boolean primary) {
        int fill =
                primary
                        ? lerpColor(-15460316, accentColor, hovered ? 0.18F : 0.11F)
                        : (hovered ? lerpColor(-15262936, color, 0.18F) : -15460316);
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                5,
                fill,
                1882471762,
                color,
                primary ? (hovered ? 0.6F : 0.4F) : (hovered ? 0.34F : 0.0F));
        this.drawCenteredText(
                context,
                label,
                rect.x + rect.w / 2,
                rect.y + 7,
                !primary && !hovered ? -6182984 : -789256);
    }

    private void drawNewConfigDialog(
            DrawContext context, ClickGuiScreen.PanelLayout layout, int mouseX, int mouseY) {
        context.fill(
                0,
                0,
                this.getScreenWidth(),
                this.getScreenHeight(),
                applyAlpha(-872415232, this.openProgress));
        int width = Math.min(310, layout.w - 40);
        int height = 118;
        int x = layout.x + (layout.w - width) / 2;
        int y = layout.y + (layout.h - height) / 2;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                width,
                height,
                8,
                -99806948,
                lerpColor(-1774500775, accentColor, 0.45F));
        this.drawText(context, "NEW CONFIG", x + 15, y + 13, -789256);
        this.drawText(context, "PROFILE NAME", x + 15, y + 31, -6182984);
        ClickGuiScreen.Rect input = new ClickGuiScreen.Rect(x + 15, y + 45, width - 30, 25);
        DashboardRenderer.drawBorderedRoundedRect(
                context, input.x, input.y, input.w, input.h, 5, -15723230, accentBorderColor);
        String cursor = isCursorVisible() ? "_" : "";
        this.drawText(
                context,
                this.ellipsize(this.newConfigName + cursor, input.w - 14),
                input.x + 7,
                input.y + 8,
                -789256);
        this.dialogCancelRect = new ClickGuiScreen.Rect(x + width - 145, y + 84, 62, 21);
        this.dialogSaveRect = new ClickGuiScreen.Rect(x + width - 76, y + 84, 61, 21);
        this.drawDialogButton(
                context,
                this.dialogCancelRect,
                "CANCEL",
                -41096,
                this.dialogCancelRect.contains(mouseX, mouseY),
                false);
        this.drawDialogButton(
                context,
                this.dialogSaveRect,
                "SAVE",
                accentColor,
                this.dialogSaveRect.contains(mouseX, mouseY),
                true);
    }

    private void drawCategoryPanel(
            DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
        List<Module> modules = this.getModulesInCategory(this.selectedCategory);
        int enabledCount = countEnabled(modules);
        boolean compact = height < 310;
        int headerHeight = 78;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                width,
                headerHeight,
                7,
                applyAlpha(-233761503, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        this.drawText(context, "CATEGORY", x + 12, y + 10, -6182984);
        this.drawText(
                context,
                this.ellipsize(title(this.selectedCategory), width - 70),
                x + 12,
                y + 23,
                -789256);
        this.prevCategoryRect = new ClickGuiScreen.Rect(x + width - 47, y + 8, 17, 17);
        this.nextCategoryRect = new ClickGuiScreen.Rect(x + width - 25, y + 8, 17, 17);
        this.drawSmallButton(
                context,
                this.prevCategoryRect,
                "<",
                this.prevCategoryRect.contains(mouseX, mouseY),
                accentColor);
        this.drawSmallButton(
                context,
                this.nextCategoryRect,
                ">",
                this.nextCategoryRect.contains(mouseX, mouseY),
                accentColor);
        int ringX = x + 36;
        int ringY = y + 52;
        int ringRadius = compact ? 15 : 20;
        float target = modules.isEmpty() ? 0.0F : (float) enabledCount / modules.size();
        String categoryKey = lower(this.selectedCategory);
        float progress =
                ThemeModule.areAnimationsEnabled()
                        ? this.categoryProgressAnimations.compute(
                                categoryKey,
                                (key, current) ->
                                        smoothTowards(
                                                current == null ? 0.0F : current,
                                                target,
                                                8.0F,
                                                this.frameDelta))
                        : target;
        this.categoryProgressAnimations.put(categoryKey, progress);
        DashboardRenderer.drawProgressRing(
                context,
                ringX,
                ringY,
                ringRadius,
                compact ? 3 : 4,
                progress,
                applyAlpha(-13617336, this.openProgress),
                applyAlpha(accentColor, this.openProgress),
                applyAlpha(-233761503, this.openProgress));
        this.drawText(context, enabledCount + " / " + modules.size(), x + 66, y + 41, accentColor);
        this.drawText(context, "Enabled", x + 66, y + 54, -6182984);
        int overviewY = y + headerHeight + (compact ? 6 : 8);
        int overviewHeight = compact ? 86 : 112;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                overviewY,
                width,
                overviewHeight,
                7,
                applyAlpha(-233761503, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        this.drawText(
                context,
                title(this.selectedCategory) + " Overview",
                x + 12,
                overviewY + 12,
                -789256);
        if (!compact) {
            DashboardRenderer.drawRoundedRect(
                    context,
                    x + 12,
                    overviewY + 34,
                    23,
                    23,
                    6,
                    applyAlpha(
                            lerpColor(-15065813, accentColor, progress * 0.07F),
                            this.openProgress));
            ItemStack icon = this.getItemStack(getCategoryIconName(this.selectedCategory));
            this.drawCategoryIcon(context, this.selectedCategory, icon, x + 15, overviewY + 37);
            this.drawText(
                    context,
                    this.ellipsize(getCategoryDescription(this.selectedCategory), width - 50),
                    x + 42,
                    overviewY + 36,
                    -6182984);
        }

        int statsY = compact ? 35 : 68;
        this.drawText(context, "Total Modules", x + 12, overviewY + statsY, -6182984);
        this.drawRightAlignedText(
                context,
                String.valueOf(modules.size()),
                x + width - 12,
                overviewY + statsY,
                accentColor);
        this.drawText(context, "Enabled", x + 12, overviewY + statsY + 15, -6182984);
        this.drawRightAlignedText(
                context,
                String.valueOf(enabledCount),
                x + width - 12,
                overviewY + statsY + 15,
                -12392796);
        this.drawText(context, "Disabled", x + 12, overviewY + statsY + 30, -6182984);
        this.drawRightAlignedText(
                context,
                String.valueOf(Math.max(0, modules.size() - enabledCount)),
                x + width - 12,
                overviewY + statsY + 30,
                -41096);
        int actionsY = overviewY + overviewHeight + (compact ? 6 : 8);
        int actionsHeight = height - (actionsY - y);
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                actionsY,
                width,
                actionsHeight,
                7,
                applyAlpha(-233761503, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        this.drawText(context, "QUICK ACTIONS", x + 12, actionsY + 9, -789256);
        this.enableAllRect = new ClickGuiScreen.Rect(x + 10, actionsY + 25, width - 20, 20);
        this.disableAllRect = new ClickGuiScreen.Rect(x + 10, actionsY + 51, width - 20, 20);
        this.drawBulkActionButton(
                context, this.enableAllRect, "Enable All", mouseX, mouseY, -12392796);
        this.drawBulkActionButton(
                context, this.disableAllRect, "Disable All", mouseX, mouseY, accentColor);
        if (actionsHeight >= 108) {
            this.guiKeybindRect = new ClickGuiScreen.Rect(x + 10, actionsY + 81, width - 20, 24);
            KeybindSetting keybind = this.getGuiKeybindSetting();
            boolean listening = keybind != null && keybind == this.listeningKeybind;
            boolean hovered = this.guiKeybindRect.contains(mouseX, mouseY);
            float hover = this.animateHover("quick:gui-keybind", hovered || listening);
            this.drawGlowRect(
                    context,
                    this.guiKeybindRect.x,
                    this.guiKeybindRect.y,
                    this.guiKeybindRect.w,
                    this.guiKeybindRect.h,
                    5,
                    lerpColor(-15723235, -15262936, hover * 0.45F),
                    1882471762,
                    accentColor,
                    listening ? 0.64F : hover * 0.34F);
            this.drawText(
                    context,
                    "GUI KEY",
                    this.guiKeybindRect.x + 8,
                    this.guiKeybindRect.y + 8,
                    listening ? -789256 : -6182984);
            ClickGuiScreen.Rect keyRect =
                    new ClickGuiScreen.Rect(
                            this.guiKeybindRect.x + this.guiKeybindRect.w - 49,
                            this.guiKeybindRect.y + 5,
                            42,
                            14);
            DashboardRenderer.drawRoundedRect(
                    context,
                    keyRect.x,
                    keyRect.y,
                    keyRect.w,
                    keyRect.h,
                    4,
                    applyAlpha(
                            listening ? lerpColor(-15392225, -12392796, 0.16F) : -15131349,
                            this.openProgress));
            String keyLabel =
                    keybind == null
                            ? "N/A"
                            : (listening ? "PRESS" : getKeyName(keybind.getValue()));
            this.drawCenteredText(
                    context,
                    this.ellipsize(keyLabel, keyRect.w - 6),
                    keyRect.x + keyRect.w / 2,
                    keyRect.y + 3,
                    listening ? -12392796 : -789256);
        }
    }

    private void drawModuleDetailPanel(
            DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
        Module module = this.selectedModule;
        this.detailPanelRect = new ClickGuiScreen.Rect(x, y, width, height);
        this.enableAllRect = ClickGuiScreen.Rect.EMPTY;
        this.disableAllRect = ClickGuiScreen.Rect.EMPTY;
        this.guiKeybindRect = ClickGuiScreen.Rect.EMPTY;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                width,
                height,
                7,
                applyAlpha(-233761503, this.openProgress),
                applyAlpha(lerpColor(-1774500775, accentColor, 0.25F), this.openProgress));
        float active = this.animateModuleActive(module, 16.0F);
        DashboardRenderer.drawRoundedRect(
                context,
                x + 10,
                y + 10,
                23,
                23,
                6,
                applyAlpha(lerpColor(-15065813, accentColor, active * 0.07F), this.openProgress));
        ItemStack icon = this.getModuleIcon(module);
        if (!icon.isEmpty()) {
            context.drawItem(icon, x + 13, y + 13);
        } else {
            this.drawCenteredText(
                    context,
                    module.title.substring(0, 1),
                    x + 21,
                    y + 17,
                    active > 0.5F ? accentColor : -6182984);
        }

        this.drawText(context, this.ellipsize(module.title, width - 75), x + 42, y + 10, -789256);
        this.drawText(
                context,
                module.isActive() ? "ACTIVE" : "DISABLED",
                x + 42,
                y + 24,
                module.isActive() ? -12392796 : -6182984);
        ClickGuiScreen.Rect toggle = new ClickGuiScreen.Rect(x + width - 27, y + 16, 16, 8);
        this.drawToggleSwitch(
                context,
                toggle,
                module,
                module.isActive() ? 1.0F : 0.0F,
                toggle.contains(mouseX, mouseY));
        this.moduleCardHits.add(
                new ClickGuiScreen.ModuleCardHit(
                        new ClickGuiScreen.Rect(x, y, 0, 0),
                        toggle,
                        ClickGuiScreen.Rect.EMPTY,
                        module));
        context.fill(
                x + 10, y + 47, x + width - 10, y + 48, applyAlpha(1715488593, this.openProgress));
        int listY = y + 55;
        int listHeight = height - 62;
        this.settingsListRect = new ClickGuiScreen.Rect(x + 7, listY, width - 14, listHeight);
        List<ClickGuiScreen.SettingEntry> entries = this.settings(module);
        int contentHeight = 0;
        String group = null;

        for (ClickGuiScreen.SettingEntry countEntry : entries) {
            if (!isHiddenHoleEspSetting(module, countEntry.setting)) {
                if (!countEntry.group.equals(group)) {
                    contentHeight += 17;
                    group = countEntry.group;
                }

                contentHeight += getSettingRowHeight(countEntry.setting) + 6;
            }
        }

        this.settingScrollMax = Math.max(0, contentHeight - listHeight);
        this.settingScrollTarget = clamp(this.settingScrollTarget, 0.0F, this.settingScrollMax);
        context.enableScissor(
                this.settingsListRect.x,
                this.settingsListRect.y,
                this.settingsListRect.x + this.settingsListRect.w,
                this.settingsListRect.y + this.settingsListRect.h);
        int rowY = listY - Math.round(this.settingScrollAnimated);
        group = null;

        for (ClickGuiScreen.SettingEntry entry : entries) {
            if (!isHiddenHoleEspSetting(module, entry.setting)) {
                if (!entry.group.equals(group)) {
                    this.drawText(
                            context,
                            entry.group.toUpperCase(Locale.ROOT),
                            x + 12,
                            rowY + 4,
                            accentColor);
                    rowY += 17;
                    group = entry.group;
                }

                int rowHeight = getSettingRowHeight(entry.setting);
                if (rowY + rowHeight >= listY && rowY <= listY + listHeight) {
                    ColorSetting linkedColor =
                            findLinkedColorSetting(module, entries, entry.setting);
                    if (linkedColor != null
                            && entry.setting instanceof BooleanSetting booleanSetting) {
                        this.drawColorToggleRow(
                                context,
                                booleanSetting,
                                linkedColor,
                                x + 9,
                                rowY,
                                width - 18,
                                rowHeight,
                                mouseX,
                                mouseY);
                    } else {
                        this.drawSettingRow(
                                context,
                                entry.setting,
                                x + 9,
                                rowY,
                                width - 18,
                                rowHeight,
                                mouseX,
                                mouseY);
                    }
                }

                rowY += rowHeight + 6;
            }
        }

        context.disableScissor();
        if (this.settingScrollMax > 1.0F) {
            this.drawScrollbar(
                    context,
                    x + width - 5,
                    listY,
                    listHeight,
                    this.settingScrollAnimated,
                    this.settingScrollMax,
                    contentHeight);
        }

        this.backRect = ClickGuiScreen.Rect.EMPTY;
    }

    private void drawSettingRow(
            DrawContext context,
            Setting<?> setting,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY) {
        ClickGuiScreen.Rect row = new ClickGuiScreen.Rect(x, y, width, height);
        boolean hovered = row.contains(mouseX, mouseY);
        float hover =
                this.animateHover(
                        setting,
                        hovered
                                || setting == this.editingSetting
                                || setting == this.listeningKeybind);
        boolean enabled =
                setting instanceof BooleanSetting booleanSetting
                        && Boolean.TRUE.equals(booleanSetting.getValue());
        int fill = lerpColor(-350938585, -266657488, hover * 0.38F);
        float glow = Math.max(hover * 0.38F, enabled ? 0.5F : 0.0F);
        if (glow > 0.002F) {
            this.drawGlowRect(context, x, y, width, height, 6, fill, 1882471762, accentColor, glow);
        } else {
            DashboardRenderer.drawBorderedRoundedRect(
                    context,
                    x,
                    y,
                    width,
                    height,
                    6,
                    applyAlpha(fill, this.openProgress),
                    applyAlpha(1882471762, this.openProgress));
        }

        String description = setting.description == null ? "" : setting.description;
        String valueText = null;
        ClickGuiScreen.Rect control;
        if (setting instanceof BooleanSetting booleanSetting) {
            control = new ClickGuiScreen.Rect(x + width - 26, y + 11, 16, 8);
            this.drawToggleSwitch(
                    context,
                    control,
                    booleanSetting,
                    booleanSetting.getValue() ? 1.0F : 0.0F,
                    hovered);
        } else if (setting instanceof IntSetting intSetting) {
            control = new ClickGuiScreen.Rect(x + 9, y + height - 12, width - 18, 5);
            double intRange = Math.max(1.0, intSetting.sliderMax - intSetting.sliderMin);
            float intProgress = (float) ((intSetting.getValue() - intSetting.sliderMin) / intRange);
            valueText = String.valueOf(intSetting.getValue());
            this.drawSlider(context, control, intProgress);
        } else if (setting instanceof NumberSetting numberSetting) {
            control = new ClickGuiScreen.Rect(x + 9, y + height - 12, width - 18, 5);
            double numberRange =
                    Math.max(1.0E-5, numberSetting.sliderMax - numberSetting.sliderMin);
            float numberProgress =
                    (float) ((numberSetting.getValue() - numberSetting.sliderMin) / numberRange);
            int decimals = getDecimalPlaces(numberSetting);
            valueText = String.format(Locale.ROOT, "%." + decimals + "f", numberSetting.getValue());
            this.drawSlider(context, control, numberProgress);
        } else if (setting instanceof ColorSetting colorSetting) {
            int colorWidth = 17;
            int colorHeight = 10;
            control =
                    new ClickGuiScreen.Rect(
                            x + width - colorWidth - 10,
                            y + (height - colorHeight) / 2,
                            colorWidth,
                            colorHeight);
            EntityColor entityColor = colorSetting.getValue();
            int argb = toArgb(entityColor);
            int opaqueColor = 0xFF000000 | argb & 16777215;
            this.drawGlow(
                    context,
                    control.x,
                    control.y,
                    control.w,
                    control.h,
                    3,
                    opaqueColor,
                    hovered ? 0.76F : 0.58F);
            DashboardRenderer.drawRoundedHighlight(
                    context,
                    control.x - 1,
                    control.y - 1,
                    control.w + 2,
                    control.h + 2,
                    4,
                    applyAlpha(opaqueColor, this.openProgress));
        } else if (setting instanceof BlockColorMapSetting blockColorSetting) {
            control = new ClickGuiScreen.Rect(x + width - 67, y + 7, 57, 20);
            this.drawSmallButton(
                    context,
                    control,
                    blockColorSetting.getValue().size() + " blocks",
                    hovered,
                    accentColor);
        } else if (setting instanceof BlockListSetting blockListSetting) {
            control = new ClickGuiScreen.Rect(x + width - 67, y + 7, 57, 20);
            this.drawSmallButton(
                    context,
                    control,
                    blockListSetting.getValue().size() + " blocks",
                    hovered,
                    accentColor);
        } else if (setting instanceof EntityTypeListSetting entityListSetting) {
            control = new ClickGuiScreen.Rect(x + width - 62, y + 7, 52, 20);
            this.drawSmallButton(
                    context,
                    control,
                    entityListSetting.getValue().size() + " mobs",
                    hovered,
                    accentColor);
        } else if (setting instanceof KeybindSetting keybindSetting) {
            control = new ClickGuiScreen.Rect(x + width - 54, y + 7, 44, 20);
            String keyLabel =
                    keybindSetting == this.listeningKeybind
                            ? "PRESS..."
                            : getKeyName(keybindSetting.getValue());
            this.drawSmallButton(
                    context,
                    control,
                    keyLabel,
                    hovered || keybindSetting == this.listeningKeybind,
                    keybindSetting == this.listeningKeybind ? -12392796 : accentColor);
        } else if (setting instanceof EnumSetting) {
            control = new ClickGuiScreen.Rect(x + width - 76, y + 7, 66, 20);
            this.drawSmallButton(
                    context,
                    control,
                    this.ellipsize(String.valueOf(setting.getValue()), 52),
                    hovered,
                    accentColor);
        } else {
            control = new ClickGuiScreen.Rect(x + 9, y + height - 21, width - 18, 16);
            String fieldText =
                    setting == this.editingSetting
                            ? this.editBuffer + (isCursorVisible() ? "_" : "")
                            : getValueText(setting);
            if ("dropper-1".equals(setting.name) || "dropper-2".equals(setting.name)) {
                fieldText = "OPEN";
            }

            if (this.selectedModule instanceof GambleRigger rigger
                    && GambleRigger.isSelectSetting(setting.name)) {
                fieldText = rigger.getSelectButtonLabel(setting.name);
            }

            this.drawGlowRect(
                    context,
                    control.x,
                    control.y,
                    control.w,
                    control.h,
                    4,
                    -15921125,
                    1882471762,
                    accentColor,
                    setting == this.editingSetting ? 0.42F : 0.0F);
            this.drawText(
                    context,
                    this.ellipsize(fieldText, control.w - 10),
                    control.x + 5,
                    control.y + 4,
                    setting == this.editingSetting ? -789256 : -6182984);
        }

        int textRight = control.x > x + 9 && control.y < y + 29 ? control.x - 6 : x + width - 9;
        int titleRight = textRight;
        if (valueText != null) {
            this.drawRightAlignedText(context, valueText, x + width - 9, y + 8, accentColor);
            titleRight =
                    Math.min(
                            textRight,
                            x + width - 15 - DashboardRenderer.width(this.textRenderer, valueText));
        }

        this.drawText(
                context,
                this.ellipsize(setting.title, Math.max(12, titleRight - (x + 9))),
                x + 9,
                y + 8,
                -789256);
        if (!description.isBlank()) {
            this.drawText(
                    context,
                    this.ellipsize(description, Math.max(12, textRight - (x + 9))),
                    x + 9,
                    y + 21,
                    -9866875);
        }

        this.settingRowHits.add(new ClickGuiScreen.SettingRowHit(row, control, setting));
    }

    private void drawColorToggleRow(
            DrawContext context,
            BooleanSetting toggleSetting,
            ColorSetting colorSetting,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY) {
        ClickGuiScreen.Rect row = new ClickGuiScreen.Rect(x, y, width, height);
        boolean hovered = row.contains(mouseX, mouseY);
        float hover = this.animateHover(toggleSetting, hovered);
        boolean enabled = Boolean.TRUE.equals(toggleSetting.getValue());
        int fill = lerpColor(-350938585, -266657488, hover * 0.38F);
        float glow = Math.max(hover * 0.38F, enabled ? 0.5F : 0.0F);
        if (glow > 0.002F) {
            this.drawGlowRect(context, x, y, width, height, 6, fill, 1882471762, accentColor, glow);
        } else {
            DashboardRenderer.drawBorderedRoundedRect(
                    context,
                    x,
                    y,
                    width,
                    height,
                    6,
                    applyAlpha(fill, this.openProgress),
                    applyAlpha(1882471762, this.openProgress));
        }

        ClickGuiScreen.Rect toggle = new ClickGuiScreen.Rect(x + width - 26, y + 11, 16, 8);
        ClickGuiScreen.Rect swatch =
                new ClickGuiScreen.Rect(x + width - 53, y + (height - 10) / 2, 17, 10);
        boolean swatchHovered = swatch.contains(mouseX, mouseY);
        this.drawToggleSwitch(
                context, toggle, toggleSetting, enabled ? 1.0F : 0.0F, hovered && !swatchHovered);
        EntityColor entityColor = colorSetting.getValue();
        int argb = toArgb(entityColor);
        int opaqueColor = 0xFF000000 | argb & 16777215;
        this.drawGlow(
                context,
                swatch.x,
                swatch.y,
                swatch.w,
                swatch.h,
                3,
                opaqueColor,
                swatchHovered ? 0.76F : 0.58F);
        DashboardRenderer.drawRoundedHighlight(
                context,
                swatch.x - 1,
                swatch.y - 1,
                swatch.w + 2,
                swatch.h + 2,
                4,
                applyAlpha(opaqueColor, this.openProgress));
        int textRight = swatch.x - 6;
        this.drawText(
                context,
                this.ellipsize(toggleSetting.title, Math.max(12, textRight - (x + 9))),
                x + 9,
                y + 8,
                -789256);
        String description = toggleSetting.description == null ? "" : toggleSetting.description;
        if (!description.isBlank()) {
            this.drawText(
                    context,
                    this.ellipsize(description, Math.max(12, textRight - (x + 9))),
                    x + 9,
                    y + 21,
                    -9866875);
        }

        this.settingRowHits.add(new ClickGuiScreen.SettingRowHit(swatch, swatch, colorSetting));
        this.settingRowHits.add(new ClickGuiScreen.SettingRowHit(row, toggle, toggleSetting));
    }

    private static boolean isHiddenHoleEspSetting(Module module, Setting<?> setting) {
        return module != null
                && setting != null
                && "hole-esp".equals(lower(module.name))
                && ("1x1-color".equals(setting.name) || "3x1-color".equals(setting.name));
    }

    private static ColorSetting findLinkedColorSetting(
            Module module, List<ClickGuiScreen.SettingEntry> entries, Setting<?> setting) {
        if (module != null && setting != null && "hole-esp".equals(lower(module.name))) {
            String colorName =
                    switch (setting.name) {
                        case "1x1-holes" -> "1x1-color";
                        case "3x1-holes" -> "3x1-color";
                        default -> null;
                    };
            if (colorName == null) {
                return null;
            } else {
                for (ClickGuiScreen.SettingEntry entry : entries) {
                    if (colorName.equals(entry.setting.name)
                            && entry.setting instanceof ColorSetting colorSetting) {
                        return colorSetting;
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    private void drawFooter(
            DrawContext context, ClickGuiScreen.PanelLayout layout, int mouseX, int mouseY) {
        int footerY = layout.y + layout.h - 36;
        context.fill(
                layout.x + 1,
                footerY,
                layout.x + layout.w - 1,
                footerY + 1,
                applyAlpha(1882471762, this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context,
                layout.x + 8,
                footerY + 5,
                layout.w - 16,
                25,
                6,
                applyAlpha(-468905958, this.openProgress));
        int tabsX = layout.x + layout.sidebarW + 4;
        int tabWidth = 70;
        this.homeTabRect = new ClickGuiScreen.Rect(tabsX, footerY + 4, tabWidth, 29);
        this.configsTabRect = new ClickGuiScreen.Rect(tabsX + tabWidth, footerY + 4, tabWidth, 29);
        this.themesTabRect =
                new ClickGuiScreen.Rect(tabsX + tabWidth * 2, footerY + 4, tabWidth, 29);
        this.drawFooterTab(
                context,
                this.homeTabRect,
                FOOTER_HOME_ICON,
                "HOME",
                !this.configsTabOpen && !this.themesTabOpen,
                mouseX,
                mouseY);
        this.drawFooterTab(
                context,
                this.configsTabRect,
                FOOTER_CONFIGS_ICON,
                "CONFIGS",
                this.configsTabOpen,
                mouseX,
                mouseY);
        this.drawFooterTab(
                context,
                this.themesTabRect,
                FOOTER_THEMES_ICON,
                "THEMES",
                this.themesTabOpen,
                mouseX,
                mouseY);
        int statusX = layout.x + layout.w - layout.rightW - 95;
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                statusX,
                footerY + 7,
                86,
                22,
                5,
                applyAlpha(-15723235, this.openProgress),
                applyAlpha(1882471762, this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context,
                statusX + 8,
                footerY + 15,
                6,
                6,
                3,
                applyAlpha(-12392796, this.openProgress));
        this.drawText(context, "Injected", statusX + 20, footerY + 14, -6182984);
        ClickGuiScreen.Rect close =
                new ClickGuiScreen.Rect(
                        layout.x + layout.w - layout.rightW + 6,
                        footerY + 7,
                        layout.rightW - 16,
                        22);
        boolean hovered = close.contains(mouseX, mouseY);
        this.drawGlowRect(
                context,
                close.x,
                close.y,
                close.w,
                close.h,
                5,
                -15657438,
                1882471762,
                accentColor,
                hovered ? 0.6F : 0.4F);
        this.drawCenteredText(context, "CLOSE", close.x + close.w / 2, close.y + 7, -789256);
        this.closeRect = close;
    }

    private void drawFooterTab(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            Identifier icon,
            String label,
            boolean selected,
            int mouseX,
            int mouseY) {
        float hover = this.animateHover("footer:" + label, rect.contains(mouseX, mouseY));
        int iconColor = selected ? accentColor : lerpColor(-6182984, -789256, hover);
        int textColor = selected ? -789256 : lerpColor(-6182984, -789256, hover);
        if (selected) {
            DashboardRenderer.drawRoundedRect(
                    context,
                    rect.x + 3,
                    rect.y + 5,
                    17,
                    17,
                    9,
                    applyAlpha(alpha(accentColor, 57), this.openProgress));
            DashboardRenderer.drawRoundedRect(
                    context,
                    rect.x + 3,
                    rect.y + rect.h - 2,
                    rect.w - 9,
                    2,
                    1,
                    applyAlpha(accentColor, this.openProgress));
        }

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                icon,
                rect.x + 5,
                rect.y + 7,
                0.0F,
                0.0F,
                13,
                13,
                128,
                128,
                128,
                128,
                applyAlpha(iconColor, this.openProgress));
        this.drawText(context, label, rect.x + 23, rect.y + 9, textColor);
    }

    private void drawSelectorDialog(DrawContext context, int mouseX, int mouseY) {
        this.refreshSelectorEntries();
        context.fill(
                0,
                0,
                this.getScreenWidth(),
                this.getScreenHeight(),
                applyAlpha(-704643072, this.selectorProgress));
        int width = Math.min(430, this.getScreenWidth() - 28);
        int height = Math.min(356, this.getScreenHeight() - 24);
        int x = (this.getScreenWidth() - width) / 2;
        int y =
                (this.getScreenHeight() - height) / 2
                        + Math.round((1.0F - easeOutCubic(this.selectorProgress)) * 12.0F);
        this.selectorDialogRect = new ClickGuiScreen.Rect(x, y, width, height);
        this.selectorItemHits.clear();
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                width,
                height,
                12,
                -100004328,
                lerpColor(-1774500775, accentColor, 0.22F));
        DashboardRenderer.drawRoundedRect(context, x + 1, y + 1, width - 2, 43, 11, -200470243);
        ItemStack headerIcon =
                this.getItemStack(
                        this.selectorMode == ClickGuiScreen.SelectorMode.ENTITIES
                                ? "zombie_head"
                                : (this.selectorMode == ClickGuiScreen.SelectorMode.BLOCKS
                                        ? "grass_block"
                                        : "respawn_anchor"));
        this.drawScaledItem(context, headerIcon, x + 16, y + 10, 1.25F);
        this.drawText(context, this.getSelectorHeading(), x + 43, y + 9, -789256);
        this.drawText(
                context,
                this.ellipsize(this.getSelectorSubtitle(), width - 190),
                x + 43,
                y + 24,
                -6182984);
        int selectedCount = this.getSelectedCount();
        ClickGuiScreen.Rect countRect = new ClickGuiScreen.Rect(x + width - 126, y + 11, 78, 21);
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                countRect.x,
                countRect.y,
                countRect.w,
                countRect.h,
                6,
                -15526107,
                1882471762);
        this.drawCenteredText(
                context,
                selectedCount + " selected",
                countRect.x + countRect.w / 2,
                countRect.y + (countRect.h - 8) / 2,
                accentColor);
        this.selectorCloseRect = new ClickGuiScreen.Rect(x + width - 38, y + 9, 24, 24);
        DashboardRenderer.drawRoundedRect(
                context,
                this.selectorCloseRect.x,
                this.selectorCloseRect.y,
                this.selectorCloseRect.w,
                this.selectorCloseRect.h,
                6,
                this.selectorCloseRect.contains(mouseX, mouseY) ? -14018523 : -15262935);
        this.drawCenteredText(
                context,
                "X",
                this.selectorCloseRect.x + 12,
                this.selectorCloseRect.y + 8,
                this.selectorCloseRect.contains(mouseX, mouseY) ? -789256 : -6182984);
        drawHorizontalGradient(
                context, x + 1, y + 43, width - 2, 2, accentColor & 16777215, accentColor);
        this.selectorAllTabRect = new ClickGuiScreen.Rect(x + 12, y + 52, 94, 24);
        this.selectorSelectedTabRect = new ClickGuiScreen.Rect(x + 110, y + 52, 112, 24);
        this.drawSelectorTab(
                context,
                this.selectorAllTabRect,
                this.selectorMode == ClickGuiScreen.SelectorMode.ENTITIES
                        ? "All Entities"
                        : "All Blocks",
                !this.selectorShowSelectedOnly,
                mouseX,
                mouseY);
        this.drawSelectorTab(
                context,
                this.selectorSelectedTabRect,
                "Selected (" + selectedCount + ")",
                this.selectorShowSelectedOnly,
                mouseX,
                mouseY);
        this.selectorSearchRect = new ClickGuiScreen.Rect(x + 12, y + 82, width - 59, 25);
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                this.selectorSearchRect.x,
                this.selectorSearchRect.y,
                this.selectorSearchRect.w,
                this.selectorSearchRect.h,
                7,
                -15920867,
                1882471762);
        this.drawText(
                context,
                "Q",
                this.selectorSearchRect.x + 9,
                this.selectorSearchRect.y + 8,
                -6182984);
        String searchHint =
                this.selectorMode == ClickGuiScreen.SelectorMode.ENTITIES
                        ? "Search entities..."
                        : "Search blocks...";
        String searchLabel =
                this.selectorSearchText.isEmpty()
                        ? searchHint
                        : this.selectorSearchText + (isCursorVisible() ? "_" : "");
        this.drawCenteredText(
                context,
                this.ellipsize(searchLabel, this.selectorSearchRect.w - 54),
                this.selectorSearchRect.x + this.selectorSearchRect.w / 2,
                this.selectorSearchRect.y + 8,
                this.selectorSearchText.isEmpty() ? -6182984 : -789256);
        this.selectorFilterRect = new ClickGuiScreen.Rect(x + width - 37, y + 82, 25, 25);
        this.drawGlowRect(
                context,
                this.selectorFilterRect.x,
                this.selectorFilterRect.y,
                this.selectorFilterRect.w,
                this.selectorFilterRect.h,
                6,
                -15591900,
                1882471762,
                accentColor,
                this.selectorShowSelectedOnly
                        ? 0.58F
                        : (this.selectorFilterRect.contains(mouseX, mouseY) ? 0.26F : 0.0F));
        this.drawCenteredText(
                context,
                "F",
                this.selectorFilterRect.x + 12,
                this.selectorFilterRect.y + 8,
                this.selectorShowSelectedOnly ? accentColor : -6182984);
        int panelX = x + 12;
        int panelY = y + 114;
        int panelWidth = width - 24;
        int panelHeight = height - 162;
        DashboardRenderer.drawBorderedRoundedRect(
                context, panelX, panelY, panelWidth, panelHeight, 8, -653520869, 1882471762);
        int gridX = panelX + 7;
        int gridY = panelY + 7;
        int gridWidth = panelWidth - 14;
        int gridHeight = panelHeight - 14;
        this.selectorGridRect = new ClickGuiScreen.Rect(gridX, gridY, gridWidth, gridHeight);
        int gap = 4;
        int columns = Math.max(8, Math.min(14, (gridWidth + gap) / 39));
        int cellWidth = (gridWidth - gap * (columns - 1)) / columns;
        int cellHeight = 36;
        int rows = (this.selectorEntries.size() + columns - 1) / columns;
        this.selectorScrollMax = Math.max(0, rows * (cellHeight + gap) - gap - gridHeight);
        this.selectorScrollTarget = clamp(this.selectorScrollTarget, 0.0F, this.selectorScrollMax);
        context.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);

        for (int i = 0; i < this.selectorEntries.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int cellX = gridX + column * (cellWidth + gap);
            int cellY = gridY + row * (cellHeight + gap) - Math.round(this.selectorScrollAnimated);
            if (cellY + cellHeight >= gridY && cellY <= gridY + gridHeight) {
                Object item = this.selectorEntries.get(i);
                ClickGuiScreen.Rect cell =
                        new ClickGuiScreen.Rect(cellX, cellY, cellWidth, cellHeight);
                boolean selected = this.isSelected(item);
                boolean hovered = cell.contains(mouseX, mouseY);
                float hover =
                        this.animateHover("selector:" + this.getEntryId(item), hovered || selected);
                int itemColor = this.getEntryColor(item);
                int opaqueColor = 0xFF000000 | itemColor & 16777215;
                int fill = lerpColor(-350938585, -266657488, hover * 0.45F);
                if (selected) {
                    DashboardRenderer.drawRoundedHighlight(
                            context,
                            cellX,
                            cellY,
                            cellWidth,
                            cellHeight,
                            7,
                            alpha(opaqueColor, 96));
                } else if (hover > 0.002F) {
                    this.drawGlowRect(
                            context,
                            cellX,
                            cellY,
                            cellWidth,
                            cellHeight,
                            7,
                            fill,
                            1882471762,
                            accentColor,
                            hover * 0.28F);
                } else {
                    DashboardRenderer.drawBorderedRoundedRect(
                            context, cellX, cellY, cellWidth, cellHeight, 7, fill, 1882471762);
                }

                this.drawEntryIcon(
                        context, item, cellX + cellWidth / 2 - 8, cellY + (cellHeight - 16) / 2);
                this.selectorItemHits.add(new ClickGuiScreen.SelectorItemHit(cell, item));
            }
        }

        context.disableScissor();
        if (this.selectorEntries.isEmpty()) {
            this.drawCenteredText(
                    context, "NO MATCHING ENTRIES", x + width / 2, y + height / 2, -6182984);
        }

        if (this.selectorScrollMax > 1.0F) {
            this.drawScrollbar(
                    context,
                    gridX + gridWidth - 2,
                    gridY,
                    gridHeight,
                    this.selectorScrollAnimated,
                    this.selectorScrollMax,
                    rows * (cellHeight + gap));
        }

        int separatorY = y + height - 42;
        context.fill(x + 1, separatorY, x + width - 1, separatorY + 1, 1882471762);
        this.selectorClearRect = new ClickGuiScreen.Rect(x + 12, y + height - 32, 92, 22);
        this.selectorCancelRect = new ClickGuiScreen.Rect(x + width - 194, y + height - 32, 82, 22);
        this.selectorDoneRect = new ClickGuiScreen.Rect(x + width - 104, y + height - 32, 92, 22);
        this.drawSelectorButton(
                context, this.selectorClearRect, "Clear All", mouseX, mouseY, false, -41096);
        this.drawSelectorButton(
                context, this.selectorCancelRect, "Cancel", mouseX, mouseY, false, accentColor);
        this.drawSelectorButton(
                context, this.selectorDoneRect, "Done", mouseX, mouseY, true, accentColor);
    }

    private void drawSelectorTab(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String label,
            boolean selected,
            int mouseX,
            int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill = hovered ? lerpColor(-15657694, -15196885, 0.55F) : -15657694;
        if (!selected && !hovered) {
            DashboardRenderer.drawBorderedRoundedRect(
                    context, rect.x, rect.y, rect.w, rect.h, 7, fill, 1882471762);
        } else {
            this.drawGlowRect(
                    context,
                    rect.x,
                    rect.y,
                    rect.w,
                    rect.h,
                    7,
                    fill,
                    1882471762,
                    accentColor,
                    selected ? 0.68F : 0.25F);
        }

        this.drawCenteredText(
                context,
                label,
                rect.x + rect.w / 2,
                rect.y + (rect.h - 8) / 2,
                selected ? accentColor : (hovered ? -789256 : -6182984));
    }

    private void drawSelectorButton(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String label,
            int mouseX,
            int mouseY,
            boolean primary,
            int color) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill = hovered ? lerpColor(-15394523, -14670538, 0.48F) : -15394523;
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                7,
                fill,
                1882471762,
                primary ? accentColor : color,
                primary ? (hovered ? 0.6F : 0.42F) : (hovered ? 0.32F : 0.0F));
        this.drawCenteredText(
                context,
                label,
                rect.x + rect.w / 2,
                rect.y + (rect.h - 8) / 2,
                !primary && !hovered ? -6182984 : -789256);
    }

    private void drawColorPicker(DrawContext context, int mouseX, int mouseY) {
        context.fill(
                0,
                0,
                this.getScreenWidth(),
                this.getScreenHeight(),
                applyAlpha(-704643072, this.pickerProgress));
        int width = 432;
        int height = 290;
        int x = (this.getScreenWidth() - width) / 2;
        int y =
                (this.getScreenHeight() - height) / 2
                        + Math.round((1.0F - easeOutCubic(this.pickerProgress)) * 12.0F);
        this.pickerDialogRect = new ClickGuiScreen.Rect(x, y, width, height);
        DashboardRenderer.drawBorderedRoundedRect(
                context,
                x,
                y,
                width,
                height,
                12,
                -100004328,
                lerpColor(-1774500775, accentColor, 0.3F));
        DashboardRenderer.drawRoundedRect(context, x + 13, y + 14, 3, 20, 2, accentColor);
        this.drawText(context, "COLOR", x + 24, y + 16, -789256);
        this.pickerAreaRect = new ClickGuiScreen.Rect(x + 17, y + 49, 220, 155);
        this.drawPickerArea(context, this.pickerAreaRect);
        this.pickerHueRect = new ClickGuiScreen.Rect(x + 247, y + 49, 18, 155);
        this.drawHueBar(context, this.pickerHueRect);
        EntityColor color = this.getPickerColor();
        int sideX = x + 284;
        int sideWidth = 131;
        this.drawText(context, "PREVIEW", sideX, y + 49, -6182984);
        DashboardRenderer.drawRoundedRect(context, sideX, y + 64, sideWidth, 48, 7, toArgb(color));
        this.drawText(context, "PRESETS", sideX, y + 128, -6182984);
        int rgb = toArgb(color) & 16777215;

        for (int i = 0; i < PICKER_PRESET_COLORS.length; i++) {
            int column = i % 5;
            int row = i / 5;
            ClickGuiScreen.Rect rect =
                    new ClickGuiScreen.Rect(sideX + 1 + column * 27, y + 145 + row * 29, 18, 18);
            this.pickerPresetRects[i] = rect;
            boolean selected = rgb == (PICKER_PRESET_COLORS[i] & 16777215);
            boolean hovered = rect.contains(mouseX, mouseY);
            if (!selected && !hovered) {
                DashboardRenderer.drawRoundedRect(
                        context, rect.x, rect.y, rect.w, rect.h, 9, PICKER_PRESET_COLORS[i]);
            } else {
                DashboardRenderer.drawRing(
                        context,
                        rect.x + 9,
                        rect.y + 9,
                        10,
                        2,
                        selected ? -789256 : accentColor,
                        PICKER_PRESET_COLORS[i]);
            }
        }

        this.drawText(context, "OPACITY", x + 17, y + 216, -6182984);
        this.drawRightAlignedText(
                context, Math.round(this.pickerOpacity * 100.0F) + "%", x + 265, y + 216, -6182984);
        this.pickerOpacityRect = new ClickGuiScreen.Rect(x + 17, y + 232, 248, 11);
        this.drawOpacityBar(context, this.pickerOpacityRect, color);
        this.pickerCancelRect = new ClickGuiScreen.Rect(x + 17, y + height - 35, 96, 22);
        this.pickerApplyRect = new ClickGuiScreen.Rect(x + width - 113, y + height - 35, 96, 22);
        this.drawPickerButton(context, this.pickerCancelRect, "Cancel", mouseX, mouseY, false);
        this.drawPickerButton(context, this.pickerApplyRect, "Apply", mouseX, mouseY, true);
    }

    private void drawPickerArea(DrawContext context, ClickGuiScreen.Rect rect) {
        int hueColor = 0xFF000000 | Color.HSBtoRGB(this.pickerHue, 1.0F, 1.0F) & 16777215;
        DashboardRenderer.drawRoundedRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                7,
                applyAlpha(1882471762, this.openProgress));
        ClickGuiScreen.Rect inner =
                new ClickGuiScreen.Rect(rect.x + 1, rect.y + 1, rect.w - 2, rect.h - 2);
        DashboardRenderer.drawRoundedRect(context, inner.x, inner.y, inner.w, inner.h, 6, hueColor);
        this.drawPickerTexture(context, PICKER_SATURATION_TEXTURE, inner, 880, 620, -1);
        this.drawPickerTexture(context, PICKER_BRIGHTNESS_TEXTURE, inner, 880, 620, -1);
        int cursorX =
                Math.max(
                        inner.x + 4,
                        Math.min(
                                inner.x + inner.w - 4,
                                inner.x + Math.round(this.pickerSaturation * (inner.w - 1))));
        int cursorY =
                Math.max(
                        inner.y + 4,
                        Math.min(
                                inner.y + inner.h - 4,
                                inner.y
                                        + Math.round(
                                                (1.0F - this.pickerBrightness) * (inner.h - 1))));
        DashboardRenderer.drawRoundedRect(context, cursorX - 2, cursorY - 2, 4, 4, 2, -16579577);
    }

    private void drawHueBar(DrawContext context, ClickGuiScreen.Rect rect) {
        this.drawPickerTexture(context, PICKER_HUE_TEXTURE, rect, 72, 620, -1);
        int cursorY =
                Math.max(
                        rect.y + 3,
                        Math.min(
                                rect.y + rect.h - 3,
                                rect.y + 1 + Math.round(this.pickerHue * (rect.h - 2))));
        DashboardRenderer.drawRoundedRect(
                context, rect.x - 2, cursorY - 1, rect.w + 4, 3, 2, -789256);
    }

    private void drawOpacityBar(DrawContext context, ClickGuiScreen.Rect rect, EntityColor color) {
        int rgb = toArgb(color) & 16777215;
        DashboardRenderer.drawRoundedRect(context, rect.x, rect.y, rect.w, rect.h, 5, -2235927);
        this.drawPickerTexture(context, PICKER_OPACITY_TEXTURE, rect, 992, 44, 0xFF000000 | rgb);
        int cursorX =
                Math.max(
                        rect.x + 5,
                        Math.min(
                                rect.x + rect.w - 5,
                                rect.x + 1 + Math.round(this.pickerOpacity * (rect.w - 2))));
        DashboardRenderer.drawRoundedRect(
                context, cursorX - 2, rect.y + rect.h / 2 - 2, 5, 5, 3, -789256);
    }

    private void drawPickerTexture(
            DrawContext context,
            Identifier texture,
            ClickGuiScreen.Rect rect,
            int textureWidth,
            int textureHeight,
            int color) {
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                rect.x,
                rect.y,
                0.0F,
                0.0F,
                rect.w,
                rect.h,
                textureWidth,
                textureHeight,
                textureWidth,
                textureHeight,
                color);
    }

    private void drawPickerButton(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String label,
            int mouseX,
            int mouseY,
            boolean primary) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = this.animateHover("picker-action:" + label, hovered);
        int fill = lerpColor(-15394523, -14670538, hover * 0.42F);
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                6,
                fill,
                1882471762,
                accentColor,
                primary ? 0.42F + hover * 0.18F : hover * 0.3F);
        this.drawCenteredText(
                context,
                label,
                rect.x + rect.w / 2,
                rect.y + 7,
                !primary && !hovered ? -6182984 : -789256);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double x = click.x() / this.guiScale;
        double y = click.y() / this.guiScale;
        int button = click.button();
        if (this.isColorPickerOpen()) {
            return this.clickColorPicker(x, y, button);
        } else if (this.selectorMode != ClickGuiScreen.SelectorMode.NONE) {
            return this.clickSelectorDialog(x, y, button);
        } else if (this.newConfigDialogOpen) {
            return this.clickNewConfigDialog(x, y, button);
        } else if (this.selectedModule != null && this.detailPanelRect.contains(x, y)) {
            if (this.editingSetting != null) {
                boolean editingControlClicked =
                        this.settingRowHits.stream()
                                .anyMatch(
                                        hit ->
                                                hit.setting == this.editingSetting
                                                        && hit.control.contains(x, y));
                if (!editingControlClicked) {
                    this.endTextEdit(true);
                }
            }

            if (this.backRect.contains(x, y) && button == 0) {
                this.selectedModule = null;
                this.settingScrollTarget = 0.0F;
                return true;
            } else {
                for (ClickGuiScreen.ModuleCardHit cardHit : this.moduleCardHits) {
                    if (cardHit.card.w == 0 && cardHit.toggle.contains(x, y) && button == 0) {
                        cardHit.module.toggle();
                        return true;
                    }
                }

                for (ClickGuiScreen.SettingRowHit rowHit : this.settingRowHits) {
                    if (this.settingsListRect.contains(x, y) && rowHit.row.contains(x, y)) {
                        return this.clickSettingRow(rowHit, x, y, button);
                    }
                }

                return true;
            }
        } else if (this.listeningKeybind != null && button >= 2 && button <= 7) {
            this.listeningKeybind.setValue(1000 + button);
            this.listeningKeybind = null;
            return true;
        } else {
            if (this.editingSetting != null) {
                boolean editingControlClicked =
                        this.settingRowHits.stream()
                                .anyMatch(
                                        hit ->
                                                hit.setting == this.editingSetting
                                                        && hit.control.contains(x, y));
                if (!editingControlClicked) {
                    this.endTextEdit(true);
                }
            }

            if (this.closeRect.contains(x, y) && button == 0) {
                this.close();
                return true;
            } else if (this.guiKeybindRect.contains(x, y)) {
                KeybindSetting keybind = this.getGuiKeybindSetting();
                if (keybind != null) {
                    if (button == 0) {
                        this.listeningKeybind = keybind;
                    } else if (button == 1) {
                        keybind.setValue(-1);
                        if (this.listeningKeybind == keybind) {
                            this.listeningKeybind = null;
                        }
                    }
                }

                return true;
            } else if (this.homeTabRect.contains(x, y) && button == 0) {
                this.configsTabOpen = false;
                this.themesTabOpen = false;
                this.browsingPresets = false;
                return true;
            } else if (this.configsTabRect.contains(x, y) && button == 0) {
                this.openConfigsTab();
                return true;
            } else if (this.themesTabRect.contains(x, y) && button == 0) {
                this.openThemesTab();
                return true;
            } else if (this.configsTabOpen) {
                for (ClickGuiScreen.CategoryHit categoryHit : this.categoryHits) {
                    if (categoryHit.rect.contains(x, y) && button == 0) {
                        this.configsTabOpen = false;
                        this.selectCategory(categoryHit.category);
                        return true;
                    }
                }

                return this.clickConfigsTab(x, y, button);
            } else if (this.themesTabOpen) {
                for (ClickGuiScreen.CategoryHit categoryHit : this.categoryHits) {
                    if (categoryHit.rect.contains(x, y) && button == 0) {
                        this.themesTabOpen = false;
                        this.selectCategory(categoryHit.category);
                        return true;
                    }
                }

                return this.clickThemesTab(x, y, button);
            } else if (this.searchRect.contains(x, y) && button == 0) {
                this.searchFocused = true;
                return true;
            } else {
                this.searchFocused = false;
                if (this.backRect.contains(x, y) && button == 0) {
                    this.selectedModule = null;
                    this.settingScrollTarget = 0.0F;
                    return true;
                } else if (this.prevCategoryRect.contains(x, y) && button == 0) {
                    this.cycleCategory(-1);
                    return true;
                } else if (this.nextCategoryRect.contains(x, y) && button == 0) {
                    this.cycleCategory(1);
                    return true;
                } else {
                    for (ClickGuiScreen.CategoryHit categoryHit : this.categoryHits) {
                        if (categoryHit.rect.contains(x, y) && button == 0) {
                            this.selectCategory(categoryHit.category);
                            return true;
                        }
                    }

                    for (ClickGuiScreen.ModuleCardHit cardHit : this.moduleCardHits) {
                        boolean detail = cardHit.card.w == 0;
                        if (detail && cardHit.toggle.contains(x, y) && button == 0) {
                            cardHit.module.toggle();
                            return true;
                        }

                        if (!detail && this.moduleGridRect.contains(x, y)) {
                            if (cardHit.settings.contains(x, y) && button == 0) {
                                this.selectedModule = cardHit.module;
                                this.settingScrollTarget = 0.0F;
                                return true;
                            }

                            if (cardHit.card.contains(x, y) && button == 0) {
                                cardHit.module.toggle();
                                return true;
                            }

                            if (cardHit.card.contains(x, y) && button == 1) {
                                this.selectedModule =
                                        this.selectedModule == cardHit.module
                                                ? null
                                                : cardHit.module;
                                this.settingScrollTarget = 0.0F;
                                this.settingScrollAnimated = 0.0F;
                                return true;
                            }
                        }
                    }

                    for (ClickGuiScreen.SettingRowHit rowHit : this.settingRowHits) {
                        if (this.settingsListRect.contains(x, y) && rowHit.row.contains(x, y)) {
                            return this.clickSettingRow(rowHit, x, y, button);
                        }
                    }

                    if (this.enableAllRect.contains(x, y) && button == 0) {
                        for (Module module : this.getModulesInCategory(this.selectedCategory)) {
                            module.setActive(true);
                        }

                        return true;
                    } else if (this.disableAllRect.contains(x, y) && button == 0) {
                        for (Module module : this.getModulesInCategory(this.selectedCategory)) {
                            module.setActive(false);
                        }

                        return true;
                    } else {
                        return true;
                    }
                }
            }
        }
    }

    private void openConfigsTab() {
        if (!this.configsTabOpen) {
            this.selectedProfileIndex = -1;
            String activeName = ConfigProfileManager.getActiveProfileName();
            List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();

            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).name.equalsIgnoreCase(activeName)) {
                    this.selectedProfileIndex = i;
                    break;
                }
            }
        }

        this.configsTabOpen = true;
        this.themesTabOpen = false;
        this.selectedModule = null;
        this.searchFocused = false;
        this.searchText = "";
        this.backRect = ClickGuiScreen.Rect.EMPTY;
        this.enableAllRect = ClickGuiScreen.Rect.EMPTY;
        this.disableAllRect = ClickGuiScreen.Rect.EMPTY;
    }

    private void openThemesTab() {
        this.themesTabOpen = true;
        this.configsTabOpen = false;
        this.browsingPresets = false;
        this.selectedModule = null;
        this.searchFocused = false;
        this.searchText = "";
        this.backRect = ClickGuiScreen.Rect.EMPTY;
        this.enableAllRect = ClickGuiScreen.Rect.EMPTY;
        this.disableAllRect = ClickGuiScreen.Rect.EMPTY;
    }

    private boolean clickThemesTab(double x, double y, int button) {
        if (button != 0) {
            return true;
        } else {
            for (ClickGuiScreen.ThemePresetHit hit : this.themePresetHits) {
                if (hit.rect.contains(x, y)) {
                    this.applyThemePreset(hit.index);
                    return true;
                }
            }

            ThemeModule theme = this.getThemeModule();
            if (theme == null) {
                return true;
            } else if (this.customAccentRect.contains(x, y)) {
                if (theme.color instanceof ColorSetting colorSetting) {
                    this.openColorPicker(colorSetting);
                }

                return true;
            } else if (this.rainbowToggleRect.contains(x, y)) {
                theme.rainbow.setValue(!Boolean.TRUE.equals(theme.rainbow.getValue()));
                return true;
            } else if (this.animationsToggleRect.contains(x, y)) {
                theme.animations.setValue(!Boolean.TRUE.equals(theme.animations.getValue()));
                return true;
            } else if (this.msaaToggleRect.contains(x, y)) {
                theme.msaa.setValue(!Boolean.TRUE.equals(theme.msaa.getValue()));
                return true;
            } else if (this.dashboardModeRect.contains(x, y)) {
                theme.guiStyle.setValue(ThemeModule.GuiStyle.DASHBOARD);
                return true;
            } else if (this.panelsModeRect.contains(x, y)) {
                theme.guiStyle.setValue(ThemeModule.GuiStyle.PANELS);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.setScreen(new PanelGuiScreen(this.parent));
                }

                return true;
            } else {
                return true;
            }
        }
    }

    private boolean clickConfigsTab(double x, double y, int button) {
        if (button != 0) {
            return true;
        } else if (this.myConfigsTabRect.contains(x, y)) {
            this.browsingPresets = false;
            return true;
        } else if (this.browseTabRect.contains(x, y)) {
            this.browsingPresets = true;
            return true;
        } else {
            if (!this.browsingPresets) {
                for (ClickGuiScreen.ProfileRowHit profileHit : this.profileRowHits) {
                    if (profileHit.load.contains(x, y)) {
                        this.selectedProfileIndex = profileHit.index;
                        this.showResult(ConfigProfileManager.loadProfile(profileHit.index));
                        return true;
                    }

                    if (profileHit.delete.contains(x, y)) {
                        this.showResult(ConfigProfileManager.deleteProfile(profileHit.index));
                        if (this.selectedProfileIndex == profileHit.index) {
                            this.selectedProfileIndex = -1;
                        } else if (this.selectedProfileIndex > profileHit.index) {
                            this.selectedProfileIndex--;
                        }

                        return true;
                    }

                    if (profileHit.row.contains(x, y)) {
                        this.selectedProfileIndex = profileHit.index;
                        return true;
                    }
                }
            } else {
                List<ConfigProfileManager.BundledConfig> bundled =
                        ConfigProfileManager.getBundledConfigs();

                for (ClickGuiScreen.PresetRowHit presetHit : this.presetRowHits) {
                    if (presetHit.apply.contains(x, y)
                            && presetHit.index >= 0
                            && presetHit.index < bundled.size()) {
                        this.showResult(
                                ConfigProfileManager.importCode(
                                        bundled.get(presetHit.index).configJson));
                        if (this.statusSuccess) {
                            this.browsingPresets = false;
                            this.selectedProfileIndex = ConfigProfileManager.profiles().size() - 1;
                        }

                        return true;
                    }
                }
            }

            if (!this.saveConfigRect.contains(x, y)) {
                if (this.newConfigRect.contains(x, y)) {
                    this.openNewConfigDialog();
                    return true;
                } else if (this.importConfigRect.contains(x, y)) {
                    ConfigProfileManager.Result importResult =
                            ConfigProfileManager.importCode(this.client.keyboard.getClipboard());
                    this.showResult(importResult);
                    if (importResult.success) {
                        this.selectedProfileIndex = ConfigProfileManager.profiles().size() - 1;
                    }

                    return true;
                } else if (this.shareConfigRect.contains(x, y)) {
                    ConfigProfileManager.Result exportResult =
                            ConfigProfileManager.exportProfile(this.selectedProfileIndex);
                    if (exportResult.success && exportResult.code != null) {
                        this.client.keyboard.setClipboard(exportResult.code);
                    }

                    this.showResult(exportResult);
                    return true;
                } else if (this.resetConfigRect.contains(x, y)) {
                    long now = System.currentTimeMillis();
                    if (now < this.resetConfirmExpireTime) {
                        this.resetConfirmExpireTime = 0L;
                        this.selectedProfileIndex = -1;
                        this.showResult(ConfigProfileManager.resetToDefaults());
                    } else {
                        this.resetConfirmExpireTime = now + 5000L;
                        this.showResult(
                                ConfigProfileManager.Result.failure(
                                        "Click reset again to confirm"));
                    }

                    return true;
                } else {
                    return true;
                }
            } else {
                List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();
                if (this.selectedProfileIndex >= 0 && this.selectedProfileIndex < profiles.size()) {
                    this.showResult(ConfigProfileManager.updateProfile(this.selectedProfileIndex));
                } else {
                    this.openNewConfigDialog();
                }

                return true;
            }
        }
    }

    private boolean clickNewConfigDialog(double x, double y, int button) {
        if (button != 0) {
            return true;
        } else {
            if (this.dialogCancelRect.contains(x, y)) {
                this.newConfigDialogOpen = false;
                this.newConfigName = "";
            } else if (this.dialogSaveRect.contains(x, y)) {
                this.createConfigFromDialog();
            }

            return true;
        }
    }

    private void openNewConfigDialog() {
        if (ConfigProfileManager.profiles().size() >= 3) {
            this.showResult(
                    ConfigProfileManager.Result.failure("Delete a config first - maximum is 3"));
        } else {
            this.newConfigDialogOpen = true;
            this.newConfigName = "";
        }
    }

    private void createConfigFromDialog() {
        ConfigProfileManager.Result result = ConfigProfileManager.saveCurrent(this.newConfigName);
        this.showResult(result);
        if (result.success) {
            this.selectedProfileIndex = ConfigProfileManager.profiles().size() - 1;
            this.newConfigDialogOpen = false;
            this.newConfigName = "";
        }
    }

    private void showResult(ConfigProfileManager.Result result) {
        this.statusMessage = result.message == null ? "" : result.message.toUpperCase(Locale.ROOT);
        this.statusSuccess = result.success;
        this.statusExpireTime = System.currentTimeMillis() + 4200L;
    }

    private boolean clickSettingRow(
            ClickGuiScreen.SettingRowHit hit, double x, double y, int button) {
        Setting<?> setting = hit.setting;
        if (button == 0
                && this.selectedModule instanceof GambleRigger dropperRigger
                && ("dropper-1".equals(setting.name) || "dropper-2".equals(setting.name))) {
            DropperEditorScreen.open(this, dropperRigger, "dropper-1".equals(setting.name) ? 0 : 1);
            return true;
        } else if (button == 0
                && this.selectedModule instanceof GambleRigger selectRigger
                && GambleRigger.isSelectSetting(setting.name)) {
            selectRigger.beginSelection(setting.name, this);
            return true;
        } else if (button == 0
                && "macro-manager".equals(setting.name)
                && this.selectedModule instanceof ChatMacros chatMacros) {
            ChatMacroScreen.open(this, chatMacros);
            return true;
        } else if (!(button == 0 && setting instanceof EnumSetting artworkSetting)
                || this.selectedModule == null
                || !"sky-arts".equals(lower(this.selectedModule.name))
                || !"artwork".equals(setting.name) && !"sky".equals(setting.name)) {
            if (setting instanceof BooleanSetting booleanSetting && button == 0) {
                booleanSetting.setValue(!booleanSetting.getValue());
            } else if (!(setting instanceof IntSetting) && !(setting instanceof NumberSetting)) {
                if (setting instanceof ColorSetting colorSetting && button == 0) {
                    this.openColorPicker(colorSetting);
                } else if (setting instanceof BlockColorMapSetting blockColorSetting
                        && button == 0) {
                    this.openColoredBlocksSelector(blockColorSetting);
                } else if (setting instanceof BlockListSetting blockListSetting && button == 0) {
                    this.openBlockSelector(blockListSetting);
                } else if (setting instanceof EntityTypeListSetting entityListSetting
                        && button == 0) {
                    this.openEntitySelector(entityListSetting);
                } else if (setting instanceof KeybindSetting keybindSetting) {
                    if (button == 1) {
                        keybindSetting.setValue(-1);
                    } else if (button == 0) {
                        this.listeningKeybind = keybindSetting;
                    }
                } else if (setting instanceof EnumSetting enumSetting) {
                    this.cycleModeSetting(enumSetting, button == 1 ? -1 : 1);
                } else if (button == 0) {
                    this.beginTextEdit(setting);
                }
            } else {
                if (button != 0) {
                    return true;
                }

                this.draggedSetting = setting;
                this.draggedSliderRect = hit.control;
                this.updateSliderFromMouse(x);
            }

            return true;
        } else {
            ArtworkGalleryScreen.open(this, artworkSetting);
            return true;
        }
    }

    private boolean clickSelectorDialog(double x, double y, int button) {
        if (this.selectorDoneRect.contains(x, y) && button == 0) {
            this.closeSelector();
            return true;
        } else if (this.selectorCancelRect.contains(x, y) && button == 0) {
            this.cancelSelector();
            return true;
        } else if (this.selectorCloseRect.contains(x, y) && button == 0) {
            this.closeSelector();
            return true;
        } else if (this.selectorClearRect.contains(x, y) && button == 0) {
            this.clearSelector();
            return true;
        } else if (this.selectorAllTabRect.contains(x, y) && button == 0) {
            this.selectorShowSelectedOnly = false;
            this.selectorDirty = true;
            this.selectorScrollTarget = 0.0F;
            return true;
        } else if ((this.selectorSelectedTabRect.contains(x, y)
                        || this.selectorFilterRect.contains(x, y))
                && button == 0) {
            this.selectorShowSelectedOnly =
                    this.selectorFilterRect.contains(x, y) ? !this.selectorShowSelectedOnly : true;
            this.selectorDirty = true;
            this.selectorScrollTarget = 0.0F;
            return true;
        } else if (this.selectorSearchRect.contains(x, y) && button == 0) {
            return true;
        } else {
            for (ClickGuiScreen.SelectorItemHit itemHit : this.selectorItemHits) {
                if (this.selectorGridRect.contains(x, y) && itemHit.rect.contains(x, y)) {
                    if (this.selectorMode == ClickGuiScreen.SelectorMode.COLORED_BLOCKS) {
                        Block coloredBlock = (Block) itemHit.item;
                        if (button == 1) {
                            if (!this.coloredBlocksSetting.hasBlock(coloredBlock)) {
                                this.coloredBlocksSetting.toggle(
                                        coloredBlock, getAccentMutableColor());
                            }

                            this.openBlockColorPicker(this.coloredBlocksSetting, coloredBlock);
                        } else if (button == 0) {
                            this.coloredBlocksSetting.toggle(coloredBlock, getAccentMutableColor());
                        }
                    } else if (this.selectorMode == ClickGuiScreen.SelectorMode.BLOCKS
                            && button == 0) {
                        Block block = (Block) itemHit.item;
                        List<Block> blocks = new ArrayList<>(this.blockListSetting.getValue());
                        if (!blocks.remove(block)) {
                            blocks.add(block);
                        }

                        this.blockListSetting.setValue(blocks);
                    } else if (this.selectorMode == ClickGuiScreen.SelectorMode.ENTITIES) {
                        EntityType<?> entityType = (EntityType<?>) itemHit.item;
                        if (button == 1) {
                            if (!this.entityListSetting.isSelected(entityType)) {
                                this.entityListSetting.toggle(entityType);
                            }

                            this.openEntityColorPicker(this.entityListSetting, entityType);
                        } else if (button == 0) {
                            this.entityListSetting.toggle(entityType);
                        }
                    }

                    if (this.selectorShowSelectedOnly) {
                        this.selectorDirty = true;
                    }

                    return true;
                }
            }

            if (!this.selectorDialogRect.contains(x, y) && button == 0) {
                this.closeSelector();
            }

            return true;
        }
    }

    private boolean clickColorPicker(double x, double y, int button) {
        if (button != 0) {
            return true;
        } else if (this.pickerApplyRect.contains(x, y)) {
            this.confirmColorPicker();
            return true;
        } else if (this.pickerCancelRect.contains(x, y)) {
            this.cancelColorPicker();
            return true;
        } else {
            if (this.pickerAreaRect.contains(x, y)) {
                this.pickerDragTarget = 1;
                this.updatePickerFromMouse(x, y);
            } else if (this.pickerHueRect.contains(x, y)) {
                this.pickerDragTarget = 2;
                this.updatePickerFromMouse(x, y);
            } else if (this.pickerOpacityRect.contains(x, y)) {
                this.pickerDragTarget = 3;
                this.updatePickerFromMouse(x, y);
            } else {
                for (int i = 0; i < this.pickerPresetRects.length; i++) {
                    ClickGuiScreen.Rect rect = this.pickerPresetRects[i];
                    if (rect != null && rect.contains(x, y)) {
                        this.setPickerColorRgb(PICKER_PRESET_COLORS[i]);
                        return true;
                    }
                }

                if (!this.pickerDialogRect.contains(x, y)) {
                    this.cancelColorPicker();
                }
            }

            return true;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double x = click.x() / this.guiScale;
        double y = click.y() / this.guiScale;
        if (this.pickerDragTarget != 0) {
            this.updatePickerFromMouse(x, y);
            return true;
        } else if (this.draggedSetting != null) {
            this.updateSliderFromMouse(x);
            return true;
        } else {
            return true;
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        this.draggedSetting = null;
        this.pickerDragTarget = 0;
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        mouseX /= this.guiScale;
        mouseY /= this.guiScale;
        float delta = (float) (-verticalAmount * 36.0);
        if (this.selectorMode != ClickGuiScreen.SelectorMode.NONE) {
            this.selectorScrollTarget =
                    clamp(this.selectorScrollTarget + delta, 0.0F, this.selectorScrollMax);
        } else if (this.selectedModule != null && this.settingsListRect.contains(mouseX, mouseY)) {
            this.settingScrollTarget =
                    clamp(this.settingScrollTarget + delta, 0.0F, this.settingScrollMax);
        } else if (this.moduleGridRect.contains(mouseX, mouseY)) {
            this.moduleScrollTarget =
                    clamp(this.moduleScrollTarget + delta, 0.0F, this.moduleScrollMax);
        }

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        int scancode = input.scancode();
        if (this.newConfigDialogOpen) {
            if (key == 256) {
                this.newConfigDialogOpen = false;
                this.newConfigName = "";
            } else if (key == 257 || key == 335) {
                this.createConfigFromDialog();
            } else if (key == 259 && !this.newConfigName.isEmpty()) {
                this.newConfigName =
                        this.newConfigName.substring(0, this.newConfigName.length() - 1);
            }

            return true;
        } else if (this.isColorPickerOpen()) {
            if (key == 256) {
                this.cancelColorPicker();
            }

            return true;
        } else if (this.selectorMode != ClickGuiScreen.SelectorMode.NONE) {
            if (key == 256) {
                this.closeSelector();
            } else if (key == 259 && !this.selectorSearchText.isEmpty()) {
                this.selectorSearchText =
                        this.selectorSearchText.substring(0, this.selectorSearchText.length() - 1);
                this.selectorDirty = true;
                this.selectorScrollTarget = 0.0F;
            }

            return true;
        } else if (this.listeningKeybind != null) {
            boolean clearKey =
                    key == 256
                            || key == 261
                            || key == 259
                            || key == 127
                            || scancode == 83
                            || scancode == 339;
            if (clearKey) {
                this.listeningKeybind.setValue(-1);
                this.listeningKeybind = null;
            } else {
                this.listeningKeybind.setValue(key);
                this.listeningKeybind = null;
            }

            return true;
        } else if (this.editingSetting == null) {
            if (this.searchFocused) {
                if (key == 256) {
                    this.searchFocused = false;
                } else if (key == 259 && !this.searchText.isEmpty()) {
                    this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
                    this.moduleScrollTarget = 0.0F;
                }

                return true;
            } else if (this.configsTabOpen) {
                if (key == 256) {
                    this.configsTabOpen = false;
                    this.browsingPresets = false;
                }

                return true;
            } else if (this.themesTabOpen) {
                if (key == 256) {
                    this.themesTabOpen = false;
                }

                return true;
            } else if (key == 256) {
                if (this.selectedModule != null) {
                    this.selectedModule = null;
                } else {
                    this.close();
                }

                return true;
            } else if (key == 47) {
                this.searchFocused = true;
                return true;
            } else {
                return true;
            }
        } else {
            if (key == 257 || key == 335) {
                this.endTextEdit(true);
            } else if (key == 256) {
                this.endTextEdit(false);
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
            String text = String.valueOf(input.asString());
            if (this.newConfigDialogOpen) {
                for (int i = 0; i < text.length() && this.newConfigName.length() < 24; i++) {
                    char c = text.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') {
                        this.newConfigName = this.newConfigName + c;
                    }
                }
            } else if (this.selectorMode != ClickGuiScreen.SelectorMode.NONE
                    && this.selectorSearchText.length() < 64) {
                this.selectorSearchText = this.selectorSearchText + text;
                this.selectorDirty = true;
                this.selectorScrollTarget = 0.0F;
            } else if (this.editingSetting != null && this.editBuffer.length() < 256) {
                this.editBuffer = this.editBuffer + text;
            } else if (this.searchFocused && this.searchText.length() < 48) {
                this.searchText = this.searchText + text;
                this.moduleScrollTarget = 0.0F;
            }

            return true;
        }
    }

    public void odinclient$drawGuiText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawBoldText(context, this.textRenderer, text, x, y, color);
    }

    public int odinclient$guiTextWidth(String text) {
        return DashboardRenderer.width(this.textRenderer, text == null ? "" : text);
    }

    public void odinclient$openEntityColor(EntityColor color) {
        if (color != null) {
            this.clearPickerTarget();
            this.pickerDirectColor = color;
            this.beginColorEdit(color);
        }
    }

    private void ensureValidCategory() {
        List<Category> categories = this.getCategories();
        if (categories.isEmpty()) {
            this.selectedCategory = "client";
        } else if (this.selectedCategory == null
                || categories.stream()
                        .noneMatch(
                                category ->
                                        category.name.equalsIgnoreCase(this.selectedCategory))) {
            this.selectedCategory = categories.getFirst().name;
        }
    }

    private void selectCategory(String category) {
        this.selectedCategory = category;
        this.searchText = "";
        this.moduleScrollTarget = 0.0F;
        this.moduleScrollAnimated = 0.0F;
        this.selectedModule = null;
        this.settingScrollTarget = 0.0F;
    }

    private void cycleCategory(int step) {
        List<Category> categories = this.getCategories();
        if (!categories.isEmpty()) {
            int current = 0;

            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).name.equalsIgnoreCase(this.selectedCategory)) {
                    current = i;
                    break;
                }
            }

            this.selectCategory(
                    categories.get(Math.floorMod(current + step, categories.size())).name);
        }
    }

    private List<Category> getCategories() {
        this.refreshModules();
        return this.categories;
    }

    private List<Module> getVisibleModules() {
        this.refreshModules();
        String query = this.searchText.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return this.getModulesInCategory(this.selectedCategory);
        } else {
            if (!query.equals(this.lastSearchQuery)) {
                this.searchResults =
                        this.allModules.stream()
                                .filter(module -> matchesSearch(module, query))
                                .toList();
                this.lastSearchQuery = query;
            }

            return this.searchResults;
        }
    }

    private List<Module> getModulesInCategory(String category) {
        this.refreshModules();
        return this.modulesByCategory.getOrDefault(lower(category), List.of());
    }

    private void refreshModules() {
        Collection<Module> all = ModuleManager.get().getAll();
        if (all.size() != this.lastModuleCount
                || this.lastModuleCount != 0 && this.categories.isEmpty()) {
            this.allModules = all.stream().filter(m -> !"theme".equalsIgnoreCase(m.name)).toList();
            this.modulesByCategory.clear();

            for (Module module : this.allModules) {
                this.modulesByCategory
                        .computeIfAbsent(getModuleCategory(module), key -> new ArrayList<>())
                        .add(module);
            }

            for (Entry<String, List<Module>> entry :
                    new ArrayList<>(this.modulesByCategory.entrySet())) {
                if ("client".equals(entry.getKey())) {
                    entry.getValue()
                            .sort(Comparator.comparingInt(ClickGuiScreen::getClientModuleOrder));
                }

                this.modulesByCategory.put(entry.getKey(), List.copyOf(entry.getValue()));
            }

            List<Category> categoryList = new ArrayList<>();

            for (String name : List.of("combat", "misc", "donut", "render", "client")) {
                List<Module> modules = this.modulesByCategory.getOrDefault(name, List.of());
                if (!modules.isEmpty()) {
                    categoryList.add(
                            new Category(name, this.getItemStack(getCategoryIconName(name))));
                }
            }

            this.categories = List.copyOf(categoryList);
            this.searchResults = List.of();
            this.lastSearchQuery = "";
            this.cardTextCache.clear();
            this.lastModuleCount = all.size();
        }
    }

    private ClickGuiScreen.CardText getCardText(Module module, int width) {
        return this.cardTextCache
                .computeIfAbsent(module, key -> new HashMap<>())
                .computeIfAbsent(
                        width,
                        cardWidth -> {
                            String displayTitle =
                                    module.title != null && !module.title.isBlank()
                                            ? module.title
                                            : title(module.name);
                            String description = getModuleDescription(module);
                            return new ClickGuiScreen.CardText(
                                    displayTitle,
                                    this.ellipsize(displayTitle, cardWidth - 70),
                                    List.copyOf(
                                            this.wrapText(
                                                    description, Math.max(36, cardWidth - 47), 3)));
                        });
    }

    private static String getModuleDescription(Module module) {
        String name = lower(module.name);

        String description =
                switch (name) {
                    case "amethyst-esp" -> "Highlights amethyst clusters through walls.";
                    case "anchor-macro" -> "Charges and detonates the anchor under your crosshair.";
                    case "auto-crystal" -> "Rapidly places and breaks End Crystals on a held key.";
                    case "aim-assist" -> "Smoothly guides your crosshair toward nearby players.";
                    case "auto-inv-totem" ->
                            "Refills offhand and hotbar totems from your inventory.";
                    case "auto-mine" -> "Keeps mining without holding the attack key.";
                    case "auto-relog" ->
                            "Relogs below the selected Y level or on an activation key.";
                    case "autolog" ->
                            "Disconnects on players, your totem pop or confirmed AntiVanish"
                                + " detection.";
                    case "auto-firework" ->
                            "Uses fireworks at random intervals, with optional logout on your own"
                                + " totem pop.";
                    case "player-debug-v2" ->
                            "Compares chunk sections and marks detected block changes with"
                                + " pillars.";
                    case "antivanish-detection" ->
                            "Detects hidden staff using TAB changes and corroborated local"
                                + " activity.";
                    case "player-notify" ->
                            "Alerts you about players, with a ten-minute cooldown per player.";
                    case "auto-tool" -> "Selects the best tool or weapon for your target.";
                    case "auto-totem" -> "Equips a totem when your health drops too low.";
                    case "bedrock-hole-esp" -> "Highlights openings in the lowest bedrock layers.";
                    case "block-entity-debug" ->
                            "Reveals underground block entities from server packets.";
                    case "block-esp" -> "Highlights selected blocks through walls.";
                    case "block-outlines" ->
                            "Customizes the outline of the block under your crosshair.";
                    case "item-glow" ->
                            "Highlights dropped and held items with a smooth colored item shader.";
                    case "inventory-hud" -> "Shows inventory items and their exact slot positions.";
                    case "block-notifier" -> "Alerts you when selected blocks are detected.";
                    case "cord-snapper" -> "Sends your coordinates to a Discord webhook.";
                    case "chat-macros" -> "Create custom keyed chat messages and commands.";
                    case "crystal-optimizer" -> "Speeds up manual crystal placing and breaking.";
                    case "discord-presence" -> "Shows OdinClient as your Discord activity.";
                    case "fake-roles" -> "Shows custom Donut roles on local player nametags.";
                    case "fake-stats" -> "Shows custom Donut stats on your local scoreboard.";
                    case "fast-place" -> "Reduces the delay between right-click actions.";
                    case "freecam" -> "Moves the camera independently from your player.";
                    case "freelook" -> "Lets you look around without turning your player.";
                    case "fullbright" -> "Brightens dark areas without placing light.";
                    case "hole-esp" -> "Highlights deep 1x1 and 3x1 holes.";
                    case "chunkfinder" ->
                            "Marks chunks with enclosed holes, mobs, fish and mined geodes.";
                    case "home-reset" -> "Deletes and recreates a selected Donut home.";
                    case "hover-totem" -> "Equips hovered totems to your offhand or hotbar.";
                    case "hud" -> "Shows customizable in-game information widgets.";
                    case "jump-circles" ->
                            "Spawns a rotating OdinClient ring when you jump or land.";
                    case "key-homesetter" -> "Sets a selected Donut home with one key.";
                    case "key-pearl" -> "Throws a pearl with one key and switches back.";
                    case "key-rtp" -> "Starts a Donut random teleport with one key.";
                    case "light-finder" -> "Highlights underground block light for base finding.";
                    case "mace-swap" -> "Switches to the best mace when you attack.";
                    case "mob-esp" -> "Highlights selected mobs with optional tracers.";
                    case "name-protect" -> "Replaces selected names in all rendered text.";
                    case "name-tags" -> "Adds detailed tags to players, items and spawners.";
                    case "pearl-esp" -> "Highlights thrown pearls with optional tracers.";
                    case "pearl-trajectory" -> "Predicts a held pearl's flight path and impact.";
                    case "player-chunk" ->
                            "Detects player activity and highlights suspected chunks.";
                    case "player-esp" -> "Highlights nearby players through walls.";
                    case "region-map" -> "Maps your current Donut region and its neighbors.";
                    case "render-cycle" -> "Cycles render distance below the selected Y level.";
                    case "safe-anchor" -> "Places, charges and safely detonates a Respawn Anchor.";
                    case "shield-breaker" ->
                            "Switches to the strongest axe against blocking players.";
                    case "skin-protect" -> "Replaces player skins to hide identities in clips.";
                    case "sky-arts" -> "Adds selectable artwork and backgrounds to the sky.";
                    case "china-head" ->
                            "A gently rotating straw hat fitted to your head, including while"
                                + " flying.";
                    case "spawner-flagger" -> "Highlights spawners and labels their mob type.";
                    case "spotify-hud" -> "Shows the current Spotify track in a compact HUD.";
                    case "sprint" -> "Automatically sprints while you move.";
                    case "staf-tracker" -> "Shows online Donut staff in a HUD list.";
                    case "storage-esp" -> "Highlights selected containers and utility blocks.";
                    case "sus-chunk-finder" ->
                            "Finds chunks showing signs of long-term player activity.";
                    case "suspicious-esp" ->
                            "Highlights selected mobs and mature amethyst clusters.";
                    case "swingspeed" -> "Changes the speed of your hand-swing animation.";
                    case "theme" -> "Controls menu colors and visual effects.";
                    case "tool-size" -> "Changes held tool and weapon size in first person.";
                    case "triggerbot" -> "Attacks players under your crosshair when ready.";
                    case "weather-notifier" -> "Alerts you whenever the weather changes.";
                    case "zoom" -> "Adds scroll-wheel zoom with hold and toggle modes.";
                    default -> module.description;
                };
        return description != null && !description.isBlank()
                ? description
                : "Configure this module.";
    }

    private ItemStack getModuleIcon(Module module) {
        return this.getItemStack(getModuleIconName(module.name));
    }

    private ItemStack getItemStack(String name) {
        return this.itemStackCache.computeIfAbsent(
                name,
                itemName -> {
                    Identifier id = Identifier.of("minecraft", itemName);
                    return Registries.ITEM.containsId(id)
                            ? new ItemStack(Registries.ITEM.get(id))
                            : ItemStack.EMPTY;
                });
    }

    private boolean drawCategoryIcon(
            DrawContext context, String category, ItemStack icon, int x, int y) {
        if ("donut".equalsIgnoreCase(category)) {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    DONUT_CATEGORY_TEXTURE,
                    x,
                    y,
                    0.0F,
                    0.0F,
                    16,
                    16,
                    1254,
                    1254,
                    1254,
                    1254,
                    applyAlpha(-1, this.openProgress));
            return true;
        } else if (icon != null && !icon.isEmpty()) {
            context.drawItem(icon, x, y);
            return true;
        } else {
            return false;
        }
    }

    private void drawBrandLogo(DrawContext context, int x, int y, int size) {
        boolean large = size >= 28;
        Identifier texture = large ? BRAND_LOGO_56 : BRAND_LOGO_48;
        int textureSize = large ? 56 : 48;
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                0.0F,
                0.0F,
                size,
                size,
                textureSize,
                textureSize,
                textureSize,
                textureSize,
                applyAlpha(-1, this.openProgress));
    }

    private List<ClickGuiScreen.SettingEntry> settings(Module module) {
        int hash = 1;

        for (SettingGroup group : module.settings) {
            hash = 31 * hash + group.name.hashCode();

            for (Setting<?> setting : group) {
                hash = 31 * hash + System.identityHashCode(setting);
                hash = 31 * hash + (setting.isVisible() ? 1 : 0);
            }
        }

        if (module == this.cachedSettingsModule && hash == this.cachedSettingsHash) {
            return this.cachedSettings;
        } else {
            List<ClickGuiScreen.SettingEntry> entries = new ArrayList<>();
            List<ClickGuiScreen.SettingEntry> extraKeybinds = new ArrayList<>();
            List<ClickGuiScreen.SettingEntry> moduleKeybinds = new ArrayList<>();

            for (SettingGroup visibleGroup : module.settings) {
                for (Setting<?> visibleSetting : visibleGroup) {
                    if (visibleSetting.isVisible()) {
                        if (visibleSetting instanceof KeybindSetting) {
                            if (visibleSetting == module.keybind) {
                                moduleKeybinds.add(
                                        new ClickGuiScreen.SettingEntry(
                                                "Controls", visibleSetting));
                            } else {
                                extraKeybinds.add(
                                        new ClickGuiScreen.SettingEntry(
                                                "Controls", visibleSetting));
                            }
                        } else {
                            entries.add(
                                    new ClickGuiScreen.SettingEntry(
                                            visibleGroup.name, visibleSetting));
                        }
                    }
                }
            }

            entries.addAll(extraKeybinds);
            entries.addAll(moduleKeybinds);
            this.cachedSettingsModule = module;
            this.cachedSettingsHash = hash;
            this.cachedSettings = List.copyOf(entries);
            return this.cachedSettings;
        }
    }

    private static int countEnabled(List<Module> modules) {
        int count = 0;

        for (Module module : modules) {
            if (module.isActive()) {
                count++;
            }
        }

        return count;
    }

    private void openColoredBlocksSelector(BlockColorMapSetting setting) {
        this.selectorMode = ClickGuiScreen.SelectorMode.COLORED_BLOCKS;
        this.coloredBlocksSetting = setting;
        this.blockListSetting = null;
        this.entityListSetting = null;
        this.pendingBlockColors = copyBlockColors(setting.getValue());
        this.pendingBlocks = null;
        this.pendingEntities = null;
        this.pendingEntityColors = null;
        this.resetSelectorState();
    }

    private void openBlockSelector(BlockListSetting setting) {
        this.selectorMode = ClickGuiScreen.SelectorMode.BLOCKS;
        this.coloredBlocksSetting = null;
        this.blockListSetting = setting;
        this.entityListSetting = null;
        this.pendingBlockColors = null;
        this.pendingBlocks = new ArrayList<>(setting.getValue());
        this.pendingEntities = null;
        this.pendingEntityColors = null;
        this.resetSelectorState();
    }

    private void openEntitySelector(EntityTypeListSetting setting) {
        this.selectorMode = ClickGuiScreen.SelectorMode.ENTITIES;
        this.coloredBlocksSetting = null;
        this.blockListSetting = null;
        this.entityListSetting = setting;
        this.pendingBlockColors = null;
        this.pendingBlocks = null;
        this.pendingEntities = new ArrayList<>(setting.getValue());
        this.pendingEntityColors = new LinkedHashMap<>();

        for (EntityType<?> entityType : this.pendingEntities) {
            EntityColor color = setting.getColor(entityType);
            if (color != null) {
                this.pendingEntityColors.put(entityType, copyColor(color));
            }
        }

        this.resetSelectorState();
    }

    private void resetSelectorState() {
        this.selectorSearchText = "";
        this.selectorShowSelectedOnly = false;
        this.selectorScrollAnimated = 0.0F;
        this.selectorScrollTarget = 0.0F;
        this.selectorProgress = 0.0F;
        this.selectorDirty = true;
    }

    private void closeSelector() {
        this.selectorMode = ClickGuiScreen.SelectorMode.NONE;
        this.coloredBlocksSetting = null;
        this.blockListSetting = null;
        this.entityListSetting = null;
        this.pendingBlockColors = null;
        this.pendingBlocks = null;
        this.pendingEntities = null;
        this.pendingEntityColors = null;
        this.selectorEntries = List.of();
        this.selectorItemHits.clear();
    }

    private void cancelSelector() {
        if (this.selectorMode == ClickGuiScreen.SelectorMode.COLORED_BLOCKS
                && this.coloredBlocksSetting != null
                && this.pendingBlockColors != null) {
            this.coloredBlocksSetting.setValue(copyBlockColors(this.pendingBlockColors));
        } else if (this.selectorMode == ClickGuiScreen.SelectorMode.BLOCKS
                && this.blockListSetting != null
                && this.pendingBlocks != null) {
            this.blockListSetting.setValue(new ArrayList<>(this.pendingBlocks));
        } else if (this.selectorMode == ClickGuiScreen.SelectorMode.ENTITIES
                && this.entityListSetting != null
                && this.pendingEntities != null) {
            this.entityListSetting.setValue(new ArrayList<>(this.pendingEntities));
            if (this.pendingEntityColors != null) {
                for (Entry<EntityType<?>, EntityColor> entry :
                        this.pendingEntityColors.entrySet()) {
                    this.entityListSetting.setColor(entry.getKey(), copyColor(entry.getValue()));
                }
            }
        }

        this.closeSelector();
    }

    private void clearSelector() {
        if (this.selectorMode == ClickGuiScreen.SelectorMode.COLORED_BLOCKS
                && this.coloredBlocksSetting != null) {
            this.coloredBlocksSetting.setValue(new LinkedHashMap<>());
        } else if (this.selectorMode == ClickGuiScreen.SelectorMode.BLOCKS
                && this.blockListSetting != null) {
            this.blockListSetting.setValue(new ArrayList<>());
        } else if (this.selectorMode == ClickGuiScreen.SelectorMode.ENTITIES
                && this.entityListSetting != null) {
            this.entityListSetting.setValue(new ArrayList<>());
        }

        this.selectorDirty = true;
        this.selectorScrollTarget = 0.0F;
    }

    private int getSelectedCount() {
        return switch (this.selectorMode) {
            case COLORED_BLOCKS ->
                    this.coloredBlocksSetting == null
                            ? 0
                            : this.coloredBlocksSetting.getValue().size();
            case BLOCKS ->
                    this.blockListSetting == null ? 0 : this.blockListSetting.getValue().size();
            case ENTITIES ->
                    this.entityListSetting == null ? 0 : this.entityListSetting.getValue().size();
            default -> 0;
        };
    }

    private static Map<Block, EntityColor> copyBlockColors(Map<Block, EntityColor> colors) {
        Map<Block, EntityColor> copy = new LinkedHashMap<>();
        if (colors != null) {
            for (Entry<Block, EntityColor> entry : colors.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), copyColor(entry.getValue()));
                }
            }
        }

        return copy;
    }

    private static EntityColor copyColor(EntityColor color) {
        return new EntityColor(color.r, color.g, color.b, color.a);
    }

    private void refreshSelectorEntries() {
        if (this.selectorDirty) {
            String query = this.selectorSearchText.strip().toLowerCase(Locale.ROOT);
            List<Object> result = new ArrayList<>();
            if (this.selectorMode == ClickGuiScreen.SelectorMode.ENTITIES
                    && this.entityListSetting != null) {
                for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
                    if (this.entityListSetting.isAllowed(entityType)
                            && SpawnEggItem.forEntity(entityType) != null
                            && (!this.selectorShowSelectedOnly
                                    || this.entityListSetting.isSelected(entityType))
                            && (query.isEmpty()
                                    || getEntityDisplayName(entityType)
                                            .toLowerCase(Locale.ROOT)
                                            .contains(query)
                                    || getEntityId(entityType).contains(query))) {
                        result.add(entityType);
                    }
                }
            } else {
                for (Block block : Registries.BLOCK) {
                    if (block != Blocks.AIR) {
                        ItemStack stack = new ItemStack(block.asItem());
                        if (!stack.isEmpty()
                                && (this.selectorMode != ClickGuiScreen.SelectorMode.BLOCKS
                                        || this.blockListSetting == null
                                        || this.blockListSetting.isAllowed(block))
                                && (!this.selectorShowSelectedOnly || this.isSelected(block))
                                && (query.isEmpty()
                                        || getBlockDisplayName(block)
                                                .toLowerCase(Locale.ROOT)
                                                .contains(query)
                                        || getBlockId(block).contains(query))) {
                            result.add(block);
                        }
                    }
                }
            }

            result.sort(
                    Comparator.comparing(this::getEntryDisplayName, String.CASE_INSENSITIVE_ORDER));
            this.selectorEntries = List.copyOf(result);
            this.selectorDirty = false;
        }
    }

    private boolean isSelected(Object item) {
        if (item instanceof Block block) {
            return this.selectorMode == ClickGuiScreen.SelectorMode.COLORED_BLOCKS
                    ? this.coloredBlocksSetting != null && this.coloredBlocksSetting.hasBlock(block)
                    : this.blockListSetting != null
                            && this.blockListSetting.getValue().contains(block);
        } else {
            return item instanceof EntityType entityType
                    && this.entityListSetting != null
                    && this.entityListSetting.isSelected(entityType);
        }
    }

    private int getEntryColor(Object item) {
        EntityColor color = null;
        if (item instanceof Block block && this.coloredBlocksSetting != null) {
            color = this.coloredBlocksSetting.getColor(block);
        } else if (item instanceof EntityType entityType && this.entityListSetting != null) {
            color = this.entityListSetting.getColor(entityType);
        }

        return color == null ? accentColor : toArgb(color);
    }

    private void drawEntryIcon(DrawContext context, Object item, int x, int y) {
        ItemStack stack = null;
        if (item instanceof Block block) {
            stack = new ItemStack(block.asItem());
        } else if (item instanceof EntityType entityType) {
            SpawnEggItem egg = SpawnEggItem.forEntity(entityType);
            if (egg != null) {
                stack = new ItemStack(egg);
            }
        }

        if (stack != null && !stack.isEmpty()) {
            this.drawScaledItem(context, stack, x, y, 1.0F);
        }
    }

    private void drawScaledItem(DrawContext context, ItemStack stack, int x, int y, float scale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    private String getEntryDisplayName(Object item) {
        return item instanceof Block block
                ? getBlockDisplayName(block)
                : getEntityDisplayName((EntityType<?>) item);
    }

    private String getEntryId(Object item) {
        return item instanceof Block block ? getBlockId(block) : getEntityId((EntityType<?>) item);
    }

    private static String getBlockDisplayName(Block block) {
        ItemStack stack = new ItemStack(block.asItem());
        return stack.isEmpty()
                ? title(getBlockId(block).substring(getBlockId(block).indexOf(58) + 1))
                : stack.getName().getString();
    }

    private static String getBlockId(Block block) {
        return Registries.BLOCK.getId(block).toString();
    }

    private static String getEntityDisplayName(EntityType<?> entityType) {
        return entityType.getName().getString();
    }

    private static String getEntityId(EntityType<?> entityType) {
        return Registries.ENTITY_TYPE.getId(entityType).toString();
    }

    private void openColorPicker(ColorSetting setting) {
        this.clearPickerTarget();
        this.pickerColorSetting = setting;
        this.beginColorEdit(setting.getValue());
    }

    private void openBlockColorPicker(BlockColorMapSetting setting, Block block) {
        this.clearPickerTarget();
        this.pickerBlockColorsSetting = setting;
        this.pickerBlock = block;
        EntityColor color = setting.getColor(block);
        this.beginColorEdit(color == null ? getAccentMutableColor() : color);
    }

    private void openEntityColorPicker(EntityTypeListSetting setting, EntityType<?> entityType) {
        this.clearPickerTarget();
        this.pickerEntitySetting = setting;
        this.pickerEntityType = entityType;
        EntityColor color = setting.getColor(entityType);
        this.beginColorEdit(color == null ? EntityTypeListSetting.defaultColor(entityType) : color);
    }

    private void beginColorEdit(EntityColor color) {
        this.pickerOriginalColor = new EntityColor(color.r, color.g, color.b, color.a);
        float[] hsb = Color.RGBtoHSB(color.r, color.g, color.b, null);
        this.pickerHue = hsb[0];
        this.pickerSaturation = hsb[1];
        this.pickerBrightness = hsb[2];
        this.pickerOpacity = color.a / 255.0F;
        this.pickerProgress = 0.0F;
    }

    private void updatePickerFromMouse(double x, double y) {
        if (this.pickerDragTarget == 1) {
            this.pickerSaturation =
                    clamp(
                            (float) ((x - this.pickerAreaRect.x) / this.pickerAreaRect.w),
                            0.0F,
                            1.0F);
            this.pickerBrightness =
                    1.0F
                            - clamp(
                                    (float) ((y - this.pickerAreaRect.y) / this.pickerAreaRect.h),
                                    0.0F,
                                    1.0F);
        } else if (this.pickerDragTarget == 2) {
            this.pickerHue =
                    clamp((float) ((y - this.pickerHueRect.y) / this.pickerHueRect.h), 0.0F, 1.0F);
        } else if (this.pickerDragTarget == 3) {
            this.pickerOpacity =
                    clamp(
                            (float) ((x - this.pickerOpacityRect.x) / this.pickerOpacityRect.w),
                            0.0F,
                            1.0F);
        }

        this.applyPickerColor();
    }

    private void setPickerColorRgb(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.pickerHue = hsb[0];
        this.pickerSaturation = hsb[1];
        this.pickerBrightness = hsb[2];
        this.applyPickerColor();
    }

    private void applyPickerColor() {
        this.applyColor(this.getPickerColor());
    }

    private void applyColor(EntityColor color) {
        if (this.pickerColorSetting != null) {
            this.pickerColorSetting.setValue(color);
        } else if (this.pickerBlockColorsSetting != null && this.pickerBlock != null) {
            this.pickerBlockColorsSetting.setColor(this.pickerBlock, color);
        } else if (this.pickerEntitySetting != null && this.pickerEntityType != null) {
            this.pickerEntitySetting.setColor(this.pickerEntityType, color);
        } else if (this.pickerDirectColor != null) {
            this.pickerDirectColor.r = color.r;
            this.pickerDirectColor.g = color.g;
            this.pickerDirectColor.b = color.b;
            this.pickerDirectColor.a = color.a;
        }
    }

    private EntityColor getPickerColor() {
        int rgb = Color.HSBtoRGB(this.pickerHue, this.pickerSaturation, this.pickerBrightness);
        return new EntityColor(
                rgb >> 16 & 0xFF,
                rgb >> 8 & 0xFF,
                rgb & 0xFF,
                Math.round(this.pickerOpacity * 255.0F));
    }

    private boolean isColorPickerOpen() {
        return this.pickerColorSetting != null
                || this.pickerBlockColorsSetting != null
                || this.pickerEntitySetting != null
                || this.pickerDirectColor != null;
    }

    private void confirmColorPicker() {
        this.clearPickerTarget();
        this.pickerDragTarget = 0;
    }

    private void cancelColorPicker() {
        if (this.isColorPickerOpen() && this.pickerOriginalColor != null) {
            this.applyColor(
                    new EntityColor(
                            this.pickerOriginalColor.r,
                            this.pickerOriginalColor.g,
                            this.pickerOriginalColor.b,
                            this.pickerOriginalColor.a));
        }

        this.confirmColorPicker();
    }

    private void clearPickerTarget() {
        this.pickerColorSetting = null;
        this.pickerBlockColorsSetting = null;
        this.pickerBlock = null;
        this.pickerEntitySetting = null;
        this.pickerEntityType = null;
        this.pickerDirectColor = null;
        this.pickerOriginalColor = null;
    }

    private void updateSliderFromMouse(double x) {
        if (this.draggedSetting != null) {
            float progress =
                    clamp(
                            (float) ((x - this.draggedSliderRect.x) / this.draggedSliderRect.w),
                            0.0F,
                            1.0F);
            if (this.draggedSetting instanceof IntSetting intSetting) {
                int intValue =
                        Math.round(
                                intSetting.sliderMin
                                        + progress * (intSetting.sliderMax - intSetting.sliderMin));
                intSetting.setValue(Math.max(intSetting.min, Math.min(intSetting.max, intValue)));
            } else if (this.draggedSetting instanceof NumberSetting numberSetting) {
                double numberValue =
                        numberSetting.sliderMin
                                + progress * (numberSetting.sliderMax - numberSetting.sliderMin);
                double scale = Math.pow(10.0, getDecimalPlaces(numberSetting));
                numberValue = Math.round(numberValue * scale) / scale;
                numberSetting.setValue(
                        Math.max(numberSetting.min, Math.min(numberSetting.max, numberValue)));
            }
        }
    }

    private static int getDecimalPlaces(NumberSetting setting) {
        return !"break-delay".equals(setting.name) && !"place-delay".equals(setting.name)
                ? Math.max(0, Math.min(6, setting.decimals))
                : 3;
    }

    private void cycleModeSetting(EnumSetting<?> setting, int step) {
        List<String> options = setting.a_();
        if (!options.isEmpty()) {
            int current = options.indexOf(String.valueOf(setting.getValue()));
            int next = Math.floorMod(current + step, options.size());
            setting.setValueFromString(options.get(next));
        }
    }

    private void beginTextEdit(Setting<?> setting) {
        this.endTextEdit(true);
        this.editingSetting = setting;
        if (setting instanceof StringListSetting listSetting) {
            this.editBuffer = String.join(",", listSetting.getValue());
        } else if (setting instanceof MapSetting mapSetting) {
            this.editBuffer = mapSetting.serialize();
        } else {
            this.editBuffer = String.valueOf(setting.getValue());
        }
    }

    private void endTextEdit(boolean save) {
        if (this.editingSetting != null && save) {
            this.editingSetting.setValueFromString(this.editBuffer);
        }

        this.editingSetting = null;
        this.editBuffer = "";
    }

    private float animateHover(Object key, boolean hovered) {
        float target = hovered ? 1.0F : 0.0F;
        float value =
                ThemeModule.areAnimationsEnabled()
                        ? smoothTowards(
                                this.hoverAnimations.getOrDefault(key, 0.0F),
                                target,
                                9.5F,
                                this.frameDelta)
                        : target;
        this.hoverAnimations.put(key, value);
        return value;
    }

    private float animateModuleActive(Module module, float speed) {
        float target = module.isActive() ? 1.0F : 0.0F;
        float value =
                ThemeModule.areAnimationsEnabled()
                        ? smoothTowards(
                                this.moduleActiveAnimations.getOrDefault(module, target),
                                target,
                                speed,
                                this.frameDelta)
                        : target;
        this.moduleActiveAnimations.put(module, value);
        return value;
    }

    private static boolean isCursorVisible() {
        return !ThemeModule.areAnimationsEnabled()
                || (System.currentTimeMillis() / 450L & 1L) == 0L;
    }

    private void drawToggleSwitch(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            Object key,
            float target,
            boolean hovered) {
        float progress = this.toggleAnimations.getOrDefault(key, clamp(target, 0.0F, 1.0F));
        progress =
                ThemeModule.areAnimationsEnabled()
                        ? smoothTowards(progress, clamp(target, 0.0F, 1.0F), 10.0F, this.frameDelta)
                        : clamp(target, 0.0F, 1.0F);
        this.toggleAnimations.put(key, progress);
        float eased = easeOutCubic(progress);
        int fill = lerpColor(hovered ? -13289141 : -14078660, accentColor, eased * 0.42F);
        DashboardRenderer.drawRoundedRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                rect.h / 2,
                applyAlpha(fill, this.openProgress));
        int knobX = rect.x + 2 + Math.round((rect.w - rect.h) * eased);
        DashboardRenderer.drawRoundedRect(
                context,
                knobX,
                rect.y + 2,
                rect.h - 4,
                rect.h - 4,
                (rect.h - 4) / 2,
                applyAlpha(-789256, this.openProgress));
    }

    private void drawSlider(DrawContext context, ClickGuiScreen.Rect rect, float progress) {
        progress = clamp(progress, 0.0F, 1.0F);
        DashboardRenderer.drawRoundedRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                3,
                applyAlpha(-14078660, this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context,
                rect.x,
                rect.y,
                Math.max(4, Math.round(rect.w * progress)),
                rect.h,
                3,
                applyAlpha(accentColor, this.openProgress));
        int knobX = rect.x + Math.round(rect.w * progress);
        float radius = (rect.h + 4) * 0.5F;
        CircleRenderer.drawCircle(
                context,
                knobX,
                rect.y + rect.h * 0.5F,
                radius,
                applyAlpha(-789256, this.openProgress));
    }

    private void drawBulkActionButton(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String label,
            int mouseX,
            int mouseY,
            int color) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = this.animateHover("action:" + label, hovered);
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                5,
                lerpColor(-15394777, -266657488, hover * 0.3F),
                1882471762,
                color,
                hover * 0.34F);
        DashboardRenderer.drawRoundedRect(
                context, rect.x + 8, rect.y + 8, 6, 6, 3, applyAlpha(color, this.openProgress));
        this.drawText(context, label, rect.x + 21, rect.y + 8, hovered ? -789256 : -2040342);
    }

    private void drawSmallButton(
            DrawContext context,
            ClickGuiScreen.Rect rect,
            String label,
            boolean hovered,
            int color) {
        this.drawGlowRect(
                context,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                5,
                hovered ? lerpColor(-15328987, -266657488, 0.32F) : -15328987,
                1882471762,
                color,
                hovered ? 0.34F : 0.0F);
        this.drawCenteredText(
                context,
                this.ellipsize(label, rect.w - 8),
                rect.x + rect.w / 2,
                rect.y + (rect.h - 8) / 2,
                hovered ? -789256 : -6182984);
    }

    private void drawScrollbar(
            DrawContext context,
            int x,
            int y,
            int height,
            float scroll,
            float maxScroll,
            int contentHeight) {
        int thumbHeight =
                Math.max(
                        18,
                        Math.round(height * ((float) height / Math.max(height, contentHeight))));
        int travel = height - thumbHeight;
        int thumbY = y + Math.round(travel * clamp(scroll / Math.max(1.0F, maxScroll), 0.0F, 1.0F));
        DashboardRenderer.drawRoundedRect(
                context, x, y, 2, height, 1, applyAlpha(1714698313, this.openProgress));
        DashboardRenderer.drawRoundedRect(
                context, x, thumbY, 2, thumbHeight, 1, applyAlpha(accentColor, this.openProgress));
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawBoldText(
                context,
                this.textRenderer,
                text == null ? "" : text,
                x,
                y,
                applyAlpha(color, this.openProgress));
    }

    private void drawTextFitted(
            DrawContext context, String text, int x, int y, int maxWidth, int color) {
        String safeText = text == null ? "" : text;
        int textWidth = DashboardRenderer.width(this.textRenderer, safeText);
        if (textWidth <= Math.max(1, maxWidth)) {
            this.drawText(context, safeText, x, y, color);
        } else {
            float scale = Math.max(0.1F, (float) maxWidth / Math.max(1, textWidth));
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(scale, scale);

            try {
                DashboardRenderer.drawBoldText(
                        context,
                        this.textRenderer,
                        safeText,
                        0,
                        0,
                        applyAlpha(color, this.openProgress));
            } finally {
                matrices.popMatrix();
            }
        }
    }

    private void drawSmallText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawBoldSmallText(
                context,
                this.textRenderer,
                text == null ? "" : text,
                x,
                y,
                applyAlpha(color, this.openProgress));
    }

    private void drawSmallTextCentered(DrawContext context, String text, int x, int y, int color) {
        String safeText = text == null ? "" : text;
        DashboardRenderer.drawBoldSmallText(
                context,
                this.textRenderer,
                safeText,
                x - DashboardRenderer.boldSmallWidth(this.textRenderer, safeText) / 2,
                y,
                applyAlpha(color, this.openProgress));
    }

    private void drawCenteredText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawCenteredBoldText(
                context,
                this.textRenderer,
                text == null ? "" : text,
                x,
                y,
                applyAlpha(color, this.openProgress));
    }

    private void drawRightAlignedText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawRightAlignedBoldText(
                context,
                this.textRenderer,
                text == null ? "" : text,
                x,
                y,
                applyAlpha(color, this.openProgress));
    }

    private String ellipsize(String text, int maxWidth) {
        if (text == null) {
            return "";
        } else if (DashboardRenderer.width(this.textRenderer, text) <= maxWidth) {
            return text;
        } else {
            String suffix = "...";
            int length = text.length();

            while (length > 0
                    && DashboardRenderer.width(
                                    this.textRenderer, text.substring(0, length) + suffix)
                            > maxWidth) {
                length--;
            }

            return text.substring(0, length) + suffix;
        }
    }

    private String ellipsizeSmall(String text, int maxWidth) {
        if (text == null) {
            return "";
        } else if (DashboardRenderer.boldSmallWidth(this.textRenderer, text) <= maxWidth) {
            return text;
        } else {
            String suffix = "...";
            int length = text.length();

            while (length > 0
                    && DashboardRenderer.boldSmallWidth(
                                    this.textRenderer,
                                    text.substring(0, length).stripTrailing() + suffix)
                            > maxWidth) {
                length--;
            }

            return text.substring(0, length).stripTrailing() + suffix;
        }
    }

    private List<String> wrapText(String text, int maxWidth, int maxLines) {
        String normalized = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (!normalized.isEmpty() && maxLines > 0) {
            String[] words = normalized.split(" ");
            List<String> lines = new ArrayList<>();
            int index = 0;

            while (index < words.length && lines.size() < maxLines) {
                StringBuilder line = new StringBuilder();

                while (index < words.length) {
                    String candidate = line.isEmpty() ? words[index] : line + " " + words[index];
                    if (!line.isEmpty()
                            && DashboardRenderer.boldSmallWidth(this.textRenderer, candidate)
                                    > maxWidth) {
                        break;
                    }

                    line.setLength(0);
                    line.append(candidate);
                    index++;
                    if (DashboardRenderer.boldSmallWidth(this.textRenderer, line.toString())
                            > maxWidth) {
                        break;
                    }
                }

                if (lines.size() == maxLines - 1 && index < words.length) {
                    StringBuilder rest = new StringBuilder(line);

                    while (index < words.length) {
                        rest.append(' ').append(words[index++]);
                    }

                    lines.add(this.ellipsizeSmall(rest.toString(), maxWidth));
                } else {
                    lines.add(this.ellipsizeSmall(line.toString(), maxWidth));
                }
            }

            return lines;
        } else {
            return List.of();
        }
    }

    private void drawGlowRect(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int fill,
            int border,
            int glowColor,
            float glowStrength) {
        float strength = clamp(glowStrength, 0.0F, 1.0F);
        this.drawGlow(context, x, y, width, height, radius, glowColor, strength);
        DashboardRenderer.drawRoundedRect(
                context, x, y, width, height, radius, applyAlpha(fill, this.openProgress));
    }

    private void drawGlow(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int color,
            float strength) {
        if (!(strength <= 0.002F) && width > 0 && height > 0) {
            int margin = 12;
            int outerWidth = width + margin * 2;
            int outerHeight = height + margin * 2;
            int corner =
                    Math.min(Math.min(outerWidth, outerHeight) / 2, margin + Math.max(3, radius));
            int middleWidth = Math.max(0, outerWidth - corner * 2);
            int middleHeight = Math.max(0, outerHeight - corner * 2);
            int alpha =
                    Math.min(
                            255,
                            Math.round(255.0F * (float) Math.sqrt(strength) * this.openProgress));
            int tint = alpha << 24 | color & 16777215;
            int left = x - margin;
            int top = y - margin;
            int cornerSize = 208;
            int edgeSize = 96;
            int farOffset = 304;
            this.drawGlowSlice(
                    context, left, top, corner, corner, 0, 0, cornerSize, cornerSize, tint);
            this.drawGlowSlice(
                    context,
                    left + corner,
                    top,
                    middleWidth,
                    corner,
                    cornerSize,
                    0,
                    edgeSize,
                    cornerSize,
                    tint);
            this.drawGlowSlice(
                    context,
                    left + corner + middleWidth,
                    top,
                    corner,
                    corner,
                    farOffset,
                    0,
                    cornerSize,
                    cornerSize,
                    tint);
            this.drawGlowSlice(
                    context,
                    left,
                    top + corner,
                    corner,
                    middleHeight,
                    0,
                    cornerSize,
                    cornerSize,
                    edgeSize,
                    tint);
            this.drawGlowSlice(
                    context,
                    left + corner,
                    top + corner,
                    middleWidth,
                    middleHeight,
                    cornerSize,
                    cornerSize,
                    edgeSize,
                    edgeSize,
                    tint);
            this.drawGlowSlice(
                    context,
                    left + corner + middleWidth,
                    top + corner,
                    corner,
                    middleHeight,
                    farOffset,
                    cornerSize,
                    cornerSize,
                    edgeSize,
                    tint);
            this.drawGlowSlice(
                    context,
                    left,
                    top + corner + middleHeight,
                    corner,
                    corner,
                    0,
                    farOffset,
                    cornerSize,
                    cornerSize,
                    tint);
            this.drawGlowSlice(
                    context,
                    left + corner,
                    top + corner + middleHeight,
                    middleWidth,
                    corner,
                    cornerSize,
                    farOffset,
                    edgeSize,
                    cornerSize,
                    tint);
            this.drawGlowSlice(
                    context,
                    left + corner + middleWidth,
                    top + corner + middleHeight,
                    corner,
                    corner,
                    farOffset,
                    farOffset,
                    cornerSize,
                    cornerSize,
                    tint);
        }
    }

    private void drawGlowSlice(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int u,
            int v,
            int regionWidth,
            int regionHeight,
            int color) {
        if (width > 0 && height > 0) {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    GLOW_TEXTURE,
                    x,
                    y,
                    u,
                    v,
                    width,
                    height,
                    regionWidth,
                    regionHeight,
                    512,
                    512,
                    color);
        }
    }

    private static void drawHorizontalGradient(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int startColor,
            int endColor) {
        int steps = Math.min(64, Math.max(1, width));

        for (int i = 0; i < steps; i++) {
            int left = x + i * width / steps;
            int right = x + (i + 1) * width / steps;
            context.fill(
                    left,
                    y,
                    right,
                    y + height,
                    lerpColor(startColor, endColor, (float) i / Math.max(1, steps - 1)));
        }
    }

    private void drawBottomGlow(DrawContext context, int x, int y, int width, int height) {
        int steps = Math.min(96, Math.max(24, width / 4));
        int midY = y + Math.round(height * 0.72F);
        int bottom = y + height;

        for (int i = 0; i < steps; i++) {
            int left = x + i * width / steps;
            int right = x + (i + 1) * width / steps;
            float t = (i + 0.5F) / steps;
            float wave = (float) Math.sin(3.141592653589793 * t);
            wave *= wave;
            int alpha = Math.round(78.0F * wave * this.openProgress);
            int transparent = accentColor & 16777215;
            int tinted = alpha << 24 | accentColor & 16777215;
            context.fillGradient(left, y, right, midY, transparent, tinted);
            context.fillGradient(left, midY, right, bottom, tinted, transparent);
        }
    }

    private static String getModuleCategory(Module module) {
        String category = module.category == null ? "" : lower(module.category.name);
        if (!category.equals("combat")
                && !category.equals("misc")
                && !category.equals("donut")
                && !category.equals("render")
                && !category.equals("client")) {
            String name = lower(module.name);

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
                case "hud", "discord-presence", "chat-macros" -> "client";
                default -> "misc";
            };
        } else {
            return category;
        }
    }

    private static int getClientModuleOrder(Module module) {
        String name = lower(module.name);

        return switch (name) {
            case "hud" -> 0;
            case "discord-presence" -> 1;
            default -> 2;
        };
    }

    private static String getCategoryIconName(String category) {
        String key = category.toLowerCase(Locale.ROOT);

        return switch (key) {
            case "combat" -> "diamond_sword";
            case "donut" -> "cookie";
            case "movement" -> "feather";
            case "player" -> "player_head";
            case "render" -> "spyglass";
            case "world" -> "grass_block";
            case "client" -> "comparator";
            case "exploits" -> "redstone_torch";
            default -> "bundle";
        };
    }

    private static String getModuleIconName(String moduleName) {
        String key = moduleName.toLowerCase(Locale.ROOT);

        return switch (key) {
            case "auto-totem", "hover-totem", "auto-inv-totem" -> "totem_of_undying";
            case "shield-breaker" -> "iron_axe";
            case "triggerbot", "aim-assist" -> "target";
            case "mace-swap" -> "mace";
            case "crystal-optimizer", "auto-crystal" -> "end_crystal";
            case "safe-anchor", "anchor-macro" -> "respawn_anchor";
            case "freecam", "zoom" -> "spyglass";
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
            case "inventory-hud" -> "chest";
            case "storage-esp" -> "chest";
            case "bedrock-hole-esp" -> "bedrock";
            case "hole-esp" -> "ladder";
            case "chunkfinder" -> "map";
            case "jump-circles" -> "feather";
            case "fullbright" -> "glowstone";
            case "sky-arts" -> "painting";
            case "china-head" -> "hay_block";
            case "block-entity-debug" -> "structure_block";
            case "light-finder" -> "torch";
            case "suspicious-esp" -> "suspicious_sand";
            case "mob-esp" -> "zombie_spawn_egg";
            case "spawner-flagger" -> "spawner";
            case "block-notifier", "odinclient-block-notifier", "player-notify" -> "bell";
            case "weather-notifier" -> "lightning_rod";
            case "amethyst-esp" -> "amethyst_cluster";
            case "region-map" -> "filled_map";
            case "sus-chunk-finder", "player-chunk", "player-debug-v2" -> "recovery_compass";
            case "theme" -> "purple_dye";
            case "hud" -> "item_frame";
            case "discord-presence" -> "redstone";
            case "spotify-hud" -> "jukebox";
            case "spawner-protect" -> "trial_key";
            case "fake-stats" -> "book";
            case "cord-snapper" -> "shears";
            case "chat-macros" -> "writable_book";
            case "staf-tracker" -> "writable_book";
            default -> "nether_star";
        };
    }

    private static boolean matchesSearch(Module module, String query) {
        return lower(module.name).contains(query)
                || lower(module.title).contains(query)
                || lower(getModuleDescription(module)).contains(query)
                || getModuleCategory(module).contains(query);
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static String title(String text) {
        if (text != null && !text.isBlank()) {
            StringBuilder builder = new StringBuilder();
            boolean capitalizeNext = true;

            for (char c : text.toCharArray()) {
                if (c != '-' && c != '_') {
                    builder.append(capitalizeNext ? Character.toUpperCase(c) : c);
                    capitalizeNext = false;
                } else {
                    builder.append(' ');
                    capitalizeNext = true;
                }
            }

            return builder.toString();
        } else {
            return "General";
        }
    }

    private static String getInitial(String text) {
        return text != null && !text.isBlank() ? title(text).substring(0, 1) : "O";
    }

    private static String getCategoryDescription(String category) {
        if (category == null) {
            return "Client controls and modules.";
        } else {
            String key = category.toLowerCase(Locale.ROOT);

            return switch (key) {
                case "combat" -> "Combat tools, timing and protection.";
                case "misc" -> "General utilities, movement and convenience.";
                case "donut", "donutsmp" -> "Utilities tuned for DonutSMP.";
                case "movement" -> "Move faster and control your camera.";
                case "player" -> "Player automation and convenience.";
                case "render" -> "Visual overlays, ESP and world clarity.";
                case "world" -> "World scanners and block utilities.";
                case "client" -> "OdinClient appearance and integration.";
                case "exploits" -> "Advanced server interaction tools.";
                default -> "Configure all modules in this category.";
            };
        }
    }

    private static int getSettingRowHeight(Setting<?> setting) {
        if (setting instanceof IntSetting || setting instanceof NumberSetting) {
            return 52;
        } else {
            return !(setting instanceof StringSetting)
                            && !(setting instanceof StringListSetting)
                            && !(setting instanceof MapSetting)
                    ? 42
                    : 58;
        }
    }

    private static String getValueText(Setting<?> setting) {
        Object value = setting.getValue();
        if (value instanceof List<?> list) {
            return list.size() + " entries";
        } else {
            return value instanceof Map<?, ?> map ? map.size() + " entries" : String.valueOf(value);
        }
    }

    private static String getKeyName(int key) {
        if (key < 0) {
            return "NONE";
        } else if (key >= 1000 && key <= 1007) {
            return "MOUSE " + (key - 999);
        } else if ((key < 48 || key > 57) && (key < 65 || key > 90)) {
            return switch (key) {
                case 32 -> "SPACE";
                case 256 -> "ESC";
                case 257 -> "ENTER";
                case 258 -> "TAB";
                case 340 -> "L-SHIFT";
                case 341 -> "L-CTRL";
                case 342 -> "L-ALT";
                case 344 -> "R-SHIFT";
                case 345 -> "R-CTRL";
                case 346 -> "R-ALT";
                default -> "KEY " + key;
            };
        } else {
            return String.valueOf((char) key);
        }
    }

    private static String getSelectorTitle(ClickGuiScreen.SelectorMode mode) {
        return switch (mode) {
            case COLORED_BLOCKS -> "BLOCK SELECTOR";
            case BLOCKS -> "BLOCK SELECTOR";
            case ENTITIES -> "ENTITY COLORS";
            default -> "SELECTOR";
        };
    }

    private String getSelectorHeading() {
        return getSelectorTitle(this.selectorMode);
    }

    private String getSelectorSubtitle() {
        return switch (this.selectorMode) {
            case COLORED_BLOCKS -> "Choose blocks and there color";
            case BLOCKS -> "Choose which blocks should be included.";
            case ENTITIES -> "Choose which entities should use custom colors.";
            default -> "Choose entries.";
        };
    }

    private static EntityColor getAccentMutableColor() {
        return new EntityColor(
                accentColor >> 16 & 0xFF, accentColor >> 8 & 0xFF, accentColor & 0xFF, 255);
    }

    private static int toArgb(EntityColor color) {
        return color.a << 24 | color.r << 16 | color.g << 8 | color.b;
    }

    private static int alpha(int color, int value) {
        return Math.max(0, Math.min(255, value)) << 24 | color & 16777215;
    }

    private static int applyAlpha(int color, float factor) {
        int baseAlpha = color >>> 24;
        int alpha = Math.round(baseAlpha * clamp(factor, 0.0F, 1.0F));
        return alpha << 24 | color & 16777215;
    }

    private static int lerpColor(int from, int to, float t) {
        t = clamp(t, 0.0F, 1.0F);
        int fromA = from >>> 24;
        int fromR = from >> 16 & 0xFF;
        int fromG = from >> 8 & 0xFF;
        int fromB = from & 0xFF;
        int toA = to >>> 24;
        int toR = to >> 16 & 0xFF;
        int toG = to >> 8 & 0xFF;
        int toB = to & 0xFF;
        return Math.round(fromA + (toA - fromA) * t) << 24
                | Math.round(fromR + (toR - fromR) * t) << 16
                | Math.round(fromG + (toG - fromG) * t) << 8
                | Math.round(fromB + (toB - fromB) * t);
    }

    private static float smoothTowards(float current, float target, float speed, float delta) {
        float factor = 1.0F - (float) Math.exp(-speed * delta);
        return current + (target - current) * factor;
    }

    private static float easeOutCubic(float t) {
        t = clamp(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CardText(String displayTitle, String title, List<String> description) {}

    private record CategoryHit(ClickGuiScreen.Rect rect, String category) {}

    private record ModuleCardHit(
            ClickGuiScreen.Rect card,
            ClickGuiScreen.Rect toggle,
            ClickGuiScreen.Rect settings,
            Module module) {}

    private record PanelLayout(int x, int y, int w, int h, int sidebarW, int rightW) {}

    private record PresetRowHit(ClickGuiScreen.Rect row, ClickGuiScreen.Rect apply, int index) {}

    private record ProfileRowHit(
            ClickGuiScreen.Rect row,
            ClickGuiScreen.Rect load,
            ClickGuiScreen.Rect delete,
            int index) {}

    private record Rect(int x, int y, int w, int h) {
        private static final ClickGuiScreen.Rect EMPTY = new ClickGuiScreen.Rect(0, 0, 0, 0);

        boolean contains(double px, double py) {
            return this.w > 0
                    && this.h > 0
                    && px >= this.x
                    && px < this.x + this.w
                    && py >= this.y
                    && py < this.y + this.h;
        }
    }

    private record SelectorItemHit(ClickGuiScreen.Rect rect, Object item) {}

    private static enum SelectorMode {
        NONE,
        COLORED_BLOCKS,
        BLOCKS,
        ENTITIES;
    }

    private record SettingEntry(String group, Setting<?> setting) {}

    private record SettingRowHit(
            ClickGuiScreen.Rect row, ClickGuiScreen.Rect control, Setting<?> setting) {}

    private record ThemePresetHit(ClickGuiScreen.Rect rect, int index) {}
}
