package odinclient.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;

import odinclient.module.render.visual.Fullbright;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    private static final String PUT_FLOAT =
            "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;";

    @Inject(
            method = "getBrightness(Lnet/minecraft/world/dimension/DimensionType;I)F",
            at = @At("HEAD"),
            cancellable = true)
    private static void odinclient$fullDimensionBrightness(
            DimensionType dimensionType, int skyLight, CallbackInfoReturnable<Float> cir) {
        if (Fullbright.isEnabled()) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "getBrightness(FI)F", at = @At("HEAD"), cancellable = true)
    private static void odinclient$fullBrightness(
            float brightness, int skyLight, CallbackInfoReturnable<Float> cir) {
        if (Fullbright.isEnabled()) {
            cir.setReturnValue(1.0F);
        }
    }

    @ModifyArg(
            method = "update",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                            ordinal = 3),
            index = 0)
    private float odinclient$nightVision(float value) {
        return Fullbright.isEnabled() ? 1.0F : value;
    }

    @ModifyArg(
            method = "update",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                            ordinal = 4),
            index = 0)
    private float odinclient$removeDarkness(float value) {
        return Fullbright.isEnabled() ? 0.0F : value;
    }

    @ModifyArg(
            method = "update",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                            ordinal = 5),
            index = 0)
    private float odinclient$removeSkyDarkness(float value) {
        return Fullbright.isEnabled() ? 0.0F : value;
    }

    @ModifyArg(
            method = "update",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                            ordinal = 6),
            index = 0)
    private float odinclient$fullGamma(float value) {
        return Fullbright.isEnabled() ? 1.0F : value;
    }
}
