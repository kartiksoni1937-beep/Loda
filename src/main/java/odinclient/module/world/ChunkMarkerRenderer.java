package odinclient.module.world;

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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import odinclient.mixin.accessor.ShaderPipelineInvoker;
import odinclient.module.hud.regionmap.RegionBorderBuilder;
import odinclient.module.hud.regionmap.RegionFillBuilder;
import odinclient.render.compat.IrisCompat;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class ChunkMarkerRenderer {
    public static final double MARKER_Y = 63.018;
    private static List<ChunkAreaMerger.ChunkArea> chunkAreas = List.of();
    private static List<ChunkAreaMerger.ChunkArea> regionAreas = List.of();
    private static List<ChunkAreaMerger.ChunkArea> cachedChunkAreas = List.of();
    private static List<ChunkAreaMerger.ChunkArea> cachedRegionAreas = List.of();
    private static List<RegionBorderBuilder.BorderLine> regionLines = List.of();
    private static List<RegionFillBuilder.ColoredArea> coloredAreas = List.of();
    private static int chunkColor;
    private static int regionColor;
    private static int lineColor;
    private static int cachedChunkColor;
    private static int cachedRegionColor;
    private static RenderPipeline pipeline;
    private static boolean disabled;
    private static Camera camera;
    private static final Matrix4f modelViewMatrix = new Matrix4f();
    private static final Matrix4f projectionMatrix = new Matrix4f();

    private ChunkMarkerRenderer() {}

    public static void clearAreas() {
        chunkAreas = List.of();
        regionAreas = List.of();
        regionLines = List.of();
    }

    public static void captureCamera(Camera cam, Matrix4f modelView, Matrix4f projection) {
        if (!IrisCompat.isRenderingShadowPass()) {
            clearAreas();
            camera = cam;
            modelViewMatrix.set(modelView);
            projectionMatrix.set(projection);
        }
    }

    public static void setChunkAreas(List<ChunkAreaMerger.ChunkArea> areas, int color) {
        chunkAreas = areas;
        chunkColor = color;
    }

    public static void setRegionOverlay(
            RegionBorderBuilder.BorderResult result, int fillColor, int borderColor) {
        regionAreas = result.areas();
        regionLines = result.lines();
        regionColor = fillColor;
        lineColor = borderColor;
    }

    public static void render() {
        Camera cam = camera;
        Matrix4f modelView = modelViewMatrix;
        Matrix4f projection = projectionMatrix;

        try {
            if (disabled
                    || chunkAreas.isEmpty() && regionAreas.isEmpty()
                    || IrisCompat.isRenderingShadowPass()) {
                return;
            }

            if (cachedChunkAreas != chunkAreas
                    || cachedRegionAreas != regionAreas
                    || cachedChunkColor != chunkColor
                    || cachedRegionColor != regionColor) {
                coloredAreas =
                        RegionFillBuilder.buildColoredAreas(
                                chunkAreas, chunkColor, regionAreas, regionColor);
                cachedChunkAreas = chunkAreas;
                cachedRegionAreas = regionAreas;
                cachedChunkColor = chunkColor;
                cachedRegionColor = regionColor;
            }

            if (coloredAreas.isEmpty() && regionLines.isEmpty()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            Framebuffer framebuffer = client.getFramebuffer();
            GpuTextureView colorView = framebuffer.getColorAttachmentView();
            GpuTextureView depthView = framebuffer.getDepthAttachmentView();
            if (colorView == null || cam == null || modelView == null || projection == null) {
                return;
            }

            ensurePipeline();
            Vec3d camPos = cam.getCameraPos();
            BufferBuilder builder =
                    Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (RegionFillBuilder.ColoredArea coloredArea : coloredAreas) {
                ChunkAreaMerger.ChunkArea area = coloredArea.area();
                addVertex(
                        builder,
                        area.minBlockX() - camPos.x,
                        63.018 - camPos.y,
                        area.minBlockZ() - camPos.z,
                        coloredArea.color());
                addVertex(
                        builder,
                        area.maxBlockX() - camPos.x,
                        63.018 - camPos.y,
                        area.minBlockZ() - camPos.z,
                        coloredArea.color());
                addVertex(
                        builder,
                        area.maxBlockX() - camPos.x,
                        63.018 - camPos.y,
                        area.maxBlockZ() - camPos.z,
                        coloredArea.color());
                addVertex(
                        builder,
                        area.minBlockX() - camPos.x,
                        63.018 - camPos.y,
                        area.maxBlockZ() - camPos.z,
                        coloredArea.color());
            }

            double lineScale =
                    1.600000023841858
                            / (Math.max(0.25, (double) Math.abs(projection.m11()))
                                    * Math.max(1, colorView.getHeight(0)));

            for (RegionBorderBuilder.BorderLine line : regionLines) {
                Vector3d start =
                        new Vector3d(
                                line.startBlockX() - camPos.x,
                                63.026 - camPos.y,
                                line.startBlockZ() - camPos.z);
                Vector3d end =
                        new Vector3d(
                                line.endBlockX() - camPos.x, start.y, line.endBlockZ() - camPos.z);
                Vector3d normal =
                        new Vector3d(end).sub(start).cross(new Vector3d(start).add(end).mul(-0.5));
                if (normal.lengthSquared() < 1.0E-12) {
                    normal.set(0.0, 1.0, 0.0);
                }

                normal.normalize();
                double startWidth = Math.max(2.0E-4, start.length() * lineScale);
                double endWidth = Math.max(2.0E-4, end.length() * lineScale);
                addVertex(
                        builder,
                        start.x - normal.x * startWidth,
                        start.y - normal.y * startWidth,
                        start.z - normal.z * startWidth,
                        lineColor);
                addVertex(
                        builder,
                        start.x + normal.x * startWidth,
                        start.y + normal.y * startWidth,
                        start.z + normal.z * startWidth,
                        lineColor);
                addVertex(
                        builder,
                        end.x + normal.x * endWidth,
                        end.y + normal.y * endWidth,
                        end.z + normal.z * endWidth,
                        lineColor);
                addVertex(
                        builder,
                        end.x - normal.x * endWidth,
                        end.y - normal.y * endWidth,
                        end.z - normal.z * endWidth,
                        lineColor);
            }

            try (BuiltBuffer built = builder.end()) {
                GpuBuffer vertexBuffer =
                        VertexFormats.POSITION_COLOR.uploadImmediateVertexBuffer(built.getBuffer());
                int indexCount = built.getDrawParameters().indexCount();
                ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
                GpuBufferSlice transforms =
                        RenderSystem.getDynamicUniforms()
                                .write(
                                        new Matrix4f(modelView).setTranslation(0.0F, 0.0F, 0.0F),
                                        new Vector4f(1.0F),
                                        new Vector3f(),
                                        new Matrix4f());
                GpuBufferSlice projectionUniform =
                        RenderSystem.getDynamicUniforms()
                                .write(
                                        projection,
                                        new Vector4f(1.0F),
                                        new Vector3f(),
                                        new Matrix4f())
                                .slice(0L, 64L);

                try (RenderPass pass =
                        RenderSystem.getDevice()
                                .createCommandEncoder()
                                .createRenderPass(
                                        () -> "OdinClient Stable Chunk Markers",
                                        colorView,
                                        OptionalInt.empty(),
                                        depthView,
                                        OptionalDouble.empty())) {
                    pass.setPipeline(pipeline);
                    pass.setUniform("DynamicTransforms", transforms);
                    pass.setUniform("Projection", projectionUniform);
                    pass.setVertexBuffer(0, vertexBuffer);
                    pass.setIndexBuffer(
                            indexBuffer.getIndexBuffer(indexCount), indexBuffer.getIndexType());
                    pass.drawIndexed(0, 0, indexCount, 1);
                }
            }
        } catch (Throwable t) {
            disabled = true;
            System.err.println("[OdinClient] Chunk marker overlay unavailable: " + t.getMessage());
            t.printStackTrace();
        } finally {
            clearAreas();
            camera = null;
        }
    }

    private static void addVertex(BufferBuilder builder, double x, double y, double z, int color) {
        builder.vertex((float) x, (float) y, (float) z).color(color);
    }

    private static void ensurePipeline() {
        if (pipeline == null) {
            Identifier shader = Identifier.of("odinclient", "chunk_marker");
            pipeline =
                    ShaderPipelineInvoker.odinclient$registerPipeline(
                            RenderPipeline.builder()
                                    .withLocation(
                                            Identifier.of("odinclient", "pipeline/chunk_marker"))
                                    .withVertexShader(shader)
                                    .withFragmentShader(shader)
                                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                                    .withBlend(BlendFunction.TRANSLUCENT)
                                    .withCull(false)
                                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                                    .withDepthWrite(false)
                                    .withColorWrite(true, false)
                                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
                                    .build());
        }
    }
}
