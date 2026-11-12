package odinclient.render.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.FogRenderer.FogType;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;

import odinclient.mixin.accessor.GameRendererAccessor;
import odinclient.render.WorldToScreen;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.reflect.Method;

public class WorldShapeRenderer {
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private static final RenderPipeline FILLED_BOX_PIPELINE;
    private static final RenderPipeline LINES_PIPELINE;
    private static final float[][] ICOSPHERE_VERTICES;
    private static final int[][] ICOSPHERE_FACES;
    private static final RenderLayer FILLED_BOX_LAYER;
    private static final RenderLayer LINES_LAYER;
    private static final ThreadLocal<WorldShapeRenderer.ThreadShapeBatch> THREAD_BATCH =
            ThreadLocal.withInitial(WorldShapeRenderer.ThreadShapeBatch::new);
    public static GpuBufferSlice savedFog;
    private static boolean frustumReady;
    private static double frustumOffsetX;
    private static double frustumOffsetY;
    private static double frustumOffsetZ;
    private static Method cameraPosMethod;

    public static RenderLayer getNoDepthFilledLayer() {
        return FILLED_BOX_LAYER;
    }

    private static void drawWithoutFog(Immediate consumers) {
        GpuBufferSlice previousFog = RenderSystem.getShaderFog();

        try {
            RenderSystem.setShaderFog(null);

            try {
                GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
                if (gameRenderer instanceof GameRendererAccessor) {
                    FogRenderer fogRenderer =
                            ((GameRendererAccessor) gameRenderer).zenya$getFogRenderer();
                    if (fogRenderer != null) {
                        GpuBufferSlice noFog = fogRenderer.getFogBuffer(FogType.NONE);
                        if (noFog != null) {
                            RenderSystem.setShaderFog(noFog);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            consumers.draw();
        } finally {
            RenderSystem.setShaderFog(previousFog);
        }
    }

    public static WorldShapeRenderer.ShapeBatch createBatch(MatrixStack matrices) {
        return new WorldShapeRenderer.ShapeBatch(matrices);
    }

    public static void updateFrustum(
            Matrix4f viewMatrix, Matrix4f projectionMatrix, Vec3d cameraPos) {
        if (viewMatrix != null && projectionMatrix != null && cameraPos != null) {
            projectionMatrix.mul(viewMatrix, MATRIX);
            FRUSTUM.set(MATRIX);
            frustumOffsetX = cameraPos.x;
            frustumOffsetY = cameraPos.y;
            frustumOffsetZ = cameraPos.z;
            frustumReady = true;
        } else {
            frustumReady = false;
        }
    }

    public static boolean isBoxInFrustum(
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!frustumReady) {
            return true;
        } else {
            int result =
                    FRUSTUM.intersectAab(
                            (float) (minX - frustumOffsetX),
                            (float) (minY - frustumOffsetY),
                            (float) (minZ - frustumOffsetZ),
                            (float) (maxX - frustumOffsetX),
                            (float) (maxY - frustumOffsetY),
                            (float) (maxZ - frustumOffsetZ));
            return result == -1 || result == -2;
        }
    }

    public static Camera getCamera() {
        return MinecraftClient.getInstance().gameRenderer.getCamera();
    }

    public static Vec3d getCameraPosition(Camera camera) {
        if (cameraPosMethod == null) {
            for (Method method : Camera.class.getMethods()) {
                if (method.getReturnType() == Vec3d.class && method.getParameterCount() == 0) {
                    cameraPosMethod = method;
                    break;
                }
            }
        }

        try {
            return (Vec3d) cameraPosMethod.invoke(camera);
        } catch (Exception e) {
            return MinecraftClient.getInstance().player.getCameraPosVec(1.0F);
        }
    }

    public static void drawFilledBox(
            MatrixStack matrices,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            Color color) {
        WorldShapeRenderer.ThreadShapeBatch batch = THREAD_BATCH.get();
        batch.begin(matrices);
        batch.drawFilledBox(minX, minY, minZ, maxX, maxY, maxZ, color);
        batch.flush();
    }

    public static void drawBoxOutline(
            MatrixStack matrices,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            Color color) {
        WorldShapeRenderer.ThreadShapeBatch batch = THREAD_BATCH.get();
        batch.begin(matrices);
        batch.drawBoxOutline(minX, minY, minZ, maxX, maxY, maxZ, color);
        batch.flush();
    }

    public static void drawLine(MatrixStack matrices, Color color, Vec3d from, Vec3d to) {
        drawLineWithWidth(matrices, color, from, to, 2.0F);
    }

    public static void drawLineWithWidth(
            MatrixStack matrices, Color color, Vec3d from, Vec3d to, float width) {
        WorldShapeRenderer.ThreadShapeBatch batch = THREAD_BATCH.get();
        batch.begin(matrices);
        batch.drawLine(color, from, to, width);
        batch.flush();
    }

    public static Vec3d getCameraForward(Camera camera) {
        return new Vec3d(0.0, 0.0, 1.0)
                .rotateX(-((float) Math.toRadians(camera.getPitch())))
                .rotateY(-((float) Math.toRadians(camera.getYaw())))
                .normalize();
    }

    public static Vec3d getCameraRight(Camera camera) {
        return new Vec3d(1.0, 0.0, 0.0)
                .rotateY(-((float) Math.toRadians(camera.getYaw())))
                .normalize();
    }

    public static Vec3d getCameraUp(Vec3d forward, Vec3d right) {
        return forward.crossProduct(right).normalize();
    }

    public static Vec3d projectToScreenEdge(
            double x,
            double y,
            double z,
            Vec3d forward,
            Vec3d right,
            Vec3d up,
            double distance,
            double minRadius) {
        double rightDot = x * right.x + y * right.y + z * right.z;
        double upDot = x * up.x + y * up.y + z * up.z;
        double forwardDot = x * forward.x + y * forward.y + z * forward.z;
        double depth = Math.max(Math.abs(forwardDot), 0.25);
        double px = rightDot / depth;
        double py = upDot / depth;
        if (forwardDot <= 0.0) {
            double radius = Math.hypot(px, py);
            if (radius < minRadius) {
                if (radius < 1.0E-4) {
                    px = minRadius;
                    py = 0.0;
                } else {
                    double scale = minRadius / radius;
                    px *= scale;
                    py *= scale;
                }
            }
        }

        return forward.add(right.multiply(px)).add(up.multiply(py)).normalize().multiply(distance);
    }

    public static Vec3d projectPointToScreenEdge(
            Vec3d point, Vec3d forward, Vec3d right, Vec3d up, double distance, double minRadius) {
        return projectToScreenEdge(
                point.x, point.y, point.z, forward, right, up, distance, minRadius);
    }

    public static Vec3d getOffscreenArrowDirection(
            Vec3d fallback, Vec3d target, Vec3d forward, Vec3d right, Vec3d up, double distance) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        Pair<Vec3d, Boolean> projected =
                WorldToScreen.project(
                        WorldToScreen.viewMatrix, WorldToScreen.projectionMatrix, target);
        if (projected != null && projected.getRight()) {
            Vec3d screenPos = projected.getLeft();
            if (screenPos.x >= 0.0
                    && screenPos.x <= width
                    && screenPos.y >= 0.0
                    && screenPos.y <= height) {
                return fallback;
            }
        }

        Vector3f clamped =
                WorldToScreen.projectClamped(
                        WorldToScreen.viewMatrix, WorldToScreen.projectionMatrix, target);
        return clamped == null
                ? fallback
                : screenPointToDirection(clamped.x, clamped.y, width, height, forward, right, up)
                        .multiply(distance);
    }

    public static Vec3d clampDirectionToScreen(
            Vec3d direction, Vec3d forward, Vec3d right, Vec3d up, double distance) {
        double forwardDot = direction.dotProduct(forward);
        double rightDot = direction.dotProduct(right);
        double upDot = direction.dotProduct(up);
        double tanY = Math.max(1.0E-4, Math.abs(WorldToScreen.projectionMatrix.m11()));
        double aspect =
                Math.abs(
                        WorldToScreen.projectionMatrix.m11()
                                / WorldToScreen.projectionMatrix.m00());
        double invTanY = 1.0 / tanY;
        if (forwardDot <= 0.05) {
            double behindX = rightDot / Math.max(1.0E-4, aspect * invTanY);
            double behindY = upDot / Math.max(1.0E-4, invTanY);
            double behindMax = Math.max(Math.abs(behindX), Math.abs(behindY));
            double edgeX;
            double edgeY;
            if (behindMax < 1.0E-4) {
                edgeX = 0.0;
                edgeY = 0.96;
            } else {
                edgeX = behindX / behindMax * 0.96;
                edgeY = behindY / behindMax * 0.96;
            }

            return forward.add(right.multiply(edgeX * aspect * invTanY))
                    .add(up.multiply(edgeY * invTanY))
                    .normalize()
                    .multiply(distance);
        } else {
            double nx = rightDot / (forwardDot * invTanY * aspect);
            double ny = upDot / (forwardDot * invTanY);
            double maxNorm = Math.max(Math.abs(nx), Math.abs(ny));
            if (maxNorm <= 1.0) {
                return direction;
            } else {
                double scale = 0.96 / maxNorm;
                double scaledY = ny * scale;
                return forward.add(right.multiply(nx * scale * aspect * invTanY))
                        .add(up.multiply(scaledY * invTanY))
                        .normalize()
                        .multiply(distance);
            }
        }
    }

    private static Vec3d screenPointToDirection(
            float screenX,
            float screenY,
            int width,
            int height,
            Vec3d forward,
            Vec3d right,
            Vec3d up) {
        double ndcX = screenX / width * 2.0 - 1.0;
        double ndcY = 1.0 - screenY / height * 2.0;
        double aspect = WorldToScreen.projectionMatrix.m11() / WorldToScreen.projectionMatrix.m00();
        double invTanY = 1.0 / WorldToScreen.projectionMatrix.m11();
        return forward.add(right.multiply(ndcX * aspect * invTanY))
                .add(up.multiply(ndcY * invTanY))
                .normalize();
    }

    static {
        FILLED_BOX_PIPELINE =
                RenderPipelines.register(
                        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                                .withLocation("pipeline/zenya_no_depth_filled_box")
                                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                .withDepthWrite(false)
                                .withCull(false)
                                .build());
        LINES_PIPELINE =
                RenderPipelines.register(
                        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                                .withLocation("pipeline/zenya_no_depth_lines")
                                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                .withDepthWrite(false)
                                .build());
        ICOSPHERE_FACES =
                new int[][] {
                    {0, 8, 4}, {0, 2, 8}, {0, 10, 2}, {0, 6, 10}, {0, 4, 6},
                    {3, 5, 9}, {3, 7, 5}, {3, 11, 7}, {3, 1, 11}, {3, 9, 1},
                    {4, 1, 6}, {4, 9, 1}, {4, 8, 9}, {8, 5, 9}, {8, 2, 5},
                    {2, 7, 5}, {2, 10, 7}, {10, 11, 7}, {10, 6, 11}, {6, 1, 11}
                };
        FILLED_BOX_LAYER =
                RenderLayer.of(
                        "zenya_no_depth_filled_box",
                        RenderSetup.builder(FILLED_BOX_PIPELINE).translucent().build());
        LINES_LAYER =
                RenderLayer.of("zenya_no_depth_lines", RenderSetup.builder(LINES_PIPELINE).build());
        double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
        double[][] vertices =
                new double[][] {
                    {0.0, 1.0, phi}, {0.0, 1.0, -phi}, {0.0, -1.0, phi}, {0.0, -1.0, -phi},
                    {1.0, phi, 0.0}, {1.0, -phi, 0.0}, {-1.0, phi, 0.0}, {-1.0, -phi, 0.0},
                    {phi, 0.0, 1.0}, {phi, 0.0, -1.0}, {-phi, 0.0, 1.0}, {-phi, 0.0, -1.0}
                };
        ICOSPHERE_VERTICES = new float[vertices.length][3];
        double norm = 1.0 / Math.sqrt(1.0 + phi * phi);

        for (int i = 0; i < vertices.length; i++) {
            ICOSPHERE_VERTICES[i][0] = (float) (vertices[i][0] * norm);
            ICOSPHERE_VERTICES[i][1] = (float) (vertices[i][1] * norm);
            ICOSPHERE_VERTICES[i][2] = (float) (vertices[i][2] * norm);
        }
    }

    public static final class ShapeBatch {
        private final MatrixStack matrices;
        private final BufferAllocator bufferAllocator;
        private final Immediate consumers;
        private boolean hasVertices;
        private boolean closed;

        private ShapeBatch(MatrixStack matrices) {
            this.matrices = matrices;
            this.bufferAllocator = new BufferAllocator(524288);
            this.consumers = VertexConsumerProvider.immediate(this.bufferAllocator);
        }

        public void drawBoxFilled(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                Color color) {
            this.drawFilledBox(minX, minY, minZ, maxX, maxY, maxZ, color);
        }

        public void drawFilledBox(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                Color color) {
            VertexConsumer buffer = this.consumers.getBuffer(WorldShapeRenderer.FILLED_BOX_LAYER);
            int argb =
                    color.getAlpha() << 24
                            | color.getRed() << 16
                            | color.getGreen() << 8
                            | color.getBlue();
            Entry entry = this.matrices.peek();
            float x1 = (float) minX;
            float y1 = (float) minY;
            float z1 = (float) minZ;
            float x2 = (float) maxX;
            float y2 = (float) maxY;
            float z2 = (float) maxZ;
            buffer.vertex(entry, x1, y1, z1).color(argb);
            buffer.vertex(entry, x2, y1, z1).color(argb);
            buffer.vertex(entry, x2, y1, z2).color(argb);
            buffer.vertex(entry, x1, y1, z2).color(argb);
            buffer.vertex(entry, x1, y2, z1).color(argb);
            buffer.vertex(entry, x1, y2, z2).color(argb);
            buffer.vertex(entry, x2, y2, z2).color(argb);
            buffer.vertex(entry, x2, y2, z1).color(argb);
            buffer.vertex(entry, x1, y1, z1).color(argb);
            buffer.vertex(entry, x1, y2, z1).color(argb);
            buffer.vertex(entry, x2, y2, z1).color(argb);
            buffer.vertex(entry, x2, y1, z1).color(argb);
            buffer.vertex(entry, x2, y1, z2).color(argb);
            buffer.vertex(entry, x2, y2, z2).color(argb);
            buffer.vertex(entry, x1, y2, z2).color(argb);
            buffer.vertex(entry, x1, y1, z2).color(argb);
            buffer.vertex(entry, x1, y1, z2).color(argb);
            buffer.vertex(entry, x1, y2, z2).color(argb);
            buffer.vertex(entry, x1, y2, z1).color(argb);
            buffer.vertex(entry, x1, y1, z1).color(argb);
            buffer.vertex(entry, x2, y1, z1).color(argb);
            buffer.vertex(entry, x2, y2, z1).color(argb);
            buffer.vertex(entry, x2, y2, z2).color(argb);
            buffer.vertex(entry, x2, y1, z2).color(argb);
            this.hasVertices = true;
        }

        public void drawBoxOutline(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                Color color) {
            double thickness =
                    Math.min(
                            0.02,
                            Math.min(
                                    (maxX - minX) / 8.0,
                                    Math.min((maxY - minY) / 8.0, (maxZ - minZ) / 8.0)));
            if (!(thickness <= 0.0)) {
                this.drawFilledBox(
                        minX, minY, minZ, maxX, minY + thickness, minZ + thickness, color);
                this.drawFilledBox(
                        minX, minY, maxZ - thickness, maxX, minY + thickness, maxZ, color);
                this.drawFilledBox(
                        minX,
                        minY,
                        minZ + thickness,
                        minX + thickness,
                        minY + thickness,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        maxX - thickness,
                        minY,
                        minZ + thickness,
                        maxX,
                        minY + thickness,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        minX, maxY - thickness, minZ, maxX, maxY, minZ + thickness, color);
                this.drawFilledBox(
                        minX, maxY - thickness, maxZ - thickness, maxX, maxY, maxZ, color);
                this.drawFilledBox(
                        minX,
                        maxY - thickness,
                        minZ + thickness,
                        minX + thickness,
                        maxY,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        maxX - thickness,
                        maxY - thickness,
                        minZ + thickness,
                        maxX,
                        maxY,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        minX,
                        minY + thickness,
                        minZ,
                        minX + thickness,
                        maxY - thickness,
                        minZ + thickness,
                        color);
                this.drawFilledBox(
                        maxX - thickness,
                        minY + thickness,
                        minZ,
                        maxX,
                        maxY - thickness,
                        minZ + thickness,
                        color);
                this.drawFilledBox(
                        minX,
                        minY + thickness,
                        maxZ - thickness,
                        minX + thickness,
                        maxY - thickness,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        maxX - thickness,
                        minY + thickness,
                        maxZ - thickness,
                        maxX,
                        maxY - thickness,
                        maxZ - thickness,
                        color);
            }
        }

        public void drawIcosphere(double x, double y, double z, double radius, Color color) {
            VertexConsumer buffer = this.consumers.getBuffer(WorldShapeRenderer.FILLED_BOX_LAYER);
            int argb =
                    color.getAlpha() << 24
                            | color.getRed() << 16
                            | color.getGreen() << 8
                            | color.getBlue();
            Entry entry = this.matrices.peek();
            float fx = (float) x;
            float fy = (float) y;
            float fz = (float) z;
            float scale = (float) radius;

            for (int[] face : WorldShapeRenderer.ICOSPHERE_FACES) {
                float[] a = WorldShapeRenderer.ICOSPHERE_VERTICES[face[0]];
                float[] b = WorldShapeRenderer.ICOSPHERE_VERTICES[face[1]];
                float[] c = WorldShapeRenderer.ICOSPHERE_VERTICES[face[2]];
                buffer.vertex(entry, fx + a[0] * scale, fy + a[1] * scale, fz + a[2] * scale)
                        .color(argb);
                buffer.vertex(entry, fx + b[0] * scale, fy + b[1] * scale, fz + b[2] * scale)
                        .color(argb);
                buffer.vertex(entry, fx + c[0] * scale, fy + c[1] * scale, fz + c[2] * scale)
                        .color(argb);
                buffer.vertex(entry, fx + c[0] * scale, fy + c[1] * scale, fz + c[2] * scale)
                        .color(argb);
            }

            this.hasVertices = true;
        }

        public void drawLine(Color color, Vec3d from, Vec3d to, float width) {
            VertexConsumer buffer = this.consumers.getBuffer(WorldShapeRenderer.LINES_LAYER);
            int argb =
                    color.getAlpha() << 24
                            | color.getRed() << 16
                            | color.getGreen() << 8
                            | color.getBlue();
            Entry entry = this.matrices.peek();
            Vector3f normal =
                    new Vector3f(
                                    (float) (to.x - from.x),
                                    (float) (to.y - from.y),
                                    (float) (to.z - from.z))
                            .normalize();
            float lineWidth = Math.max(0.1F, width);
            buffer.vertex(entry, (float) from.x, (float) from.y, (float) from.z)
                    .color(argb)
                    .normal(entry, normal)
                    .lineWidth(lineWidth);
            buffer.vertex(entry, (float) to.x, (float) to.y, (float) to.z)
                    .color(argb)
                    .normal(entry, normal)
                    .lineWidth(lineWidth);
            this.hasVertices = true;
        }

        public void drawGlowLine(Color color, Vec3d from, Vec3d to, float width) {
            if (color != null
                    && from != null
                    && to != null
                    && !(from.squaredDistanceTo(to) < 1.0E-8)) {
                float coreWidth = Math.max(0.75F, width);
                float outerWidth = coreWidth + 1.8F;
                this.drawLine(withAlphaScale(color, 0.1F), from, to, outerWidth);
                float innerWidth = coreWidth + 0.8F;
                this.drawLine(withAlphaScale(color, 0.24F), from, to, innerWidth);
                this.drawLine(color, from, to, coreWidth);
            }
        }

        private static Color withAlphaScale(Color color, float scale) {
            int alpha = Math.max(1, Math.min(255, Math.round(color.getAlpha() * scale)));
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }

        public void drawScreenSpaceLine(Color color, Vec3d from, Vec3d to, float width) {
            if (!(to.subtract(from).lengthSquared() < 1.0E-8)) {
                Camera camera = WorldShapeRenderer.getCamera();
                if (camera != null) {
                    Vec3d forward = WorldShapeRenderer.getCameraForward(camera);
                    Vec3d right = WorldShapeRenderer.getCameraRight(camera);
                    Vec3d up = WorldShapeRenderer.getCameraUp(forward, right);
                    int screenWidth =
                            Math.max(
                                    1,
                                    MinecraftClient.getInstance()
                                            .getWindow()
                                            .getFramebufferWidth());
                    int screenHeight =
                            Math.max(
                                    1,
                                    MinecraftClient.getInstance()
                                            .getWindow()
                                            .getFramebufferHeight());
                    double scaleX =
                            Math.max(1.0E-4, Math.abs(WorldToScreen.projectionMatrix.m00()));
                    double scaleY =
                            Math.max(1.0E-4, Math.abs(WorldToScreen.projectionMatrix.m11()));
                    double lineWidth = Math.max(0.35, width);
                    double fromDepth = Math.max(0.05, from.dotProduct(forward));
                    double toDepth = Math.max(0.05, to.dotProduct(forward));
                    double fromX = from.dotProduct(right) * scaleX / fromDepth;
                    double fromY = from.dotProduct(up) * scaleY / fromDepth;
                    double pixelDx =
                            (to.dotProduct(right) * scaleX / toDepth - fromX) * screenWidth * 0.5;
                    double pixelDy =
                            (to.dotProduct(up) * scaleY / toDepth - fromY) * screenHeight * 0.5;
                    double pixelLength = Math.hypot(pixelDx, pixelDy);
                    double dirY = pixelLength < 1.0E-5 ? 1.0 : -pixelDy / pixelLength;
                    double dirX = pixelLength < 1.0E-5 ? 0.0 : pixelDx / pixelLength;

                    VertexConsumer buffer =
                            this.consumers.getBuffer(WorldShapeRenderer.FILLED_BOX_LAYER);
                    int argb =
                            color.getAlpha() << 24
                                    | color.getRed() << 16
                                    | color.getGreen() << 8
                                    | color.getBlue();
                    Entry entry = this.matrices.peek();
                    int glowArgb =
                            Math.max(1, Math.round(color.getAlpha() * 0.08F)) << 24
                                    | color.getRed() << 16
                                    | color.getGreen() << 8
                                    | color.getBlue();
                    double glowHalfWidth = (lineWidth + 0.6) * 0.5;
                    addQuad(
                            buffer,
                            entry,
                            from,
                            to,
                            getLineOffset(
                                    right,
                                    up,
                                    dirY,
                                    dirX,
                                    glowHalfWidth,
                                    fromDepth,
                                    scaleX,
                                    scaleY,
                                    screenWidth,
                                    screenHeight),
                            getLineOffset(
                                    right,
                                    up,
                                    dirY,
                                    dirX,
                                    glowHalfWidth,
                                    toDepth,
                                    scaleX,
                                    scaleY,
                                    screenWidth,
                                    screenHeight),
                            glowArgb);
                    double halfWidth = lineWidth * 0.5;
                    addQuad(
                            buffer,
                            entry,
                            from,
                            to,
                            getLineOffset(
                                    right,
                                    up,
                                    dirY,
                                    dirX,
                                    halfWidth,
                                    fromDepth,
                                    scaleX,
                                    scaleY,
                                    screenWidth,
                                    screenHeight),
                            getLineOffset(
                                    right,
                                    up,
                                    dirY,
                                    dirX,
                                    halfWidth,
                                    toDepth,
                                    scaleX,
                                    scaleY,
                                    screenWidth,
                                    screenHeight),
                            argb);
                    this.hasVertices = true;
                }
            }
        }

        private static Vec3d getLineOffset(
                Vec3d right,
                Vec3d up,
                double dirY,
                double dirX,
                double halfWidth,
                double depth,
                double scaleX,
                double scaleY,
                int screenWidth,
                int screenHeight) {
            double upAmount = dirX * halfWidth * 2.0 * depth / (screenHeight * scaleY);
            return right.multiply(dirY * halfWidth * 2.0 * depth / (screenWidth * scaleX))
                    .add(up.multiply(upAmount));
        }

        private static void addQuad(
                VertexConsumer buffer,
                Entry entry,
                Vec3d from,
                Vec3d to,
                Vec3d fromOffset,
                Vec3d toOffset,
                int argb) {
            Vec3d a = from.add(fromOffset);
            Vec3d b = from.subtract(fromOffset);
            Vec3d c = to.subtract(toOffset);
            Vec3d d = to.add(toOffset);
            buffer.vertex(entry, (float) a.x, (float) a.y, (float) a.z).color(argb);
            buffer.vertex(entry, (float) b.x, (float) b.y, (float) b.z).color(argb);
            buffer.vertex(entry, (float) c.x, (float) c.y, (float) c.z).color(argb);
            buffer.vertex(entry, (float) d.x, (float) d.y, (float) d.z).color(argb);
        }

        public void finish() {
            if (!this.closed) {
                try {
                    if (this.hasVertices) {
                        boolean depthTest = GL11.glIsEnabled(2929);
                        boolean lineSmooth = GL11.glIsEnabled(2848);
                        boolean multisample = GL11.glIsEnabled(32925);
                        GL11.glDisable(2929);
                        GL11.glEnable(2848);
                        GL11.glEnable(32925);
                        GL11.glHint(3154, 4354);
                        GL11.glDepthMask(false);
                        WorldShapeRenderer.drawWithoutFog(this.consumers);
                        GL11.glDepthMask(true);
                        if (!lineSmooth) {
                            GL11.glDisable(2848);
                        }

                        if (!multisample) {
                            GL11.glDisable(32925);
                        }

                        if (depthTest) {
                            GL11.glEnable(2929);
                        }

                        this.hasVertices = false;
                    }
                } finally {
                    this.bufferAllocator.close();
                    this.closed = true;
                }
            }
        }
    }

    private static final class ThreadShapeBatch {
        private final BufferAllocator bufferAllocator = new BufferAllocator(524288);
        private final Immediate consumers = VertexConsumerProvider.immediate(this.bufferAllocator);
        private MatrixStack matrices;
        private boolean hasVertices;

        void begin(MatrixStack matrices) {
            this.matrices = matrices;
            this.hasVertices = false;
        }

        void drawBoxFilled(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                Color color) {
            this.drawFilledBox(minX, minY, minZ, maxX, maxY, maxZ, color);
        }

        void drawFilledBox(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                Color color) {
            VertexConsumer buffer = this.consumers.getBuffer(WorldShapeRenderer.FILLED_BOX_LAYER);
            int argb =
                    color.getAlpha() << 24
                            | color.getRed() << 16
                            | color.getGreen() << 8
                            | color.getBlue();
            Entry entry = this.matrices.peek();
            float x1 = (float) minX;
            float y1 = (float) minY;
            float z1 = (float) minZ;
            float x2 = (float) maxX;
            float y2 = (float) maxY;
            float z2 = (float) maxZ;
            buffer.vertex(entry, x1, y1, z1).color(argb);
            buffer.vertex(entry, x2, y1, z1).color(argb);
            buffer.vertex(entry, x2, y1, z2).color(argb);
            buffer.vertex(entry, x1, y1, z2).color(argb);
            buffer.vertex(entry, x1, y2, z1).color(argb);
            buffer.vertex(entry, x1, y2, z2).color(argb);
            buffer.vertex(entry, x2, y2, z2).color(argb);
            buffer.vertex(entry, x2, y2, z1).color(argb);
            buffer.vertex(entry, x1, y1, z1).color(argb);
            buffer.vertex(entry, x1, y2, z1).color(argb);
            buffer.vertex(entry, x2, y2, z1).color(argb);
            buffer.vertex(entry, x2, y1, z1).color(argb);
            buffer.vertex(entry, x2, y1, z2).color(argb);
            buffer.vertex(entry, x2, y2, z2).color(argb);
            buffer.vertex(entry, x1, y2, z2).color(argb);
            buffer.vertex(entry, x1, y1, z2).color(argb);
            buffer.vertex(entry, x1, y1, z2).color(argb);
            buffer.vertex(entry, x1, y2, z2).color(argb);
            buffer.vertex(entry, x1, y2, z1).color(argb);
            buffer.vertex(entry, x1, y1, z1).color(argb);
            buffer.vertex(entry, x2, y1, z1).color(argb);
            buffer.vertex(entry, x2, y2, z1).color(argb);
            buffer.vertex(entry, x2, y2, z2).color(argb);
            buffer.vertex(entry, x2, y1, z2).color(argb);
            this.hasVertices = true;
        }

        void drawBoxOutline(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                Color color) {
            double thickness =
                    Math.min(
                            0.02,
                            Math.min(
                                    (maxX - minX) / 8.0,
                                    Math.min((maxY - minY) / 8.0, (maxZ - minZ) / 8.0)));
            if (!(thickness <= 0.0)) {
                this.drawFilledBox(
                        minX, minY, minZ, maxX, minY + thickness, minZ + thickness, color);
                this.drawFilledBox(
                        minX, minY, maxZ - thickness, maxX, minY + thickness, maxZ, color);
                this.drawFilledBox(
                        minX,
                        minY,
                        minZ + thickness,
                        minX + thickness,
                        minY + thickness,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        maxX - thickness,
                        minY,
                        minZ + thickness,
                        maxX,
                        minY + thickness,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        minX, maxY - thickness, minZ, maxX, maxY, minZ + thickness, color);
                this.drawFilledBox(
                        minX, maxY - thickness, maxZ - thickness, maxX, maxY, maxZ, color);
                this.drawFilledBox(
                        minX,
                        maxY - thickness,
                        minZ + thickness,
                        minX + thickness,
                        maxY,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        maxX - thickness,
                        maxY - thickness,
                        minZ + thickness,
                        maxX,
                        maxY,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        minX,
                        minY + thickness,
                        minZ,
                        minX + thickness,
                        maxY - thickness,
                        minZ + thickness,
                        color);
                this.drawFilledBox(
                        maxX - thickness,
                        minY + thickness,
                        minZ,
                        maxX,
                        maxY - thickness,
                        minZ + thickness,
                        color);
                this.drawFilledBox(
                        minX,
                        minY + thickness,
                        maxZ - thickness,
                        minX + thickness,
                        maxY - thickness,
                        maxZ - thickness,
                        color);
                this.drawFilledBox(
                        maxX - thickness,
                        minY + thickness,
                        maxZ - thickness,
                        maxX,
                        maxY - thickness,
                        maxZ - thickness,
                        color);
            }
        }

        void drawIcosphere(double x, double y, double z, double radius, Color color) {
            VertexConsumer buffer = this.consumers.getBuffer(WorldShapeRenderer.FILLED_BOX_LAYER);
            int argb =
                    color.getAlpha() << 24
                            | color.getRed() << 16
                            | color.getGreen() << 8
                            | color.getBlue();
            Entry entry = this.matrices.peek();
            float fx = (float) x;
            float fy = (float) y;
            float fz = (float) z;
            float scale = (float) radius;

            for (int[] face : WorldShapeRenderer.ICOSPHERE_FACES) {
                float[] a = WorldShapeRenderer.ICOSPHERE_VERTICES[face[0]];
                float[] b = WorldShapeRenderer.ICOSPHERE_VERTICES[face[1]];
                float[] c = WorldShapeRenderer.ICOSPHERE_VERTICES[face[2]];
                buffer.vertex(entry, fx + a[0] * scale, fy + a[1] * scale, fz + a[2] * scale)
                        .color(argb);
                buffer.vertex(entry, fx + b[0] * scale, fy + b[1] * scale, fz + b[2] * scale)
                        .color(argb);
                buffer.vertex(entry, fx + c[0] * scale, fy + c[1] * scale, fz + c[2] * scale)
                        .color(argb);
                buffer.vertex(entry, fx + c[0] * scale, fy + c[1] * scale, fz + c[2] * scale)
                        .color(argb);
            }

            this.hasVertices = true;
        }

        void drawLine(Color color, Vec3d from, Vec3d to, float width) {
            VertexConsumer buffer = this.consumers.getBuffer(WorldShapeRenderer.LINES_LAYER);
            int argb =
                    color.getAlpha() << 24
                            | color.getRed() << 16
                            | color.getGreen() << 8
                            | color.getBlue();
            Entry entry = this.matrices.peek();
            Vector3f normal =
                    new Vector3f(
                                    (float) (to.x - from.x),
                                    (float) (to.y - from.y),
                                    (float) (to.z - from.z))
                            .normalize();
            float lineWidth = Math.max(1.0F, width);
            buffer.vertex(entry, (float) from.x, (float) from.y, (float) from.z)
                    .color(argb)
                    .normal(entry, normal)
                    .lineWidth(lineWidth);
            buffer.vertex(entry, (float) to.x, (float) to.y, (float) to.z)
                    .color(argb)
                    .normal(entry, normal)
                    .lineWidth(lineWidth);
            this.hasVertices = true;
        }

        void flush() {
            if (this.hasVertices) {
                boolean depthTest = GL11.glIsEnabled(2929);
                boolean lineSmooth = GL11.glIsEnabled(2848);
                boolean multisample = GL11.glIsEnabled(32925);
                GL11.glDisable(2929);
                GL11.glEnable(2848);
                GL11.glEnable(32925);
                GL11.glHint(3154, 4354);
                GL11.glDepthMask(false);
                WorldShapeRenderer.drawWithoutFog(this.consumers);
                GL11.glDepthMask(true);
                if (!lineSmooth) {
                    GL11.glDisable(2848);
                }

                if (!multisample) {
                    GL11.glDisable(32925);
                }

                if (depthTest) {
                    GL11.glEnable(2929);
                }

                this.hasVertices = false;
            }
        }
    }
}
