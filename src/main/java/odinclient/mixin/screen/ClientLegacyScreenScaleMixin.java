package odinclient.mixin.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import odinclient.ui.scale.ScreenScaleCompat;
import odinclient.ui.screen.ConfigScreen;
import odinclient.ui.screen.HudPositionEditorScreen;
import odinclient.ui.screen.IgnoredPlayersScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {ConfigScreen.class, HudPositionEditorScreen.class, IgnoredPlayersScreen.class})
public abstract class ClientLegacyScreenScaleMixin {
    @Unique private boolean odinclient$drawingFixed;
    @Unique private boolean odinclient$fixedInput;

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/DrawContext;getScaledWindowWidth()I"),
            require = 0)
    private int odinclient$canvasWidth(DrawContext context) {
        return ScreenScaleCompat.getScaledWidth();
    }

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/DrawContext;getScaledWindowHeight()I"),
            require = 0)
    private int odinclient$canvasHeight(DrawContext context) {
        return ScreenScaleCompat.getScaledHeight();
    }

    @Inject(
            method = {"render", "method_25394"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void odinclient$fixedRender(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.odinclient$drawingFixed) {
            this.odinclient$drawingFixed = true;
            float scale = ScreenScaleCompat.getScale(0);

            try (ScreenScaleCompat.ScaleScope scope = ScreenScaleCompat.enter(context)) {
                ((Screen) (Object) this)
                        .render(
                                context,
                                Math.round(mouseX / scale),
                                Math.round(mouseY / scale),
                                delta);
            } finally {
                this.odinclient$drawingFixed = false;
            }

            ci.cancel();
        }
    }

    @Inject(
            method = {"mouseClicked", "method_25402"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void odinclient$fixedClick(
            Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!this.odinclient$fixedInput) {
            this.odinclient$fixedInput = true;

            try {
                cir.setReturnValue(
                        ((Screen) (Object) this)
                                .mouseClicked(ScreenScaleCompat.unscaleClick(click), doubled));
            } finally {
                this.odinclient$fixedInput = false;
            }
        }
    }

    @Inject(
            method = {"mouseDragged", "method_25403"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void odinclient$fixedDrag(
            Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!this.odinclient$fixedInput) {
            this.odinclient$fixedInput = true;
            float scale = ScreenScaleCompat.getScale(0);

            try {
                cir.setReturnValue(
                        ((Screen) (Object) this)
                                .mouseDragged(
                                        ScreenScaleCompat.unscaleClick(click),
                                        deltaX / scale,
                                        deltaY / scale));
            } finally {
                this.odinclient$fixedInput = false;
            }
        }
    }

    @Inject(
            method = {"mouseReleased", "method_25406"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void odinclient$fixedRelease(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (!this.odinclient$fixedInput) {
            this.odinclient$fixedInput = true;

            try {
                cir.setReturnValue(
                        ((Screen) (Object) this)
                                .mouseReleased(ScreenScaleCompat.unscaleClick(click)));
            } finally {
                this.odinclient$fixedInput = false;
            }
        }
    }

    @Inject(
            method = {"mouseScrolled", "method_25401"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void odinclient$fixedScroll(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir) {
        if (!this.odinclient$fixedInput) {
            this.odinclient$fixedInput = true;
            float scale = ScreenScaleCompat.getScale(0);

            try {
                cir.setReturnValue(
                        ((Screen) (Object) this)
                                .mouseScrolled(
                                        mouseX / scale,
                                        mouseY / scale,
                                        horizontalAmount,
                                        verticalAmount));
            } finally {
                this.odinclient$fixedInput = false;
            }
        }
    }
}
