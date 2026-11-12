package odinclient.mixin.fake;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import odinclient.module.fake.FakeSpawner;
import odinclient.ui.screen.FakeSpawnerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class FakeSpawnerInteractMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void odinclient$openFakeSpawner(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (FakeSpawner.isEnabled()
                && client.world != null
                && player != null
                && hitResult != null
                && client.currentScreen == null
                && !player.isSneaking()) {
            BlockPos pos = hitResult.getBlockPos();
            if (client.world.getBlockState(pos).isOf(Blocks.YELLOW_STAINED_GLASS)) {
                double dx = player.getX() - (pos.getX() + 0.5);
                double dy = player.getY() - (pos.getY() + 0.5);
                double dz = player.getZ() - (pos.getZ() + 0.5);
                if (dx * dx + dy * dy + dz * dz <= 16.0) {
                    client.setScreen(new FakeSpawnerScreen());
                    cir.setReturnValue(ActionResult.SUCCESS);
                }
            }
        }
    }
}
