package odinclient.render.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import odinclient.render.projection.WorldToScreen;
import odinclient.ui.draw.SdfShapeRenderer;

import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class TracerLineBatch {
    private static final int MAX_QUEUED = 32768;
    private static final List<TracerLineBatch.QueuedTracer> queue = new ArrayList<>();

    private TracerLineBatch() {}

    public static boolean queueBlockTracer(
            Matrix4f modelView, Matrix4f projection, Vec3d target, int color) {
        if (target != null) {
            queueTracer(target, color, 1.25F);
        }

        return true;
    }

    public static void queueTracer(Vec3d target, int color, float width) {
        if (target != null && queue.size() < 32768) {
            queue.add(new TracerLineBatch.QueuedTracer(target, color, Math.max(0.75F, width)));
        }
    }

    public static void render(DrawContext context) {
        try {
            if (queue.isEmpty() || !WorldToScreen.isMatrixFresh()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null || client.world == null) {
                return;
            }

            int windowWidth = client.getWindow().getWidth();
            int framebufferHeight = client.getWindow().getFramebufferHeight();
            int scaledWidth = client.getWindow().getScaledWidth();
            int scaledHeight = client.getWindow().getScaledHeight();
            if (windowWidth > 0 && framebufferHeight > 0 && scaledWidth > 0 && scaledHeight > 0) {
                float scale = (float) scaledWidth / windowWidth;
                float centerX = scaledWidth * 0.5F;
                float centerY = scaledHeight * 0.5F;
                Vector3d projected = new Vector3d();
                List<SdfShapeRenderer.LineSegment> segments = new ArrayList<>(queue.size());

                for (TracerLineBatch.QueuedTracer tracer : queue) {
                    if (WorldToScreen.projectClamped(tracer.target(), projected)) {
                        segments.add(
                                new SdfShapeRenderer.LineSegment(
                                        centerX,
                                        centerY,
                                        (float) projected.x,
                                        (float) projected.y,
                                        Math.max(0.5F, tracer.width()) * scale,
                                        tracer.color()));
                    }
                }

                SdfShapeRenderer.drawLines(context, segments);
                return;
            }
        } finally {
            clear();
        }
    }

    public static void clear() {
        queue.clear();
    }

    private record QueuedTracer(Vec3d target, int color, float width) {}
}
