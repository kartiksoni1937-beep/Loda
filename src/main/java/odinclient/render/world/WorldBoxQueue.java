package odinclient.render.world;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import odinclient.render.Color;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class WorldBoxQueue {
    private static final double FILL_INSET_MIN = 0.05;
    private static final double FILL_INSET_MAX = 0.95;
    private static final List<WorldBoxQueue.QueuedBox> queuedBoxes = new ArrayList<>();

    private WorldBoxQueue() {}

    public static synchronized void addBlockBox(
            double x, double y, double z, Color fill, Color outline) {
        WorldBoxQueue.QueuedBox box =
                new WorldBoxQueue.QueuedBox(
                        x + 0.05, y + 0.05, z + 0.05, x + 0.95, y + 0.95, z + 0.95, true, fill.r,
                        fill.g, fill.b, fill.a, true, outline.r, outline.g, outline.b, outline.a);
        if (!queuedBoxes.contains(box)) {
            queuedBoxes.add(box);
        }
    }

    public static synchronized void addBox(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            Color color) {
        WorldBoxQueue.QueuedBox box =
                new WorldBoxQueue.QueuedBox(
                        minX, minY, minZ, maxX, maxY, maxZ, false, 0, 0, 0, 0, true, color.r,
                        color.g, color.b, color.a);
        if (!queuedBoxes.contains(box)) {
            queuedBoxes.add(box);
        }
    }

    public static void renderQueued(Camera camera, Matrix4f projection) {
        List<WorldBoxQueue.QueuedBox> boxes;
        synchronized (WorldBoxQueue.class) {
            if (queuedBoxes.isEmpty()) {
                return;
            }

            boxes = new ArrayList<>(queuedBoxes);
            queuedBoxes.clear();
        }

        if (camera != null && projection != null) {
            Matrix4f matrix = new Matrix4f(projection).setTranslation(0.0F, 0.0F, 0.0F);
            MatrixStack matrices = new MatrixStack();
            matrices.multiplyPositionMatrix(matrix);
            WorldShapeRenderer.ShapeBatch batch = WorldShapeRenderer.createBatch(matrices);
            Vec3d cameraPos = camera.getCameraPos();

            for (WorldBoxQueue.QueuedBox box : boxes) {
                double minX = box.minX - cameraPos.x;
                double minY = box.minY - cameraPos.y;
                double minZ = box.minZ - cameraPos.z;
                double maxX = box.maxX - cameraPos.x;
                double maxY = box.maxY - cameraPos.y;
                double maxZ = box.maxZ - cameraPos.z;
                if (box.renderFill) {
                    batch.drawFilledBox(
                            minX,
                            minY,
                            minZ,
                            maxX,
                            maxY,
                            maxZ,
                            new java.awt.Color(box.fillR, box.fillG, box.fillB, box.fillA));
                }

                if (box.renderOutline) {
                    batch.drawBoxOutline(
                            minX,
                            minY,
                            minZ,
                            maxX,
                            maxY,
                            maxZ,
                            new java.awt.Color(
                                    box.outlineR, box.outlineG, box.outlineB, box.outlineA));
                }
            }

            batch.finish();
        }
    }

    private record QueuedBox(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            boolean renderFill,
            int fillR,
            int fillG,
            int fillB,
            int fillA,
            boolean renderOutline,
            int outlineR,
            int outlineG,
            int outlineB,
            int outlineA) {}
}
