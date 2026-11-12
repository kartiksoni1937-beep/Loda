package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.KeybindSetting;
import odinclient.util.InventoryActions;

@Environment(EnvType.CLIENT)
public final class SafeAnchor extends Module {
    private final Setting<Integer> activateKey =
            this.settings
                    .getGeneral()
                    .add(
                            new KeybindSetting.Builder()
                                    .name("activate-key")
                                    .description("Hold this key to run the safe anchor sequence.")
                                    .defaultValue(-1)
                                    .build());
    private final Setting<Integer> delayMs =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("delay-ms")
                                    .description("Delay between each safe anchor step.")
                                    .defaultValue(30)
                                    .range(0, 200)
                                    .sliderRange(0, 200)
                                    .build());
    private final Setting<Integer> totemSlot =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("totem-slot")
                                    .description(
                                            "Hotbar slot selected before exploding the anchor.")
                                    .defaultValue(1)
                                    .range(1, 9)
                                    .sliderRange(1, 9)
                                    .build());
    private int step;
    private long lastStepTime;
    private BlockPos anchorPos;

    public SafeAnchor(Category category) {
        super(
                category,
                "safe-anchor",
                "Places, shields, charges and safely explodes a Respawn Anchor.");
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
    private void onTick(TickEvent.Post event) {
        if (InventoryActions.isReady(this.mc)
                && this.mc.currentScreen == null
                && InventoryActions.isInputPressed(this.mc, this.activateKey.getValue())) {
            if (System.currentTimeMillis() - this.lastStepTime
                    >= this.delayMs.getValue().intValue()) {
                if (this.mc.crosshairTarget instanceof BlockHitResult hitResult) {
                    switch (this.step) {
                        case 0:
                            if (InventoryActions.selectItem(this.mc, Items.RESPAWN_ANCHOR)) {
                                this.nextStep();
                            } else {
                                this.reset();
                            }
                            break;
                        case 1:
                            BlockPos placePos = hitResult.getBlockPos().offset(hitResult.getSide());
                            if (this.mc.world.getBlockState(placePos).getBlock()
                                    == Blocks.RESPAWN_ANCHOR) {
                                this.anchorPos = placePos;
                                this.nextStep();
                            } else if (this.canPlaceAt(placePos)) {
                                InventoryActions.placeBlock(this.mc, hitResult);
                                this.anchorPos = placePos;
                                this.nextStep();
                            } else {
                                this.reset();
                            }
                            break;
                        case 2:
                            this.placeGlowstoneNextToAnchor();
                            break;
                        case 3:
                            this.chargeAnchor();
                            break;
                        case 4:
                            int slot = this.totemSlot.getValue() - 1;
                            if (slot >= 0 && slot < 9) {
                                this.mc.player.getInventory().setSelectedSlot(slot);
                                this.nextStep();
                            } else {
                                this.reset();
                            }
                            break;
                        case 5:
                            this.explodeAnchor();
                            break;
                        default:
                            this.reset();
                    }
                }
            }
        }
    }

    private void placeGlowstoneNextToAnchor() {
        if (!this.isAnchorPlaced()) {
            this.reset();
        } else {
            BlockPos glowstonePos = this.findGlowstonePosition(this.anchorPos);
            if (glowstonePos == null) {
                this.reset();
            } else if (this.mc.world.getBlockState(glowstonePos).getBlock() == Blocks.GLOWSTONE) {
                this.nextStep();
            } else if (!InventoryActions.selectItem(this.mc, Items.GLOWSTONE)) {
                this.reset();
            } else {
                BlockPos supportPos = glowstonePos.down();
                BlockHitResult hitResult =
                        new BlockHitResult(
                                Vec3d.ofCenter(supportPos).add(0.0, 0.5, 0.0),
                                Direction.UP,
                                supportPos,
                                false);
                InventoryActions.placeBlock(this.mc, hitResult);
                this.nextStep();
            }
        }
    }

    private void chargeAnchor() {
        if (!this.isAnchorPlaced()) {
            this.reset();
        } else {
            if (this.mc.world.getBlockState(this.anchorPos).get(Properties.CHARGES) <= 0) {
                if (!InventoryActions.selectItem(this.mc, Items.GLOWSTONE)) {
                    this.reset();
                    return;
                }

                InventoryActions.placeBlock(this.mc, this.getAnchorHitResult());
            }

            this.nextStep();
        }
    }

    private void explodeAnchor() {
        if (!this.isAnchorPlaced()) {
            this.reset();
        } else if (this.mc.world.getBlockState(this.anchorPos).get(Properties.CHARGES) <= 0) {
            this.reset();
        } else {
            InventoryActions.placeBlock(this.mc, this.getAnchorHitResult());
            this.lastStepTime = System.currentTimeMillis();
        }
    }

    private BlockHitResult getAnchorHitResult() {
        return new BlockHitResult(
                Vec3d.ofCenter(this.anchorPos), Direction.UP, this.anchorPos, false);
    }

    private boolean isAnchorPlaced() {
        return this.anchorPos != null
                && this.mc.world.getBlockState(this.anchorPos).getBlock() == Blocks.RESPAWN_ANCHOR;
    }

    private boolean canPlaceAt(BlockPos pos) {
        return this.mc.world.getBlockState(pos).isReplaceable()
                && this.mc.world.getCrammedEntities(this.mc.player, new Box(pos)).isEmpty();
    }

    private BlockPos findGlowstonePosition(BlockPos anchor) {
        double playerX = this.mc.player.getX();
        double playerZ = this.mc.player.getZ();
        double anchorX = anchor.getX() + 0.5;
        double anchorZ = anchor.getZ() + 0.5;
        int[][] offsets =
                new int[][] {{0, -1}, {0, 1}, {1, 0}, {-1, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        BlockPos best = null;
        double bestScore = -1.7976931348623157E308;

        for (int[] offset : offsets) {
            BlockPos candidate = anchor.add(offset[0], 0, offset[1]);
            BlockState state = this.mc.world.getBlockState(candidate);
            BlockState belowState = this.mc.world.getBlockState(candidate.down());
            if (state.isReplaceable() && !belowState.isAir() && !belowState.isReplaceable()) {
                double score = (playerX - anchorX) * offset[0] + (playerZ - anchorZ) * offset[1];
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        return best;
    }

    private void nextStep() {
        this.step++;
        this.lastStepTime = System.currentTimeMillis();
    }

    private void reset() {
        this.step = 0;
        this.anchorPos = null;
        this.lastStepTime = System.currentTimeMillis();
    }
}
