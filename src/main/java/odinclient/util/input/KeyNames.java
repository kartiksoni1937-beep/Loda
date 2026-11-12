package odinclient.util.input;

import net.minecraft.client.util.InputUtil.Type;

import java.util.Locale;

public final class KeyNames {
    private KeyNames() {}

    public static String getKeyName(int keyCode) {
        if (keyCode < 0) {
            return "NONE";
        } else if (keyCode >= 1000 && keyCode <= 1007) {
            return "MOUSE " + (keyCode - 999);
        } else {
            try {
                return Type.KEYSYM
                        .createFromCode(keyCode)
                        .getLocalizedText()
                        .getString()
                        .toUpperCase(Locale.ROOT);
            } catch (Throwable e) {
                return "KEY " + keyCode;
            }
        }
    }
}
