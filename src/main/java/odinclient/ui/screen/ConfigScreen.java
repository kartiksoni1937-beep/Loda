package odinclient.ui.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import odinclient.manager.ConfigProfileManager;
import odinclient.ui.draw.DashboardRenderer;

import java.util.List;

public final class ConfigScreen extends Screen {
    private static final Identifier ODINCLIENT_MARK =
            Identifier.of("odinclient", "textures/gui/odinclient_mark.png");
    private static final int CYAN = -10892801;
    private static final int PURPLE = -3512589;
    private static final int TEXT = -855046;
    private static final int MUTED = -9143918;
    private static final int GREEN = -13121427;
    private static final int RED = -2601631;
    private static final int AMBER = -1990088;
    private static final int PANEL = -586346471;
    private static final int PANEL_BORDER = -1203549855;
    private static final int ROW = -1206445273;
    private static final int ROW_HOVER = -618913231;
    private final Screen parent;
    private boolean browseTab;
    private int selected = -1;
    private boolean naming;
    private String nameInput = "";
    private String status = "";
    private boolean statusGood = true;
    private long statusUntil;
    private long resetArmedUntil;

    public ConfigScreen(Screen parent) {
        super(Text.literal("OdinClient Configs"));
        this.parent = parent;
        List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();
        String activeName = ConfigProfileManager.getActiveProfileName();

        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).name.equalsIgnoreCase(activeName)) {
                this.selected = i;
                break;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        ConfigScreen.Layout bounds =
                layout(context.getScaledWindowWidth(), context.getScaledWindowHeight());
        TextRenderer font = this.textRenderer;
        context.fill(0, 0, bounds.screenW, bounds.screenH, 1979711750);
        DashboardRenderer.drawOutlinedRoundedRect(
                context, bounds.x, bounds.y, bounds.w, bounds.h, 8, -586346471, -1203549855);
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                ODINCLIENT_MARK,
                bounds.x + 15,
                bounds.y + 13,
                0.0F,
                0.0F,
                18,
                10,
                1672,
                941,
                1672,
                941,
                -1);
        DashboardRenderer.drawBoldText(
                context, font, "ODINCLIENT CONFIGS", bounds.x + 39, bounds.y + 14, -855046);
        button(
                context,
                font,
                "X",
                bounds.x + bounds.w - 31,
                bounds.y + 10,
                20,
                20,
                inside(mouseX, mouseY, bounds.x + bounds.w - 31, bounds.y + 10, 20, 20),
                -2601631);
        int tabY = bounds.y + 43;
        tab(context, font, "MY CONFIGS", bounds.x + 15, tabY, 92, !this.browseTab, mouseX, mouseY);
        tab(context, font, "BROWSE", bounds.x + 113, tabY, 74, this.browseTab, mouseX, mouseY);
        DashboardRenderer.drawRightAlignedBoldText(
                context,
                font,
                ConfigProfileManager.profiles().size() + " / 3 CONFIGS",
                bounds.x + bounds.w - 16,
                tabY + 7,
                -9143918);
        context.fill(bounds.x + 14, tabY + 27, bounds.x + bounds.w - 14, tabY + 28, 1363364449);
        if (this.browseTab) {
            this.renderBrowse(context, font, bounds, mouseX, mouseY);
        } else {
            this.renderProfiles(context, font, bounds, mouseX, mouseY);
        }

        if (!this.status.isBlank() && System.currentTimeMillis() < this.statusUntil) {
            DashboardRenderer.drawCenteredBoldText(
                    context,
                    font,
                    this.status,
                    bounds.x + bounds.w / 2,
                    bounds.y + bounds.h - 48,
                    this.statusGood ? -9509732 : -35962);
        }

        if (this.naming) {
            this.renderNaming(context, font, bounds, mouseX, mouseY);
        }
    }

    private void renderProfiles(
            DrawContext context,
            TextRenderer font,
            ConfigScreen.Layout layout,
            int mouseX,
            int mouseY) {
        List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();
        int x = layout.x + 14;
        int width = layout.w - 28;
        int startY = layout.y + 78;
        if (profiles.isEmpty()) {
            DashboardRenderer.drawCenteredBoldText(
                    context,
                    font,
                    "NO CONFIGS SAVED YET",
                    layout.x + layout.w / 2,
                    layout.y + 154,
                    -9143918);
            DashboardRenderer.drawCenteredBoldText(
                    context,
                    font,
                    "CREATE YOUR FIRST LOCAL PROFILE BELOW",
                    layout.x + layout.w / 2,
                    layout.y + 171,
                    -11183246);
        }

        for (int i = 0; i < profiles.size(); i++) {
            ConfigProfileManager.Profile profile = profiles.get(i);
            int y = startY + i * 47;
            boolean hovered = inside(mouseX, mouseY, x, y, width, 41);
            boolean active =
                    profile.name.equalsIgnoreCase(ConfigProfileManager.getActiveProfileName());
            boolean isSelected = this.selected == i;
            int borderColor =
                    active
                            ? -1286893634
                            : (isSelected ? -1487185798 : (hovered ? -1706536597 : 1144470865));
            DashboardRenderer.drawBorderedRoundedRect(
                    context, x, y, width, 41, 6, hovered ? -618913231 : -1206445273, borderColor);
            if (active || isSelected) {
                DashboardRenderer.drawRoundedRect(
                        context, x + 1, y + 5, 3, 31, 2, active ? -10892801 : -3512589);
            }

            DashboardRenderer.drawBoldText(
                    context, font, profile.name.toUpperCase(), x + 11, y + 8, -855046);
            DashboardRenderer.drawBoldText(
                    context,
                    font,
                    active ? "ACTIVE" : "SAVED CONFIG",
                    x + 11,
                    y + 23,
                    active ? -10892801 : -9143918);
            int deleteX = x + width - 61;
            int loadX = deleteX - 56;
            button(
                    context,
                    font,
                    active ? "RELOAD" : "LOAD",
                    loadX,
                    y + 11,
                    49,
                    20,
                    inside(mouseX, mouseY, loadX, y + 11, 49, 20),
                    -10892801);
            button(
                    context,
                    font,
                    "DELETE",
                    deleteX,
                    y + 11,
                    55,
                    20,
                    inside(mouseX, mouseY, deleteX, y + 11, 55, 20),
                    -2601631);
        }

        this.renderBottomBar(context, font, layout, mouseX, mouseY);
    }

    private void renderBottomBar(
            DrawContext context,
            TextRenderer font,
            ConfigScreen.Layout layout,
            int mouseX,
            int mouseY) {
        int y = layout.y + layout.h - 32;
        button(
                context,
                font,
                "SAVE CURRENT",
                layout.x + 14,
                y,
                68,
                20,
                inside(mouseX, mouseY, layout.x + 14, y, 68, 20),
                -10892801);
        button(
                context,
                font,
                "+ NEW CONFIG",
                layout.x + 88,
                y,
                72,
                20,
                inside(mouseX, mouseY, layout.x + 88, y, 72, 20),
                -3512589);
        int resetX = layout.x + (layout.w - 66) / 2;
        boolean resetArmed = System.currentTimeMillis() < this.resetArmedUntil;
        button(
                context,
                font,
                resetArmed ? "CONFIRM" : "RESET",
                resetX,
                y,
                66,
                20,
                inside(mouseX, mouseY, resetX, y, 66, 20),
                -1990088);
        int importX = layout.x + layout.w - 86;
        int shareX = importX - 71;
        button(
                context,
                font,
                "SHARE",
                shareX,
                y,
                64,
                20,
                inside(mouseX, mouseY, shareX, y, 64, 20),
                -3512589);
        button(
                context,
                font,
                "IMPORT",
                importX,
                y,
                72,
                20,
                inside(mouseX, mouseY, importX, y, 72, 20),
                -13121427);
    }

    private void renderBrowse(
            DrawContext context,
            TextRenderer font,
            ConfigScreen.Layout layout,
            int mouseX,
            int mouseY) {
        List<ConfigProfileManager.BundledConfig> configs = ConfigProfileManager.getBundledConfigs();
        if (configs.isEmpty()) {
            int centerX = layout.x + layout.w / 2;
            int centerY = layout.y + 164;
            DashboardRenderer.drawBorderedRoundedRect(
                    context, centerX - 150, centerY - 36, 300, 72, 7, -1793844957, 1799045978);
            DashboardRenderer.drawCenteredBoldText(
                    context, font, "NO BUILT-IN CONFIGS YET", centerX, centerY - 12, -855046);
            DashboardRenderer.drawCenteredBoldText(
                    context,
                    font,
                    "OFFLINE PRESETS WILL APPEAR HERE",
                    centerX,
                    centerY + 8,
                    -9143918);
        } else {
            int y = layout.y + 78;

            for (ConfigProfileManager.BundledConfig config : configs) {
                DashboardRenderer.drawBorderedRoundedRect(
                        context, layout.x + 14, y, layout.w - 28, 41, 6, -1206445273, 1715159898);
                DashboardRenderer.drawBoldText(
                        context, font, config.name.toUpperCase(), layout.x + 25, y + 8, -855046);
                DashboardRenderer.drawBoldText(
                        context, font, config.description, layout.x + 25, y + 23, -9143918);
                button(
                        context,
                        font,
                        "APPLY",
                        layout.x + layout.w - 75,
                        y + 11,
                        49,
                        20,
                        inside(mouseX, mouseY, layout.x + layout.w - 75, y + 11, 49, 20),
                        -13121427);
                y += 47;
            }
        }
    }

    private void renderNaming(
            DrawContext context,
            TextRenderer font,
            ConfigScreen.Layout layout,
            int mouseX,
            int mouseY) {
        context.fill(0, 0, layout.screenW, layout.screenH, -2080374784);
        int width = Math.min(330, layout.screenW - 30);
        int height = 120;
        int x = (layout.screenW - width) / 2;
        int y = (layout.screenH - height) / 2;
        DashboardRenderer.drawOutlinedRoundedRect(
                context, x, y, width, height, 8, -267118557, -917217154);
        DashboardRenderer.drawBoldText(context, font, "NEW CONFIG", x + 15, y + 14, -855046);
        DashboardRenderer.drawBoldText(context, font, "NAME", x + 15, y + 33, -9143918);
        DashboardRenderer.drawBorderedRoundedRect(
                context, x + 15, y + 47, width - 30, 25, 5, -16118249, -1655941519);
        String cursor = System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "";
        DashboardRenderer.drawBoldText(
                context, font, this.nameInput + cursor, x + 23, y + 55, -855046);
        button(
                context,
                font,
                "CANCEL",
                x + width - 143,
                y + 87,
                61,
                20,
                inside(mouseX, mouseY, x + width - 143, y + 87, 61, 20),
                -2601631);
        button(
                context,
                font,
                "SAVE",
                x + width - 75,
                y + 87,
                60,
                20,
                inside(mouseX, mouseY, x + width - 75, y + 87, 60, 20),
                -13121427);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return false;
        } else {
            double mouseX = click.x();
            double mouseY = click.y();
            ConfigScreen.Layout bounds = layout(this.width, this.height);
            if (this.naming) {
                int dialogWidth = Math.min(330, bounds.screenW - 30);
                int dialogHeight = 120;
                int dialogX = (bounds.screenW - dialogWidth) / 2;
                int dialogY = (bounds.screenH - dialogHeight) / 2;
                if (inside(mouseX, mouseY, dialogX + dialogWidth - 143, dialogY + 87, 61, 20)) {
                    this.naming = false;
                    this.nameInput = "";
                } else if (inside(
                        mouseX, mouseY, dialogX + dialogWidth - 75, dialogY + 87, 60, 20)) {
                    this.finishCreate();
                }

                return true;
            } else if (inside(mouseX, mouseY, bounds.x + bounds.w - 31, bounds.y + 10, 20, 20)) {
                this.close();
                return true;
            } else if (inside(mouseX, mouseY, bounds.x + 15, bounds.y + 43, 92, 27)) {
                this.browseTab = false;
                return true;
            } else if (inside(mouseX, mouseY, bounds.x + 113, bounds.y + 43, 74, 27)) {
                this.browseTab = true;
                return true;
            } else if (this.browseTab) {
                return this.handleBrowseClick(mouseX, mouseY, bounds);
            } else {
                List<ConfigProfileManager.Profile> profiles = ConfigProfileManager.profiles();
                int listX = bounds.x + 14;
                int listWidth = bounds.w - 28;
                int startY = bounds.y + 78;

                for (int i = 0; i < profiles.size(); i++) {
                    int rowY = startY + i * 47;
                    int deleteX = listX + listWidth - 61;
                    int loadX = deleteX - 56;
                    if (inside(mouseX, mouseY, loadX, rowY + 11, 49, 20)) {
                        this.selected = i;
                        this.show(ConfigProfileManager.loadProfile(i));
                        return true;
                    }

                    if (inside(mouseX, mouseY, deleteX, rowY + 11, 55, 20)) {
                        this.show(ConfigProfileManager.deleteProfile(i));
                        if (this.selected == i) {
                            this.selected = -1;
                        } else if (this.selected > i) {
                            this.selected--;
                        }

                        return true;
                    }

                    if (inside(mouseX, mouseY, listX, rowY, listWidth, 41)) {
                        this.selected = i;
                        return true;
                    }
                }

                int barY = bounds.y + bounds.h - 32;
                if (!inside(mouseX, mouseY, bounds.x + 14, barY, 68, 20)) {
                    if (inside(mouseX, mouseY, bounds.x + 88, barY, 72, 20)) {
                        this.startCreate();
                        return true;
                    } else {
                        int resetX = bounds.x + (bounds.w - 66) / 2;
                        if (inside(mouseX, mouseY, resetX, barY, 66, 20)) {
                            long now = System.currentTimeMillis();
                            if (now < this.resetArmedUntil) {
                                this.resetArmedUntil = 0L;
                                this.selected = -1;
                                this.show(ConfigProfileManager.resetToDefaults());
                            } else {
                                this.resetArmedUntil = now + 5000L;
                                this.show(
                                        ConfigProfileManager.Result.failure(
                                                "CLICK RESET AGAIN TO CONFIRM"));
                            }

                            return true;
                        } else {
                            int importX = bounds.x + bounds.w - 86;
                            int shareX = importX - 71;
                            if (inside(mouseX, mouseY, shareX, barY, 64, 20)) {
                                ConfigProfileManager.Result exported =
                                        ConfigProfileManager.exportProfile(this.selected);
                                if (exported.success && exported.code != null) {
                                    this.client.keyboard.setClipboard(exported.code);
                                }

                                this.show(exported);
                                return true;
                            } else if (inside(mouseX, mouseY, importX, barY, 72, 20)) {
                                ConfigProfileManager.Result imported =
                                        ConfigProfileManager.importCode(
                                                this.client.keyboard.getClipboard());
                                this.show(imported);
                                if (imported.success) {
                                    this.selected = ConfigProfileManager.profiles().size() - 1;
                                }

                                return true;
                            } else {
                                return true;
                            }
                        }
                    }
                } else {
                    if (this.selected >= 0 && this.selected < profiles.size()) {
                        this.show(ConfigProfileManager.updateProfile(this.selected));
                    } else {
                        this.startCreate();
                    }

                    return true;
                }
            }
        }
    }

    private boolean handleBrowseClick(double mouseX, double mouseY, ConfigScreen.Layout layout) {
        List<ConfigProfileManager.BundledConfig> configs = ConfigProfileManager.getBundledConfigs();
        int y = layout.y + 78;

        for (ConfigProfileManager.BundledConfig config : configs) {
            if (inside(mouseX, mouseY, layout.x + layout.w - 75, y + 11, 49, 20)) {
                this.show(ConfigProfileManager.importCode(config.configJson));
                if (this.statusGood) {
                    this.browseTab = false;
                    this.selected = ConfigProfileManager.profiles().size() - 1;
                }

                return true;
            }

            y += 47;
        }

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (this.naming) {
            if (key == 256) {
                this.naming = false;
                this.nameInput = "";
                return true;
            } else if (key == 257 || key == 335) {
                this.finishCreate();
                return true;
            } else if (key == 259 && !this.nameInput.isEmpty()) {
                this.nameInput = this.nameInput.substring(0, this.nameInput.length() - 1);
                return true;
            } else {
                return true;
            }
        } else if (key == 256) {
            this.close();
            return true;
        } else {
            return super.keyPressed(input);
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!this.naming) {
            return super.charTyped(input);
        } else {
            String typed = input.asString();

            for (int i = 0; i < typed.length() && this.nameInput.length() < 24; i++) {
                char c = typed.charAt(i);
                if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') {
                    this.nameInput = this.nameInput + c;
                }
            }

            return true;
        }
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    private void startCreate() {
        if (ConfigProfileManager.profiles().size() >= 3) {
            this.show(ConfigProfileManager.Result.failure("DELETE A CONFIG FIRST - MAXIMUM IS 3"));
        } else {
            this.naming = true;
            this.nameInput = "";
        }
    }

    private void finishCreate() {
        ConfigProfileManager.Result result = ConfigProfileManager.saveCurrent(this.nameInput);
        this.show(result);
        if (result.success) {
            this.selected = ConfigProfileManager.profiles().size() - 1;
            this.naming = false;
            this.nameInput = "";
        }
    }

    private void show(ConfigProfileManager.Result result) {
        this.status = result.message.toUpperCase();
        this.statusGood = result.success;
        this.statusUntil = System.currentTimeMillis() + 4200L;
    }

    private static ConfigScreen.Layout layout(int screenW, int screenH) {
        int w = Math.min(460, Math.max(420, screenW - 420));
        int h = Math.min(340, Math.max(300, screenH - 140));
        w = Math.min(w, screenW - 20);
        h = Math.min(h, screenH - 20);
        return new ConfigScreen.Layout(
                screenW, screenH, (screenW - w) / 2, (screenH - h) / 2, w, h);
    }

    private static void tab(
            DrawContext context,
            TextRenderer font,
            String label,
            int x,
            int y,
            int width,
            boolean active,
            int mouseX,
            int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, 27);
        if (active || hovered) {
            DashboardRenderer.drawRoundedRect(
                    context, x, y, width, 25, 5, active ? -1524093636 : 1696342320);
        }

        if (active) {
            DashboardRenderer.drawRoundedRect(context, x + 5, y + 24, width - 10, 2, 1, -10892801);
        }

        DashboardRenderer.drawBoldText(
                context, font, label, x + 9, y + 8, active ? -855046 : -9143918);
    }

    private static void button(
            DrawContext context,
            TextRenderer font,
            String label,
            int x,
            int y,
            int width,
            int height,
            boolean hovered,
            int accent) {
        int borderColor = hovered ? -299290816 : -652599508;
        int fillColor = hovered ? withAlpha(accent, 210) : 1547453274;
        DashboardRenderer.drawBorderedRoundedRect(
                context, x, y, width, height, 5, borderColor, fillColor);
        DashboardRenderer.drawCenteredBoldText(
                context, font, label, x + width / 2, y + 6, hovered ? -855046 : -1644303);
    }

    private static int withAlpha(int color, int alpha) {
        return alpha << 24 | color & 16777215;
    }

    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    private record Layout(int screenW, int screenH, int x, int y, int w, int h) {}
}
