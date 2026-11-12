package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import odinclient.module.hud.ThemeModule;
import odinclient.module.shortcut.ChatMacros;
import odinclient.ui.draw.BoldText;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.ScreenScaleCompat;
import odinclient.util.input.KeyNames;

import java.util.ArrayList;
import java.util.List;

public final class ChatMacroScreen extends Screen {
    private static final int MAX_WIDTH = 520;
    private static final int MAX_HEIGHT = 404;
    private static final int HEADER_HEIGHT = 52;
    private static final int LIST_TOP_GAP = 38;
    private static final int FOOTER_HEIGHT = 46;
    private static final int ROW_HEIGHT = 66;
    private static final int ROW_GAP = 7;
    private final Screen parent;
    private final ChatMacros module;
    private final boolean usePanelStyle;
    private final String[] macroTexts = new String[10];
    private final int[] macroKeys = new int[10];
    private final boolean[] active = new boolean[10];
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int editingTextIndex = -1;
    private int listeningKeyIndex = -1;
    private int scrollOffset;
    private String status = "";
    private boolean statusSuccess;
    private long statusExpiry;
    private float uiScale = 1.0F;

    private ChatMacroScreen(Screen parent, ChatMacros module) {
        super(Text.literal("Chat Macro Manager"));
        this.parent = parent;
        this.module = module;
        this.usePanelStyle = parent instanceof PanelGuiScreen || parent instanceof OdinGuiScreen;

        for (int i = 0; i < 10; i++) {
            String text = module.getMacroText(i);
            this.macroTexts[i] = text == null ? "" : text;
            this.macroKeys[i] = module.getMacroKey(i);
            this.active[i] = module.isMacroActive(i);
        }
    }

    public static void open(Screen parent, ChatMacros module) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && module != null) {
            client.setScreen(new ChatMacroScreen(parent, module));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.uiScale = ScreenScaleCompat.getScale(this.width);
        int scaledMouseX = Math.round(mouseX / this.uiScale);
        int scaledMouseY = Math.round(mouseY / this.uiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(this.uiScale, this.uiScale);
        this.updateLayout();
        this.clampScroll();
        int accent = ThemeModule.getAccentColorAt(0.5F);
        context.fill(0, 0, this.getScaledWidth(), this.getScaledHeight(), -1140587253);
        if (this.usePanelStyle) {
            DashboardRenderer.drawRoundedHighlight(
                    context,
                    this.panelX + 2,
                    this.panelY + 5,
                    this.panelWidth,
                    this.panelHeight,
                    12,
                    1962934272);
            this.drawRoundedRect(
                    context,
                    this.panelX,
                    this.panelY,
                    this.panelWidth,
                    this.panelHeight,
                    12,
                    -66449899);
            this.drawRoundedRect(
                    context,
                    this.panelX + 2,
                    this.panelY + 2,
                    this.panelWidth - 4,
                    50,
                    10,
                    -200470243);
        } else {
            this.drawRoundedRect(
                    context,
                    this.panelX - 3,
                    this.panelY - 3,
                    this.panelWidth + 6,
                    this.panelHeight + 6,
                    13,
                    withAlpha(accent, 34));
            this.drawRoundedRect(
                    context,
                    this.panelX,
                    this.panelY,
                    this.panelWidth,
                    this.panelHeight,
                    11,
                    withAlpha(accent, 112));
            this.drawRoundedRect(
                    context,
                    this.panelX + 1,
                    this.panelY + 1,
                    this.panelWidth - 2,
                    this.panelHeight - 2,
                    10,
                    -83227112);
            this.drawRoundedRect(
                    context,
                    this.panelX + 2,
                    this.panelY + 2,
                    this.panelWidth - 4,
                    50,
                    9,
                    -200470243);
        }

        if (!this.usePanelStyle) {
            this.drawRoundedRect(
                    context, this.panelX + 14, this.panelY + 12, 3, 22, 2, withAlpha(accent, 245));
        }

        int textX = this.panelX + (this.usePanelStyle ? 14 : 25);
        this.drawText(context, "CHAT MACROS", textX, this.panelY + 10, -789254);
        this.drawText(
                context,
                "ADD A MESSAGE OR COMMAND AND GIVE IT ONE UNIQUE KEY",
                textX,
                this.panelY + 25,
                -7827550);
        int closeX = this.panelX + this.panelWidth - 37;
        int closeY = this.panelY + 12;
        boolean closeHovered = isInside(scaledMouseX, scaledMouseY, closeX, closeY, 23, 23);
        this.drawRoundedRect(
                context, closeX, closeY, 23, 23, 6, closeHovered ? -14018523 : -15262935);
        this.drawCenteredText(
                context, "X", closeX + 11, closeY + 7, closeHovered ? -789254 : -7827550);
        int dividerColor = this.usePanelStyle ? -14407116 : withAlpha(accent, 165);
        context.fill(
                this.panelX + 13,
                this.panelY + 52 - 1,
                this.panelX + this.panelWidth - 13,
                this.panelY + 52,
                dividerColor);
        List<Integer> slots = this.getActiveMacroSlots();
        int addY = this.panelY + 52 + 7;
        this.drawText(context, slots.size() + " / 10 MACROS", this.panelX + 14, addY + 8, -6840657);
        this.drawButton(
                context,
                this.panelX + this.panelWidth - 119,
                addY,
                105,
                25,
                "+ ADD MACRO",
                scaledMouseX,
                scaledMouseY,
                true,
                accent,
                slots.size() >= 10);
        context.enableScissor(
                this.listX, this.listY, this.listX + this.listWidth, this.listY + this.listHeight);
        if (slots.isEmpty()) {
            int emptyY = this.listY + Math.max(24, this.listHeight / 2 - 14);
            this.drawCenteredText(
                    context,
                    "NO CHAT MACROS YET",
                    this.listX + this.listWidth / 2,
                    emptyY,
                    -5196348);
            this.drawCenteredText(
                    context,
                    "PRESS + ADD MACRO TO CREATE YOUR FIRST ONE",
                    this.listX + this.listWidth / 2,
                    emptyY + 16,
                    -9932927);
        } else {
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.get(i);
                int rowY = this.listY + i * 73 - this.scrollOffset;
                if (rowY + 66 >= this.listY && rowY <= this.listY + this.listHeight) {
                    this.drawMacroRow(
                            context,
                            slot,
                            i + 1,
                            this.listX,
                            rowY,
                            this.listWidth,
                            scaledMouseX,
                            scaledMouseY,
                            accent);
                }
            }
        }

        context.disableScissor();
        int footerY = this.panelY + this.panelHeight - 46;
        context.fill(
                this.panelX + 13,
                footerY,
                this.panelX + this.panelWidth - 13,
                footerY + 1,
                -14538953);
        if (!this.status.isEmpty() && System.currentTimeMillis() < this.statusExpiry) {
            String statusText =
                    BoldText.truncateToWidth(this.textRenderer, this.status, this.panelWidth - 210);
            this.drawText(
                    context,
                    statusText,
                    this.panelX + 14,
                    footerY + 17,
                    this.statusSuccess ? -10099022 : -39288);
        }

        int saveX = this.panelX + this.panelWidth - 94;
        int buttonY = footerY + 11;
        this.drawButton(
                context,
                this.panelX + this.panelWidth - 176,
                buttonY,
                74,
                24,
                "CANCEL",
                scaledMouseX,
                scaledMouseY,
                false,
                accent,
                false);
        this.drawButton(
                context,
                saveX,
                buttonY,
                80,
                24,
                "SAVE",
                scaledMouseX,
                scaledMouseY,
                true,
                accent,
                false);
        context.getMatrices().popMatrix();
    }

    private void drawMacroRow(
            DrawContext context,
            int slot,
            int number,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            int accent) {
        boolean rowHovered = isInside(mouseX, mouseY, x, y, width, 66);
        if (this.usePanelStyle) {
            this.drawRoundedRect(context, x, y, width, 66, 8, rowHovered ? -15262683 : -15657443);
        } else {
            this.drawRoundedRect(
                    context, x, y, width, 66, 8, rowHovered ? withAlpha(accent, 115) : -14341574);
            this.drawRoundedRect(
                    context, x + 1, y + 1, width - 2, 64, 7, rowHovered ? -15394264 : -15657695);
        }

        if (!this.usePanelStyle) {
            this.drawRoundedRect(context, x + 8, y + 9, 3, 12, 2, withAlpha(accent, 225));
        }

        this.drawText(
                context, "MACRO " + number, x + (this.usePanelStyle ? 9 : 17), y + 10, -1446925);
        int removeX = x + width - 25;
        boolean removeHovered = isInside(mouseX, mouseY, removeX, y + 7, 18, 18);
        this.drawRoundedRect(
                context, removeX, y + 7, 18, 18, 5, removeHovered ? -11920084 : -14670540);
        this.drawCenteredText(context, "X", removeX + 9, y + 12, removeHovered ? -34925 : -7432793);
        int keyX = x + width - 112 - 9;
        int textX = x + 9;
        int fieldY = y + 31;
        int textWidth = keyX - textX - 7;
        boolean editing = this.editingTextIndex == slot;
        boolean textHovered = isInside(mouseX, mouseY, textX, fieldY, textWidth, 26);
        if (this.usePanelStyle) {
            int textFieldColor;
            if (editing) {
                textFieldColor = -14669005;
            } else if (textHovered) {
                textFieldColor = -15459035;
            } else {
                textFieldColor = -15854308;
            }

            this.drawRoundedRect(context, textX, fieldY, textWidth, 26, 6, textFieldColor);
        } else {
            int textBorderColor;
            if (editing) {
                textBorderColor = withAlpha(accent, 225);
            } else if (textHovered) {
                textBorderColor = withAlpha(accent, 125);
            } else {
                textBorderColor = -14012608;
            }

            this.drawRoundedRect(context, textX, fieldY, textWidth, 26, 6, textBorderColor);
            this.drawRoundedRect(context, textX + 1, fieldY + 1, textWidth - 2, 24, 5, -15986662);
        }

        String macroText = this.macroTexts[slot];
        String displayText = macroText.isEmpty() ? "/rtp eu" : macroText;
        int textColor = macroText.isEmpty() ? -10919821 : -2301718;
        String cursor = editing && (System.currentTimeMillis() / 450L & 1L) == 0L ? "_" : "";
        this.drawText(
                context,
                this.truncateStart(displayText + cursor, textWidth - 14),
                textX + 7,
                fieldY + 8,
                textColor);
        boolean keyHovered = isInside(mouseX, mouseY, keyX, fieldY, 112, 26);
        boolean listening = this.listeningKeyIndex == slot;
        if (this.usePanelStyle) {
            int keyFieldColor;
            if (listening) {
                keyFieldColor = -14405575;
            } else if (keyHovered) {
                keyFieldColor = -15063763;
            } else {
                keyFieldColor = -15393499;
            }

            this.drawRoundedRect(context, keyX, fieldY, 112, 26, 6, keyFieldColor);
        } else {
            int keyBorderColor;
            if (listening) {
                keyBorderColor = withAlpha(accent, 240);
            } else if (keyHovered) {
                keyBorderColor = withAlpha(accent, 145);
            } else {
                keyBorderColor = -14012608;
            }

            this.drawRoundedRect(context, keyX, fieldY, 112, 26, 6, keyBorderColor);
            this.drawRoundedRect(
                    context,
                    keyX + 1,
                    fieldY + 1,
                    110,
                    24,
                    5,
                    listening ? withAlpha(accent, 72) : -15394523);
        }

        String keyLabel = listening ? "PRESS A KEY..." : KeyNames.getKeyName(this.macroKeys[slot]);
        String keyText = BoldText.truncateToWidth(this.textRenderer, keyLabel, 100);
        this.drawCenteredText(
                context, keyText, keyX + 56, fieldY + 8, listening ? -658945 : -4669747);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        this.updateLayout();
        double mouseX = click.x() / this.uiScale;
        double mouseY = click.y() / this.uiScale;
        int button = click.button();
        if (this.listeningKeyIndex >= 0 && button >= 2 && button <= 7) {
            this.setActivationKey(this.listeningKeyIndex, 1000 + button);
            this.listeningKeyIndex = -1;
            return true;
        } else if (button != 0 && button != 1) {
            return true;
        } else {
            int closeX = this.panelX + this.panelWidth - 37;
            int closeY = this.panelY + 12;
            if (button == 0 && isInside(mouseX, mouseY, closeX, closeY, 23, 23)) {
                this.closeScreen();
                return true;
            } else {
                int addY = this.panelY + 52 + 7;
                int addX = this.panelX + this.panelWidth - 119;
                if (button == 0 && isInside(mouseX, mouseY, addX, addY, 105, 25)) {
                    this.addMacro();
                    return true;
                } else {
                    int buttonY = this.panelY + this.panelHeight - 46 + 11;
                    int cancelX = this.panelX + this.panelWidth - 176;
                    int saveX = this.panelX + this.panelWidth - 94;
                    if (button == 0 && isInside(mouseX, mouseY, cancelX, buttonY, 74, 24)) {
                        this.closeScreen();
                        return true;
                    } else if (button == 0 && isInside(mouseX, mouseY, saveX, buttonY, 80, 24)) {
                        this.saveAndClose();
                        return true;
                    } else {
                        if (isInside(
                                mouseX,
                                mouseY,
                                this.listX,
                                this.listY,
                                this.listWidth,
                                this.listHeight)) {
                            List<Integer> slots = this.getActiveMacroSlots();

                            for (int i = 0; i < slots.size(); i++) {
                                int slot = slots.get(i);
                                int rowY = this.listY + i * 73 - this.scrollOffset;
                                if (isInside(
                                        mouseX, mouseY, this.listX, rowY, this.listWidth, 66)) {
                                    int removeX = this.listX + this.listWidth - 25;
                                    if (button == 0
                                            && isInside(
                                                    mouseX, mouseY, removeX, rowY + 7, 18, 18)) {
                                        this.removeMacro(slot);
                                        return true;
                                    }

                                    int keyX = this.listX + this.listWidth - 112 - 9;
                                    int textX = this.listX + 9;
                                    int fieldY = rowY + 31;
                                    int textWidth = keyX - textX - 7;
                                    if (button == 0
                                            && isInside(
                                                    mouseX, mouseY, textX, fieldY, textWidth, 26)) {
                                        this.editingTextIndex = slot;
                                        this.listeningKeyIndex = -1;
                                        return true;
                                    }

                                    if (isInside(mouseX, mouseY, keyX, fieldY, 112, 26)) {
                                        this.editingTextIndex = -1;
                                        if (button == 1) {
                                            this.macroKeys[slot] = -1;
                                            this.listeningKeyIndex = -1;
                                        } else {
                                            this.listeningKeyIndex = slot;
                                        }

                                        return true;
                                    }
                                }
                            }
                        }

                        this.editingTextIndex = -1;
                        if (!isInside(
                                mouseX,
                                mouseY,
                                this.panelX,
                                this.panelY,
                                this.panelWidth,
                                this.panelHeight)) {
                            this.closeScreen();
                        }

                        return true;
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInside(
                mouseX / this.uiScale,
                mouseY / this.uiScale,
                this.listX,
                this.listY,
                this.listWidth,
                this.listHeight)) {
            this.scrollOffset = this.scrollOffset + (int) Math.round(-verticalAmount * 31.0);
            this.clampScroll();
        }

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        int scancode = input.scancode();
        if (this.listeningKeyIndex >= 0) {
            boolean clearKey =
                    key == 256 || key == 261 || key == 259 || scancode == 83 || scancode == 339;
            if (clearKey) {
                this.macroKeys[this.listeningKeyIndex] = -1;
            } else {
                this.setActivationKey(this.listeningKeyIndex, key);
            }

            this.listeningKeyIndex = -1;
            return true;
        } else if (this.editingTextIndex < 0) {
            if (key == 256) {
                this.closeScreen();
            }

            return true;
        } else {
            if (key != 256 && key != 257 && key != 335) {
                if (key == 259 && !this.macroTexts[this.editingTextIndex].isEmpty()) {
                    String current = this.macroTexts[this.editingTextIndex];
                    this.macroTexts[this.editingTextIndex] =
                            current.substring(0, current.length() - 1);
                } else if (key == 86 && (input.modifiers() & 2) != 0) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        String clipboard = client.keyboard.getClipboard();
                        if (clipboard != null) {
                            String pasted = clipboard.replace('\r', ' ').replace('\n', ' ');
                            this.macroTexts[this.editingTextIndex] =
                                    limitLength(
                                            this.macroTexts[this.editingTextIndex] + pasted, 256);
                        }
                    }
                }
            } else {
                this.editingTextIndex = -1;
            }

            return true;
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (this.editingTextIndex >= 0 && input.isValidChar()) {
            String typed = input.asString();
            if (typed != null && !typed.isEmpty()) {
                this.macroTexts[this.editingTextIndex] =
                        limitLength(this.macroTexts[this.editingTextIndex] + typed, 256);
            }
        }

        return true;
    }

    @Override
    public void close() {
        this.closeScreen();
    }

    private void addMacro() {
        for (int i = 0; i < 10; i++) {
            if (!this.active[i]) {
                this.active[i] = true;
                this.macroTexts[i] = "";
                this.macroKeys[i] = -1;
                this.editingTextIndex = i;
                this.listeningKeyIndex = -1;
                this.scrollOffset = this.getMaxScroll();
                this.setStatus("MACRO ADDED - ENTER ITS TEXT AND KEY", true);
                return;
            }
        }

        this.setStatus("THE MAXIMUM IS 10 MACROS", false);
    }

    private void removeMacro(int slot) {
        this.active[slot] = false;
        this.macroTexts[slot] = "";
        this.macroKeys[slot] = -1;
        if (this.editingTextIndex == slot) {
            this.editingTextIndex = -1;
        }

        if (this.listeningKeyIndex == slot) {
            this.listeningKeyIndex = -1;
        }

        this.clampScroll();
        this.setStatus("MACRO REMOVED", true);
    }

    private void setActivationKey(int slot, int key) {
        if (key == this.module.keybind.getValue()) {
            this.setStatus("THAT KEY TOGGLES CHAT MACROS", false);
        } else {
            for (int i = 0; i < 10; i++) {
                if (i != slot && this.active[i] && this.macroKeys[i] == key) {
                    this.setStatus("THAT ACTIVATION KEY IS ALREADY IN USE", false);
                    return;
                }
            }

            this.macroKeys[slot] = key;
            this.setStatus("ACTIVATION KEY SET TO " + KeyNames.getKeyName(key), true);
        }
    }

    private void saveAndClose() {
        String[] texts = new String[10];
        int[] keys = new int[10];

        for (int i = 0; i < 10; i++) {
            texts[i] = this.active[i] ? this.macroTexts[i] : "";
            keys[i] = this.active[i] ? this.macroKeys[i] : -1;
        }

        String error = this.module.applyMacros(texts, keys);
        if (error != null) {
            this.setStatus(error.toUpperCase(), false);
        } else {
            this.closeScreen();
        }
    }

    private List<Integer> getActiveMacroSlots() {
        List<Integer> slots = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            if (this.active[i]) {
                slots.add(i);
            }
        }

        return slots;
    }

    private void updateLayout() {
        this.panelWidth = Math.min(520, Math.max(330, this.getScaledWidth() - 24));
        this.panelHeight = Math.min(404, Math.max(300, this.getScaledHeight() - 20));
        this.panelX = (this.getScaledWidth() - this.panelWidth) / 2;
        this.panelY = Math.max(10, (this.getScaledHeight() - this.panelHeight) / 2);
        this.listX = this.panelX + 13;
        this.listY = this.panelY + 52 + 38;
        this.listWidth = this.panelWidth - 26;
        this.listHeight = this.panelHeight - 52 - 38 - 46 - 3;
    }

    private int getScaledWidth() {
        return ScreenScaleCompat.getScaledWidth();
    }

    private int getScaledHeight() {
        return ScreenScaleCompat.getScaledHeight();
    }

    private int getMaxScroll() {
        return Math.max(0, this.getActiveMacroSlots().size() * 73 - 7 - this.listHeight);
    }

    private void clampScroll() {
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.getMaxScroll()));
    }

    private void setStatus(String message, boolean success) {
        this.status = message == null ? "" : message;
        this.statusSuccess = success;
        this.statusExpiry = System.currentTimeMillis() + 3500L;
    }

    private String truncateStart(String text, int maxWidth) {
        if (BoldText.width(this.textRenderer, text) <= maxWidth) {
            return text;
        }

        String shortened = text;
        while (!shortened.isEmpty()
                && BoldText.width(this.textRenderer, "..." + shortened) > maxWidth) {
            shortened = shortened.substring(1);
        }

        return "..." + shortened;
    }

    private void drawButton(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            String label,
            int mouseX,
            int mouseY,
            boolean primary,
            int accent,
            boolean disabled) {
        boolean hovered = !disabled && isInside(mouseX, mouseY, x, y, width, height);

        int fillColor;
        if (disabled) {
            fillColor = -15657955;
        } else if (this.usePanelStyle) {
            if (primary) {
                if (hovered) {
                    fillColor = -13418425;
                } else {
                    fillColor = -14207940;
                }
            } else if (hovered) {
                fillColor = -14800334;
            } else {
                fillColor = -15393242;
            }
        } else if (primary) {
            fillColor = withAlpha(accent, hovered ? 116 : 82);
        } else if (hovered) {
            fillColor = -14933712;
        } else {
            fillColor = -15394523;
        }

        if (this.usePanelStyle) {
            this.drawRoundedRect(context, x, y, width, height, 6, fillColor);
        } else {
            int borderColor;
            if (disabled) {
                borderColor = -14539212;
            } else if (primary) {
                borderColor = withAlpha(accent, hovered ? 245 : 190);
            } else if (hovered) {
                borderColor = withAlpha(accent, 130);
            } else {
                borderColor = -14012608;
            }

            this.drawRoundedRect(context, x, y, width, height, 6, borderColor);
            this.drawRoundedRect(context, x + 1, y + 1, width - 2, height - 2, 5, fillColor);
        }

        int textColor;
        if (disabled) {
            textColor = -11643545;
        } else if (!primary && !hovered) {
            textColor = -7169622;
        } else {
            textColor = -789254;
        }

        this.drawCenteredText(context, label, x + width / 2, y + 8, textColor);
    }

    private void closeScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        BoldText.draw(context, this.textRenderer, text, x, y, color);
    }

    private void drawCenteredText(DrawContext context, String text, int x, int y, int color) {
        this.drawText(context, text, x - BoldText.width(this.textRenderer, text) / 2, y, color);
    }

    private void drawRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        DashboardRenderer.drawRoundedRect(context, x, y, width, height, radius, color);
    }

    private static String limitLength(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
    }

    private static int lerpColor(int from, int to, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        return Math.round((from >>> 24) * (1.0F - t) + (to >>> 24) * t) << 24
                | Math.round((from >> 16 & 0xFF) * (1.0F - t) + (to >> 16 & 0xFF) * t) << 16
                | Math.round((from >> 8 & 0xFF) * (1.0F - t) + (to >> 8 & 0xFF) * t) << 8
                | Math.round((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
    }

    private static boolean isInside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
