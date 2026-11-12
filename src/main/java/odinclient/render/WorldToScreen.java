package odinclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public class WorldToScreen {
    public static final Matrix4f projectionMatrix = new Matrix4f();
    public static final Matrix4f viewMatrix = new Matrix4f();
    public static final Matrix4f modelViewMatrix = new Matrix4f();
    static MinecraftClient mc = MinecraftClient.getInstance();

    public static Vec3d worldToScreen(Vec3d pos) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        int height = mc.getWindow().getHeight();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(2978, viewport);
        Vector3f result = new Vector3f();
        double dx = pos.x - camera.getCameraPos().x;
        double dy = pos.y - camera.getCameraPos().y;
        double dz = pos.z - camera.getCameraPos().z;
        Vector4f transformed =
                new Vector4f((float) dx, (float) dy, (float) dz, 1.0F).mul(modelViewMatrix);
        new Matrix4f(projectionMatrix)
                .mul(new Matrix4f(viewMatrix))
                .project(transformed.x(), transformed.y(), transformed.z(), viewport, result);
        return new Vec3d(
                result.x / mc.getWindow().getScaleFactor(),
                (height - result.y) / mc.getWindow().getScaleFactor(),
                result.z);
    }

    public static Pair<Vec3d, Boolean> project(Matrix4f projection, Matrix4f view, Vec3d pos) {
        if (mc.gameRenderer != null && mc.getCameraEntity() != null) {
            WorldToScreen.ScreenPoint point = new WorldToScreen.ScreenPoint();
            return !projectInto(projection, view, pos.x, pos.y, pos.z, point)
                    ? null
                    : new Pair<>(new Vec3d(point.x, point.y, point.z), point.isInFront);
        } else {
            return null;
        }
    }

    public static boolean projectInto(
            Matrix4f modelView,
            Matrix4f projection,
            double x,
            double y,
            double z,
            WorldToScreen.ScreenPoint out) {
        if (mc.gameRenderer != null && mc.getCameraEntity() != null && out != null) {
            Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
            double dx = x - camPos.x;
            double dy = y - camPos.y;
            double dz = z - camPos.z;
            double viewX =
                    modelView.m00() * dx
                            + modelView.m10() * dy
                            + modelView.m20() * dz
                            + modelView.m30();
            double viewY =
                    modelView.m01() * dx
                            + modelView.m11() * dy
                            + modelView.m21() * dz
                            + modelView.m31();
            double viewZ =
                    modelView.m02() * dx
                            + modelView.m12() * dy
                            + modelView.m22() * dz
                            + modelView.m32();
            double viewW =
                    modelView.m03() * dx
                            + modelView.m13() * dy
                            + modelView.m23() * dz
                            + modelView.m33();
            double clipX =
                    projection.m00() * viewX
                            + projection.m10() * viewY
                            + projection.m20() * viewZ
                            + projection.m30() * viewW;
            double clipY =
                    projection.m01() * viewX
                            + projection.m11() * viewY
                            + projection.m21() * viewZ
                            + projection.m31() * viewW;
            double clipZ =
                    projection.m02() * viewX
                            + projection.m12() * viewY
                            + projection.m22() * viewZ
                            + projection.m32() * viewW;
            double clipW =
                    projection.m03() * viewX
                            + projection.m13() * viewY
                            + projection.m23() * viewZ
                            + projection.m33() * viewW;
            boolean inFront = clipW > 0.0;
            double invW = clipW != 0.0 ? 1.0 / clipW : 0.0;
            double ndcY = clipY * invW;
            double ndcZ = clipZ * invW;
            out.set(
                    (clipX * invW * 0.5 + 0.5) * mc.getWindow().getScaledWidth(),
                    (0.5 - ndcY * 0.5) * mc.getWindow().getScaledHeight(),
                    ndcZ,
                    clipW,
                    inFront);
            return true;
        } else {
            return false;
        }
    }

    public static Vector3f projectToVector(Matrix4f projection, Matrix4f view, Vec3d pos) {
        Pair<Vec3d, Boolean> projected = project(projection, view, pos);
        if (projected == null) {
            return null;
        } else {
            Vec3d screenPos = projected.getLeft();
            return new Vector3f((float) screenPos.x, (float) screenPos.y, (float) screenPos.z);
        }
    }

    public static Vector3f toScreenVector(double x, double y, double z) {
        return mc.gameRenderer != null && mc.getCameraEntity() != null
                ? toScreenVector(new Vec3d(x, y, z))
                : null;
    }

    public static Vector3f toScreenVector(Vec3d pos) {
        if (mc.gameRenderer != null && mc.getCameraEntity() != null) {
            Vec3d screenPos = worldToScreen(pos);
            return !(screenPos.z < 0.0) && !(screenPos.z > 1.0)
                    ? new Vector3f((float) screenPos.x, (float) screenPos.y, (float) screenPos.z)
                    : null;
        } else {
            return null;
        }
    }

    public static Vector3f projectClamped(Matrix4f modelView, Matrix4f projection, Vec3d pos) {
        if (mc.gameRenderer != null && mc.getCameraEntity() != null) {
            Vec3d relative = pos.subtract(mc.gameRenderer.getCamera().getCameraPos());
            if (relative.lengthSquared() < 1.0E-4) {
                return new Vector3f(
                        mc.getWindow().getScaledWidth() / 2.0F,
                        mc.getWindow().getScaledHeight() / 2.0F,
                        0.0F);
            } else {
                Vector4f vec =
                        new Vector4f(
                                (float) relative.x, (float) relative.y, (float) relative.z, 1.0F);
                vec.mul(modelView);
                vec.mul(projection);
                boolean behind = vec.w() <= 0.0F;

                float w = Math.abs(vec.w());
                if (w < 0.001F) {
                    w = 0.001F;
                }

                float ndcX = vec.x() / w;
                float ndcY = vec.y() / w;
                float screenWidth = mc.getWindow().getScaledWidth();
                float screenHeight = mc.getWindow().getScaledHeight();
                float centerX = screenWidth / 2.0F;
                float centerY = screenHeight / 2.0F;
                float screenX = (ndcX * 0.5F + 0.5F) * screenWidth;
                float screenY = (0.5F - ndcY * 0.5F) * screenHeight;
                if (!behind
                        && screenX >= 0.0F
                        && screenX <= screenWidth
                        && screenY >= 0.0F
                        && screenY <= screenHeight) {
                    return new Vector3f(screenX, screenY, 0.0F);
                } else {
                    float offsetX = screenX - centerX;
                    float offsetY = screenY - centerY;
                    if (offsetX == 0.0F && offsetY == 0.0F) {
                        offsetY = 1.0F;
                    }

                    float limitX = screenWidth / 2.0F - 10.0F;
                    float limitY = screenHeight / 2.0F - 10.0F;
                    float scaleX = Float.MAX_VALUE;
                    float scaleY = Float.MAX_VALUE;
                    if (offsetX != 0.0F) {
                        scaleX = Math.abs(limitX / offsetX);
                    }

                    if (offsetY != 0.0F) {
                        scaleY = Math.abs(limitY / offsetY);
                    }

                    float scale = Math.min(scaleX, scaleY);
                    return new Vector3f(centerX + offsetX * scale, centerY + offsetY * scale, 0.0F);
                }
            }
        } else {
            return null;
        }
    }

    public static final class ScreenPoint {
        public double x;
        public double y;
        public double z;
        public double w;
        public boolean isInFront;

        public void set(double x, double y, double z, double w, boolean isInFront) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
            this.isInFront = isInFront;
        }
    }
}
