package odinclient.mixin.screen;

import net.minecraft.client.gui.screen.Screen;

import odinclient.ui.scale.ScreenScaleCompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Screen.class)
public abstract class ClientLegacyScreenSizeMixin {
    @ModifyVariable(
            method = {"init", "resize"},
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private int odinclient$fixedWidth(int width) {
        return ScreenScaleCompat.isScaledScreen(this) ? ScreenScaleCompat.getScaledWidth() : width;
    }

    @ModifyVariable(
            method = {"init", "resize"},
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1)
    private int odinclient$fixedHeight(int height) {
        return ScreenScaleCompat.isScaledScreen(this)
                ? ScreenScaleCompat.getScaledHeight()
                : height;
    }
}
