package odinclient.mixin.render;

import net.minecraft.client.render.GameRenderer;

import odinclient.module.world.ChunkMarkerRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ChunkMarkerOverlayMixin {
    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void odinclient$stableChunkColors(CallbackInfo ci) {
        ChunkMarkerRenderer.render();
    }
}
