package odinclient.mixin.shader;

import net.minecraft.client.render.RenderLayer;

import odinclient.render.world.TracerBatchRenderer;
import odinclient.render.world.XrayGizmoLayers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TracerBatchRenderer.class)
public abstract class ShaderTracerMixin {
    @Redirect(
            method = "add",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/RenderLayers;lines()Lnet/minecraft/client/render/RenderLayer;"))
    private static RenderLayer odinclient$xrayTracerLayer() {
        return XrayGizmoLayers.getLinesLayer();
    }
}
