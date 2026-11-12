package odinclient.module.playerchunk;

public final class LightNibbleUtil {
    public static final int NIBBLE_ARRAY_SIZE = 2048;

    private LightNibbleUtil() {}

    public static boolean isAllZero(byte[] data) {
        if (data != null && data.length == 2048) {
            for (byte b : data) {
                if (b != 0) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }
}
