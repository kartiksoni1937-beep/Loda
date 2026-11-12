package odinclient.mixin.fake;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.WorldEventHandler;

import odinclient.module.fake.FakeSpawner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldEventHandler.class)
public abstract class FakeSpawnerBreakMixin {
    @ModifyArg(
            method = "processWorldEvent",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/block/Block;getStateFromRawId(I)Lnet/minecraft/block/BlockState;",
                            ordinal = 0),
            index = 0)
    private int odinclient$fakeSpawnerBreakParticles(int rawId) {
        return FakeSpawner.isEnabled()
                        && Block.getStateFromRawId(rawId).isOf(Blocks.YELLOW_STAINED_GLASS)
                ? Block.getRawIdFromState(Blocks.SPAWNER.getDefaultState())
                : rawId;
    }
}
