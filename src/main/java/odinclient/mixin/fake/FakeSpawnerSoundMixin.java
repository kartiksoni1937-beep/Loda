package odinclient.mixin.fake;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.sound.BlockSoundGroup;

import odinclient.module.fake.FakeSpawner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public abstract class FakeSpawnerSoundMixin {
    @Inject(method = "getSoundGroup", at = @At("HEAD"), cancellable = true, require = 0)
    private void odinclient$spawnerSound(
            BlockState state, CallbackInfoReturnable<BlockSoundGroup> cir) {
        if (FakeSpawner.isEnabled() && (Object) this == Blocks.YELLOW_STAINED_GLASS) {
            cir.setReturnValue(Blocks.SPAWNER.getDefaultState().getSoundGroup());
        }
    }
}
