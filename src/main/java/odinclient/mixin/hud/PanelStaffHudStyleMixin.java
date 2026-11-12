package odinclient.mixin.hud;

import net.minecraft.client.MinecraftClient;

import odinclient.event.Render2DEvent;
import odinclient.module.hud.StaffTracker;
import odinclient.module.hud.ThemeModule;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StaffTracker.class)
public abstract class PanelStaffHudStyleMixin {
    @Inject(method = "onRender2D", at = @At("HEAD"), cancellable = true, remap = false)
    private void odinclient$hideStaffBehindPanelScreens(Render2DEvent event, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ThemeModule.isPanelStyle() && client != null && client.currentScreen != null) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "drawPanel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lodinclient/module/hud/StaffTracker;drawRoundedRect(Lnet/minecraft/client/gui/DrawContext;IIIIII)V",
                            ordinal = 0),
            index = 6)
    private int odinclient$hideStaffOuterBorder(int color) {
        return ThemeModule.isPanelStyle() ? color & 16777215 : color;
    }

    @ModifyArg(
            method = "drawPanel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lodinclient/module/hud/StaffTracker;drawRoundedRect(Lnet/minecraft/client/gui/DrawContext;IIIIII)V",
                            ordinal = 1),
            index = 6)
    private int odinclient$darkStaffSurface(int color) {
        return ThemeModule.isPanelStyle() ? color & 0xFF000000 | 658965 : color;
    }

    @ModifyArg(
            method = "drawPanel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lodinclient/module/hud/StaffTracker;drawRoundedRect(Lnet/minecraft/client/gui/DrawContext;IIIIII)V",
                            ordinal = 2),
            index = 6)
    private int odinclient$hideStaffHeaderLine(int color) {
        return ThemeModule.isPanelStyle() ? color & 16777215 : color;
    }
}
