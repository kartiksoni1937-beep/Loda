package odinclient.render.projection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

public final class WorldToScreen {
    private static final Matrix4f viewProjection = new Matrix4f();
    private static final Vector4f scratchVector = new Vector4f();
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static long lastUpdateNanos;

    private WorldToScreen() {}

    public static void updateMatrices(Camera camera, Matrix4f modelView, Matrix4f projection) {
        if (camera != null && modelView != null && projection != null) {
            Vec3d cameraPos = camera.getCameraPos();
            cameraX = cameraPos.x;
            cameraY = cameraPos.y;
            cameraZ = cameraPos.z;
            viewProjection.set(projection).mul(modelView);
            lastUpdateNanos = System.nanoTime();
        } else {
            lastUpdateNanos = 0L;
        }
    }

    public static boolean isMatrixFresh() {
        return lastUpdateNanos != 0L && System.nanoTime() - lastUpdateNanos < 1000000000L;
    }

    public static boolean project(Vector3d point) {
        if (isMatrixFresh() && point != null) {
            scratchVector.set(
                    (float) (point.x - cameraX),
                    (float) (point.y - cameraY),
                    (float) (point.z - cameraZ),
                    1.0F);
            viewProjection.transform(scratchVector);
            if (Float.isFinite(scratchVector.w) && !(scratchVector.w <= 1.0E-4F)) {
                float invW = 1.0F / scratchVector.w;
                float ndcX = scratchVector.x * invW;
                float ndcY = scratchVector.y * invW;
                if (Float.isFinite(ndcX) && Float.isFinite(ndcY)) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    int width = client.getWindow().getScaledWidth();
                    int height = client.getWindow().getScaledHeight();
                    double screenX = (ndcX * 0.5 + 0.5) * width;
                    double screenY = (0.5 - ndcY * 0.5) * height;
                    point.set(screenX, screenY, scratchVector.w);
                    return screenX >= -30.0
                            && screenX <= width + 30.0
                            && screenY >= -30.0
                            && screenY <= height + 30.0;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean projectClamped(Vec3d worldPos, Vector3d out) {
        if (isMatrixFresh() && worldPos != null && out != null) {
            scratchVector.set(
                    (float) (worldPos.x - cameraX),
                    (float) (worldPos.y - cameraY),
                    (float) (worldPos.z - cameraZ),
                    1.0F);
            viewProjection.transform(scratchVector);
            if (Float.isFinite(scratchVector.x)
                    && Float.isFinite(scratchVector.y)
                    && Float.isFinite(scratchVector.w)) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getWindow() != null) {
                    int width = client.getWindow().getScaledWidth();
                    int height = client.getWindow().getScaledHeight();
                    if (width > 0 && height > 0) {
                        double w = Math.max(Math.abs((double) scratchVector.w), 1.0E-4);
                        double x = scratchVector.x / w * width * 0.5;
                        double y = -scratchVector.y / w * height * 0.5;
                        double maxX = Math.max(1.0, width * 0.5 - 10.0);
                        double maxY = Math.max(1.0, height * 0.5 - 10.0);
                        if (scratchVector.w <= 0.0F && Math.hypot(x, y) < 1.0E-4) {
                            y = -maxY;
                        }

                        double scale =
                                Math.min(
                                        1.0,
                                        Math.min(
                                                maxX / Math.max(Math.abs(x), 1.0E-4),
                                                maxY / Math.max(Math.abs(y), 1.0E-4)));
                        out.set(width * 0.5 + x * scale, height * 0.5 + y * scale, scratchVector.w);
                        return Double.isFinite(out.x) && Double.isFinite(out.y);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
