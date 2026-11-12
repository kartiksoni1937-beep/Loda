package odinclient.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.util.math.Vec3d;

import odinclient.module.fake.FakeRoles;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(
            method =
                    "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void odinclient$replaceLocalNametag(
            PlayerLikeEntity entity,
            PlayerEntityRenderState state,
            float tickDelta,
            CallbackInfo ci) {
        FakeRoles roles = FakeRoles.getInstance();
        if (roles != null && roles.shouldOverrideName(entity)) {
            state.playerName = null;
            state.displayName = roles.getFakeName(entity);
            if (state.nameLabelPos == null) {
                state.nameLabelPos =
                        entity.getAttachments()
                                .getPointNullable(
                                        EntityAttachmentType.NAME_TAG,
                                        0,
                                        entity.getLerpedYaw(tickDelta));
                if (state.nameLabelPos == null) {
                    state.nameLabelPos = new Vec3d(0.0, entity.getHeight() + 0.5, 0.0);
                }
            }
        }
    }
}
