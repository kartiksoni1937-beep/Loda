package odinclient.module.render.itemglow;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
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
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup.Texture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.ItemGlowLayerAccessor;
import odinclient.mixin.accessor.ShaderPipelineInvoker;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class ItemGlowRenderer {
    private static RenderPipeline maskPipeline;
    private static RenderPipeline blurPipeline;
    private static RenderPipeline compositePipeline;
    private static SimpleFramebuffer maskFramebuffer;
    private static SimpleFramebuffer horizontal;
    private static SimpleFramebuffer vertical;
    private static GpuBuffer quadBuffer;
    private static boolean disabled;

    private ItemGlowRenderer() {}

    public static boolean isAvailable() {
        return !disabled;
    }

    public static boolean render(
            List<ItemGlowRenderer.GlowQuadBatch> batches,
            float blurRadius,
            float intensity,
            float haloRadius) {
        if (!disabled && !batches.isEmpty()) {
            try {
                Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
                GpuTextureView colorView =
                        RenderSystem.outputColorTextureOverride != null
                                ? RenderSystem.outputColorTextureOverride
                                : framebuffer.getColorAttachmentView();
                GpuTextureView depthView =
                        RenderSystem.outputDepthTextureOverride != null
                                ? RenderSystem.outputDepthTextureOverride
                                : framebuffer.getDepthAttachmentView();
                if (colorView != null
                        && depthView != null
                        && colorView.getWidth(0) >= 2
                        && colorView.getHeight(0) >= 2
                        && depthView.getWidth(0) == colorView.getWidth(0)
                        && depthView.getHeight(0) == colorView.getHeight(0)) {
                    initResources();
                    ensureFramebuffers(colorView.getWidth(0), colorView.getHeight(0));
                    LinkedHashMap<RenderLayer, List<ItemGlowRenderer.GlowQuadBatch>>
                            batchesByLayer = new LinkedHashMap<>();

                    for (ItemGlowRenderer.GlowQuadBatch batch : batches) {
                        batchesByLayer
                                .computeIfAbsent(batch.layer(), layer -> new ArrayList<>())
                                .add(batch);
                    }

                    LinkedHashMap<RenderLayer, Texture> textures = new LinkedHashMap<>();

                    for (RenderLayer renderLayer : batchesByLayer.keySet()) {
                        Texture texture =
                                ((ItemGlowLayerAccessor) renderLayer)
                                        .odinclient$itemRenderSetup()
                                        .resolveTextures()
                                        .get("Sampler0");
                        if (texture == null) {
                            return false;
                        }

                        textures.put(renderLayer, texture);
                    }

                    ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
                    GpuBufferSlice transforms =
                            RenderSystem.getDynamicUniforms()
                                    .write(
                                            RenderSystem.getModelViewMatrix(),
                                            new Vector4f(1.0F),
                                            new Vector3f(),
                                            new Matrix4f());
                    boolean first = true;

                    for (Entry<RenderLayer, List<ItemGlowRenderer.GlowQuadBatch>> entry :
                            batchesByLayer.entrySet()) {
                        BufferBuilder builder =
                                Tessellator.getInstance()
                                        .begin(
                                                DrawMode.QUADS,
                                                VertexFormats
                                                        .POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

                        for (ItemGlowRenderer.GlowQuadBatch glowBatch : entry.getValue()) {
                            for (BakedQuad quad : glowBatch.quads()) {
                                int color = glowBatch.color();
                                builder.quad(
                                        glowBatch.pose(),
                                        quad,
                                        (color >> 16 & 0xFF) / 255.0F,
                                        (color >> 8 & 0xFF) / 255.0F,
                                        (color & 0xFF) / 255.0F,
                                        (color >>> 24 & 0xFF) / 255.0F,
                                        15728880,
                                        0);
                            }
                        }

                        try (BuiltBuffer built = builder.end()) {
                            GpuBuffer vertexBuffer =
                                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
                                            .uploadImmediateVertexBuffer(built.getBuffer());
                            int indexCount = built.getDrawParameters().indexCount();
                            Texture layerTexture = textures.get(entry.getKey());

                            try (RenderPass pass =
                                    RenderSystem.getDevice()
                                            .createCommandEncoder()
                                            .createRenderPass(
                                                    () -> "OdinClient Item Shader Mask",
                                                    maskFramebuffer.getColorAttachmentView(),
                                                    first ? OptionalInt.of(0) : OptionalInt.empty(),
                                                    depthView,
                                                    OptionalDouble.empty())) {
                                pass.setPipeline(maskPipeline);
                                pass.setUniform(
                                        "Projection", RenderSystem.getProjectionMatrixBuffer());
                                pass.setUniform("DynamicTransforms", transforms);
                                pass.setVertexBuffer(0, vertexBuffer);
                                pass.setIndexBuffer(
                                        indexBuffer.getIndexBuffer(indexCount),
                                        indexBuffer.getIndexType());
                                pass.bindTexture(
                                        "Sampler0",
                                        layerTexture.textureView(),
                                        layerTexture.sampler());
                                pass.drawIndexed(0, 0, indexCount, 1);
                            }
                        }

                        first = false;
                    }

                    float blurScale = Math.clamp(blurRadius, 0.5F, 4.0F) * 1.75F;
                    drawPostPass(
                            blurPipeline,
                            horizontal.getColorAttachmentView(),
                            maskFramebuffer.getColorAttachmentView(),
                            blurScale / maskFramebuffer.textureWidth,
                            0.0F,
                            1.0F,
                            true);
                    drawPostPass(
                            blurPipeline,
                            vertical.getColorAttachmentView(),
                            horizontal.getColorAttachmentView(),
                            0.0F,
                            blurScale / maskFramebuffer.textureHeight,
                            1.0F,
                            true);
                    float haloScale = Math.clamp(haloRadius, 0.5F, 4.0F) * 1.25F;
                    drawPostPass(
                            compositePipeline,
                            colorView,
                            vertical.getColorAttachmentView(),
                            haloScale / maskFramebuffer.textureWidth,
                            haloScale / maskFramebuffer.textureHeight,
                            Math.clamp(intensity, 0.5F, 3.0F),
                            false);
                    return true;
                } else {
                    return false;
                }
            } catch (Throwable e) {
                disabled = true;
                deleteFramebuffers();
                System.err.println(
                        "[OdinClient Item Glow] Item shader unavailable; keeping regular outlines: "
                                + e.getMessage());
                return false;
            }
        } else {
            return false;
        }
    }

    private static void drawPostPass(
            RenderPipeline pipeline,
            GpuTextureView target,
            GpuTextureView source,
            float offsetX,
            float offsetY,
            float offsetZ,
            boolean clear) {
        GpuBufferSlice transforms =
                RenderSystem.getDynamicUniforms()
                        .write(
                                new Matrix4f(),
                                new Vector4f(1.0F),
                                new Vector3f(),
                                new Matrix4f().translation(offsetX, offsetY, offsetZ));
        ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);

        try (RenderPass pass =
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                                () -> "OdinClient Item Shader Halo",
                                target,
                                clear ? OptionalInt.of(0) : OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, quadBuffer);
            pass.setIndexBuffer(indexBuffer.getIndexBuffer(6), indexBuffer.getIndexType());
            GpuSampler sampler =
                    RenderSystem.getSamplerCache()
                            .get(
                                    AddressMode.CLAMP_TO_EDGE,
                                    AddressMode.CLAMP_TO_EDGE,
                                    FilterMode.LINEAR,
                                    FilterMode.LINEAR,
                                    false);
            pass.bindTexture("Sampler0", source, sampler);
            if (pipeline == compositePipeline) {
                pass.bindTexture("Sampler1", maskFramebuffer.getColorAttachmentView(), sampler);
            }

            pass.drawIndexed(0, 0, 6, 1);
        }
    }

    private static void initResources() {
        if (maskPipeline == null) {
            Identifier maskShader = Identifier.of("odinclient", "item_glow_mask");
            maskPipeline =
                    ShaderPipelineInvoker.odinclient$registerPipeline(
                            RenderPipeline.builder()
                                    .withLocation(
                                            Identifier.of("odinclient", "pipeline/item_glow_mask"))
                                    .withVertexShader(maskShader)
                                    .withFragmentShader(maskShader)
                                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                                    .withSampler("Sampler0")
                                    .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                                    .withCull(false)
                                    .withDepthWrite(false)
                                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                                    .withVertexFormat(
                                            VertexFormats
                                                    .POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                                            DrawMode.QUADS)
                                    .build());
        }

        if (blurPipeline == null) {
            blurPipeline = createPostPipeline("item_glow_blur", false);
        }

        if (compositePipeline == null) {
            compositePipeline = createPostPipeline("item_glow_composite", true);
        }

        if (quadBuffer == null) {
            try (BufferAllocator allocator =
                    BufferAllocator.fixedSized(
                            4 * VertexFormats.POSITION_TEXTURE.getVertexSize())) {
                BufferBuilder builder =
                        new BufferBuilder(
                                allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                builder.vertex(-1.0F, -1.0F, 0.0F).texture(0.0F, 0.0F);
                builder.vertex(1.0F, -1.0F, 0.0F).texture(1.0F, 0.0F);
                builder.vertex(1.0F, 1.0F, 0.0F).texture(1.0F, 1.0F);
                builder.vertex(-1.0F, 1.0F, 0.0F).texture(0.0F, 1.0F);

                try (BuiltBuffer built = builder.end()) {
                    quadBuffer =
                            RenderSystem.getDevice()
                                    .createBuffer(
                                            () -> "OdinClient Item Shader Quad",
                                            32,
                                            built.getBuffer());
                }
            }
        }
    }

    private static RenderPipeline createPostPipeline(String name, boolean composite) {
        Builder builder =
                RenderPipeline.builder()
                        .withLocation(Identifier.of("odinclient", "pipeline/" + name))
                        .withVertexShader(Identifier.of("odinclient", "item_glow_post"))
                        .withFragmentShader(Identifier.of("odinclient", name))
                        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                        .withSampler("Sampler0")
                        .withCull(false)
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withVertexFormat(VertexFormats.POSITION_TEXTURE, DrawMode.QUADS);
        if (composite) {
            builder.withSampler("Sampler1")
                    .withBlend(BlendFunction.ADDITIVE)
                    .withColorWrite(true, false);
        }

        return ShaderPipelineInvoker.odinclient$registerPipeline(builder.build());
    }

    private static void ensureFramebuffers(int width, int height) {
        if (maskFramebuffer == null
                || maskFramebuffer.textureWidth != width
                || maskFramebuffer.textureHeight != height) {
            deleteFramebuffers();
            maskFramebuffer =
                    new SimpleFramebuffer("OdinClient Item Shader Mask", width, height, false);
            horizontal =
                    new SimpleFramebuffer(
                            "OdinClient Item Shader Blur X",
                            (width + 1) / 2,
                            (height + 1) / 2,
                            false);
            vertical =
                    new SimpleFramebuffer(
                            "OdinClient Item Shader Blur Y",
                            (width + 1) / 2,
                            (height + 1) / 2,
                            false);
        }
    }

    private static void deleteFramebuffers() {
        if (maskFramebuffer != null) {
            maskFramebuffer.delete();
            maskFramebuffer = null;
        }

        if (horizontal != null) {
            horizontal.delete();
            horizontal = null;
        }

        if (vertical != null) {
            vertical.delete();
            vertical = null;
        }
    }

    public static void cleanup() {
        deleteFramebuffers();
        if (quadBuffer != null) {
            quadBuffer.close();
            quadBuffer = null;
        }

        disabled = false;
    }

    public record GlowQuadBatch(
            net.minecraft.client.util.math.MatrixStack.Entry pose,
            List<BakedQuad> quads,
            RenderLayer layer,
            int color) {}
}
