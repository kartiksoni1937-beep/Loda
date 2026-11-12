package odinclient.mixin.fake;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.util.math.Vec3d;

import odinclient.module.fake.FakeElytra;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class FakeElytraPlayerRenderMixin {
    @Inject(
            method =
                    "getPositionOffset(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"),
            cancellable = true)
    private void odinclient$fakeGlideLift(
            PlayerEntityRenderState state, CallbackInfoReturnable<Vec3d> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && state.id == client.player.getId() && FakeElytra.isGliding()) {
            cir.setReturnValue(cir.getReturnValue().add(0.0, FakeElytra.getGlideYOffset(), 0.0));
        }
    }

    @Inject(
            method =
                    "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void odinclient$fakeGlidePose(
            PlayerLikeEntity entity,
            PlayerEntityRenderState state,
            float tickDelta,
            CallbackInfo ci) {
        if (entity == MinecraftClient.getInstance().player && FakeElytra.isGliding()) {
            state.glidingTicks = FakeElytra.getGlideTicks() + tickDelta;
        }
    }
}
