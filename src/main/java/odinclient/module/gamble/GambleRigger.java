package odinclient.module.gamble;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.DispenserBlock;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.KeybindSetting;
import odinclient.setting.type.StringSetting;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Environment(EnvType.CLIENT)
public final class GambleRigger extends Module {
    private static final Gson GSON = new Gson();
    private static final Block LEVER = getVanillaBlock("lever");
    private static final Block DROPPER = getVanillaBlock("dropper");
    private static final Block DISPENSER = getVanillaBlock("dispenser");
    private static GambleRigger instance;
    private final Setting<String>[] dropperContentSettings;
    private final Setting<String>[] positionSettings;
    private final Setting<Integer>[] activationKeySettings;
    private final Setting<Boolean> chatMessages;
    private final int[] lastKeyCodes;
    private final boolean[] keyHeld;
    private final Map<ItemEntity, GambleRigger.DropperSlotEntry> entityOutcomes;
    private final OutcomePlanner<GambleRigger.DropperSlotEntry> outcomePlanner;
    private final ItemSlotTracker<ItemStack, GambleRigger.DropperSlotEntry> stackTracker;
    private final Map<ItemStack, GambleRigger.DropperSlotEntry> stackOutcomes;
    private Object currentWorld;
    private GambleRigger.RigSetup armedSetup;
    private GambleRigger.DropperSlotEntry[][] armedContents;
    private OutcomePlanner.ActiveRoll<GambleRigger.DropperSlotEntry> activeRoll;
    private GambleRigger.RigSetup rollSetup;
    private GambleRigger.DropperSlotEntry[][] rollContents;
    private final GambleRigger.DropperView[] dropperViews;
    private GambleRigger.PendingDropperClick pendingDropperClick;
    private GambleRigger.OpenDropperScreen openDropper;
    private GambleRigger.SelectionTarget selectionTarget;
    private Screen returnScreen;

    public GambleRigger(Category category) {
        super(
                category,
                "gamble-rigger",
                "Locally shows configured dropper outcomes after the next lever pull; never changes"
                    + " server items.");
        this.lastKeyCodes = new int[] {-2147483648, -2147483648, -2147483648};
        this.keyHeld = new boolean[3];
        this.entityOutcomes = new WeakHashMap<>();
        this.outcomePlanner = new OutcomePlanner<>();
        this.stackTracker = new ItemSlotTracker<>(GambleRigger::stacksMatch, ItemStack::getCount);
        this.stackOutcomes = new IdentityHashMap<>();
        this.dropperViews = new GambleRigger.DropperView[2];
        this.title = "Gamble Rigger";
        SettingGroup general = this.settings.getGeneral();
        this.dropperContentSettings = new Setting[2];
        this.positionSettings = new Setting[3];
        this.activationKeySettings = new Setting[3];

        for (int i = 0; i < 2; i++) {
            int number = i + 1;
            general.add(
                    new StringSetting.Builder()
                            .name("dropper-" + number)
                            .description(
                                    "Open the nine block/item and display-name slots for Dropper "
                                            + number
                                            + ".")
                            .defaultValue("OPEN MENU")
                            .build());
            this.dropperContentSettings[i] =
                    general.add(
                            new StringSetting.Builder()
                                    .name("dropper-" + number + "-contents")
                                    .defaultValue("[]")
                                    .visibleWhen(() -> false)
                                    .build());
        }

        String[] selectNames =
                new String[] {"dropper-1-select", "dropper-2-select", "lever-select"};
        String[] positionNames =
                new String[] {"dropper-1-position", "dropper-2-position", "lever-position"};

        for (int j = 0; j < 3; j++) {
            String targetName = j == 2 ? "Lever" : "Dropper " + (j + 1);
            String selectDescription =
                    "Click, then right-click the actual "
                            + targetName
                            + " in the world. This selection click stays local.";
            general.add(
                    new StringSetting.Builder()
                            .name(selectNames[j])
                            .description(selectDescription)
                            .defaultValue("SELECT")
                            .build());
            this.positionSettings[j] =
                    general.add(
                            new StringSetting.Builder()
                                    .name(positionNames[j])
                                    .defaultValue("")
                                    .visibleWhen(() -> false)
                                    .build());
        }

        String[] keyNames =
                new String[] {"lose-activation-key", "tie-activation-key", "win-activation-key"};

        for (int k = 0; k < 3; k++) {
            int index = k;
            this.activationKeySettings[k] =
                    general.add(
                            new KeybindSetting.Builder()
                                    .name(keyNames[k])
                                    .description(
                                            "Arm the next lever pull for "
                                                    + keyNames[k].split("-")[0]
                                                    + ". Unbound disables this outcome.")
                                    .defaultValue(-1)
                                    .onChange(key -> this.validateOutcomeKey(index, key))
                                    .build());
        }

        this.chatMessages =
                general.add(
                        new BooleanSetting.Builder()
                                .name("chat-messages")
                                .description("Show setup, activation and warning messages in chat.")
                                .defaultValue(false)
                                .build());
        instance = this;
    }

    public GambleRigger.DropperSlotEntry[] getDropperContents(int index) {
        GambleRigger.DropperSlotEntry[] contents = new GambleRigger.DropperSlotEntry[9];
        if (index >= 0 && index <= 1) {
            try {
                JsonArray array =
                        GSON.fromJson(
                                this.dropperContentSettings[index].getValue(), JsonArray.class);
                if (array == null) {
                    return contents;
                }

                for (int i = 0; i < Math.min(9, array.size()); i++) {
                    JsonElement element = array.get(i);
                    if (element != null && element.isJsonObject()) {
                        JsonObject object = element.getAsJsonObject();
                        if ((object.has("item") || object.has("block")) && object.has("name")) {
                            String itemId =
                                    object.has("item")
                                            ? object.get("item").getAsString()
                                            : object.get("block").getAsString();
                            String name = object.get("name").getAsString();
                            if (isValidItemId(itemId)) {
                                contents[i] =
                                        new GambleRigger.DropperSlotEntry(
                                                itemId, sanitizeDisplayName(name));
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }

        return contents;
    }

    public void setDropperContents(int index, GambleRigger.DropperSlotEntry[] contents) {
        if (index >= 0 && index <= 1 && contents != null && contents.length == 9) {
            JsonArray array = new JsonArray();

            for (GambleRigger.DropperSlotEntry entry : contents) {
                JsonObject object = new JsonObject();
                if (entry != null && isValidItemId(entry.itemId())) {
                    object.addProperty("item", entry.itemId());
                    object.addProperty("name", sanitizeDisplayName(entry.name()));
                }

                array.add(object);
            }

            this.dropperContentSettings[index].setValue(GSON.toJson(array));
        }
    }

    public static boolean isValidItemId(String id) {
        if (id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            String[] parts = id.split(":", 2);
            Item item = Registries.ITEM.get(Identifier.of(parts[0], parts[1]));
            return item != null && !new ItemStack(item).isEmpty();
        } else {
            return false;
        }
    }

    private static String sanitizeDisplayName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\p{Cntrl}\u00a7]", "").strip();
        return cleaned.substring(0, Math.min(48, cleaned.length()));
    }

    public static boolean isSelectSetting(String settingName) {
        return "dropper-1-select".equals(settingName)
                || "dropper-2-select".equals(settingName)
                || "lever-select".equals(settingName);
    }

    private static GambleRigger.SelectionTarget targetForSetting(String settingName) {
        return switch (settingName) {
            case "dropper-1-select" -> GambleRigger.SelectionTarget.DROPPER_1;
            case "dropper-2-select" -> GambleRigger.SelectionTarget.DROPPER_2;
            case "lever-select" -> GambleRigger.SelectionTarget.LEVER;
            default -> null;
        };
    }

    public String getSelectButtonLabel(String settingName) {
        GambleRigger.SelectionTarget target = targetForSetting(settingName);
        return target != null && this.getSelectedPos(target) != null ? "SET" : "SELECT";
    }

    public void beginSelection(String settingName, Screen screen) {
        GambleRigger.SelectionTarget target = targetForSetting(settingName);
        if (target != null) {
            if (this.mc.world != null && this.mc.player != null) {
                this.selectionTarget = target;
                this.returnScreen = screen;
                this.outcomePlanner.clearPlan();
                this.armedSetup = null;
                this.armedContents = null;
                this.mc.setScreen(null);
                this.sendInfo(
                        "Right-click %s to select it. The click will not reach the server.",
                        getTargetLabel(target));
            } else {
                this.sendWarning("Enter a world before selecting a dropper or lever.");
            }
        }
    }

    public static boolean onSelectionClick(BlockHitResult hitResult) {
        GambleRigger rigger = instance;
        if (rigger != null && rigger.selectionTarget != null) {
            GambleRigger.SelectionTarget target = rigger.selectionTarget;
            if (hitResult != null && rigger.mc.world != null) {
                BlockPos blockPos = hitResult.getBlockPos();
                boolean valid;
                if (target == GambleRigger.SelectionTarget.LEVER) {
                    valid = rigger.isBlockAt(blockPos, "lever");
                } else {
                    valid =
                            rigger.isBlockAt(blockPos, "dropper")
                                    || rigger.isBlockAt(blockPos, "dispenser");
                }

                if (!valid) {
                    rigger.sendWarning(
                            "That is not %s. Right-click the correct block.",
                            getTargetLabel(target));
                    return true;
                } else {
                    rigger.positionSettings[target.ordinal()].setValue(
                            rigger.getWorldKey()
                                    + "|"
                                    + blockPos.getX()
                                    + ","
                                    + blockPos.getY()
                                    + ","
                                    + blockPos.getZ());
                    if (target != GambleRigger.SelectionTarget.LEVER) {
                        rigger.dropperViews[target.ordinal()] = null;
                    }

                    rigger.pendingDropperClick = null;
                    rigger.openDropper = null;
                    rigger.selectionTarget = null;
                    Screen previousScreen = rigger.returnScreen;
                    rigger.returnScreen = null;
                    rigger.sendInfo("%s selected.", getTargetLabel(target));
                    if (previousScreen != null) {
                        rigger.mc.setScreen(previousScreen);
                    }

                    return true;
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private static String getTargetLabel(GambleRigger.SelectionTarget target) {
        return switch (target) {
            case DROPPER_1 -> "Dropper 1";
            case DROPPER_2 -> "Dropper 2";
            case LEVER -> "the lever";
        };
    }

    private String getWorldKey() {
        return this.mc.world.getRegistryKey().getValue().toString();
    }

    private BlockPos getSelectedPos(GambleRigger.SelectionTarget target) {
        if (this.mc.world == null) {
            return null;
        } else {
            try {
                String[] worldAndPos =
                        this.positionSettings[target.ordinal()].getValue().split("\\|", 2);
                if (worldAndPos.length == 2 && worldAndPos[0].equals(this.getWorldKey())) {
                    String[] coords = worldAndPos[1].split(",", 3);
                    return coords.length != 3
                            ? null
                            : new BlockPos(
                                    Integer.parseInt(coords[0]),
                                    Integer.parseInt(coords[1]),
                                    Integer.parseInt(coords[2]));
                } else {
                    return null;
                }
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private GambleRigger.RigSetup buildSetup() {
        BlockPos firstDropper = this.getSelectedPos(GambleRigger.SelectionTarget.DROPPER_1);
        BlockPos secondDropper = this.getSelectedPos(GambleRigger.SelectionTarget.DROPPER_2);
        BlockPos lever = this.getSelectedPos(GambleRigger.SelectionTarget.LEVER);
        return firstDropper != null
                        && secondDropper != null
                        && lever != null
                        && !firstDropper.equals(secondDropper)
                        && (this.isBlockAt(firstDropper, "dropper")
                                || this.isBlockAt(firstDropper, "dispenser"))
                        && (this.isBlockAt(secondDropper, "dropper")
                                || this.isBlockAt(secondDropper, "dispenser"))
                        && this.isBlockAt(lever, "lever")
                ? new GambleRigger.RigSetup(lever, firstDropper, secondDropper)
                : null;
    }

    private void validateOutcomeKey(int index, Integer keyCode) {
        if (keyCode != null && keyCode >= 0) {
            if (keyCode == this.keybind.getValue()) {
                this.activationKeySettings[index].setValue(-1);
                this.sendWarning("An outcome key cannot also toggle Gamble Rigger.");
            } else {
                for (int i = 0; i < this.activationKeySettings.length; i++) {
                    if (i != index
                            && this.activationKeySettings[i] != null
                            && keyCode.equals(this.activationKeySettings[i].getValue())) {
                        this.activationKeySettings[index].setValue(-1);
                        this.sendWarning("Lose, Tie and Win need different activation keys.");
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    private void reset() {
        this.outcomePlanner.reset();
        this.armedSetup = null;
        this.armedContents = null;
        this.activeRoll = null;
        this.rollSetup = null;
        this.rollContents = null;
        this.dropperViews[0] = null;
        this.dropperViews[1] = null;
        this.pendingDropperClick = null;
        this.openDropper = null;
        this.entityOutcomes.clear();
        this.stackTracker.reset();
        this.stackOutcomes.clear();
        this.currentWorld = null;
        this.selectionTarget = null;
        this.returnScreen = null;

        for (int i = 0; i < 3; i++) {
            this.lastKeyCodes[i] = -2147483648;
            this.keyHeld[i] = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.world != null && this.mc.player != null && this.mc.getWindow() != null) {
            this.checkWorldChange();
            if (this.activeRoll != null && this.activeRoll.isExpired(System.nanoTime())) {
                this.activeRoll = null;
            }

            this.entityOutcomes.keySet().removeIf(Entity::isRemoved);
            this.trackInventory();

            for (int i = 0; i < 3; i++) {
                int keyCode = this.activationKeySettings[i].getValue();
                boolean pressed = this.isInputPressed(keyCode);
                if (keyCode != this.lastKeyCodes[i]) {
                    this.lastKeyCodes[i] = keyCode;
                    this.keyHeld[i] = pressed;
                } else {
                    boolean justPressed = pressed && !this.keyHeld[i];
                    this.keyHeld[i] = pressed;
                    if (justPressed
                            && this.mc.currentScreen == null
                            && keyCode != this.keybind.getValue()) {
                        this.armOutcome(i);
                    }
                }
            }
        } else {
            this.reset();
        }
    }

    private void checkWorldChange() {
        if (this.currentWorld != this.mc.world) {
            this.reset();
            this.currentWorld = this.mc.world;
        }
    }

    private void armOutcome(int outcomeIndex) {
        GambleRigger.RigSetup setup = this.buildSetup();
        this.outcomePlanner.clearPlan();
        this.armedSetup = null;
        this.armedContents = null;
        if (setup == null) {
            this.sendWarning("Select both droppers and the lever first.");
        } else {
            GambleRigger.DropperSlotEntry[][] contents =
                    new GambleRigger.DropperSlotEntry[][] {
                        this.getDropperContents(0), this.getDropperContents(1)
                    };
            if (this.outcomePlanner.plan(
                            OutcomePlanner.Outcome.values()[outcomeIndex],
                            contents[0],
                            contents[1],
                            ThreadLocalRandom.current())
                    == null) {
                this.sendWarning("No configured item pair can produce this outcome.");
            } else {
                this.armedSetup = setup;
                this.armedContents = contents;
                this.sendInfo(
                        "%s ready for the next selected lever pull.",
                        GambleRigger.Outcome.values()[outcomeIndex]);
            }
        }
    }

    private void sendInfo(String message, Object... args) {
        if (this.chatMessages != null && Boolean.TRUE.equals(this.chatMessages.getValue())) {
            this.info(message, args);
        }
    }

    private void sendWarning(String message, Object... args) {
        if (this.chatMessages != null && Boolean.TRUE.equals(this.chatMessages.getValue())) {
            this.warning(message, args);
        }
    }

    public static void onKeyEvent(long window, int key, int action) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.mc.world != null
                && rigger.mc.player != null
                && rigger.mc.getWindow() != null
                && window == rigger.mc.getWindow().getHandle()) {
            rigger.checkWorldChange();

            for (int i = 0; i < 3; i++) {
                if (key >= 0 && key == rigger.activationKeySettings[i].getValue()) {
                    rigger.lastKeyCodes[i] = key;
                    if (action == 0) {
                        rigger.keyHeld[i] = false;
                    } else if (action == 1 && !rigger.keyHeld[i]) {
                        rigger.keyHeld[i] = true;
                        if (rigger.mc.currentScreen == null && key != rigger.keybind.getValue()) {
                            rigger.armOutcome(i);
                        }
                    }
                }
            }
        }
    }

    private boolean isInputPressed(int keyCode) {
        if (keyCode >= 1000 && keyCode <= 1007) {
            int button = keyCode - 1000;
            return GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), button) == 1;
        } else {
            return keyCode >= 0
                    && keyCode <= 348
                    && InputUtil.isKeyPressed(this.mc.getWindow(), keyCode);
        }
    }

    public static void onLeverUse(BlockHitResult hitResult) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.outcomePlanner.hasPlan()
                && hitResult != null
                && rigger.mc.world != null
                && rigger.mc.player != null) {
            BlockPos blockPos = hitResult.getBlockPos();
            if (rigger.armedSetup != null
                    && rigger.armedSetup.lever().equals(blockPos)
                    && rigger.isBlockAt(blockPos, "lever")) {
                rigger.checkWorldChange();
                if (rigger.armedSetup != null) {
                    if (!rigger.armedSetup.equals(rigger.buildSetup())) {
                        rigger.outcomePlanner.clearPlan();
                        rigger.armedSetup = null;
                        rigger.armedContents = null;
                        rigger.sendWarning(
                                "The selected dropper setup changed. Select the droppers and lever"
                                    + " again.");
                    } else {
                        rigger.startRiggedPull(rigger.armedSetup);
                    }
                }
            }
        }
    }

    private void startRiggedPull(GambleRigger.RigSetup setup) {
        this.activeRoll =
                this.outcomePlanner.start(
                        this.getDropPoint(setup.left()),
                        this.getDropPoint(setup.right()),
                        System.nanoTime());
        this.rollSetup = setup;
        this.rollContents = this.armedContents;
        this.armedSetup = null;
        this.armedContents = null;
        this.trackInventory();
    }

    private OutcomePlanner.DropPoint getDropPoint(BlockPos pos) {
        Direction facing = this.mc.world.getBlockState(pos).get(DispenserBlock.FACING);
        double x = pos.getX() + 0.5 + facing.getOffsetX() * 0.7;
        double y = pos.getY() + 0.5 + facing.getOffsetY() * 0.7;
        double yOffset = facing.getOffsetY() == 0 ? 0.15625 : 0.125;
        return new OutcomePlanner.DropPoint(
                x, y - yOffset, pos.getZ() + 0.5 + facing.getOffsetZ() * 0.7);
    }

    private boolean isBlockAt(BlockPos pos, String blockName) {
        return this.mc
                .world
                .getBlockState(pos)
                .isOf(
                        switch (blockName) {
                            case "lever" -> LEVER;
                            case "dropper" -> DROPPER;
                            case "dispenser" -> DISPENSER;
                            default -> getVanillaBlock(blockName);
                        });
    }

    private static Block getVanillaBlock(String name) {
        return Registries.BLOCK.get(Identifier.of("minecraft", name));
    }

    public static void onItemSpawn(Object world, Entity entity) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && world == rigger.currentWorld
                && rigger.activeRoll != null
                && entity instanceof ItemEntity itemEntity) {
            GambleRigger.DropperSlotEntry entry =
                    rigger.activeRoll.claimItem(
                            itemEntity.getId(),
                            new OutcomePlanner.DropPoint(
                                    itemEntity.getX(), itemEntity.getY(), itemEntity.getZ()),
                            System.nanoTime());
            if (entry != null) {
                rigger.entityOutcomes.put(itemEntity, entry);
                int dropperIndex = rigger.activeRoll.getDropperIndex(itemEntity.getId());
                if (dropperIndex >= 0 && rigger.rollSetup != null && rigger.rollContents != null) {
                    int slotIndex =
                            dropperIndex == 0
                                    ? rigger.activeRoll.getPlan().pair().left()
                                    : rigger.activeRoll.getPlan().pair().right();
                    BlockPos dropperPos =
                            dropperIndex == 0 ? rigger.rollSetup.left() : rigger.rollSetup.right();

                    rigger.dropperViews[dropperIndex] =
                            new GambleRigger.DropperView(
                                    dropperPos,
                                    new odinclient.module.gamble.DropperView<>(
                                            rigger.rollContents[dropperIndex],
                                            slotIndex,
                                            GambleRigger::stacksMatch,
                                            ItemStack::getCount));
                }
            }
        }
    }

    public static void onDropperUse(BlockHitResult hitResult) {
        GambleRigger rigger = instance;
        if (rigger != null && rigger.isActive() && hitResult != null && rigger.mc.world != null) {
            rigger.checkWorldChange();
            rigger.pendingDropperClick = null;
            BlockPos blockPos = hitResult.getBlockPos();

            for (int i = 0; i < 2; i++) {
                if (blockPos.equals(rigger.getSelectedPos(GambleRigger.SelectionTarget.values()[i]))
                        && (rigger.isBlockAt(blockPos, "dropper")
                                || rigger.isBlockAt(blockPos, "dispenser"))) {
                    rigger.pendingDropperClick =
                            new GambleRigger.PendingDropperClick(
                                    rigger.mc.world, blockPos, i, System.nanoTime());
                    return;
                }
            }
        }
    }

    public static void onOpenScreen(OpenScreenS2CPacket packet) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.mc.player != null
                && rigger.mc.isOnThread()) {
            rigger.openDropper = null;
            GambleRigger.PendingDropperClick click = rigger.pendingDropperClick;
            rigger.pendingDropperClick = null;
            ScreenHandler handler = rigger.mc.player.currentScreenHandler;
            if (click != null
                    && click.world() == rigger.mc.world
                    && System.nanoTime() - click.started() <= 10000000000L
                    && handler instanceof Generic3x3ContainerScreenHandler
                    && handler.syncId == packet.getSyncId()) {
                rigger.openDropper =
                        new GambleRigger.OpenDropperScreen(handler, click.pos(), click.side());
            }

            syncInventory();
        }
    }

    public static void onInventoryUpdate(int syncId, boolean synced) {
        syncInventory();
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.mc.isOnThread()
                && rigger.mc.player != null
                && rigger.currentWorld == rigger.mc.world) {
            GambleRigger.OpenDropperScreen screen = rigger.openDropper;
            if (screen != null
                    && screen.handler == rigger.mc.player.currentScreenHandler
                    && screen.handler.syncId == syncId) {
                if (synced) {
                    screen.synced = true;
                }

                rigger.refreshDropperView(screen);
            }
        }
    }

    private void refreshDropperView(GambleRigger.OpenDropperScreen screen) {
        GambleRigger.DropperView dropperView = this.dropperViews[screen.dropperIndex];
        if (screen.synced && dropperView != null && dropperView.pos().equals(screen.pos)) {
            ArrayList<ItemStack> snapshot = new ArrayList<>(9);

            for (int i = 0; i < 9; i++) {
                snapshot.add(screen.handler.getSlot(i).getStack().copy());
            }

            dropperView.view().updateSnapshot(snapshot);
        }
    }

    public static void onSlotsUpdated(ScreenHandler handler) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.mc.player != null
                && rigger.currentWorld == rigger.mc.world) {
            if (rigger.openDropper != null && rigger.openDropper.handler != handler) {
                rigger.openDropper = null;
            }

            rigger.trackInventory();
            if (rigger.openDropper != null) {
                rigger.refreshDropperView(rigger.openDropper);
            }
        }
    }

    public static ItemStack getDisplayedSlotStack(
            ScreenHandler handler, Slot slot, ItemStack stack) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.mc.player != null
                && rigger.currentWorld == rigger.mc.world) {
            GambleRigger.OpenDropperScreen screen = rigger.openDropper;
            if (screen != null
                    && screen.handler == handler
                    && handler == rigger.mc.player.currentScreenHandler) {
                int slotId = slot.id;
                if (slotId >= 0 && slotId < 9 && handler.getSlot(slotId) == slot) {
                    GambleRigger.DropperView dropperView = rigger.dropperViews[screen.dropperIndex];
                    if (dropperView != null
                            && dropperView.pos().equals(screen.pos)
                            && screen.pos.equals(
                                    rigger.getSelectedPos(
                                            GambleRigger.SelectionTarget.values()[
                                                    screen.dropperIndex]))) {
                        odinclient.module.gamble.DropperView.DropperSlotView slotView =
                                screen.synced
                                        ? dropperView.view().viewSlot(slotId, stack.getCount())
                                        : dropperView.view().viewSlotFromSnapshot(slotId);
                        if (slotView == null) {
                            return null;
                        } else {
                            return slotView.empty()
                                    ? ItemStack.EMPTY
                                    : createDisplayStack(
                                            (GambleRigger.DropperSlotEntry) slotView.appearance(),
                                            slotView.count());
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static void onSlotClick(
            ScreenHandler handler,
            int slotIndex,
            int button,
            SlotActionType actionType,
            boolean clientSide) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.mc.player != null
                && rigger.currentWorld == rigger.mc.world
                && clientSide
                && actionType == SlotActionType.PICKUP
                && button >= 0
                && button <= 1
                && slotIndex >= 0
                && slotIndex < 9) {
            GambleRigger.OpenDropperScreen screen = rigger.openDropper;
            if (screen != null
                    && screen.synced
                    && screen.handler == handler
                    && handler == rigger.mc.player.currentScreenHandler
                    && !handler.getSlot(slotIndex).getStack().isEmpty()) {
                GambleRigger.DropperView dropperView = rigger.dropperViews[screen.dropperIndex];
                if (dropperView != null && dropperView.pos().equals(screen.pos)) {
                    dropperView.view().markTaken(slotIndex);
                    rigger.refreshDropperView(screen);
                }
            }
        }
    }

    public static void onItemPickup(ItemPickupAnimationS2CPacket packet) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.mc.isOnThread()
                && rigger.mc.world != null
                && rigger.mc.player != null
                && rigger.currentWorld == rigger.mc.world
                && packet.getCollectorEntityId() == rigger.mc.player.getId()) {
            if (rigger.mc.world.getEntityById(packet.getEntityId())
                    instanceof ItemEntity itemEntity) {
                GambleRigger.DropperSlotEntry entry = rigger.entityOutcomes.get(itemEntity);
                if (entry != null) {
                    rigger.trackInventory();
                    rigger.stackTracker.recordPickup(
                            itemEntity.getStack().copy(),
                            entry,
                            packet.getStackAmount(),
                            System.nanoTime());
                    rigger.rebuildStackMap();
                }
            }
        }
    }

    public static void syncInventory() {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && rigger.mc.isOnThread()
                && rigger.mc.player != null
                && rigger.currentWorld == rigger.mc.world) {
            rigger.trackInventory();
        }
    }

    public static ItemStack getEntityDisplayStack(ItemEntity itemEntity) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && itemEntity != null
                && rigger.currentWorld == rigger.mc.world) {
            GambleRigger.DropperSlotEntry entry = rigger.entityOutcomes.get(itemEntity);
            return entry == null
                    ? null
                    : createDisplayStack(entry, itemEntity.getStack().getCount());
        } else {
            return null;
        }
    }

    public static ItemStack getStackDisplayOverride(ItemStack stack) {
        GambleRigger rigger = instance;
        if (rigger != null
                && rigger.isActive()
                && stack != null
                && rigger.currentWorld == rigger.mc.world) {
            GambleRigger.DropperSlotEntry entry = rigger.stackOutcomes.get(stack);
            return entry == null ? null : createDisplayStack(entry, stack.getCount());
        } else {
            return null;
        }
    }

    private static ItemStack createDisplayStack(GambleRigger.DropperSlotEntry entry, int count) {
        String[] parts = entry.itemId().split(":", 2);
        Item item = Registries.ITEM.get(Identifier.of(parts[0], parts[1]));
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (!entry.name().isBlank()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(entry.name()));
        }

        return stack;
    }

    private void trackInventory() {
        ArrayList<ItemStack> stacks = new ArrayList<>();
        int size = this.mc.player.getInventory().size();

        for (int i = 0; i <= size; i++) {
            stacks.add(this.getTrackedStack(i).copy());
        }

        this.stackTracker.updateSnapshot(stacks, System.nanoTime());
        this.rebuildStackMap();
    }

    private ItemStack getTrackedStack(int slot) {
        return slot == this.mc.player.getInventory().size()
                ? this.mc.player.currentScreenHandler.getCursorStack()
                : this.mc.player.getInventory().getStack(slot);
    }

    private void rebuildStackMap() {
        this.stackOutcomes.clear();
        this.stackTracker
                .getAppearances()
                .forEach(
                        (slot, entry) -> this.stackOutcomes.put(this.getTrackedStack(slot), entry));
    }

    private static boolean stacksMatch(ItemStack first, ItemStack second) {
        return first != null
                && second != null
                && !first.isEmpty()
                && !second.isEmpty()
                && ItemStack.areItemsAndComponentsEqual(first, second);
    }

    public record DropperSlotEntry(String itemId, String name) {}

    private record DropperView(
            BlockPos pos,
            odinclient.module.gamble.DropperView<GambleRigger.DropperSlotEntry, ItemStack> view) {}

    private static final class OpenDropperScreen {
        final ScreenHandler handler;
        final BlockPos pos;
        final int dropperIndex;
        boolean synced;

        OpenDropperScreen(ScreenHandler handler, BlockPos pos, int dropperIndex) {
            this.handler = handler;
            this.pos = pos;
            this.dropperIndex = dropperIndex;
        }
    }

    public static enum Outcome {
        LOSE,
        TIE,
        WIN;
    }

    private record PendingDropperClick(Object world, BlockPos pos, int side, long started) {}

    private record RigSetup(BlockPos lever, BlockPos left, BlockPos right) {}

    public static enum SelectionTarget {
        DROPPER_1,
        DROPPER_2,
        LEVER;
    }
}
