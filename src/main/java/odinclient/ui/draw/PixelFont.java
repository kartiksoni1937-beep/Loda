package odinclient.ui.draw;

import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

public final class PixelFont {
    private PixelFont() {}

    public static int width(String text) {
        int total = 0;

        for (int i = 0; i < text.length(); i++) {
            total += text.charAt(i) == ' ' ? 4 : 6;
        }

        return Math.max(0, total - 1);
    }

    public static void drawString(DrawContext context, String text, int x, int y, int color) {
        byte offset = 0;
        String upper = text.toUpperCase(Locale.ROOT);

        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == ' ') {
                offset += 4;
            } else {
                String[] glyph = getGlyph(c);
                if (glyph != null) {
                    int yOffset = c == '1' ? 0 : 2;

                    for (int row = 0; row < glyph.length; row++) {
                        for (int col = 0; col < 5; col++) {
                            if (glyph[row].charAt(col) == '1') {
                                context.fill(
                                        x + offset + col,
                                        y + yOffset + row,
                                        x + offset + col + 1,
                                        y + yOffset + row + 1,
                                        color);
                            }
                        }
                    }
                }

                offset += 6;
            }
        }
    }

    private static String[] getGlyph(char c) {
        return switch (c) {
            case '1' ->
                    new String[] {"00100", "01100", "00100", "00100", "00100", "00100", "11111"};
            default -> null;
            case 'A' -> new String[] {"01110", "10001", "11111", "10001", "10001"};
            case 'B' -> new String[] {"11110", "10001", "11110", "10001", "11110"};
            case 'C' -> new String[] {"01111", "10000", "10000", "10000", "01111"};
            case 'D' -> new String[] {"11110", "10001", "10001", "10001", "11110"};
            case 'E' -> new String[] {"11111", "10000", "11100", "10000", "11111"};
            case 'G' -> new String[] {"01111", "10000", "10111", "10001", "01111"};
            case 'I' -> new String[] {"11111", "00100", "00100", "00100", "11111"};
            case 'K' -> new String[] {"10001", "10010", "11100", "10010", "10001"};
            case 'L' -> new String[] {"10000", "10000", "10000", "10000", "11111"};
            case 'M' -> new String[] {"10001", "11011", "10101", "10001", "10001"};
            case 'N' -> new String[] {"10001", "11001", "10101", "10011", "10001"};
            case 'O' -> new String[] {"01110", "10001", "10001", "10001", "01110"};
            case 'P' -> new String[] {"11110", "10001", "11110", "10000", "10000"};
            case 'R' -> new String[] {"11110", "10001", "11110", "10001", "10001"};
            case 'S' -> new String[] {"01111", "10000", "01110", "00001", "11110"};
            case 'T' -> new String[] {"11111", "00100", "00100", "00100", "00100"};
            case 'W' -> new String[] {"10001", "10001", "10101", "11011", "10001"};
            case 'Z' -> new String[] {"11111", "00010", "00100", "01000", "11111"};
        };
    }
}
