package odinclient.module.render.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
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
import net.minecraft.util.math.RotationAxis;

import odinclient.mixin.accessor.RenderPipelinesAccessor;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class SkyArtRenderer {
    private static RenderPipeline pipeline;
    private static GpuBuffer vertexBuffer;
    private static int indexCount;
    private static boolean initFailed;

    private SkyArtRenderer() {}

    public static void render(SkyArts.Artwork artwork) {
        if (artwork != null && !initFailed) {
            ensureInitialized();
            if (pipeline != null && vertexBuffer != null && indexCount > 0) {
                MinecraftClient client = MinecraftClient.getInstance();
                Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
                if (client != null && framebuffer != null) {
                    GpuTextureView colorView = framebuffer.getColorAttachmentView();
                    if (colorView != null) {
                        GpuTextureView textureView =
                                client.getTextureManager()
                                        .getTexture(artwork.getTexture())
                                        .getGlTextureView();
                        GpuTextureView depthView =
                                framebuffer.useDepthAttachment
                                        ? framebuffer.getDepthAttachmentView()
                                        : null;
                        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
                        drawSkyQuad(artwork, colorView, depthView, textureView, modelView, 0.0F);
                        if (SkyArts.isDoubleSided()) {
                            drawSkyQuad(
                                    artwork,
                                    colorView,
                                    depthView,
                                    textureView,
                                    modelView,
                                    3.1415927F);
                        }
                    }
                }
            }
        }
    }

    private static void drawSkyQuad(
            SkyArts.Artwork artwork,
            GpuTextureView colorView,
            GpuTextureView depthView,
            GpuTextureView textureView,
            Matrix4f modelView,
            float yaw) {
        Matrix4f matrix = new Matrix4f(modelView);
        if (yaw != 0.0F) {
            matrix.rotateY(yaw);
        }

        matrix.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        matrix.scale(artwork.getAspectRatio() / 0.8F, 1.0F, 1.0F);
        GpuBufferSlice uniforms =
                RenderSystem.getDynamicUniforms()
                        .write(
                                matrix,
                                new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                                new Vector3f(),
                                new Matrix4f());
        ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);

        try (RenderPass pass =
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                                () -> "OdinClient Sky Art",
                                colorView,
                                OptionalInt.empty(),
                                depthView,
                                OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(pipeline);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer.getIndexBuffer(indexCount), indexBuffer.getIndexType());
            pass.setUniform("DynamicTransforms", uniforms);
            pass.bindTexture(
                    "Sampler0", textureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.drawIndexed(0, 0, indexCount, 1);
        }
    }

    private static void ensureInitialized() {
        if (pipeline == null && !initFailed) {
            try {
                Identifier shader = Identifier.of("odinclient", "sky_art");
                pipeline =
                        RenderPipeline.builder(
                                        RenderPipelinesAccessor
                                                .odinclient$getMatricesProjectionSnippet())
                                .withLocation(Identifier.of("odinclient", "pipeline/sky_art"))
                                .withVertexShader(shader)
                                .withFragmentShader(shader)
                                .withDepthWrite(false)
                                .withColorWrite(true, false)
                                .withBlend(BlendFunction.TRANSLUCENT)
                                .withCull(false)
                                .withSampler("Sampler0")
                                .withVertexFormat(VertexFormats.POSITION_TEXTURE, DrawMode.QUADS)
                                .build();
                createQuadBuffer();
            } catch (Throwable e) {
                initFailed = true;
                pipeline = null;
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                    vertexBuffer = null;
                }

                System.err.println(
                        "[OdinClient Sky Arts] Failed to initialize the Skyboxify-style sky"
                            + " renderer: "
                                + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void createQuadBuffer() {
        BufferAllocator allocator =
                BufferAllocator.fixedSized(VertexFormats.POSITION_TEXTURE.getVertexSize() * 4);

        try {
            BufferBuilder builder =
                    new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            float z = -100.0F;
            float halfWidth = 38.4F;
            float bottom = -27.0F;
            float top = 69.0F;
            builder.vertex(-halfWidth, bottom, z).texture(0.0F, 1.0F);
            builder.vertex(halfWidth, bottom, z).texture(1.0F, 1.0F);
            builder.vertex(halfWidth, top, z).texture(1.0F, 0.0F);
            builder.vertex(-halfWidth, top, z).texture(0.0F, 0.0F);
            BuiltBuffer built = builder.end();

            try {
                vertexBuffer =
                        RenderSystem.getDevice()
                                .createBuffer(
                                        () -> "OdinClient Sky Art Geometry", 32, built.getBuffer());
                indexCount = built.getDrawParameters().indexCount();
            } finally {
                built.close();
            }
        } finally {
            allocator.close();
        }
    }
}
