package odinclient.mixin.render;

import net.minecraft.entity.Entity;

import odinclient.module.render.esp.PlayerEsp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
    private void odinclient$playerShaderColor(CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity) (Object) this;
        PlayerEsp playerEsp = PlayerEsp.getInstance();
        if (playerEsp != null && playerEsp.shouldApplyShader(entity)) {
            cir.setReturnValue(playerEsp.getColorRgb());
        }
    }
}
