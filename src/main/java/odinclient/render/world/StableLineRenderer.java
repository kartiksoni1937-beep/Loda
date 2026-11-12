package odinclient.render.world;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import java.awt.Color;

public final class StableLineRenderer {
    private static WorldShapeRenderer.ShapeBatch renderer;
    private static final ThreadLocal<Float> pendingLineWidth = new ThreadLocal<>();

    public static void setNextLineWidth(float width) {
        pendingLineWidth.set(Math.max(0.5F, width));
    }

    public static void drawLine(Matrix4f matrix, Vec3d origin, Vec3d from, Vec3d to, int color) {
        if (renderer == null) {
            Matrix4f translated = new Matrix4f(matrix).setTranslation(0.0F, 0.0F, 0.0F);
            MatrixStack stack = new MatrixStack();
            stack.multiplyPositionMatrix(translated);
            renderer = WorldShapeRenderer.createBatch(stack);
        }

        Float width = pendingLineWidth.get();
        pendingLineWidth.remove();
        renderer.drawLine(
                new Color(color, true),
                from.subtract(origin),
                to.subtract(origin),
                width == null ? 1.5F : width);
    }

    public static void flush() {
        pendingLineWidth.remove();
        WorldShapeRenderer.ShapeBatch batch = renderer;
        renderer = null;
        if (batch != null) {
            batch.finish();
        }
    }
}
