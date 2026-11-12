package odinclient.render.projection;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import odinclient.mixin.accessor.GameRendererInvoker;
import odinclient.ui.draw.DrawContextHolder;
import odinclient.ui.scale.ScreenScaleCompat;

import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class ScreenProjection {
    private static final Vector3f scratchPos = new Vector3f();
    private static final Quaternionf scratchRotation = new Quaternionf();
    private static double scale = 1.0;

    private ScreenProjection() {}

    public static boolean project(Vector3d pos, double distance) {
        scale = Math.max(0.08, distance * 0.4);
        if (WorldToScreen.isMatrixFresh()) {
            return WorldToScreen.project(pos);
        } else {
            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = client.gameRenderer.getCamera();
            Vec3d cameraPos = camera.getCameraPos();
            scratchPos.set(
                    (float) (pos.x - cameraPos.x),
                    (float) (pos.y - cameraPos.y),
                    (float) (pos.z - cameraPos.z));
            scratchRotation.set(camera.getRotation()).conjugate();
            scratchPos.rotate(scratchRotation);
            double depth = -scratchPos.z;
            if (depth <= 0.05) {
                return false;
            } else {
                int screenWidth = client.getWindow().getScaledWidth();
                int screenHeight = client.getWindow().getScaledHeight();
                double tanHalfFov =
                        Math.tan(
                                Math.toRadians(
                                                ((GameRendererInvoker) client.gameRenderer)
                                                        .odinclient$invokeGetFov(
                                                                camera,
                                                                camera.getLastTickProgress(),
                                                                false))
                                        * 0.5);
                double aspect = (double) screenWidth / Math.max(1, screenHeight);
                double screenX =
                        (scratchPos.x / (depth * tanHalfFov * aspect) + 1.0) * screenWidth * 0.5;
                double screenY = (1.0 - scratchPos.y / (depth * tanHalfFov)) * screenHeight * 0.5;
                pos.set(screenX, screenY, depth);
                return screenX >= -30.0
                        && screenX <= screenWidth + 30.0
                        && screenY >= -30.0
                        && screenY <= screenHeight + 30.0;
            }
        }
    }

    public static void pushTransform(Vector3d pos) {
        pushTransformTo(pos, DrawContextHolder.get());
    }

    public static void pushTransformTo(Vector3d pos, DrawContext context) {
        if (context != null) {
            DrawContextHolder.set(context);
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            float compensation = ScreenScaleCompat.getCompensation();
            matrices.translate((float) pos.x / compensation, (float) pos.y / compensation);
            matrices.scale((float) scale, (float) scale);
        }
    }

    public static void popTransform() {
        popTransformFrom(DrawContextHolder.get());
    }

    public static void popTransformFrom(DrawContext context) {
        if (context != null) {
            context.getMatrices().popMatrix();
        }
    }
}
