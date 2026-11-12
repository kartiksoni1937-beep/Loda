package odinclient.render.world;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.gizmo.GizmoDrawerImpl;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.ShaderLayerInvoker;
import odinclient.mixin.accessor.ShaderPipelineInvoker;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

public final class XrayGizmoLayers {
    private static final RenderLayer LINES_LAYER = createLinesLayer();
    private static final RenderLayer QUADS_LAYER = createQuadsLayer();
    private static GizmoDrawerImpl capturedDrawer;
    private static CameraRenderState capturedCameraState;
    private static Matrix4f capturedMatrix;

    private XrayGizmoLayers() {}

    public static RenderLayer getLinesLayer() {
        return LINES_LAYER;
    }

    public static RenderLayer getQuadsLayer() {
        return QUADS_LAYER;
    }

    public static void captureGizmoState(
            GizmoDrawerImpl drawer, CameraRenderState cameraState, Matrix4f matrix) {
        capturedDrawer = drawer;
        capturedCameraState = cameraState;
        capturedMatrix = new Matrix4f(matrix);
    }

    public static void drawCapturedGizmos(MinecraftClient client) {
        if (client != null && client.world != null && client.gameRenderer != null) {
            Framebuffer framebuffer = client.getFramebuffer();
            GizmoDrawerImpl drawer = capturedDrawer;
            CameraRenderState cameraState = capturedCameraState;
            Matrix4f matrix = capturedMatrix;
            clearCapturedState();
            if (framebuffer != null && drawer != null && cameraState != null && matrix != null) {
                Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
                boolean pushed = false;

                try {
                    Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
                    RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
                    RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();
                    modelViewStack.pushMatrix();
                    pushed = true;
                    modelViewStack.mul(matrix);
                    drawer.draw(new MatrixStack(), consumers, cameraState, matrix);
                    consumers.draw();
                } finally {
                    if (pushed) {
                        modelViewStack.popMatrix();
                    }

                    RenderSystem.outputColorTextureOverride = null;
                    RenderSystem.outputDepthTextureOverride = null;
                }
            }
        } else {
            clearCapturedState();
        }
    }

    private static void clearCapturedState() {
        capturedDrawer = null;
        capturedCameraState = null;
        capturedMatrix = null;
    }

    private static RenderLayer createLinesLayer() {
        RenderPipeline pipeline =
                ShaderPipelineInvoker.odinclient$registerPipeline(
                        RenderPipeline.builder(ShaderPipelineInvoker.odinclient$getLinesSnippet())
                                .withLocation(Identifier.of("odinclient", "pipeline/xray_lines"))
                                .withDepthWrite(false)
                                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                .build());
        RenderSetup setup =
                RenderSetup.builder(pipeline)
                        .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                        .build();
        return ShaderLayerInvoker.odinclient$createLayer("odinclient_xray_lines", setup);
    }

    private static RenderLayer createQuadsLayer() {
        RenderPipeline pipeline =
                ShaderPipelineInvoker.odinclient$registerPipeline(
                        RenderPipeline.builder(
                                        ShaderPipelineInvoker.odinclient$getPositionColorSnippet())
                                .withLocation(Identifier.of("odinclient", "pipeline/xray_quads"))
                                .withCull(false)
                                .withDepthWrite(false)
                                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                .build());
        RenderSetup setup =
                RenderSetup.builder(pipeline)
                        .translucent()
                        .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .build();
        return ShaderLayerInvoker.odinclient$createLayer("odinclient_xray_quads", setup);
    }
}
