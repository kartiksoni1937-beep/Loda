package odinclient.mixin.gamble;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import odinclient.module.gamble.GambleRigger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class GambleSpawnMixin {
    @Inject(method = "addEntity", at = @At("TAIL"))
    private void odinclient$dropperItem(Entity entity, CallbackInfo ci) {
        GambleRigger.onItemSpawn(this, entity);
    }
}
