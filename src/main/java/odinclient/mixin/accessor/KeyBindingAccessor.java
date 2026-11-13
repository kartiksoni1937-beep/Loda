package odinclient.mixin.accessor;

import net.minecraft.client.option.KeyBinding;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the vanilla press counter so a simulated press is visible to {@code wasPressed()}. */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor("timesPressed")
    int odinclient$getTimesPressed();

    @Accessor("timesPressed")
    void odinclient$setTimesPressed(int timesPressed);
}
