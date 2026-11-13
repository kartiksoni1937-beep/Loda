package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.NumberSetting;
import odinclient.util.InventoryActions;
import odinclient.util.WorldUtils;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Places and breaks End Crystals on the base under the crosshair while right click is held.
 *
 * <p>The macro is a state machine: it queues one action, then waits for the client world to show
 * the result before queueing the next step. A step that never materialises within
 * {@link #TIMEOUT_TICKS} drops the macro back to idle instead of retrying blindly.
 */
@Environment(EnvType.CLIENT)
public class AutoCrystal extends Module {

    /** Ticks a wait phase tolerates before the macro goes idle. */
    private static final int TIMEOUT_TICKS = 10;

    private enum Phase {
        IDLE,
        PLACE,
        WAIT_PLACED,
        BREAK,
        WAIT_BROKEN
    }

    private static AutoCrystal instance;
    private final Setting<Double> breakDelay;
    private final Setting<Double> placeDelay;
    private double breakTimer;
    private double placeTimer;
    private BlockPos targetPos;
    private BlockPos lastPlacePos;
    private int lastBrokenCrystalId = -1;
    private Phase phase = Phase.IDLE;
    private int waitTicks;

    public AutoCrystal(Category category) {
        super(
                category,
                "auto-crystal",
                "Hold RMB to place on existing bases; breaks only crystals directly under your"
                    + " crosshair.");
        instance = this;
        SettingGroup general = this.settings.getGeneral();
        this.breakDelay =
                general.add(
                        new NumberSetting.Builder()
                                .name("break-delay")
                                .description("Ticks between crystal breaks.")
                                .defaultValue(1.0)
                                .range(0.0, 20.0)
                                .sliderRange(0.0, 20.0)
                                .build());
        this.placeDelay =
                general.add(
                        new NumberSetting.Builder()
                                .name("place-delay")
                                .description("Ticks between crystal placements.")
                                .defaultValue(1.0)
                                .range(0.0, 20.0)
                                .sliderRange(0.0, 20.0)
                                .build());
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        this.resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!this.canRun()) {
            this.reset();
            return;
        }

        this.tickDelays();

        switch (this.phase) {
            case IDLE -> this.tickIdle();
            case PLACE -> this.tickPlace();
            case WAIT_PLACED -> this.tickWaitPlaced();
            case BREAK -> this.tickBreak();
            case WAIT_BROKEN -> this.tickWaitBroken();
        }
    }

    /** Phase 0: pick a base under the crosshair and decide whether to place or break. */
    private void tickIdle() {
        MinecraftClient client = this.mc;
        if (!(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != Type.BLOCK) {
            this.targetPos = null;
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (!isCrystalBase(client.world, pos)
                || !client.world.getBlockState(pos.up()).isAir()) {
            this.targetPos = null;
            return;
        }

        this.targetPos = pos;
        if (getCrystalAbove(client.world, pos) != null) {
            if (this.breakTimer <= 0.0) {
                this.phase = Phase.BREAK;
            }

            return;
        }

        if (this.placeTimer > 0.0 || !canPlaceCrystalAt(client.world, pos)) {
            return;
        }

        this.phase = Phase.PLACE;
    }

    /** Phase 1: queue the crystal placement. */
    private void tickPlace() {
        MinecraftClient client = this.mc;
        BlockPos pos = this.targetPos;
        if (pos == null || !canPlaceCrystalAt(client.world, pos)) {
            this.reset();
            return;
        }

        Hand hand = this.getCrystalHand(client);
        if (hand == null) {
            this.phase = Phase.IDLE;
            return;
        }

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        if (WorldUtils.interactBlock(hit, hand)) {
            this.lastPlacePos = pos;
            this.lastBrokenCrystalId = -1;
            this.placeTimer = this.placeTimer + this.placeDelay.getValue();
            this.phase = Phase.WAIT_PLACED;
            this.waitTicks = TIMEOUT_TICKS;
        }
    }

    /** Phase 2: wait for the crystal entity to exist. */
    private void tickWaitPlaced() {
        if (this.targetPos == null) {
            this.reset();
            return;
        }

        if (getCrystalAbove(this.mc.world, this.targetPos) != null) {
            this.phase = Phase.BREAK;
            return;
        }

        if (--this.waitTicks <= 0) {
            this.reset();
        }
    }

    /** Phase 3: queue the attack on the crystal that is now there. */
    private void tickBreak() {
        if (this.targetPos == null) {
            this.reset();
            return;
        }

        EndCrystalEntity crystal = getCrystalAbove(this.mc.world, this.targetPos);
        if (crystal == null) {
            this.phase = Phase.IDLE;
            return;
        }

        if (this.breakTimer > 0.0 || crystal.getId() == this.lastBrokenCrystalId) {
            return;
        }

        if (WorldUtils.hitEntity(crystal)) {
            this.lastBrokenCrystalId = crystal.getId();
            this.breakTimer = this.breakTimer + this.breakDelay.getValue();
            this.phase = Phase.WAIT_BROKEN;
            this.waitTicks = TIMEOUT_TICKS;
        }
    }

    /** Phase 4: wait for the crystal to be gone. */
    private void tickWaitBroken() {
        if (this.targetPos == null
                || getCrystalAbove(this.mc.world, this.targetPos) == null
                || --this.waitTicks <= 0) {
            this.phase = Phase.IDLE;
            this.targetPos = null;
        }
    }

    private boolean canRun() {
        MinecraftClient client = this.mc;
        if (!this.isActive()
                || client.player == null
                || client.world == null
                || client.getNetworkHandler() == null
                || client.currentScreen != null
                || !client.isWindowFocused()
                || !client.player.isAlive()
                || client.player.isSpectator()
                || !this.isUseButtonHeld(client)
                || client.player.isUsingItem()) {
            return false;
        }

        boolean holdingCrystal = client.player.getMainHandStack().isOf(Items.END_CRYSTAL);
        boolean mainHandEmpty = client.player.getMainHandStack().isEmpty();
        boolean offHandCrystal = client.player.getOffHandStack().isOf(Items.END_CRYSTAL);
        return holdingCrystal || mainHandEmpty && offHandCrystal;
    }

    private Hand getCrystalHand(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            return Hand.MAIN_HAND;
        } else {
            return player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null;
        }
    }

    private static boolean isCrystalBase(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    private static EndCrystalEntity getCrystalAbove(World world, BlockPos pos) {
        BlockPos above = pos.up();
        List<EndCrystalEntity> crystals =
                world.getEntitiesByClass(
                        EndCrystalEntity.class,
                        new Box(
                                above.getX() + 0.125,
                                above.getY() - 0.1,
                                above.getZ() + 0.125,
                                above.getX() + 0.875,
                                above.getY() + 2.5,
                                above.getZ() + 0.875),
                        e -> true);
        return crystals.isEmpty() ? null : crystals.get(0);
    }

    private static boolean canPlaceCrystalAt(World world, BlockPos pos) {
        BlockPos above = pos.up();
        if (!world.getBlockState(above).isAir()) {
            return false;
        } else {
            return world.getNonSpectatingEntities(
                            Entity.class,
                            new Box(
                                    above.getX(),
                                    above.getY(),
                                    above.getZ(),
                                    above.getX() + 1.0,
                                    above.getY() + 2.0,
                                    above.getZ() + 1.0))
                    .isEmpty();
        }
    }

    private void tickDelays() {
        if (this.breakTimer > 0.0) {
            this.breakTimer--;
        }

        if (this.placeTimer > 0.0) {
            this.placeTimer--;
        }
    }

    private boolean isUseButtonHeld(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 1) == 1;
    }

    private void reset() {
        this.phase = Phase.IDLE;
        this.targetPos = null;
        this.waitTicks = 0;
    }

    private void resetState() {
        this.breakTimer = 0.0;
        this.placeTimer = 0.0;
        this.lastPlacePos = null;
        this.lastBrokenCrystalId = -1;
        this.reset();
    }
}
