package odinclient.render.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.FogRenderer.FogType;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.math.Vec3d;

import odinclient.mixin.accessor.GameRendererInvoker;
import odinclient.module.hud.ThemeModule;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

public final class TracerBatchRenderer {
    private static final int GL_MULTISAMPLE = 32925;
    private static Matrix4f matrix;
    private static MatrixStack matrices;
    private static BufferAllocator allocator;
    private static Immediate consumers;
    private static boolean hasTracers;
    private static boolean smoothBatch;

    private TracerBatchRenderer() {}

    public static void add(
            Matrix4f projection, Vec3d origin, Vec3d target, Vec3d point, int color, float width) {
        if (projection != null && origin != null && target != null && point != null) {
            if (consumers == null || matrix != projection) {
                flush();
                matrix = projection;
                matrices = new MatrixStack();
                matrices.multiplyPositionMatrix(projection);
                allocator = new BufferAllocator(2097152);
                consumers = VertexConsumerProvider.immediate(allocator);
                smoothBatch = ThemeModule.isMsaaEnabled();
            }

            double dx = target.x - origin.x;
            double dy = target.y - origin.y;
            double dz = target.z - origin.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (Double.isFinite(length) && !(length < 1.0E-8)) {
                dx /= length;
                dy /= length;
                dz /= length;
                double upY = 0.0;
                double upZ = -dx;
                double horizontal = Math.sqrt(dz * dz + upZ * upZ);
                double upX;
                if (Double.isFinite(horizontal) && horizontal >= 1.0E-8) {
                    upX = dz / horizontal;
                    upZ /= horizontal;
                } else {
                    upX = 1.0;
                    upZ = 0.0;
                }

                double sideX = dy * upZ - dz * upY;
                double sideY = dz * upX - dx * upZ;
                double sideZ = dx * upY - dy * upX;
                double sideLength = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
                if (Double.isFinite(sideLength) && !(sideLength < 1.0E-8)) {
                    sideX /= sideLength;
                    sideY /= sideLength;
                    sideZ /= sideLength;
                    double px = point.x - origin.x;
                    double py = point.y - origin.y;
                    double pz = point.z - origin.z;
                    double localU = px * upX + py * upY + pz * upZ;
                    double localV = px * sideX + py * sideY + pz * sideZ;
                    double depth = px * dx + py * dy + pz * dz;
                    double scaledDepth = Math.max(Math.abs(depth), 0.25);
                    double u = localU / scaledDepth;
                    double v = localV / scaledDepth;
                    if (depth <= 0.0) {
                        double radius = Math.hypot(u, v);
                        if (radius < 2.75) {
                            if (radius < 1.0E-4) {
                                u = 2.75;
                                v = 0.0;
                            } else {
                                double factor = 2.75 / radius;
                                u *= factor;
                                v *= factor;
                            }
                        }
                    }

                    double endX = dx + upX * u + sideX * v;
                    double endY = dy + upY * u + sideY * v;
                    double endZ = dz + upZ * u + sideZ * v;
                    double endLength = Math.sqrt(endX * endX + endY * endY + endZ * endZ);
                    if (Double.isFinite(endLength) && !(endLength < 1.0E-8)) {
                        endX = endX / endLength * 24.0;
                        endY = endY / endLength * 24.0;
                        endZ = endZ / endLength * 24.0;
                        float startX = (float) (dx * 150.0);
                        float startY = (float) (dy * 150.0);
                        float startZ = (float) (dz * 150.0);
                        float normalX = (float) (endX - startX);
                        float normalY = (float) (endY - startY);
                        float normalZ = (float) (endZ - startZ);
                        Vector3f normal = new Vector3f(normalX, normalY, normalZ).normalize();
                        float lineWidth = Math.max(1.0F, width);
                        VertexConsumer buffer = consumers.getBuffer(RenderLayers.lines());
                        Entry entry = matrices.peek();
                        if (smoothBatch) {
                            writeLine(
                                    buffer,
                                    entry,
                                    startX,
                                    startY,
                                    startZ,
                                    (float) endX,
                                    (float) endY,
                                    (float) endZ,
                                    withOpacity(color, 0.18F),
                                    normal,
                                    lineWidth + 2.0F);
                            writeLine(
                                    buffer,
                                    entry,
                                    startX,
                                    startY,
                                    startZ,
                                    (float) endX,
                                    (float) endY,
                                    (float) endZ,
                                    withOpacity(color, 0.42F),
                                    normal,
                                    lineWidth + 1.0F);
                        }

                        writeLine(
                                buffer,
                                entry,
                                startX,
                                startY,
                                startZ,
                                (float) endX,
                                (float) endY,
                                (float) endZ,
                                color,
                                normal,
                                lineWidth);
                        hasTracers = true;
                    }
                }
            }
        }
    }

    private static void writeLine(
            VertexConsumer buffer,
            Entry entry,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            int color,
            Vector3f normal,
            float lineWidth) {
        buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, normal).lineWidth(lineWidth);
        buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, normal).lineWidth(lineWidth);
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Math.max(0.0F, Math.min(1.0F, opacity)));
        return color & 16777215 | alpha << 24;
    }

    public static void flush() {
        Immediate batch = consumers;
        BufferAllocator buffers = allocator;
        if (batch != null) {
            boolean depthTestWasEnabled = GL11.glIsEnabled(2929);
            boolean multisampleWasEnabled = GL11.glIsEnabled(32925);
            GpuBufferSlice previousFog = RenderSystem.getShaderFog();
            boolean fogChanged = false;

            try {
                if (hasTracers) {
                    GL11.glDisable(2929);
                    if (smoothBatch) {
                        GL11.glEnable(32925);
                    }

                    try {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null
                                && client.gameRenderer instanceof GameRendererInvoker accessor) {
                            FogRenderer fogRenderer = accessor.odinclient$getFogRenderer();
                            if (fogRenderer != null) {
                                RenderSystem.setShaderFog(fogRenderer.getFogBuffer(FogType.NONE));
                                fogChanged = true;
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    batch.draw();
                }
            } finally {
                if (fogChanged) {
                    RenderSystem.setShaderFog(previousFog);
                }

                setEnabled(2929, depthTestWasEnabled);
                setEnabled(32925, multisampleWasEnabled);
                if (buffers != null) {
                    buffers.close();
                }

                matrix = null;
                matrices = null;
                allocator = null;
                consumers = null;
                hasTracers = false;
                smoothBatch = false;
            }
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
