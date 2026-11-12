package odinclient.module.render.visual;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.ShaderLayerInvoker;
import odinclient.mixin.accessor.ShaderPipelineInvoker;

import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class StrawHatFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public StrawHatFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    public void render(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (ChinaHead.isEnabled() && player != null && state.id == player.getId()) {
            boolean hasHelmet = !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
            StrawHatGeometry.HatPlacement placement =
                    StrawHatGeometry.computePlacement(
                            state.glidingTicks, state.isGliding, hasHelmet, state.hatVisible);
            matrices.push();

            try {
                this.getContextModel().head.applyTransform(matrices);
                matrices.translate(0.0F, placement.seatY(), placement.rearZ());
                matrices.multiply(new Quaternionf().rotationX(placement.backwardTilt()));
                matrices.multiply(
                        new Quaternionf()
                                .rotationY(StrawHatGeometry.getSpinAngle(System.nanoTime())));
                queue.submitCustom(
                        matrices,
                        StrawHatFeatureRenderer.StrawHatLayers.STRAW_HAT_LAYER,
                        (buffer, entry) -> drawHat(entry, buffer));
            } finally {
                matrices.pop();
            }
        }
    }

    private static void drawHat(VertexConsumer consumer, Entry entry) {
        Vector3f normal = new Vector3f();

        for (StrawHatGeometry.HatQuad quad : StrawHatGeometry.getQuads()) {
            writeVertex(consumer, entry, quad.a(), normal);
            writeVertex(consumer, entry, quad.b(), normal);
            writeVertex(consumer, entry, quad.c(), normal);
            writeVertex(consumer, entry, quad.d(), normal);
        }
    }

    private static void writeVertex(
            VertexConsumer consumer,
            Entry entry,
            StrawHatGeometry.HatVertex vertex,
            Vector3f normal) {
        entry.transformNormal(vertex.nx(), vertex.ny(), vertex.nz(), normal);
        consumer.vertex(entry.getPositionMatrix(), vertex.x(), vertex.y(), vertex.z())
                .color(vertex.color())
                .texture(vertex.x(), vertex.z())
                .normal(normal.x, normal.y, normal.z);
    }

    private static final class StrawHatLayers {
        private static final RenderLayer STRAW_HAT_LAYER = createLayer();

        private static RenderLayer createLayer() {
            Identifier shader = Identifier.of("odinclient", "china_head");
            RenderPipeline pipeline =
                    ShaderPipelineInvoker.odinclient$registerPipeline(
                            RenderPipeline.builder()
                                    .withLocation(Identifier.of("odinclient", "pipeline/straw_hat"))
                                    .withVertexShader(shader)
                                    .withFragmentShader(shader)
                                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                                    .withDepthWrite(true)
                                    .withColorWrite(true, false)
                                    .withCull(true)
                                    .withVertexFormat(
                                            VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,
                                            DrawMode.QUADS)
                                    .build());
            return ShaderLayerInvoker.odinclient$createLayer(
                    "odinclient_straw_hat", RenderSetup.builder(pipeline).build());
        }
    }
}
