package odinclient.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

@Environment(EnvType.CLIENT)
public final class CameraDistance {
    private CameraDistance() {}

    public static double distanceToCamera(Entity entity) {
        return entity == null
                ? Double.POSITIVE_INFINITY
                : Math.sqrt(
                        entity.squaredDistanceTo(
                                MinecraftClient.getInstance()
                                        .gameRenderer
                                        .getCamera()
                                        .getCameraPos()));
    }

    public static boolean isWithinDistance(Entity entity, double distance) {
        if (entity == null) {
            return false;
        } else {
            return entity.squaredDistanceTo(
                            MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos())
                    <= distance * distance;
        }
    }

    public static boolean isPosWithinDistance(double x, double y, double z, double distance) {
        double maxDistanceSq = distance * distance;
        return MinecraftClient.getInstance()
                        .gameRenderer
                        .getCamera()
                        .getCameraPos()
                        .squaredDistanceTo(x, y, z)
                <= maxDistanceSq;
    }
}
