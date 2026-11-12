package odinclient.mixin.gamble;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class GambleContainerRenderMixin {
    @Shadow @Final protected ScreenHandler handler;
    @Unique private boolean odinclient$hadCursorItem;

    @Inject(method = "renderMain", at = @At("HEAD"))
    private void odinclient$refreshBeforeIcons(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GambleRigger.onSlotsUpdated(this.handler);
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"))
    private void odinclient$rememberReturn(
            Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        this.odinclient$hadCursorItem = !this.handler.getCursorStack().isEmpty();
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("TAIL"))
    private void odinclient$observeReturn(
            Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        GambleRigger.onSlotClick(
                this.handler, slotId, button, actionType, this.odinclient$hadCursorItem);
    }

    @Redirect(
            method = {"drawSlot", "drawMouseoverTooltip"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack odinclient$dropperCell(Slot slot) {
        ItemStack actual = slot.getStack();
        ItemStack displayed = GambleRigger.getDisplayedSlotStack(this.handler, slot, actual);
        return displayed == null ? actual : displayed;
    }

    @Redirect(
            method = "drawMouseoverTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;hasStack()Z"))
    private boolean odinclient$visibleCellHasTooltip(Slot slot) {
        ItemStack displayed =
                GambleRigger.getDisplayedSlotStack(this.handler, slot, slot.getStack());
        return displayed == null ? slot.hasStack() : !displayed.isEmpty();
    }
}
