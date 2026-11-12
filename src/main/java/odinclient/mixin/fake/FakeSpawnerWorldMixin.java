package odinclient.mixin.fake;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;

import odinclient.module.fake.FakeSpawnerRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class FakeSpawnerWorldMixin {
    @Inject(method = "pushEntityRenders", at = @At("HEAD"))
    private void odinclient$renderFakeSpawner(
            MatrixStack matrices,
            WorldRenderState state,
            OrderedRenderCommandQueue queue,
            CallbackInfo ci) {
        FakeSpawnerRenderer.render(matrices, state, queue);
    }
}
