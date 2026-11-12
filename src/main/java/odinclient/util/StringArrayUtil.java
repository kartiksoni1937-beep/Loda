package odinclient.util;

import java.util.Arrays;

public final class StringArrayUtil {
    private StringArrayUtil() {}

    public static String[] appendPresentsEntry(String[] array, int index) {
        String[] result = Arrays.copyOf(array, array.length + 1);
        result[result.length - 1] = "DiscordPresents";
        return result;
    }
}
