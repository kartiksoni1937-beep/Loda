package odinclient.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.render.world.GizmoRenderer;

@Environment(EnvType.CLIENT)
public final class WorldRenderEvent {
    public final GizmoRenderer renderer;

    public WorldRenderEvent(GizmoRenderer renderer) {
        this.renderer = renderer;
    }
}
