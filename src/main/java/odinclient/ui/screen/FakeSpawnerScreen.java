package odinclient.ui.screen;

import net.minecraft.block.Blocks;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import odinclient.module.fake.FakeSpawner;
import odinclient.ui.draw.PixelFont;
import odinclient.ui.scale.ScreenScaleCompat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class FakeSpawnerScreen extends Screen {
    private static final Identifier TITLE_TEXTURE =
            Identifier.of("odinclient", "textures/gui/fake_spawner_title.png");
    private static final Identifier TOOLTIP_TEXTURE =
            Identifier.of("odinclient", "textures/gui/fake_spawner_tooltip.png");
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 222;
    private static final int SLOT_SIZE = 18;
    private static final int BACKGROUND_COLOR = 1610612736;
    private static final int LABEL_COLOR = -12566464;
    private static final List<Text> SELL_ALL_TOOLTIP;
    private static final List<Text> DROP_LOOT_TOOLTIP;
    private ItemStack sellStack;
    private ItemStack spawnerStack;
    private ItemStack dropStack;
    private boolean stacksBuilt;
    private static boolean errorLogged;
    private int panelX;
    private int panelY;

    public FakeSpawnerScreen() {
        super(Text.literal(FakeSpawner.getMobType().getDisplayName() + " Spawner"));
    }

    @Override
    protected void init() {
        this.panelX = (ScreenScaleCompat.getScaledWidth() - 176) / 2;
        this.panelY = (ScreenScaleCompat.getScaledHeight() - 222) / 2;
        this.buildMenuStacks();
    }

    private void buildMenuStacks() {
        this.sellStack = createIconStack(Items.GOLD_INGOT);
        Item headItem = FakeSpawner.getMobType().getHeadItem();
        this.spawnerStack = createIconStack(headItem == null ? Items.SKELETON_SKULL : headItem);
        if (headItem == null && this.spawnerStack != null) {
            this.spawnerStack.set(
                    DataComponentTypes.ITEM_MODEL,
                    Identifier.of("odinclient", "fake_" + FakeSpawner.getMobType().id() + "_head"));
        }

        this.dropStack = createIconStack(Blocks.DROPPER);
        this.stacksBuilt = true;
    }

    private static ItemStack createIconStack(ItemConvertible item) {
        if (item == null) {
            return null;
        } else {
            try {
                ItemStack stack = new ItemStack(item);
                return !stack.isEmpty() ? stack : null;
            } catch (Throwable e) {
                logError("building icon stack", e);
                return null;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (context != null) {
            float scale = ScreenScaleCompat.getScale(0);

            try (ScreenScaleCompat.ScaleScope scope = ScreenScaleCompat.enter(context)) {
                this.panelX = (ScreenScaleCompat.getScaledWidth() - 176) / 2;
                this.panelY = (ScreenScaleCompat.getScaledHeight() - 222) / 2;
                this.renderScreen(
                        context,
                        Math.round(mouseX / scale),
                        Math.round(mouseY / scale),
                        deltaTicks);
            }
        }
    }

    private void renderScreen(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (context != null) {
            try {
                context.fill(
                        0,
                        0,
                        ScreenScaleCompat.getScaledWidth(),
                        ScreenScaleCompat.getScaledHeight(),
                        1610612736);
            } catch (Throwable e6) {
                logError("drawing background", e6);
            }

            if (this.client != null) {
                if (!this.stacksBuilt) {
                    this.buildMenuStacks();
                }

                try {
                    this.drawPanel(context);
                } catch (Throwable e5) {
                    logError("rendering panel", e5);
                }

                try {
                    this.drawMenuItems(context);
                } catch (Throwable e4) {
                    logError("rendering menu items", e4);
                }

                try {
                    this.drawPlayerInventory(context);
                } catch (Throwable e3) {
                    logError("rendering inventory", e3);
                }

                try {
                    this.drawSlotHighlight(context, mouseX, mouseY);
                } catch (Throwable e2) {
                    logError("rendering slot highlight", e2);
                }

                try {
                    this.drawTooltip(context, mouseX, mouseY);
                } catch (Throwable e) {
                    logError("rendering tooltip", e);
                }
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        try {
            int keyCode = input == null ? -1 : input.key();
            boolean inventoryKeyPressed =
                    this.client != null
                            && this.client.options != null
                            && this.client.options.inventoryKey != null
                            && input != null
                            && this.client.options.inventoryKey.matchesKey(input);

            if (inventoryKeyPressed || keyCode == 69 || keyCode == 256) {
                this.close();
                return true;
            }
        } catch (Throwable e) {
            logError("handling key press", e);
            this.close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean mouseReleased(Click click) {
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    private void drawPanel(DrawContext context) {
        int x = this.panelX;
        int y = this.panelY;
        context.fill(x, y, x + 176, y + 222, -3750202);
        context.fill(x, y, x + 176, y + 2, -1);
        context.fill(x, y, x + 2, y + 222, -1);
        context.fill(x, y + 222 - 2, x + 176, y + 222, -11184811);
        context.fill(x + 176 - 2, y, x + 176, y + 222, -11184811);
        if (FakeSpawner.getMobType() == FakeSpawner.MobType.Skeleton) {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    TITLE_TEXTURE,
                    x + 8,
                    y + 6,
                    0.0F,
                    0.0F,
                    103,
                    7,
                    206,
                    14,
                    206,
                    14,
                    -1);
        } else if (this.textRenderer != null) {
            int titleX = x + 8;
            int titleY = y + 6;
            PixelFont.drawString(
                    context,
                    "1 "
                            + FakeSpawner.getMobType().getDisplayName().toUpperCase(Locale.ROOT)
                            + " SPAWNER",
                    titleX,
                    titleY,
                    -11184811);
        }

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(context, x + 7 + col * 18, y + 17 + row * 18);
            }
        }

        if (this.textRenderer != null) {
            context.drawText(this.textRenderer, "Inventory", x + 8, y + 128, -12566464, false);
        }

        for (int invRow = 0; invRow < 3; invRow++) {
            for (int col3 = 0; col3 < 9; col3++) {
                drawSlot(context, x + 7 + col3 * 18, y + 139 + invRow * 18);
            }
        }

        for (int hotbarCol = 0; hotbarCol < 9; hotbarCol++) {
            drawSlot(context, x + 7 + hotbarCol * 18, y + 197);
        }
    }

    private static void drawSlot(DrawContext context, int x, int y) {
        context.fill(x, y, x + 18, y + 18, -13158601);
        context.fill(x + 1, y + 1, x + 18, y + 18, -1);
        context.fill(x + 1, y + 1, x + 17, y + 17, -7631989);
    }

    private void drawSlotHighlight(DrawContext context, int mouseX, int mouseY) {
        if (context != null) {
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) {
                    if (this.highlightSlotIfHovered(
                            context,
                            mouseX,
                            mouseY,
                            this.panelX + 8 + col * 18,
                            this.panelY + 18 + row * 18)) {
                        return;
                    }
                }
            }

            for (int invRow = 0; invRow < 3; invRow++) {
                for (int invCol = 0; invCol < 9; invCol++) {
                    if (this.highlightSlotIfHovered(
                            context,
                            mouseX,
                            mouseY,
                            this.panelX + 8 + invCol * 18,
                            this.panelY + 140 + invRow * 18)) {
                        return;
                    }
                }
            }

            for (int hotbarCol = 0; hotbarCol < 9; hotbarCol++) {
                if (this.highlightSlotIfHovered(
                        context,
                        mouseX,
                        mouseY,
                        this.panelX + 8 + hotbarCol * 18,
                        this.panelY + 198)) {
                    return;
                }
            }
        }
    }

    private boolean highlightSlotIfHovered(
            DrawContext context, int mouseX, int mouseY, int slotX, int slotY) {
        if (mouseX >= slotX && mouseY >= slotY && mouseX < slotX + 16 && mouseY < slotY + 16) {
            context.fill(slotX, slotY, slotX + 16, slotY + 16, -2130706433);
            return true;
        } else {
            return false;
        }
    }

    private void drawMenuItems(DrawContext context) {
        this.drawItem(context, this.sellStack, this.getMenuSlotX(3), this.getMenuSlotY());
        this.drawItem(context, this.spawnerStack, this.getMenuSlotX(4), this.getMenuSlotY());
        this.drawItem(context, this.dropStack, this.getMenuSlotX(5), this.getMenuSlotY());
    }

    private void drawPlayerInventory(DrawContext context) {
        if (this.client != null && this.client.player != null) {
            PlayerInventory inventory = this.client.player.getInventory();
            if (inventory != null) {
                int size = inventory.size();

                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 9; col++) {
                        this.drawItem(
                                context,
                                getInventoryStack(inventory, 9 + row * 9 + col, size),
                                this.panelX + 8 + col * 18,
                                this.panelY + 140 + row * 18);
                    }
                }

                for (int slot = 0; slot < 9; slot++) {
                    this.drawItem(
                            context,
                            getInventoryStack(inventory, slot, size),
                            this.panelX + 8 + slot * 18,
                            this.panelY + 198);
                }
            }
        }
    }

    private static ItemStack getInventoryStack(PlayerInventory inventory, int slot, int size) {
        if (inventory != null && slot >= 0 && slot < size) {
            try {
                return inventory.getStack(slot);
            } catch (Throwable e) {
                return null;
            }
        } else {
            return null;
        }
    }

    private void drawItem(DrawContext context, ItemStack stack, int x, int y) {
        if (context != null && stack != null && !stack.isEmpty()) {
            try {
                context.drawItem(stack, x, y);
                if (this.textRenderer != null) {
                    context.drawStackOverlay(this.textRenderer, stack, x, y);
                }
            } catch (Throwable e) {
                logError("rendering menu item", e);
            }
        }
    }

    private void drawTooltip(DrawContext context, int mouseX, int mouseY) {
        if (this.textRenderer != null) {
            if (hasStack(this.sellStack)
                    && isInsideSlot(mouseX, mouseY, this.getMenuSlotX(3), this.getMenuSlotY())) {
                this.drawTextTooltip(context, SELL_ALL_TOOLTIP, mouseX, mouseY);
            } else if (isInsideSlot(mouseX, mouseY, this.getMenuSlotX(4), this.getMenuSlotY())) {
                this.drawSpawnerTooltip(context, mouseX, mouseY);
            } else if (hasStack(this.dropStack)
                    && isInsideSlot(mouseX, mouseY, this.getMenuSlotX(5), this.getMenuSlotY())) {
                this.drawTextTooltip(context, DROP_LOOT_TOOLTIP, mouseX, mouseY);
            }
        }
    }

    private static boolean hasStack(ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    private void drawSpawnerTooltip(DrawContext context, int mouseX, int mouseY) {
        String label =
                "1 "
                        + FakeSpawner.getMobType().getDisplayName().toUpperCase(Locale.ROOT)
                        + " SPAWNER";
        int boxWidth = Math.max(104, PixelFont.width(label) + 2);
        int boxX = Math.max(4, Math.min(mouseX + 12, this.width - boxWidth - 4));
        int boxY = Math.max(4, Math.min(mouseY - 12, this.height - 20 - 4));
        context.fillGradient(
                boxX - 3, boxY - 4, boxX + boxWidth + 3, boxY - 3, -267386864, -267386864);
        context.fillGradient(
                boxX - 3,
                boxY + 20 + 3,
                boxX + boxWidth + 3,
                boxY + 20 + 4,
                -267386864,
                -267386864);
        context.fillGradient(
                boxX - 3, boxY - 3, boxX + boxWidth + 3, boxY + 20 + 3, -267386864, -267386864);
        context.fillGradient(boxX - 4, boxY - 3, boxX - 3, boxY + 20 + 3, -267386864, -267386864);
        context.fillGradient(
                boxX + boxWidth + 3,
                boxY - 3,
                boxX + boxWidth + 4,
                boxY + 20 + 3,
                -267386864,
                -267386864);
        context.fillGradient(boxX - 3, boxY - 2, boxX - 2, boxY + 20 + 2, 1347420415, 1344798847);
        context.fillGradient(
                boxX + boxWidth + 2,
                boxY - 2,
                boxX + boxWidth + 3,
                boxY + 20 + 2,
                1347420415,
                1344798847);
        context.fillGradient(
                boxX - 3, boxY - 3, boxX + boxWidth + 3, boxY - 2, 1347420415, 1347420415);
        context.fillGradient(
                boxX - 3,
                boxY + 20 + 2,
                boxX + boxWidth + 3,
                boxY + 20 + 3,
                1344798847,
                1344798847);
        if (FakeSpawner.getMobType() == FakeSpawner.MobType.Skeleton) {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    TOOLTIP_TEXTURE,
                    boxX,
                    boxY,
                    0.0F,
                    0.0F,
                    104,
                    20,
                    208,
                    40,
                    208,
                    40,
                    -1);
        } else {
            PixelFont.drawString(context, label, boxX, boxY, -16712568);
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    TOOLTIP_TEXTURE,
                    boxX,
                    boxY + 12,
                    0.0F,
                    24.0F,
                    49,
                    8,
                    98,
                    16,
                    208,
                    40,
                    -1);
        }
    }

    private void drawTextTooltip(DrawContext context, List<Text> lines, int x, int y) {
        if (context != null && this.textRenderer != null && lines != null && !lines.isEmpty()) {
            try {
                context.drawTooltip(this.textRenderer, lines, x, y);
            } catch (Throwable e) {
                logError("rendering tooltip", e);
            }
        }
    }

    private int getMenuSlotX(int index) {
        return this.panelX + 8 + index * 18;
    }

    private int getMenuSlotY() {
        return this.panelY + 108;
    }

    private static boolean isInsideSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseY >= slotY && mouseX < slotX + 18 && mouseY < slotY + 18;
    }

    private static void logError(String action, Throwable error) {
        if (!errorLogged) {
            errorLogged = true;
            System.err.println(
                    "[OdinClient] Fake Spawner visual menu error while " + action + ": " + error);
        }
    }

    static {
        SELL_ALL_TOOLTIP =
                Arrays.asList(
                        Text.literal("Sell All"),
                        Text.literal("Click to sell all mob drops!").formatted(Formatting.GRAY));
        DROP_LOOT_TOOLTIP =
                Arrays.asList(
                        Text.literal("Drop Loot"),
                        Text.literal("Click to drop all loot on the page")
                                .formatted(Formatting.GRAY));
    }
}
