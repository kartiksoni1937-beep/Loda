package odinclient.mixin.accessor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.fog.FogRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Accessor("fogRenderer")
    FogRenderer odinclient$getFogRenderer();

    @Invoker("getFov")
    float odinclient$invokeGetFov(Camera camera, float tickProgress, boolean changingFov);
}
