package odinclient.module.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.FogRenderer.FogType;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.Vec3d;

import odinclient.mixin.accessor.GameRendererAccessor;
import odinclient.render.world.WorldShapeRenderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class LightFinderRenderer {
    private static final float CUBE_INSET = 0.025F;
    private static final int VERTICES_PER_CUBE = 8;
    private static final int INDICES_PER_CUBE = 36;
    private static final int VERTEX_SIZE = 16;
    private static final int SHORT_INDEX_SIZE = 2;
    private static final long MAX_UPLOAD_BYTES_PER_FRAME = 8388608L;
    private static final int[] LIGHT_LEVEL_COLORS = createLightColors();
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final Matrix4f baseModelView = new Matrix4f();
    private static final Matrix4f chunkModelView = new Matrix4f();
    private static final ConcurrentLinkedQueue<LightFinderRenderer.ChunkLightMesh> releaseQueue =
            new ConcurrentLinkedQueue<>();
    private static LightFinderRenderer.ChunkLightMesh[] queuedMeshes =
            new LightFinderRenderer.ChunkLightMesh[256];
    private static GpuBufferSlice[] transformSlices = new GpuBufferSlice[256];
    private static int queuedCount;

    private LightFinderRenderer() {}

    public static void clearQueued() {
        Arrays.fill(queuedMeshes, 0, queuedCount, null);
        queuedCount = 0;
    }

    public static void queueMesh(LightFinderRenderer.ChunkLightMesh mesh) {
        if (mesh != null && !mesh.isDisposed()) {
            if (queuedCount == queuedMeshes.length) {
                queuedMeshes = Arrays.copyOf(queuedMeshes, queuedMeshes.length << 1);
                transformSlices = Arrays.copyOf(transformSlices, transformSlices.length << 1);
            }

            queuedMeshes[queuedCount++] = mesh;
        }
    }

    public static LightFinderRenderer.ChunkLightMesh buildMesh(
            int originX, int originY, int originZ, int[] packedCubes) {
        if (packedCubes != null && packedCubes.length != 0) {
            int vertexCount = packedCubes.length * 8;
            boolean intIndices = vertexCount > 65535;
            int indexSize = intIndices ? 4 : 2;

            int indexCount = packedCubes.length * 36;
            long vertexBytes = vertexCount * 16L;
            int indexBytes = indexCount * indexSize;
            int allocatorSize = (int) Math.min(2147483639L, Math.max(4096L, vertexBytes));
            BufferAllocator allocator = new BufferAllocator(allocatorSize);
            BuiltBuffer builtBuffer = null;
            ByteBuffer indexData = null;

            try {
                BufferBuilder builder =
                        new BufferBuilder(allocator, DrawMode.POINTS, VertexFormats.POSITION_COLOR);
                indexData = MemoryUtil.memAlloc(indexBytes);
                int cubeIndex = 0;

                for (int packed : packedCubes) {
                    int x = packed >>> 4 & 15;
                    int z = packed >>> 8 & 15;
                    int y = packed >>> 12;
                    addCubeCorners(
                            builder,
                            x + 0.025F,
                            y + 0.025F,
                            z + 0.025F,
                            x + 1.0F - 0.025F,
                            y + 1.0F - 0.025F,
                            z + 1.0F - 0.025F,
                            LIGHT_LEVEL_COLORS[packed & 15]);
                    putCubeIndices(indexData, cubeIndex++ * 8, intIndices);
                }

                indexData.flip();
                builtBuffer = builder.end();
                if (builtBuffer == null) {
                    MemoryUtil.memFree(indexData);
                    allocator.close();
                    return null;
                } else {
                    long totalBytes = vertexBytes + indexBytes;
                    IndexType indexType = intIndices ? IndexType.INT : IndexType.SHORT;
                    return new LightFinderRenderer.ChunkLightMesh(
                            originX,
                            originY,
                            originZ,
                            indexCount,
                            totalBytes,
                            indexType,
                            allocator,
                            builtBuffer,
                            indexData);
                }
            } catch (Throwable error) {
                if (builtBuffer != null) {
                    try {
                        builtBuffer.close();
                    } catch (Throwable bufferCloseError) {
                    }
                }

                try {
                    allocator.close();
                } catch (Throwable allocatorCloseError) {
                }

                if (indexData != null) {
                    MemoryUtil.memFree(indexData);
                }

                throw error;
            }
        } else {
            return null;
        }
    }

    private static void addCubeCorners(
            BufferBuilder builder,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            int color) {
        addVertex(builder, minX, minY, minZ, color);
        addVertex(builder, maxX, minY, minZ, color);
        addVertex(builder, maxX, minY, maxZ, color);
        addVertex(builder, minX, minY, maxZ, color);
        addVertex(builder, minX, maxY, minZ, color);
        addVertex(builder, minX, maxY, maxZ, color);
        addVertex(builder, maxX, maxY, maxZ, color);
        addVertex(builder, maxX, maxY, minZ, color);
    }

    private static void addVertex(BufferBuilder builder, float x, float y, float z, int color) {
        builder.vertex(x, y, z).color(color);
    }

    private static void putCubeIndices(ByteBuffer buffer, int base, boolean intIndices) {
        putQuadIndices(buffer, base, 0, 1, 2, 3, intIndices);
        putQuadIndices(buffer, base, 4, 5, 6, 7, intIndices);
        putQuadIndices(buffer, base, 0, 4, 7, 1, intIndices);
        putQuadIndices(buffer, base, 2, 6, 5, 3, intIndices);
        putQuadIndices(buffer, base, 3, 5, 4, 0, intIndices);
        putQuadIndices(buffer, base, 1, 7, 6, 2, intIndices);
    }

    private static void putQuadIndices(
            ByteBuffer buffer, int base, int a, int b, int c, int d, boolean intIndices) {
        putIndex(buffer, base + a, intIndices);
        putIndex(buffer, base + b, intIndices);
        putIndex(buffer, base + c, intIndices);
        putIndex(buffer, base + c, intIndices);
        putIndex(buffer, base + d, intIndices);
        putIndex(buffer, base + a, intIndices);
    }

    private static void putIndex(ByteBuffer buffer, int index, boolean intIndices) {
        if (intIndices) {
            buffer.putInt(index);
        } else {
            buffer.putShort((short) index);
        }
    }

    public static void scheduleRelease(LightFinderRenderer.ChunkLightMesh mesh) {
        if (mesh != null && mesh.markDisposed()) {
            releaseQueue.offer(mesh);
        }
    }

    public static void renderQueued(Camera camera, Matrix4f modelView) {
        releasePending();
        int count = queuedCount;
        queuedCount = 0;
        if (count != 0 && camera != null && modelView != null) {
            int uploads = 0;
            long uploadedBytes = 0L;

            for (int i = 0; i < count; i++) {
                LightFinderRenderer.ChunkLightMesh uploadMesh = queuedMeshes[i];
                if (uploadMesh != null && !uploadMesh.isDisposed() && !uploadMesh.hasGpuBuffers()) {
                    long meshBytes = uploadMesh.getByteSize();
                    if ((uploads <= 0 || uploads < 2 && uploadedBytes + meshBytes <= 8388608L)
                            && uploadMesh.uploadBuffers()) {
                        uploads++;
                        uploadedBytes += meshBytes;
                    }
                }
            }

            int drawableCount = 0;

            for (int j = 0; j < count; j++) {
                LightFinderRenderer.ChunkLightMesh drawableMesh = queuedMeshes[j];
                if (drawableMesh != null && drawableMesh.isDrawable()) {
                    drawableCount++;
                }
            }

            if (drawableCount == 0) {
                Arrays.fill(queuedMeshes, 0, count, null);
            } else {
                RenderPipeline pipeline =
                        WorldShapeRenderer.getNoDepthFilledLayer().getRenderPipeline();
                Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
                GpuTextureView colorView =
                        RenderSystem.outputColorTextureOverride != null
                                ? RenderSystem.outputColorTextureOverride
                                : framebuffer.getColorAttachmentView();
                GpuTextureView depthView;
                if (framebuffer.useDepthAttachment) {
                    depthView =
                            RenderSystem.outputDepthTextureOverride != null
                                    ? RenderSystem.outputDepthTextureOverride
                                    : framebuffer.getDepthAttachmentView();
                } else {
                    depthView = null;
                }

                Vec3d cameraPos = camera.getCameraPos();
                baseModelView.set(modelView).setTranslation(0.0F, 0.0F, 0.0F);

                for (int k = 0; k < count; k++) {
                    LightFinderRenderer.ChunkLightMesh transformMesh = queuedMeshes[k];
                    if (transformMesh != null && transformMesh.isDrawable()) {
                        chunkModelView
                                .set(baseModelView)
                                .translate(
                                        (float) (transformMesh.originX - cameraPos.x),
                                        (float) (transformMesh.originY - cameraPos.y),
                                        (float) (transformMesh.originZ - cameraPos.z));
                        transformSlices[k] =
                                RenderSystem.getDynamicUniforms()
                                        .write(
                                                chunkModelView,
                                                COLOR_MODULATOR,
                                                MODEL_OFFSET,
                                                TEXTURE_MATRIX);
                    } else {
                        transformSlices[k] = null;
                    }
                }

                GpuBufferSlice fog = RenderSystem.getShaderFog();
                applyFog(fog);

                try {
                    try (RenderPass renderPass =
                            RenderSystem.getDevice()
                                    .createCommandEncoder()
                                    .createRenderPass(
                                            () -> "OdinClient Light Finder cached chunks",
                                            colorView,
                                            OptionalInt.empty(),
                                            depthView,
                                            OptionalDouble.empty())) {
                        renderPass.setPipeline(pipeline);
                        RenderSystem.bindDefaultUniforms(renderPass);

                        for (int n = 0; n < count; n++) {
                            LightFinderRenderer.ChunkLightMesh chunkMesh = queuedMeshes[n];
                            GpuBufferSlice transform = transformSlices[n];
                            if (chunkMesh != null && chunkMesh.isDrawable() && transform != null) {
                                renderPass.setUniform("DynamicTransforms", transform);
                                renderPass.setVertexBuffer(0, chunkMesh.getVertexBuffer());
                                renderPass.setIndexBuffer(
                                        chunkMesh.getIndexBuffer(), chunkMesh.indexType);
                                renderPass.drawIndexed(0, 0, chunkMesh.indexCount, 1);
                            }
                        }
                    }
                } catch (Throwable error) {
                    RenderSystem.setShaderFog(fog);
                    Arrays.fill(queuedMeshes, 0, count, null);
                    Arrays.fill(transformSlices, 0, count, null);
                    throw error;
                }

                RenderSystem.setShaderFog(fog);
                Arrays.fill(queuedMeshes, 0, count, null);
                Arrays.fill(transformSlices, 0, count, null);
            }
        } else {
            Arrays.fill(queuedMeshes, 0, count, null);
        }
    }

    private static void applyFog(GpuBufferSlice fallback) {
        RenderSystem.setShaderFog(null);

        try {
            GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
            if (gameRenderer instanceof GameRendererAccessor) {
                FogRenderer fogRenderer =
                        ((GameRendererAccessor) gameRenderer).zenya$getFogRenderer();
                if (fogRenderer != null) {
                    GpuBufferSlice fogBuffer = fogRenderer.getFogBuffer(FogType.NONE);
                    if (fogBuffer != null) {
                        RenderSystem.setShaderFog(fogBuffer);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (RenderSystem.getShaderFog() == null) {
            RenderSystem.setShaderFog(fallback);
        }
    }

    private static void releasePending() {
        while (true) {
            LightFinderRenderer.ChunkLightMesh mesh = releaseQueue.poll();
            if (mesh == null) {
                return;
            }

            mesh.closeGpuBuffers();
        }
    }

    private static int[] createLightColors() {
        int[] colors = new int[16];

        for (int level = 1; level <= 15; level++) {
            double ratio = level / 15.0;
            double curve = Math.pow(ratio, 1.25);
            int red = clampColorChannel((int) Math.round(32.0 + 223.0 * curve));
            int green = clampColorChannel((int) Math.round(27.0 + 228.0 * curve));
            int blue = clampColorChannel((int) Math.round(2.0 + 10.0 * curve));
            colors[level] =
                    clampColorChannel((int) Math.round(18.0 + 122.0 * ratio)) << 24
                            | red << 16
                            | green << 8
                            | blue;
        }

        return colors;
    }

    private static int clampColorChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static final class ChunkLightMesh {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int indexCount;
        private final long byteSize;
        private final IndexType indexType;
        private BufferAllocator allocator;
        private BuiltBuffer builtBuffer;
        private ByteBuffer indexData;
        private volatile GpuBuffer vertexBuffer;
        private volatile GpuBuffer indexBuffer;
        private volatile boolean disposed;

        private ChunkLightMesh(
                int originX,
                int originY,
                int originZ,
                int indexCount,
                long byteSize,
                IndexType indexType,
                BufferAllocator allocator,
                BuiltBuffer builtBuffer,
                ByteBuffer indexData) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.indexCount = indexCount;
            this.byteSize = byteSize;
            this.indexType = indexType;
            this.allocator = allocator;
            this.builtBuffer = builtBuffer;
            this.indexData = indexData;
        }

        private long getByteSize() {
            return this.byteSize;
        }

        private boolean isDisposed() {
            return this.disposed;
        }

        private boolean hasGpuBuffers() {
            return this.vertexBuffer != null && this.indexBuffer != null;
        }

        private boolean isDrawable() {
            GpuBuffer vertex = this.vertexBuffer;
            GpuBuffer index = this.indexBuffer;
            return !this.disposed
                    && vertex != null
                    && index != null
                    && !vertex.isClosed()
                    && !index.isClosed();
        }

        private GpuBuffer getVertexBuffer() {
            return this.vertexBuffer;
        }

        private GpuBuffer getIndexBuffer() {
            return this.indexBuffer;
        }

        private synchronized boolean uploadBuffers() {
            if (!this.disposed
                    && this.vertexBuffer == null
                    && this.indexBuffer == null
                    && this.builtBuffer != null
                    && this.indexData != null) {
                GpuBuffer vertex = null;
                GpuBuffer index = null;

                try {
                    vertex =
                            RenderSystem.getDevice()
                                    .createBuffer(
                                            () -> "OdinClient Light Finder chunk vertices",
                                            32,
                                            this.builtBuffer.getBuffer());
                    index =
                            RenderSystem.getDevice()
                                    .createBuffer(
                                            () -> "OdinClient Light Finder chunk indices",
                                            64,
                                            this.indexData);
                    this.vertexBuffer = vertex;
                    this.indexBuffer = index;
                    this.releaseCpuData();
                    return true;
                } catch (Throwable error) {
                    if (vertex != null && !vertex.isClosed()) {
                        vertex.close();
                    }

                    if (index != null && !index.isClosed()) {
                        index.close();
                    }

                    throw error;
                }
            } else {
                return false;
            }
        }

        private synchronized boolean markDisposed() {
            if (this.disposed) {
                return false;
            } else {
                this.disposed = true;
                return true;
            }
        }

        private synchronized void closeGpuBuffers() {
            GpuBuffer vertex = this.vertexBuffer;
            GpuBuffer index = this.indexBuffer;
            this.vertexBuffer = null;
            this.indexBuffer = null;
            if (vertex != null && !vertex.isClosed()) {
                vertex.close();
            }

            if (index != null && !index.isClosed()) {
                index.close();
            }

            this.releaseCpuData();
        }

        private void releaseCpuData() {
            if (this.builtBuffer != null) {
                try {
                    this.builtBuffer.close();
                } catch (Throwable bufferError) {
                    this.builtBuffer = null;
                    throw bufferError;
                }

                this.builtBuffer = null;
            }

            if (this.allocator != null) {
                try {
                    this.allocator.close();
                } catch (Throwable allocatorError) {
                    this.allocator = null;
                    throw allocatorError;
                }

                this.allocator = null;
            }

            if (this.indexData != null) {
                MemoryUtil.memFree(this.indexData);
                this.indexData = null;
            }
        }
    }

    record LightCell(int x, int y, int z, int level) {}
}
