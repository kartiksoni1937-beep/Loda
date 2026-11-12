package odinclient.module.render.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.RenderPipelinesAccessor;
import odinclient.mixin.accessor.ShaderPipelineInvoker;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public final class SkyOverlayRenderer {
    public static final int QUAD_LAYERS = 32;
    private static RenderPipeline pipeline;
    private static GpuBuffer vertexBuffer;
    private static int indexCount;
    private static boolean disabled;

    private SkyOverlayRenderer() {}

    public static void render(
            GpuTextureView colorTarget, GpuTextureView depthTarget, GpuBufferSlice transforms) {
        if (!disabled) {
            try {
                initPipeline();
                ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
                Supplier<String> label = () -> "OdinClient Living Sky";

                try (RenderPass pass =
                        RenderSystem.getDevice()
                                .createCommandEncoder()
                                .createRenderPass(
                                        label,
                                        colorTarget,
                                        OptionalInt.empty(),
                                        depthTarget,
                                        OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setPipeline(pipeline);
                    pass.setVertexBuffer(0, vertexBuffer);
                    pass.setIndexBuffer(
                            indexBuffer.getIndexBuffer(indexCount), indexBuffer.getIndexType());
                    pass.setUniform("DynamicTransforms", transforms);
                    pass.drawIndexed(0, 0, indexCount, 1);
                }
            } catch (Throwable e) {
                disabled = true;
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                    vertexBuffer = null;
                }

                System.err.println(
                        "[OdinClient Sky Arts] Sky animation overlay disabled: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void initPipeline() {
        if (pipeline == null) {
            Identifier id = Identifier.of("odinclient", "sky_life");
            pipeline =
                    ShaderPipelineInvoker.odinclient$registerPipeline(
                            RenderPipeline.builder(
                                            RenderPipelinesAccessor
                                                    .odinclient$getMatricesProjectionSnippet())
                                    .withLocation(Identifier.of("odinclient", "pipeline/sky_life"))
                                    .withVertexShader(id)
                                    .withFragmentShader(id)
                                    .withBlend(BlendFunction.TRANSLUCENT)
                                    .withCull(false)
                                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                    .withDepthWrite(false)
                                    .withColorWrite(true, false)
                                    .withVertexFormat(
                                            VertexFormats.POSITION_TEXTURE, DrawMode.QUADS)
                                    .build());

            try (BufferAllocator allocator =
                    BufferAllocator.fixedSized(
                            VertexFormats.POSITION_TEXTURE.getVertexSize() * 32 * 4)) {
                BufferBuilder builder =
                        new BufferBuilder(
                                allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

                for (int i = 0; i < 32; i++) {
                    builder.vertex(-1.0F, -1.0F, i).texture(0.0F, 0.0F);
                    builder.vertex(1.0F, -1.0F, i).texture(1.0F, 0.0F);
                    builder.vertex(1.0F, 1.0F, i).texture(1.0F, 1.0F);
                    builder.vertex(-1.0F, 1.0F, i).texture(0.0F, 1.0F);
                }

                try (BuiltBuffer built = builder.end()) {
                    Supplier<String> bufferLabel = () -> "OdinClient Cached Sky Life";
                    vertexBuffer =
                            RenderSystem.getDevice()
                                    .createBuffer(bufferLabel, 32, built.getBuffer());
                    indexCount = built.getDrawParameters().indexCount();
                }
            }
        }
    }
}
