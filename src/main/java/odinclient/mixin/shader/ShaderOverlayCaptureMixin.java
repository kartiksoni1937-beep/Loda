package odinclient.mixin.shader;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.gizmo.GizmoDrawerImpl;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

import odinclient.render.world.XrayGizmoLayers;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public abstract class ShaderOverlayCaptureMixin {
    @Redirect(
            method = "method_75413",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/gizmo/GizmoDrawerImpl;draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/state/CameraRenderState;Lorg/joml/Matrix4f;)V"))
    private void odinclient$deferHighestPriorityGizmos(
            GizmoDrawerImpl drawer,
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            CameraRenderState cameraState,
            Matrix4f projection) {
        XrayGizmoLayers.captureGizmoState(drawer, cameraState, projection);
    }
}
