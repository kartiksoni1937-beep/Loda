package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.util.InventoryActions;
import odinclient.util.InputSimulator;

@Environment(EnvType.CLIENT)
public final class CrystalOptimizer extends Module {
    private static final double REACH = 4.5;
    private static final int ATTACK_DELAY = 0;
    private int cooldown;

    public CrystalOptimizer(Category category) {
        super(
                category,
                "crystal-optimizer",
                "Optimizes manual End Crystal placement and breaking.");
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
        if (InventoryActions.isReady(this.mc) && this.mc.currentScreen == null) {
            boolean attacking = this.mc.options.attackKey.isPressed();
            boolean using = this.mc.options.useKey.isPressed();
            if (!attacking && !using) {
                this.cooldown = 0;
            } else if (this.cooldown > 0) {
                this.cooldown--;
            } else {
                if (attacking) {
                    HitResult target = this.mc.crosshairTarget;
                    if (target instanceof EntityHitResult) {
                        Entity entity = ((EntityHitResult) target).getEntity();
                        if (entity instanceof EndCrystalEntity) {
                            InputSimulator.click(InputSimulator.Click.LEFT);
                            this.cooldown = 0;
                            return;
                        }
                    }
                }

                if (using
                        && this.mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)
                        && this.mc.player.raycast(4.5, 0.0F, false) instanceof BlockHitResult hit) {
                    BlockPos pos = hit.getBlockPos();
                    if ((this.mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN
                                    || this.mc.world.getBlockState(pos).getBlock()
                                            == Blocks.BEDROCK)
                            && InventoryActions.canPlaceAt(this.mc, pos.add(0, 1, 0))) {
                        InventoryActions.placeBlock(this.mc, hit);
                        this.cooldown = 0;
                    }
                }
            }
        } else {
            this.reset();
        }
    }

    private void reset() {
        this.cooldown = 0;
    }
}
