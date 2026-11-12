package odinclient.mixin.fake;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ElytraFlightController;

import odinclient.module.fake.FakeElytra;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ElytraFlightController.class)
public abstract class FakeElytraFlightMixin {
    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isGliding()Z"))
    private boolean odinclient$fakeGlideAnimation(LivingEntity entity) {
        return entity.isGliding() || FakeElytra.isFakeGliding(entity);
    }
}
