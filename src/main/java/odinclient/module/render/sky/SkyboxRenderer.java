package odinclient.module.render.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.RenderPipelinesAccessor;
import odinclient.mixin.accessor.ShaderPipelineInvoker;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public final class SkyboxRenderer {
    private static final long START_NANOS = System.nanoTime();
    private static RenderPipeline pipeline;
    private static GpuBuffer vertexBuffer;
    private static int indexCount;
    private static boolean disabled;

    private SkyboxRenderer() {}

    public static void render(SkyArts.Sky sky) {
        if (sky != null && sky.hasTexture() && !disabled) {
            try {
                initPipeline();
                MinecraftClient client = MinecraftClient.getInstance();
                Framebuffer framebuffer = client == null ? null : client.getFramebuffer();

                if (framebuffer == null) {
                    return;
                }

                GpuTextureView colorView = framebuffer.getColorAttachmentView();
                if (colorView == null) {
                    return;
                }

                GpuTextureView panoramaView =
                        client.getTextureManager().getTexture(sky.getPanorama()).getGlTextureView();
                if (panoramaView == null) {
                    return;
                }

                GpuTextureView depthView =
                        framebuffer.useDepthAttachment
                                ? framebuffer.getDepthAttachmentView()
                                : null;

                float time = (float) ((System.nanoTime() - START_NANOS) * 1.0E-9);
                Matrix4f matrix = new Matrix4f();
                float themeIndex = sky.getThemeIndex();
                float animated = SkyArts.isSkyAnimated() ? 1.0F : 0.0F;
                Matrix4f transform = matrix.translation(time, themeIndex, animated).m03(1.0F);
                GpuBufferSlice uniforms =
                        RenderSystem.getDynamicUniforms()
                                .write(
                                        new Matrix4f(RenderSystem.getModelViewStack()),
                                        new Vector4f(1.0F),
                                        new Vector3f(),
                                        transform);
                ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
                Supplier<String> label = () -> "OdinClient Smooth Sky";

                try (RenderPass pass =
                        RenderSystem.getDevice()
                                .createCommandEncoder()
                                .createRenderPass(
                                        label,
                                        colorView,
                                        OptionalInt.empty(),
                                        depthView,
                                        OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setPipeline(pipeline);
                    pass.setVertexBuffer(0, vertexBuffer);
                    pass.setIndexBuffer(
                            indexBuffer.getIndexBuffer(indexCount), indexBuffer.getIndexType());
                    pass.setUniform("DynamicTransforms", uniforms);
                    pass.bindTexture(
                            "Sampler0",
                            panoramaView,
                            RenderSystem.getSamplerCache()
                                    .get(
                                            AddressMode.REPEAT,
                                            AddressMode.CLAMP_TO_EDGE,
                                            FilterMode.LINEAR,
                                            FilterMode.LINEAR,
                                            false));
                    pass.drawIndexed(0, 0, indexCount, 1);
                }

                if (SkyArts.isSkyAnimated()) {
                    SkyOverlayRenderer.render(colorView, depthView, uniforms);
                }
            } catch (Throwable t) {
                disabled = true;
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                    vertexBuffer = null;
                }

                System.err.println(
                        "[OdinClient Sky Arts] Smooth sky rendering disabled: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    private static void initPipeline() {
        if (pipeline == null) {
            Identifier shader = Identifier.of("odinclient", "sky_background");
            Snippet[] snippets = {
                RenderPipelinesAccessor.odinclient$getMatricesProjectionSnippet()
            };
            pipeline =
                    ShaderPipelineInvoker.odinclient$registerPipeline(
                            RenderPipeline.builder(snippets)
                                    .withLocation(
                                            Identifier.of("odinclient", "pipeline/smooth_sky"))
                                    .withVertexShader(shader)
                                    .withFragmentShader(shader)
                                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                    .withDepthWrite(false)
                                    .withColorWrite(true, false)
                                    .withCull(false)
                                    .withSampler("Sampler0")
                                    .withVertexFormat(
                                            VertexFormats.POSITION_TEXTURE, DrawMode.QUADS)
                                    .build());

            try (BufferAllocator allocator =
                    BufferAllocator.fixedSized(
                            VertexFormats.POSITION_TEXTURE.getVertexSize() * 24)) {
                BufferBuilder builder =
                        new BufferBuilder(
                                allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                float size = 99.0F;
                addQuad(
                        builder, -size, -size, size, size, -size, size, size, -size, -size, -size,
                        -size, -size);
                addQuad(
                        builder, -size, size, -size, size, size, -size, size, size, size, -size,
                        size, size);
                addQuad(
                        builder, size, -size, size, -size, -size, size, -size, size, size, size,
                        size, size);
                addQuad(
                        builder, -size, -size, size, -size, -size, -size, -size, size, -size, -size,
                        size, size);
                addQuad(
                        builder, -size, -size, -size, size, -size, -size, size, size, -size, -size,
                        size, -size);
                addQuad(
                        builder, size, -size, -size, size, -size, size, size, size, size, size,
                        size, -size);

                try (BuiltBuffer built = builder.end()) {
                    Supplier<String> label = () -> "OdinClient Smooth Sky Cube";
                    vertexBuffer =
                            RenderSystem.getDevice().createBuffer(label, 32, built.getBuffer());
                    indexCount = built.getDrawParameters().indexCount();
                }
            }
        }
    }

    private static void addQuad(
            BufferBuilder builder,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4) {
        builder.vertex(x1, y1, z1).texture(0.0F, 1.0F);
        builder.vertex(x2, y2, z2).texture(1.0F, 1.0F);
        builder.vertex(x3, y3, z3).texture(1.0F, 0.0F);
        builder.vertex(x4, y4, z4).texture(0.0F, 0.0F);
    }
}
