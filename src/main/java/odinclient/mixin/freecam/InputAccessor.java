package odinclient.mixin.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(Input.class)
public interface InputAccessor {
    @Accessor("playerInput")
    PlayerInput odinclient$getPlayerInput();

    @Accessor("playerInput")
    void odinclient$setPlayerInput(PlayerInput playerInput);

    @Accessor("movementVector")
    Vec2f odinclient$getMovementVector();

    @Accessor("movementVector")
    void odinclient$setMovementVector(Vec2f playerInput);
}
