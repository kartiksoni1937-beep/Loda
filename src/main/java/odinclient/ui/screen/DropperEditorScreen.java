package odinclient.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import odinclient.module.gamble.GambleRigger;
import odinclient.ui.draw.BoldText;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.ScreenScaleCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DropperEditorScreen extends Screen {
    private final Screen parent;
    private final GambleRigger dropperConfig;
    private final int dropperIndex;
    private final String[] items = new String[9];
    private final String[] names = new String[9];
    private int editingNameRow = -1;
    private int pickingItemRow = -1;
    private String searchQuery = "";
    private List<Item> searchResults = List.of();
    private int resultScroll;
    private int rowScroll;
    private String lastSearchQuery;
    private int panelX;
    private int y;
    private int panelWidth;
    private int panelHeight;
    private int rowsTop;
    private int rowHeight;
    private float scale = 1.0F;

    private DropperEditorScreen(Screen parent, GambleRigger dropperConfig, int dropperIndex) {
        super(Text.literal("Dropper " + (dropperIndex + 1)));
        this.parent = parent;
        this.dropperConfig = dropperConfig;
        this.dropperIndex = dropperIndex;
        GambleRigger.DropperSlotEntry[] contents = dropperConfig.getDropperContents(dropperIndex);

        for (int i = 0; i < 9; i++) {
            this.items[i] = contents[i] == null ? "" : contents[i].itemId();
            this.names[i] = contents[i] == null ? "" : contents[i].name();
        }
    }

    public static void open(Screen parent, GambleRigger config, int index) {
        if (config != null && index >= 0 && index <= 1) {
            MinecraftClient.getInstance().setScreen(new DropperEditorScreen(parent, config, index));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.scale = ScreenScaleCompat.getScale(this.width);
        int scaledMouseX = Math.round(mouseX / this.scale);
        int scaledMouseY = Math.round(mouseY / this.scale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(this.scale, this.scale);
        this.updateLayout();
        context.fill(
                0,
                0,
                ScreenScaleCompat.getScaledWidth(),
                ScreenScaleCompat.getScaledHeight(),
                -1107098359);
        DashboardRenderer.drawRoundedRect(
                context, this.panelX, this.y, this.panelWidth, this.panelHeight, 11, -15986663);
        this.drawLabel(
                context,
                "DROPPER " + (this.dropperIndex + 1) + "  -  BLOCKS, ITEMS & NAMES",
                this.panelX + 17,
                this.y + 15,
                -855048);
        this.drawLabel(
                context,
                "Left dropper is always Dropper 1. Rows are values 1 through 9.",
                this.panelX + 17,
                this.y + 31,
                -7629915);
        this.button(
                context,
                this.panelX + this.panelWidth - 38,
                this.y + 11,
                24,
                24,
                "X",
                scaledMouseX,
                scaledMouseY,
                false);
        if (this.pickingItemRow >= 0) {
            this.renderItemPicker(context, scaledMouseX, scaledMouseY);
        } else {
            int itemBoxWidth = Math.max(115, Math.min(205, (this.panelWidth - 70) / 2));
            int nameColumnX = this.panelX + 52 + itemBoxWidth;
            this.drawLabel(context, "#", this.panelX + 21, this.rowsTop - 14, -8484965);
            this.drawLabel(context, "BLOCK / ITEM", this.panelX + 52, this.rowsTop - 14, -8484965);
            this.drawLabel(context, "VISIBLE NAME", nameColumnX + 10, this.rowsTop - 14, -8484965);
            context.enableScissor(
                    this.panelX + 10,
                    this.rowsTop - 2,
                    this.panelX + this.panelWidth - 10,
                    this.y + this.panelHeight - 53);

            for (int row = this.rowScroll; row < 9; row++) {
                int rowY = this.rowsTop + (row - this.rowScroll) * this.rowHeight;
                if (rowY + this.rowHeight > this.y + this.panelHeight - 50) {
                    break;
                }

                int rowX = this.panelX + 15;
                int rowWidth = this.panelWidth - 30;
                int rowBackgroundHeight = this.rowHeight - 4;
                int rowColor = row % 2 == 0 ? -15394267 : -15657440;
                DashboardRenderer.drawRoundedRect(
                        context, rowX, rowY, rowWidth, rowBackgroundHeight, 5, rowColor);
                int numberY = rowY + 9;
                this.drawLabel(
                        context, Integer.toString(row + 1), this.panelX + 24, numberY, -3295745);
                DashboardRenderer.drawRoundedRect(
                        context,
                        this.panelX + 47,
                        rowY + 3,
                        itemBoxWidth,
                        this.rowHeight - 10,
                        4,
                        -14670280);
                String itemId = this.items[row];
                if (!itemId.isBlank()) {
                    Item rowItem = item(itemId);
                    if (rowItem != null) {
                        context.drawItem(new ItemStack(rowItem), this.panelX + 51, rowY + 6);
                    }
                }

                String itemLabel =
                        itemId.isBlank()
                                ? "Choose block or item..."
                                : itemId.substring(itemId.indexOf(':') + 1).replace('_', ' ');
                String trimmedItemLabel = this.trimToWidth(itemLabel, itemBoxWidth - 30);
                int itemLabelX = this.panelX + 70;
                int itemLabelY = rowY + 10;
                int itemLabelColor = itemId.isBlank() ? -8419175 : -2301461;
                this.drawLabel(context, trimmedItemLabel, itemLabelX, itemLabelY, itemLabelColor);
                int nameWidth = this.panelWidth - (nameColumnX - this.panelX) - 26;
                int nameBoxX = nameColumnX + 5;
                int nameBoxY = rowY + 3;
                int nameBoxHeight = this.rowHeight - 10;
                int nameBoxColor = this.editingNameRow == row ? -14208182 : -15065040;
                DashboardRenderer.drawRoundedRect(
                        context, nameBoxX, nameBoxY, nameWidth, nameBoxHeight, 4, nameBoxColor);
                String baseName = this.names[row].isBlank() ? "Display name..." : this.names[row];
                String displayName = baseName;
                if (this.editingNameRow == row && (System.currentTimeMillis() / 450L & 1L) == 0L) {
                    displayName = baseName + "_";
                }

                String trimmedName = this.trimToWidth(displayName, nameWidth - 12);
                int nameTextX = nameColumnX + 11;
                int nameTextY = rowY + 10;
                int nameColor = this.names[row].isBlank() ? -8419175 : -1578511;
                this.drawLabel(context, trimmedName, nameTextX, nameTextY, nameColor);
            }

            context.disableScissor();
            if (this.rowScroll > 0 || this.getVisibleRowCount() < 9) {
                this.drawLabel(
                        context,
                        "Scroll for the remaining rows",
                        this.panelX + 17,
                        this.y + this.panelHeight - 54,
                        -7629915);
            }
        }

        int footerY = this.y + this.panelHeight - 42;
        context.fill(
                this.panelX + 14,
                footerY - 6,
                this.panelX + this.panelWidth - 14,
                footerY - 5,
                -14407113);
        this.button(
                context,
                this.panelX + this.panelWidth - 185,
                footerY,
                78,
                25,
                "CANCEL",
                scaledMouseX,
                scaledMouseY,
                false);
        this.button(
                context,
                this.panelX + this.panelWidth - 99,
                footerY,
                85,
                25,
                "SAVE",
                scaledMouseX,
                scaledMouseY,
                true);
        context.getMatrices().popMatrix();
    }

    private void renderItemPicker(DrawContext context, int mouseX, int mouseY) {
        this.drawLabel(
                context,
                "CHOOSE BLOCK OR ITEM FOR ROW " + (this.pickingItemRow + 1),
                this.panelX + 17,
                this.y + 58,
                -3295745);
        DashboardRenderer.drawRoundedRect(
                context, this.panelX + 17, this.y + 78, this.panelWidth - 34, 27, 5, -14998992);
        this.drawLabel(
                context,
                this.trimToWidth(
                        "Search blocks and items: " + this.searchQuery + "_", this.panelWidth - 50),
                this.panelX + 26,
                this.y + 87,
                -1972752);
        this.refreshSearchResults();
        int listTop = this.y + 113;
        int listBottom = this.y + this.panelHeight - 52;
        context.enableScissor(
                this.panelX + 16, listTop, this.panelX + this.panelWidth - 16, listBottom);
        int visibleRows = Math.max(1, (listBottom - listTop) / 27);

        for (int i = this.resultScroll;
                i < Math.min(this.searchResults.size(), this.resultScroll + visibleRows);
                i++) {
            Item resultItem = this.searchResults.get(i);
            int rowY = listTop + (i - this.resultScroll) * 27;
            int rowX = this.panelX + 18;
            int rowWidth = this.panelWidth - 36;
            int rowColor =
                    isInside(mouseX, mouseY, this.panelX + 18, rowY, this.panelWidth - 36, 24)
                            ? -14274489
                            : -15394008;

            DashboardRenderer.drawRoundedRect(context, rowX, rowY, rowWidth, 24, 4, rowColor);
            context.drawItem(new ItemStack(resultItem), this.panelX + 23, rowY + 4);
            this.drawLabel(
                    context,
                    this.trimToWidth(
                            Registries.ITEM.getId(resultItem).toString(), this.panelWidth - 80),
                    this.panelX + 45,
                    rowY + 8,
                    -2038546);
        }

        context.disableScissor();
        int footerY = listBottom + 6;
        this.drawLabel(
                context,
                this.searchResults.size() + " choices  -  scroll to browse  -  Esc to return",
                this.panelX + 18,
                footerY,
                -7629915);
    }

    private void refreshSearchResults() {
        if (!this.searchQuery.equals(this.lastSearchQuery)) {
            this.lastSearchQuery = this.searchQuery;
            String query = this.searchQuery.toLowerCase(Locale.ROOT).strip();
            ArrayList<Item> results = new ArrayList<>();

            for (Item candidate : Registries.ITEM) {
                ItemStack stack = new ItemStack(candidate);
                if (!stack.isEmpty()) {
                    String id = Registries.ITEM.getId(candidate).toString();
                    if (query.isEmpty()
                            || id.contains(query)
                            || stack.getName()
                                    .getString()
                                    .toLowerCase(Locale.ROOT)
                                    .contains(query)) {
                        results.add(candidate);
                    }
                }
            }

            results.sort(Comparator.comparing(entry -> Registries.ITEM.getId(entry).toString()));
            this.searchResults = results;
            this.resultScroll =
                    Math.max(
                            0,
                            Math.min(
                                    this.resultScroll, Math.max(0, this.searchResults.size() - 1)));
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        this.updateLayout();
        double mouseX = click.x() / this.scale;
        double mouseY = click.y() / this.scale;
        if (click.button() != 0) {
            return true;
        } else if (isInside(mouseX, mouseY, this.panelX + this.panelWidth - 38, this.y + 11, 24, 24)
                || isInside(
                        mouseX,
                        mouseY,
                        this.panelX + this.panelWidth - 185,
                        this.y + this.panelHeight - 42,
                        78,
                        25)) {
            this.closeScreen();
            return true;
        } else if (isInside(
                mouseX,
                mouseY,
                this.panelX + this.panelWidth - 99,
                this.y + this.panelHeight - 42,
                85,
                25)) {
            this.save();
            return true;
        } else if (this.pickingItemRow >= 0) {
            int listTop = this.y + 113;
            int listHeight = Math.max(1, (this.y + this.panelHeight - 52 - listTop) / 27) * 27;
            if (isInside(
                    mouseX, mouseY, this.panelX + 18, listTop, this.panelWidth - 36, listHeight)) {
                int index = this.resultScroll + ((int) mouseY - listTop) / 27;
                if (index >= 0 && index < this.searchResults.size()) {
                    this.items[this.pickingItemRow] =
                            Registries.ITEM.getId(this.searchResults.get(index)).toString();
                    this.pickingItemRow = -1;
                    this.searchQuery = "";
                    this.resultScroll = 0;
                }
            }

            return true;
        } else {
            int itemBoxWidth = Math.max(115, Math.min(205, (this.panelWidth - 70) / 2));
            int nameColumnX = this.panelX + 52 + itemBoxWidth;

            for (int row = this.rowScroll; row < 9; row++) {
                int rowY = this.rowsTop + (row - this.rowScroll) * this.rowHeight;
                if (rowY + this.rowHeight > this.y + this.panelHeight - 50) {
                    break;
                }

                if (isInside(
                        mouseX,
                        mouseY,
                        this.panelX + 47,
                        rowY + 3,
                        itemBoxWidth,
                        this.rowHeight - 10)) {
                    this.pickingItemRow = row;
                    this.editingNameRow = -1;
                    this.refreshSearchResults();
                    return true;
                }

                if (isInside(
                        mouseX,
                        mouseY,
                        nameColumnX + 5,
                        rowY + 3,
                        this.panelWidth - (nameColumnX - this.panelX) - 26,
                        this.rowHeight - 10)) {
                    this.editingNameRow = row;
                    return true;
                }
            }

            this.editingNameRow = -1;
            return true;
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.pickingItemRow >= 0) {
            int visibleRows = Math.max(1, (this.panelHeight - 165) / 27);
            this.resultScroll =
                    Math.max(
                            0,
                            Math.min(
                                    Math.max(0, this.searchResults.size() - visibleRows),
                                    this.resultScroll - (int) Math.signum(verticalAmount) * 3));
        } else {
            this.rowScroll =
                    Math.max(
                            0,
                            Math.min(
                                    Math.max(0, 9 - this.getVisibleRowCount()),
                                    this.rowScroll - (int) Math.signum(verticalAmount)));
        }

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (this.pickingItemRow >= 0) {
            if (keyCode == 256) {
                this.pickingItemRow = -1;
                this.searchQuery = "";
                this.resultScroll = 0;
            } else if (keyCode == 259 && !this.searchQuery.isEmpty()) {
                this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                this.resultScroll = 0;
            } else if (keyCode == 86 && (input.modifiers() & 2) != 0) {
                this.searchQuery = sanitizeInput(this.searchQuery + this.getClipboardText(), 48);
                this.resultScroll = 0;
            }

            return true;
        } else if (this.editingNameRow < 0) {
            if (keyCode == 256) {
                this.closeScreen();
            }

            return true;
        } else {
            if (keyCode != 256 && keyCode != 257 && keyCode != 335) {
                if (keyCode == 259 && !this.names[this.editingNameRow].isEmpty()) {
                    this.names[this.editingNameRow] =
                            this.names[this.editingNameRow].substring(
                                    0, this.names[this.editingNameRow].length() - 1);
                } else if (keyCode == 86 && (input.modifiers() & 2) != 0) {
                    this.names[this.editingNameRow] =
                            sanitizeInput(
                                    this.names[this.editingNameRow] + this.getClipboardText(), 48);
                }
            } else {
                this.editingNameRow = -1;
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
            if (this.pickingItemRow >= 0) {
                this.searchQuery = sanitizeInput(this.searchQuery + typed, 48);
                this.resultScroll = 0;
            } else if (this.editingNameRow >= 0) {
                this.names[this.editingNameRow] =
                        sanitizeInput(this.names[this.editingNameRow] + typed, 48);
            }

            return true;
        }
    }

    @Override
    public void close() {
        this.closeScreen();
    }

    private void save() {
        GambleRigger.DropperSlotEntry[] contents = new GambleRigger.DropperSlotEntry[9];

        for (int i = 0; i < 9; i++) {
            if (GambleRigger.isValidItemId(this.items[i])) {
                contents[i] = new GambleRigger.DropperSlotEntry(this.items[i], this.names[i]);
            }
        }

        this.dropperConfig.setDropperContents(this.dropperIndex, contents);
        this.closeScreen();
    }

    private void closeScreen() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void updateLayout() {
        int screenWidth = ScreenScaleCompat.getScaledWidth();
        int screenHeight = ScreenScaleCompat.getScaledHeight();
        this.panelWidth = Math.min(550, screenWidth - 20);
        this.panelHeight = Math.min(478, screenHeight - 20);
        this.panelX = (screenWidth - this.panelWidth) / 2;
        this.y = (screenHeight - this.panelHeight) / 2;
        this.rowsTop = this.y + 88;
        this.rowHeight = Math.max(27, Math.min(38, (this.panelHeight - 147) / 9));
    }

    private int getVisibleRowCount() {
        return Math.max(1, Math.min(9, (this.panelHeight - 139) / this.rowHeight));
    }

    private String getClipboardText() {
        String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
        return clipboard == null ? "" : clipboard;
    }

    private static String sanitizeInput(String text, int maxLength) {
        String cleaned = text.replaceAll("[\\p{Cntrl}\u00a7]", " ");
        return cleaned.substring(0, Math.min(maxLength, cleaned.length()));
    }

    private static Item item(String itemId) {
        String[] parts = itemId.split(":", 2);
        return parts.length == 2 ? Registries.ITEM.get(Identifier.of(parts[0], parts[1])) : null;
    }

    private String trimToWidth(String text, int maxWidth) {
        return BoldText.truncateToWidth(this.textRenderer, text, maxWidth);
    }

    private void drawLabel(DrawContext context, String text, int textX, int textY, int color) {
        DashboardRenderer.drawBoldText(context, this.textRenderer, text, textX, textY, color);
    }

    private void button(
            DrawContext context,
            int left,
            int top,
            int width,
            int height,
            String label,
            int mouseX,
            int mouseY,
            boolean primary) {
        int color =
                primary
                        ? -12574600
                        : (isInside(mouseX, mouseY, left, top, width, height)
                                ? -14208182
                                : -15064527);
        DashboardRenderer.drawRoundedRect(context, left, top, width, height, 5, color);
        DashboardRenderer.drawCenteredBoldText(
                context,
                this.textRenderer,
                label,
                left + width / 2,
                top + (height - 8) / 2,
                -920841);
    }

    private static boolean isInside(
            double pointX, double pointY, int left, int top, int width, int height) {
        return pointX >= left && pointX < left + width && pointY >= top && pointY < top + height;
    }
}
