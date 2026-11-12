package odinclient.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import odinclient.event.BlockUpdateEvent;
import odinclient.module.ModuleContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class BlockUpdateEventMixin {
    @Unique private BlockState odinclient$oldState;

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void odinclient$beforeBlockUpdate(
            BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        this.odinclient$oldState = ((ClientWorld) (Object) this).getBlockState(pos);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void odinclient$afterBlockUpdate(
            BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        ModuleContext.EVENT_BUS.post(
                new BlockUpdateEvent(pos.toImmutable(), this.odinclient$oldState, state));
        this.odinclient$oldState = null;
    }
}
