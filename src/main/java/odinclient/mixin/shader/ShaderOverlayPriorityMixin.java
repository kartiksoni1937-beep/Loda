package odinclient.mixin.shader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;

import odinclient.render.world.XrayGizmoLayers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ShaderOverlayPriorityMixin {
    @Inject(
            method = "renderWorld",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/systems/ProjectionType;)V",
                            ordinal = 1))
    private void odinclient$renderEspAfterShaderComposition(CallbackInfo ci) {
        XrayGizmoLayers.drawCapturedGizmos(MinecraftClient.getInstance());
    }
}
