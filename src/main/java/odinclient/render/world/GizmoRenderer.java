package odinclient.render.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import odinclient.render.BoxRenderMode;
import odinclient.render.Color;

import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class GizmoRenderer {
    private final Camera camera;
    private final Matrix4f projectionMatrix;
    private final Matrix4f modelViewMatrix;

    public GizmoRenderer() {
        this(null, null, null);
    }

    public GizmoRenderer(Camera camera, Matrix4f projectionMatrix, Matrix4f modelViewMatrix) {
        this.camera = camera;
        this.projectionMatrix = projectionMatrix == null ? null : new Matrix4f(projectionMatrix);
        this.modelViewMatrix = modelViewMatrix == null ? null : new Matrix4f(modelViewMatrix);
    }

    public void box(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            Color fillColor,
            Color outlineColor,
            BoxRenderMode mode,
            int duration) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            int fillArgb = fillColor == null ? 0 : fillColor.toArgb();
            int outlineArgb = outlineColor == null ? 0 : outlineColor.toArgb();
            if (maxY - minY > 4.0
                    && Math.abs(maxX - minX) <= 1.01
                    && Math.abs(maxZ - minZ) <= 1.01) {
                int argb = 0xFF000000 | (outlineArgb != 0 ? outlineArgb : fillArgb) & 16777215;
                this.drawBox(
                        new Box(minX, minY, minZ, maxX, maxY, maxZ),
                        argb,
                        argb,
                        mode,
                        this.getCameraPos(client),
                        false);
            } else {
                DrawStyle style =
                        switch (mode) {
                            case Sides -> DrawStyle.filled(fillArgb);
                            case Both -> DrawStyle.filledAndStroked(outlineArgb, 1.0F, fillArgb);
                            default -> DrawStyle.stroked(outlineArgb, 1.0F);
                        };
                GizmoDrawing.box(new Box(minX, minY, minZ, maxX, maxY, maxZ), style)
                        .ignoreOcclusion();
            }
        }
    }

    public void stableBox(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            Color fillColor,
            Color outlineColor,
            BoxRenderMode mode) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            int fillArgb = fillColor == null ? 0 : fillColor.toArgb();
            int outlineArgb = outlineColor == null ? 0 : outlineColor.toArgb();
            Vec3d cameraPos = this.getCameraPos(client);
            this.drawBox(
                    new Box(minX, minY, minZ, maxX, maxY, maxZ),
                    fillArgb,
                    outlineArgb,
                    mode,
                    cameraPos,
                    true);
        }
    }

    private void drawBox(
            Box box,
            int fillColor,
            int lineColor,
            BoxRenderMode mode,
            Vec3d cameraPos,
            boolean cullFaces) {
        Vec3d minMinMin = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d minMinMax = new Vec3d(box.minX, box.minY, box.maxZ);
        Vec3d minMaxMin = new Vec3d(box.minX, box.maxY, box.minZ);
        Vec3d minMaxMax = new Vec3d(box.minX, box.maxY, box.maxZ);
        Vec3d maxMinMin = new Vec3d(box.maxX, box.minY, box.minZ);
        Vec3d maxMinMax = new Vec3d(box.maxX, box.minY, box.maxZ);
        Vec3d maxMaxMin = new Vec3d(box.maxX, box.maxY, box.minZ);
        Vec3d maxMaxMax = new Vec3d(box.maxX, box.maxY, box.maxZ);
        boolean showAll =
                !cullFaces
                        || cameraPos == null
                        || box.contains(cameraPos.x, cameraPos.y, cameraPos.z);
        boolean showEast = showAll || cameraPos.x >= box.maxX;
        boolean showWest = showAll || cameraPos.x <= box.minX;
        boolean showNorth = showAll || cameraPos.z <= box.minZ;
        boolean showSouth = showAll || cameraPos.z >= box.maxZ;
        boolean showTop = showAll || cameraPos.y >= box.maxY;
        boolean showBottom = showAll || cameraPos.y <= box.minY;
        GizmoDrawing.collect(
                        (drawer, context) -> {
                            if (mode == BoxRenderMode.Sides || mode == BoxRenderMode.Both) {
                                if (showEast) {
                                    drawer.addQuad(
                                            maxMinMin, maxMaxMin, maxMaxMax, maxMinMax, fillColor);
                                }

                                if (showWest) {
                                    drawer.addQuad(
                                            minMinMin, minMinMax, minMaxMax, minMaxMin, fillColor);
                                }

                                if (showNorth) {
                                    drawer.addQuad(
                                            minMinMin, minMaxMin, maxMaxMin, maxMinMin, fillColor);
                                }

                                if (showSouth) {
                                    drawer.addQuad(
                                            minMinMax, maxMinMax, maxMaxMax, minMaxMax, fillColor);
                                }

                                if (showTop) {
                                    drawer.addQuad(
                                            minMaxMin, minMaxMax, maxMaxMax, maxMaxMin, fillColor);
                                }

                                if (showBottom) {
                                    drawer.addQuad(
                                            minMinMin, maxMinMin, maxMinMax, minMinMax, fillColor);
                                }
                            }

                            if (mode == BoxRenderMode.Lines || mode == BoxRenderMode.Both) {
                                this.drawEdge(
                                        drawer, minMinMin, minMinMax, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, minMinMin, minMaxMin, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, minMinMin, maxMinMin, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, minMinMax, minMaxMax, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, minMinMax, maxMinMax, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, minMaxMin, minMaxMax, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, minMaxMin, maxMaxMin, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, minMaxMax, maxMaxMax, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, maxMinMin, maxMinMax, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, maxMinMin, maxMaxMin, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, maxMinMax, maxMaxMax, lineColor, 0.85F, cameraPos);
                                this.drawEdge(
                                        drawer, maxMaxMin, maxMaxMax, lineColor, 0.85F, cameraPos);
                            }
                        })
                .ignoreOcclusion();
    }

    public void line(Vec3d from, Vec3d to, Color color, float width) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && from != null && to != null && color != null) {
            GizmoDrawing.line(from, to, color.toArgb(), Math.max(0.5F, width)).ignoreOcclusion();
        }
    }

    private void drawScreenLine(Vec3d from, Vec3d to, Color color, float width) {
        StableLineRenderer.drawLine(
                this.projectionMatrix,
                this.getCameraPos(MinecraftClient.getInstance()),
                from,
                to,
                color.toArgb());
    }

    private void drawEdge(
            GizmoDrawer drawer, Vec3d from, Vec3d to, int color, float width, Vec3d cameraPos) {
        if (cameraPos == null) {
            drawer.addLine(from, to, color, width);
        } else {
            Vec3d direction = to.subtract(from);
            if (!(direction.lengthSquared() < 1.0E-10)) {
                Vec3d center = from.add(to).multiply(0.5);
                Vec3d side = direction.crossProduct(cameraPos.subtract(center));
                if (side.lengthSquared() < 1.0E-10) {
                    Vec3d toCenter = center.subtract(cameraPos);
                    side = toCenter.crossProduct(new Vec3d(0.0, 1.0, 0.0));
                    if (side.lengthSquared() < 1.0E-10) {
                        side = new Vec3d(1.0, 0.0, 0.0);
                    }
                }

                side = side.normalize();
                double fromHalfWidth = this.getLineHalfWidth(from, cameraPos, width);
                double toHalfWidth = this.getLineHalfWidth(to, cameraPos, width);
                Vec3d fromOffset = side.multiply(fromHalfWidth);
                Vec3d toOffset = side.multiply(toHalfWidth);
                drawer.addQuad(
                        from.subtract(fromOffset),
                        from.add(fromOffset),
                        to.add(toOffset),
                        to.subtract(toOffset),
                        color);
            }
        }
    }

    private double getLineHalfWidth(Vec3d point, Vec3d cameraPos, float width) {
        MinecraftClient client = MinecraftClient.getInstance();
        double distance = Math.max(0.05, point.distanceTo(cameraPos));
        double scale =
                this.modelViewMatrix == null
                        ? 1.428
                        : Math.max(0.25, Math.abs(this.modelViewMatrix.m11()));
        int screenHeight =
                client.getWindow() == null
                        ? 1080
                        : Math.max(1, client.getWindow().getFramebufferHeight());
        return Math.max(2.0E-4, width * distance / (scale * screenHeight));
    }

    private Vec3d getCameraPos(MinecraftClient client) {
        Camera activeCamera = this.camera;
        if (activeCamera == null && client.gameRenderer != null) {
            activeCamera = client.gameRenderer.getCamera();
        }

        return activeCamera == null ? null : activeCamera.getCameraPos();
    }

    public void tracer(Vec3d target, Color color, float width) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null
                && client.gameRenderer != null
                && target != null
                && color != null) {
            Camera activeCamera =
                    this.camera == null ? client.gameRenderer.getCamera() : this.camera;
            this.drawScreenLine(this.getTracerOrigin(activeCamera), target, color, width);
        }
    }

    public void stableTracer(Vec3d target, Color color, float width) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null
                && client.gameRenderer != null
                && target != null
                && color != null) {
            Camera activeCamera =
                    this.camera == null ? client.gameRenderer.getCamera() : this.camera;
            this.drawScreenLine(this.getTracerOrigin(activeCamera), target, color, width);
        }
    }

    private Vec3d getTracerOrigin(Camera camera) {
        return camera.getCameraPos().add(WorldShapeRenderer.getCameraForward(camera).multiply(0.1));
    }

    public void stableLine(Vec3d from, Vec3d to, Color color, float width) {
        this.drawScreenLine(from, to, color, width);
    }

    public void stableBoxCompat(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            Color fillColor,
            Color outlineColor,
            BoxRenderMode mode,
            int duration) {
        this.stableBox(minX, minY, minZ, maxX, maxY, maxZ, fillColor, outlineColor, mode);
    }

    public void blockEspHybridTracer(Vec3d target, Color color, float width) {
        if (!TracerLineBatch.queueBlockTracer(
                this.projectionMatrix, this.modelViewMatrix, target, color.toArgb())) {
            this.stableTracer(target, color, width);
        }
    }
}
