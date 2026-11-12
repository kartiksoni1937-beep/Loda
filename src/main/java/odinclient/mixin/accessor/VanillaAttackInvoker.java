package odinclient.mixin.accessor;

import net.minecraft.client.MinecraftClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface VanillaAttackInvoker {
    @Invoker("doAttack")
    boolean odinclient$doAttack();
}
