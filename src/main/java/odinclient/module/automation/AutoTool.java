package odinclient.module.automation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.BambooShootBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.AttributeModifiersComponent.Entry;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;

@Environment(EnvType.CLIENT)
public final class AutoTool extends Module {
    public AutoTool(Category category) {
        super(
                category,
                "auto-tool",
                "Automatically selects the best tool for blocks and the preferred weapon for"
                    + " entities.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player != null
                && this.mc.world != null
                && this.mc.currentScreen == null
                && this.mc.options.attackKey.isPressed()
                && this.mc.crosshairTarget != null) {
            int slot = -1;
            HitResult target = this.mc.crosshairTarget;
            if (target instanceof BlockHitResult blockHit) {
                slot = this.findBestToolSlot(this.mc.world.getBlockState(blockHit.getBlockPos()));
            } else if (this.mc.crosshairTarget instanceof EntityHitResult) {
                slot = this.findBestWeaponSlot();
            }

            this.selectSlot(slot);
        }
    }

    private int findBestToolSlot(BlockState state) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                double speed = stack.getMiningSpeedMultiplier(state);
                boolean suitable = stack.isSuitableFor(state);
                boolean shearsTarget =
                        stack.getItem() instanceof ShearsItem
                                && (state.getBlock() instanceof LeavesBlock
                                        || state.isIn(BlockTags.WOOL));
                boolean bambooSword =
                        getItemPath(stack).endsWith("_sword")
                                && (state.getBlock() instanceof BambooBlock
                                        || state.getBlock() instanceof BambooShootBlock);

                if (suitable || !(speed <= 1.0) || shearsTarget || bambooSword) {
                    double score = speed;
                    if (suitable) {
                        score = speed + 1000.0;
                    }

                    if (shearsTarget || bambooSword) {
                        score += 2000.0;
                    }

                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = i;
                    }
                }
            }
        }

        return bestSlot;
    }

    private int findBestWeaponSlot() {
        int swordSlot = this.findWeaponSlot(AutoTool.WeaponType.SWORD);
        if (swordSlot >= 0) {
            return swordSlot;
        } else {
            int axeSlot = this.findWeaponSlot(AutoTool.WeaponType.AXE);
            return axeSlot >= 0 ? axeSlot : this.findWeaponSlot(AutoTool.WeaponType.TRIDENT);
        }
    }

    private int findWeaponSlot(AutoTool.WeaponType type) {
        int bestSlot = -1;
        double bestDamage = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && type.matches(getItemPath(stack))) {
                double damage = this.getAttackDamage(stack);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private double getAttackDamage(ItemStack stack) {
        double damage = 0.0;
        AttributeModifiersComponent component = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (component != null) {
            for (Entry entry : component.modifiers()) {
                if (entry.attribute().matches(EntityAttributes.ATTACK_DAMAGE)) {
                    damage += entry.modifier().value();
                }
            }
        }

        return damage;
    }

    private static String getItemPath(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }

    private void selectSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            if (this.mc.player.getInventory().getSelectedSlot() != slot) {
                this.mc.player.getInventory().setSelectedSlot(slot);
            }
        }
    }

    private static enum WeaponType {
        SWORD {
            @Override
            boolean matches(String path) {
                return path.endsWith("_sword");
            }
        },
        AXE {
            @Override
            boolean matches(String path) {
                return path.endsWith("_axe");
            }
        },
        TRIDENT {
            @Override
            boolean matches(String path) {
                return path.equals("trident") || path.endsWith("_trident");
            }
        };

        abstract boolean matches(String path);
    }
}
