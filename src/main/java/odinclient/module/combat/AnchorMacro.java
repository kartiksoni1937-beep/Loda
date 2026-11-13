package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.IntSetting;
import odinclient.util.InventoryActions;
import odinclient.util.WorldUtils;

import org.lwjgl.glfw.GLFW;

/**
 * Charges and detonates the Respawn Anchor under the crosshair.
 *
 * <p>Each step is its own phase: one action is queued, then the module waits for the client world to
 * actually show the result before queueing the next one. Nothing is chained blindly, and a step that
 * does not materialise within {@link #TIMEOUT_TICKS} returns the macro to idle.
 */
@Environment(EnvType.CLIENT)
public final class AnchorMacro extends Module {

    /** Ticks a wait phase tolerates before the macro gives up and goes idle. */
    private static final int TIMEOUT_TICKS = 10;

    private enum Phase {
        IDLE,
        CHARGE,
        WAIT_CHARGED,
        EXPLODE,
        WAIT_EXPLODED
    }

    private final Setting<Integer> switchDelay =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("switch-delay")
                                    .description("Ticks before switching items.")
                                    .defaultValue(0)
                                    .range(0, 20)
                                    .sliderRange(0, 20)
                                    .build());
    private final Setting<Integer> glowstoneDelay =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("glowstone-delay")
                                    .description("Ticks before charging the anchor.")
                                    .defaultValue(0)
                                    .range(0, 20)
                                    .sliderRange(0, 20)
                                    .build());
    private final Setting<Integer> explodeDelay =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("explode-delay")
                                    .description("Ticks before exploding the charged anchor.")
                                    .defaultValue(0)
                                    .range(0, 20)
                                    .sliderRange(0, 20)
                                    .build());
    private final Setting<Integer> totemSlot =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("totem-slot")
                                    .description("Hotbar slot selected before exploding.")
                                    .defaultValue(1)
                                    .range(1, 9)
                                    .sliderRange(1, 9)
                                    .build());

    private Phase phase = Phase.IDLE;
    private BlockPos anchorPos;
    private int waitTicks;
    private int delayTicks;

    public AnchorMacro(Category category) {
        super(
                category,
                "anchor-macro",
                "Charges and explodes the Respawn Anchor under the crosshair while right click is"
                    + " held.");
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!InventoryActions.isReady(this.mc)
                || this.mc.currentScreen != null
                || !this.isUseButtonHeld()) {
            this.reset();
            return;
        }

        switch (this.phase) {
            case IDLE -> this.tickIdle();
            case CHARGE -> this.tickCharge();
            case WAIT_CHARGED -> this.tickWaitCharged();
            case EXPLODE -> this.tickExplode();
            case WAIT_EXPLODED -> this.tickWaitExploded();
        }
    }

    /** Waits for the player to aim at an anchor, then picks the next step from the block state. */
    private void tickIdle() {
        BlockPos pos = this.crosshairAnchor();
        if (pos == null) {
            this.delayTicks = 0;
            return;
        }

        this.anchorPos = pos;
        int charges = this.chargesAt(pos);
        if (charges <= 0) {
            if (!this.mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                if (this.delayTicks++ < this.switchDelay.getValue()) {
                    return;
                }

                this.delayTicks = 0;
                InventoryActions.selectItem(this.mc, Items.GLOWSTONE);
                return;
            }

            if (this.delayTicks++ < this.glowstoneDelay.getValue()) {
                return;
            }

            this.delayTicks = 0;
            this.phase = Phase.CHARGE;
        } else {
            int slot = this.totemSlot.getValue() - 1;
            if (this.mc.player.getInventory().getSelectedSlot() != slot) {
                if (this.delayTicks++ < this.switchDelay.getValue()) {
                    return;
                }

                this.delayTicks = 0;
                WorldUtils.setHotbarSlot(slot);
                return;
            }

            if (this.delayTicks++ < this.explodeDelay.getValue()) {
                return;
            }

            this.delayTicks = 0;
            this.phase = Phase.EXPLODE;
        }
    }

    /** Phase 1: queue the glowstone placement on the anchor. */
    private void tickCharge() {
        if (!this.isAnchorAt(this.anchorPos)) {
            this.reset();
            return;
        }

        if (WorldUtils.interactBlock(this.anchorHit(), Hand.MAIN_HAND)) {
            this.phase = Phase.WAIT_CHARGED;
            this.waitTicks = TIMEOUT_TICKS;
        }
    }

    /** Phase 2: wait for the anchor to actually carry charges. */
    private void tickWaitCharged() {
        if (!this.isAnchorAt(this.anchorPos)) {
            this.reset();
            return;
        }

        if (this.chargesAt(this.anchorPos) > 0) {
            this.phase = Phase.EXPLODE;
            return;
        }

        if (--this.waitTicks <= 0) {
            this.reset();
        }
    }

    /** Phase 3: queue the detonation, after the totem slot is selected. */
    private void tickExplode() {
        if (!this.isAnchorAt(this.anchorPos)) {
            this.reset();
            return;
        }

        int slot = this.totemSlot.getValue() - 1;
        if (this.mc.player.getInventory().getSelectedSlot() != slot) {
            WorldUtils.setHotbarSlot(slot);
            return;
        }

        if (WorldUtils.interactBlock(this.anchorHit(), Hand.MAIN_HAND)) {
            this.phase = Phase.WAIT_EXPLODED;
            this.waitTicks = TIMEOUT_TICKS;
        }
    }

    /** Phase 4: wait for the anchor to disappear. */
    private void tickWaitExploded() {
        if (!this.isAnchorAt(this.anchorPos) || --this.waitTicks <= 0) {
            this.reset();
        }
    }

    private BlockHitResult anchorHit() {
        return new BlockHitResult(
                Vec3d.ofCenter(this.anchorPos), Direction.UP, this.anchorPos, false);
    }

    private BlockPos crosshairAnchor() {
        if (this.mc.crosshairTarget instanceof BlockHitResult hit
                && this.mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {
            return hit.getBlockPos();
        }

        return null;
    }

    private boolean isAnchorAt(BlockPos pos) {
        return pos != null && this.mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR);
    }

    private int chargesAt(BlockPos pos) {
        if (pos == null) {
            return 0;
        }

        if (!this.mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
            return 0;
        }

        return this.mc.world.getBlockState(pos).get(Properties.CHARGES);
    }

    private boolean isUseButtonHeld() {
        return GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), 1) == 1;
    }

    private void reset() {
        this.phase = Phase.IDLE;
        this.anchorPos = null;
        this.waitTicks = 0;
        this.delayTicks = 0;
    }
}
