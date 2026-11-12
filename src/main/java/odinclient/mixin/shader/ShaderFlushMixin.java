package odinclient.mixin.shader;

import net.minecraft.client.render.WorldRenderer;

import odinclient.render.world.TracerBatchRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class ShaderFlushMixin {
    @Inject(
            method = "method_62214",
            at =
                    @At(
                            value = "INVOKE:LAST",
                            target =
                                    "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V"))
    private void odinclient$flushShaderSafeTracers(CallbackInfo ci) {
        TracerBatchRenderer.flush();
    }
}
