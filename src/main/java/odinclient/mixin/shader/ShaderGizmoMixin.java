package odinclient.mixin.shader;

import net.minecraft.client.render.RenderLayer;

import odinclient.render.world.XrayGizmoLayers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.render.gizmo.GizmoDrawerImpl$Division")
public abstract class ShaderGizmoMixin {
    @Redirect(
            method = "drawLines",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/RenderLayers;lines()Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer odinclient$xrayOpaqueLines() {
        return XrayGizmoLayers.getLinesLayer();
    }

    @Redirect(
            method = "drawLines",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/RenderLayers;linesTranslucent()Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer odinclient$xrayTranslucentLines() {
        return XrayGizmoLayers.getLinesLayer();
    }

    @Redirect(
            method = "drawQuads",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/RenderLayers;debugFilledBox()Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer odinclient$xrayFilledBoxes() {
        return XrayGizmoLayers.getQuadsLayer();
    }
}
