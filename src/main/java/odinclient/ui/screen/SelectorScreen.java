package odinclient.ui.screen;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import odinclient.module.hud.ThemeModule;
import odinclient.render.EntityColor;
import odinclient.setting.type.BlockColorMapSetting;
import odinclient.setting.type.BlockListSetting;
import odinclient.setting.type.EntityTypeListSetting;
import odinclient.ui.draw.BoldText;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.scale.ScreenScaleCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

public final class SelectorScreen extends Screen {
    private static final int WIDTH = 430;
    private static final int HEIGHT = 356;
    private static final int CELL_SIZE = 36;
    private static final int CELL_GAP = 4;
    private final Screen parent;
    private final SelectorScreen.SelectorMode mode;
    private final BlockColorMapSetting colorMapSetting;
    private final BlockListSetting blocks;
    private final EntityTypeListSetting entitiesSetting;
    private final Map<Block, EntityColor> originalColorMap;
    private final List<Block> originalBlocks;
    private final List<EntityType<?>> originalEntities;
    private final Map<EntityType<?>, EntityColor> originalEntityColors;
    private final List<SelectorScreen.ItemSlot> slots = new ArrayList<>();
    private List<Object> items = List.of();
    private Object hoveredItem;
    private String searchText = "";
    private boolean showSelectedOnly;
    private boolean itemsDirty = true;
    private int scrollOffset;
    private int panelX;
    private int panelY;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;
    private float guiScale = 1.0F;

    private SelectorScreen(Screen parent, BlockColorMapSetting setting) {
        super(Text.literal("Block Selector"));
        this.parent = parent;
        this.mode = SelectorScreen.SelectorMode.COLORED_BLOCKS;
        this.colorMapSetting = setting;
        this.blocks = null;
        this.entitiesSetting = null;
        this.originalColorMap = copyColorMap(setting.getValue());
        this.originalBlocks = null;
        this.originalEntities = null;
        this.originalEntityColors = null;
    }

    private SelectorScreen(Screen parent, BlockListSetting setting) {
        super(Text.literal("Block Selector"));
        this.parent = parent;
        this.mode = SelectorScreen.SelectorMode.BLOCKS;
        this.colorMapSetting = null;
        this.blocks = setting;
        this.entitiesSetting = null;
        this.originalColorMap = null;
        this.originalBlocks = new ArrayList<>(setting.getValue());
        this.originalEntities = null;
        this.originalEntityColors = null;
    }

    private SelectorScreen(Screen parent, EntityTypeListSetting setting) {
        super(Text.literal("Entity Selector"));
        this.parent = parent;
        this.mode = SelectorScreen.SelectorMode.ENTITIES;
        this.colorMapSetting = null;
        this.blocks = null;
        this.entitiesSetting = setting;
        this.originalColorMap = null;
        this.originalBlocks = null;
        this.originalEntities = new ArrayList<>(setting.getValue());
        this.originalEntityColors = new LinkedHashMap<>();

        for (EntityType<?> type : this.originalEntities) {
            EntityColor color = setting.getColor(type);
            if (color != null) {
                this.originalEntityColors.put(type, copyColor(color));
            }
        }
    }

    public static void open(Screen parent, BlockColorMapSetting setting) {
        showScreen(setting == null ? null : new SelectorScreen(parent, setting));
    }

    public static void open(Screen parent, BlockListSetting setting) {
        showScreen(setting == null ? null : new SelectorScreen(parent, setting));
    }

    public static void open(Screen parent, EntityTypeListSetting setting) {
        showScreen(setting == null ? null : new SelectorScreen(parent, setting));
    }

    private static void showScreen(SelectorScreen screen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && screen != null) {
            client.setScreen(screen);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.guiScale = ScreenScaleCompat.getScale(this.width);
        int scaledMouseX = Math.round(mouseX / this.guiScale);
        int scaledMouseY = Math.round(mouseY / this.guiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(this.guiScale, this.guiScale);
        this.updateLayout();
        this.refreshItems();
        this.clampScroll();
        this.slots.clear();
        this.hoveredItem = null;
        int accent = ThemeModule.getAccentColorAt(0.5F);
        context.fill(0, 0, this.getScaledWidth(), this.getScaledHeight(), -1107098359);
        this.drawPanel(context, this.panelX, this.panelY, 430, 356);
        String title =
                this.mode == SelectorScreen.SelectorMode.ENTITIES
                        ? "ENTITY SELECTOR"
                        : "BLOCK SELECTOR";
        this.drawText(context, title, this.panelX + 14, this.panelY + 12, -854792);
        this.drawText(
                context,
                this.getSelectedCount() + " selected  -  right-click to edit color",
                this.panelX + 14,
                this.panelY + 27,
                -8551020);
        this.button(
                context,
                this.panelX + 430 - 37,
                this.panelY + 11,
                24,
                24,
                "X",
                scaledMouseX,
                scaledMouseY,
                false,
                accent);
        int searchX = this.panelX + 14;
        int searchY = this.panelY + 51;
        DashboardRenderer.drawRoundedRect(context, searchX, searchY, 220, 27, 7, -15657442);
        String searchLabel =
                this.searchText.isEmpty()
                        ? "Search by name or id..."
                        : this.searchText + (isCursorVisible() ? "_" : "");
        String searchDisplay = this.truncate(searchLabel, 200);
        int searchTextX = searchX + 10;
        int searchTextY = searchY + 9;
        this.drawText(
                context,
                searchDisplay,
                searchTextX,
                searchTextY,
                this.searchText.isEmpty() ? -9932416 : -1973014);
        int filterX = this.panelX + 241;
        String filterLabel = this.showSelectedOnly ? "SELECTED" : "ALL";
        this.button(
                context,
                filterX,
                searchY,
                92,
                27,
                filterLabel,
                scaledMouseX,
                scaledMouseY,
                this.showSelectedOnly,
                accent);
        this.button(
                context,
                this.panelX + 340,
                searchY,
                76,
                27,
                "CLEAR",
                scaledMouseX,
                scaledMouseY,
                false,
                accent);
        context.enableScissor(
                this.gridX, this.gridY, this.gridX + this.gridWidth, this.gridY + this.gridHeight);
        if (this.items.isEmpty()) {
            this.drawCenteredText(
                    context,
                    "NO MATCHING ENTRIES",
                    this.gridX + this.gridWidth / 2,
                    this.gridY + 42,
                    -7629919);
        } else {
            int columns = this.getColumnCount();
            int cellWidth = (this.gridWidth - 4 * (columns - 1)) / columns;

            for (int i = 0; i < this.items.size(); i++) {
                int column = i % columns;
                int row = i / columns;
                int cellX = this.gridX + column * (cellWidth + 4);
                int cellY = this.gridY + row * 40 - this.scrollOffset;
                if (cellY + 36 >= this.gridY && cellY <= this.gridY + this.gridHeight) {
                    this.drawEntry(
                            context,
                            this.items.get(i),
                            cellX,
                            cellY,
                            cellWidth,
                            scaledMouseX,
                            scaledMouseY,
                            accent);
                }
            }
        }

        context.disableScissor();
        if (this.hoveredItem != null) {
            String hoveredName = this.name(this.hoveredItem);
            int tooltipWidth =
                    Math.min(402, DashboardRenderer.width(this.textRenderer, hoveredName) + 16);
            int tooltipX =
                    Math.max(
                            this.panelX + 14,
                            Math.min(scaledMouseX + 12, this.panelX + 430 - 14 - tooltipWidth));
            int tooltipY =
                    Math.max(this.panelY + 81, Math.min(scaledMouseY + 12, this.panelY + 356 - 67));
            DashboardRenderer.drawRoundedRect(
                    context, tooltipX, tooltipY, tooltipWidth, 20, 5, -234222059);
            this.drawText(
                    context,
                    this.truncate(hoveredName, tooltipWidth - 12),
                    tooltipX + 6,
                    tooltipY + 6,
                    -1775121);
        }

        int footerY = this.panelY + 356 - 45;
        context.fill(this.panelX + 14, footerY, this.panelX + 430 - 14, footerY + 1, -14407116);
        this.button(
                context,
                this.panelX + 430 - 178,
                footerY + 10,
                76,
                25,
                "CANCEL",
                scaledMouseX,
                scaledMouseY,
                false,
                accent);
        this.button(
                context,
                this.panelX + 430 - 94,
                footerY + 10,
                80,
                25,
                "SAVE",
                scaledMouseX,
                scaledMouseY,
                true,
                accent);
        context.getMatrices().popMatrix();
    }

    private void drawEntry(
            DrawContext context,
            Object item,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            int accent) {
        boolean selected = this.isSelected(item);
        boolean hovered = isInside(mouseX, mouseY, x, y, width, 36);
        if (hovered) {
            this.hoveredItem = item;
        }

        int background = selected ? -14405576 : (hovered ? -14867155 : -15393757);
        DashboardRenderer.drawRoundedRect(context, x, y, width, 36, 5, background);
        this.drawIcon(context, item, x + width / 2 - 8, y + 10);
        if (selected && this.mode != SelectorScreen.SelectorMode.BLOCKS) {
            EntityColor color = this.color(item);
            int markerX = x + width - 8;
            int markerY = y + 36 - 9;
            int markerColor = color == null ? accent : 0xFF000000 | color.toArgb() & 16777215;
            DashboardRenderer.drawRoundedHighlight(context, markerX, markerY, 5, 5, 3, markerColor);
        }

        this.slots.add(new SelectorScreen.ItemSlot(new SelectorScreen.Rect(x, y, width, 36), item));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        this.updateLayout();
        double mouseX = click.x() / this.guiScale;
        double mouseY = click.y() / this.guiScale;
        int button = click.button();
        if (button != 0 && button != 1) {
            return true;
        } else if (button == 0
                && isInside(mouseX, mouseY, this.panelX + 430 - 37, this.panelY + 11, 24, 24)) {
            this.save();
            return true;
        } else {
            int controlsY = this.panelY + 51;
            if (button == 0 && isInside(mouseX, mouseY, this.panelX + 241, controlsY, 92, 27)) {
                this.showSelectedOnly = !this.showSelectedOnly;
                this.markDirty();
                return true;
            } else if (button == 0
                    && isInside(mouseX, mouseY, this.panelX + 340, controlsY, 76, 27)) {
                this.clearSelection();
                return true;
            } else {
                int footerY = this.panelY + 356 - 45;
                if (button == 0
                        && isInside(
                                mouseX, mouseY, this.panelX + 430 - 178, footerY + 10, 76, 25)) {
                    this.cancel();
                    return true;
                } else if (button == 0
                        && isInside(mouseX, mouseY, this.panelX + 430 - 94, footerY + 10, 80, 25)) {
                    this.save();
                    return true;
                } else {
                    if (isInside(
                            mouseX,
                            mouseY,
                            this.gridX,
                            this.gridY,
                            this.gridWidth,
                            this.gridHeight)) {
                        for (SelectorScreen.ItemSlot slot : this.slots) {
                            if (slot.rect.contains(mouseX, mouseY)) {
                                this.onEntryClicked(slot.item, button);
                                return true;
                            }
                        }
                    }

                    if (button == 0
                            && !isInside(mouseX, mouseY, this.panelX, this.panelY, 430, 356)) {
                        this.save();
                    }

                    return true;
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInside(
                mouseX / this.guiScale,
                mouseY / this.guiScale,
                this.gridX,
                this.gridY,
                this.gridWidth,
                this.gridHeight)) {
            this.scrollOffset = this.scrollOffset + (int) Math.round(-verticalAmount * 30.0);
            this.clampScroll();
        }

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == 256) {
            this.cancel();
        } else if (key == 257 || key == 335) {
            this.save();
        } else if (key == 259 && !this.searchText.isEmpty()) {
            this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
            this.markDirty();
        }

        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.isValidChar() && this.searchText.length() < 48) {
            this.searchText = this.searchText + input.asString();
            this.markDirty();
        }

        return true;
    }

    @Override
    public void close() {
        this.cancel();
    }

    private void onEntryClicked(Object item, int button) {
        if (this.mode == SelectorScreen.SelectorMode.COLORED_BLOCKS) {
            Block block = (Block) item;
            if (button == 1) {
                if (!this.colorMapSetting.hasBlock(block)) {
                    this.colorMapSetting.toggle(block, this.createDefaultColor());
                }

                Consumer<EntityColor> callback =
                        color -> this.colorMapSetting.setColor(block, color);
                ColorPickerScreen.openWithCallback(
                        this, this.colorMapSetting.getColor(block), callback);
            } else {
                this.colorMapSetting.toggle(block, this.createDefaultColor());
            }
        } else if (this.mode == SelectorScreen.SelectorMode.BLOCKS && button == 0) {
            Block clicked = (Block) item;
            List<Block> selected = new ArrayList<>(this.blocks.getValue());
            if (!selected.remove(clicked)) {
                selected.add(clicked);
            }

            this.blocks.setValue(selected);
        } else if (this.mode == SelectorScreen.SelectorMode.ENTITIES) {
            EntityType<?> entityType = (EntityType<?>) item;
            if (button == 1) {
                if (!this.entitiesSetting.isSelected(entityType)) {
                    this.entitiesSetting.toggle(entityType);
                }

                EntityColor current = this.entitiesSetting.getColor(entityType);
                EntityColor initial =
                        current == null ? EntityTypeListSetting.defaultColor(entityType) : current;
                ColorPickerScreen.openWithCallback(
                        this, initial, color -> this.entitiesSetting.setColor(entityType, color));
            } else {
                this.entitiesSetting.toggle(entityType);
            }
        }

        if (this.showSelectedOnly) {
            this.markDirty();
        }
    }

    private void refreshItems() {
        if (this.itemsDirty) {
            String query = this.searchText.strip().toLowerCase(Locale.ROOT);
            List<Object> result = new ArrayList<>();
            if (this.mode == SelectorScreen.SelectorMode.ENTITIES) {
                for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
                    if (this.entitiesSetting.isAllowed(entityType)
                            && SpawnEggItem.forEntity(entityType) != null
                            && (!this.showSelectedOnly
                                    || this.entitiesSetting.isSelected(entityType))
                            && (query.isEmpty()
                                    || this.name(entityType)
                                            .toLowerCase(Locale.ROOT)
                                            .contains(query)
                                    || id(entityType).contains(query))) {
                        result.add(entityType);
                    }
                }
            } else {
                for (Block block : Registries.BLOCK) {
                    if (block != Blocks.AIR
                            && !new ItemStack(block.asItem()).isEmpty()
                            && (this.mode != SelectorScreen.SelectorMode.BLOCKS
                                    || this.blocks.isAllowed(block))
                            && (!this.showSelectedOnly || this.isSelected(block))
                            && (query.isEmpty()
                                    || this.name(block).toLowerCase(Locale.ROOT).contains(query)
                                    || id(block).contains(query))) {
                        result.add(block);
                    }
                }
            }

            result.sort(Comparator.comparing(this::name, String.CASE_INSENSITIVE_ORDER));
            this.items = List.copyOf(result);
            this.itemsDirty = false;
            this.clampScroll();
        }
    }

    private void clearSelection() {
        if (this.mode == SelectorScreen.SelectorMode.COLORED_BLOCKS) {
            this.colorMapSetting.setValue(new LinkedHashMap<>());
        } else if (this.mode == SelectorScreen.SelectorMode.BLOCKS) {
            this.blocks.setValue(new ArrayList<>());
        } else {
            this.entitiesSetting.setValue(new ArrayList<>());
        }

        this.markDirty();
    }

    private boolean isSelected(Object item) {
        if (item instanceof Block block) {
            if (this.mode == SelectorScreen.SelectorMode.COLORED_BLOCKS) {
                return this.colorMapSetting.hasBlock(block);
            }

            return this.blocks.getValue().contains(block);
        } else {
            return this.entitiesSetting.isSelected((EntityType<?>) item);
        }
    }

    private EntityColor color(Object item) {
        return item instanceof Block
                ? this.colorMapSetting.getColor((Block) item)
                : this.entitiesSetting.getColor((EntityType<?>) item);
    }

    private int getSelectedCount() {
        if (this.mode == SelectorScreen.SelectorMode.COLORED_BLOCKS) {
            return this.colorMapSetting.getValue().size();
        } else if (this.mode == SelectorScreen.SelectorMode.BLOCKS) {
            return this.blocks.getValue().size();
        } else {
            return this.entitiesSetting.getValue().size();
        }
    }

    private void cancel() {
        if (this.mode == SelectorScreen.SelectorMode.COLORED_BLOCKS) {
            this.colorMapSetting.setValue(copyColorMap(this.originalColorMap));
        } else if (this.mode == SelectorScreen.SelectorMode.BLOCKS) {
            this.blocks.setValue(new ArrayList<>(this.originalBlocks));
        } else {
            this.entitiesSetting.setValue(new ArrayList<>(this.originalEntities));

            for (Entry<EntityType<?>, EntityColor> entry : this.originalEntityColors.entrySet()) {
                this.entitiesSetting.setColor(entry.getKey(), copyColor(entry.getValue()));
            }
        }

        this.closeScreen();
    }

    private void save() {
        this.closeScreen();
    }

    private void closeScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    private void updateLayout() {
        this.panelX = (this.getScaledWidth() - 430) / 2;
        this.panelY = Math.max(8, (this.getScaledHeight() - 356) / 2);
        this.gridX = this.panelX + 14;
        this.gridY = this.panelY + 87;
        this.gridWidth = 402;
        this.gridHeight = 217;
    }

    private int getMaxScroll() {
        return Math.max(
                0,
                (this.items.size() + this.getColumnCount() - 1) / this.getColumnCount() * 40
                        - 4
                        - this.gridHeight);
    }

    private int getColumnCount() {
        return Math.max(6, Math.min(12, (this.gridWidth + 4) / 39));
    }

    private int getScaledWidth() {
        return ScreenScaleCompat.getScaledWidth();
    }

    private int getScaledHeight() {
        return ScreenScaleCompat.getScaledHeight();
    }

    private void clampScroll() {
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.getMaxScroll()));
    }

    private void markDirty() {
        this.itemsDirty = true;
        this.scrollOffset = 0;
    }

    private void drawIcon(DrawContext context, Object item, int x, int y) {
        ItemStack stack = null;
        if (item instanceof Block block) {
            stack = new ItemStack(block.asItem());
        } else {
            SpawnEggItem egg = SpawnEggItem.forEntity((EntityType<?>) item);
            if (egg != null) {
                stack = new ItemStack(egg);
            }
        }

        if (stack != null && !stack.isEmpty()) {
            context.drawItem(stack, x, y);
        }
    }

    private String name(Object item) {
        if (item instanceof Block block) {
            ItemStack stack = new ItemStack(block.asItem());
            if (stack.isEmpty()) {
                return title(id(block).substring(id(block).indexOf(58) + 1));
            }

            return stack.getName().getString();
        } else {
            return ((EntityType<?>) item).getName().getString();
        }
    }

    private static String id(Object item) {
        if (item instanceof Block) {
            return Registries.BLOCK.getId((Block) item).toString();
        }

        return Registries.ENTITY_TYPE.getId((EntityType<?>) item).toString();
    }

    private static String title(String id) {
        String[] parts = id.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }

                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }

        return builder.toString();
    }

    private EntityColor createDefaultColor() {
        int accent = ThemeModule.getAccentColorAt(0.5F);
        return new EntityColor(accent >> 16 & 0xFF, accent >> 8 & 0xFF, accent & 0xFF, 255);
    }

    private String truncate(String text, int maxWidth) {
        return BoldText.truncateToWidth(this.textRenderer, text, maxWidth);
    }

    private void drawPanel(DrawContext context, int x, int y, int width, int height) {
        DashboardRenderer.drawRoundedHighlight(
                context, x + 2, y + 5, width, height, 12, 1962934272);
        DashboardRenderer.drawRoundedRect(context, x, y, width, height, 12, -66449899);
    }

    private void button(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            String label,
            int mouseX,
            int mouseY,
            boolean active,
            int accent) {
        boolean hovered = isInside(mouseX, mouseY, x, y, width, height);
        int background = active ? -14339782 : (hovered ? -14998484 : -15459293);
        DashboardRenderer.drawRoundedRect(context, x, y, width, height, 7, background);
        int centerX = x + width / 2;
        int textY = y + (height - 8) / 2;
        this.drawCenteredText(context, label, centerX, textY, active ? -723464 : -6182731);
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawBoldText(context, this.textRenderer, text, x, y, color);
    }

    private void drawCenteredText(DrawContext context, String text, int x, int y, int color) {
        DashboardRenderer.drawCenteredBoldText(context, this.textRenderer, text, x, y, color);
    }

    private static Map<Block, EntityColor> copyColorMap(Map<Block, EntityColor> source) {
        Map<Block, EntityColor> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach(
                    (block, color) -> {
                        if (block != null && color != null) {
                            copy.put(block, copyColor(color));
                        }
                    });
        }

        return copy;
    }

    private static EntityColor copyColor(EntityColor color) {
        return new EntityColor(color.r, color.g, color.b, color.a);
    }

    private static int lerpColor(int from, int to, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        return Math.round((from >>> 24) * (1.0F - t) + (to >>> 24) * t) << 24
                | Math.round((from >> 16 & 0xFF) * (1.0F - t) + (to >> 16 & 0xFF) * t) << 16
                | Math.round((from >> 8 & 0xFF) * (1.0F - t) + (to >> 8 & 0xFF) * t) << 8
                | Math.round((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
    }

    private static boolean isCursorVisible() {
        return (System.currentTimeMillis() / 450L & 1L) == 0L;
    }

    private static boolean isInside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    private record ItemSlot(SelectorScreen.Rect rect, Object item) {}

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return SelectorScreen.isInside(px, py, this.x, this.y, this.w, this.h);
        }
    }

    private static enum SelectorMode {
        COLORED_BLOCKS,
        BLOCKS,
        ENTITIES;
    }
}
