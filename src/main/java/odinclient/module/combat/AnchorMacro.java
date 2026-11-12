package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.IntSetting;
import odinclient.util.InventoryActions;

import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class AnchorMacro extends Module {
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
    private int switchTicks;
    private int glowstoneTicks;
    private int explodeTicks;

    public AnchorMacro(Category category) {
        super(
                category,
                "anchor-macro",
                "Charges and explodes the Respawn Anchor under the crosshair while right click is"
                    + " held.");
    }

    @Override
    public void onActivate() {
        this.resetDelays();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (InventoryActions.isReady(this.mc)
                && this.mc.currentScreen == null
                && this.isUseButtonHeld()
                && !this.mc.player.isUsingItem()) {
            if (this.mc.crosshairTarget instanceof BlockHitResult hit
                    && this.mc.world.getBlockState(hit.getBlockPos()).getBlock()
                            == Blocks.RESPAWN_ANCHOR) {
                this.mc.options.useKey.setPressed(false);
                if (this.mc.world.getBlockState(hit.getBlockPos()).get(Properties.CHARGES) <= 0) {
                    this.explodeTicks = 0;
                    if (!this.mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                        if (this.switchTicks++ < this.switchDelay.getValue()) {
                            return;
                        }

                        this.switchTicks = 0;
                        if (!InventoryActions.selectItem(this.mc, Items.GLOWSTONE)) {
                            return;
                        }
                    }

                    if (this.glowstoneTicks++ < this.glowstoneDelay.getValue()) {
                        return;
                    }

                    this.glowstoneTicks = 0;
                    InventoryActions.placeBlock(this.mc, hit);
                } else {
                    this.glowstoneTicks = 0;
                    int slot = this.totemSlot.getValue() - 1;
                    if (this.mc.player.getInventory().getSelectedSlot() != slot) {
                        if (this.switchTicks++ < this.switchDelay.getValue()) {
                            return;
                        }

                        this.switchTicks = 0;
                        this.mc.player.getInventory().setSelectedSlot(slot);
                    }

                    if (this.explodeTicks++ < this.explodeDelay.getValue()) {
                        return;
                    }

                    this.explodeTicks = 0;
                    InventoryActions.placeBlock(this.mc, hit);
                }
            } else {
                this.resetDelays();
            }
        } else {
            this.resetDelays();
        }
    }

    private boolean isUseButtonHeld() {
        return GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), 1) == 1;
    }

    private void resetDelays() {
        this.switchTicks = 0;
        this.glowstoneTicks = 0;
        this.explodeTicks = 0;
    }
}
