package odinclient.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;

@Environment(EnvType.CLIENT)
public final class KeyBindingUtil {
    private KeyBindingUtil() {}

    public static boolean isPressed(KeyBinding keyBinding) {
        return keyBinding != null && keyBinding.isPressed();
    }
}
