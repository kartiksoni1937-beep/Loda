package odinclient.ui.draw;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.StyleSpriteSource.Font;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class BoldText {
    private static final int MAX_CACHE_SIZE = 1024;
    private static final StyleSpriteSource FONT_SOURCE =
            new Font(Identifier.of("odinclient", "clickgui"));
    private static final Style FONT_STYLE = Style.EMPTY.withFont(FONT_SOURCE);
    private static final Map<String, MutableText> TEXT_CACHE =
            new LinkedHashMap<String, MutableText>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, MutableText> eldest) {
                    return this.size() > MAX_CACHE_SIZE;
                }
            };
    private static final Map<String, Integer> WIDTH_CACHE =
            new LinkedHashMap<String, Integer>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, Integer> eldest) {
                    return this.size() > MAX_CACHE_SIZE;
                }
            };

    private BoldText() {}

    public static MutableText styledText(String text) {
        String content = text == null ? "" : text;
        MutableText cached = TEXT_CACHE.get(content);
        if (cached != null) {
            return cached;
        } else {
            MutableText styled = Text.literal(content).setStyle(FONT_STYLE);
            TEXT_CACHE.put(content, styled);
            return styled;
        }
    }

    public static void draw(
            DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        if (context != null && color >>> 24 >= 2) {
            SdfFontRenderer.drawTextAtTop(
                    context, SdfFontRenderer.FontStyle.BOLD, text, x, y, color);
        }
    }

    public static int width(TextRenderer textRenderer, String text) {
        return Math.round(SdfFontRenderer.width(SdfFontRenderer.FontStyle.BOLD, text));
    }

    public static String truncateToWidth(TextRenderer textRenderer, String text, int maxWidth) {
        String content = text == null ? "" : text;
        if (maxWidth <= 0) {
            return "";
        } else if (width(textRenderer, content) <= maxWidth) {
            return content;
        } else {
            String ellipsis = "...";
            int available = maxWidth - width(textRenderer, ellipsis);
            if (available <= 0) {
                return ellipsis;
            } else {
                int low = 0;
                int high = content.length();

                while (low < high) {
                    int mid = low + high + 1 >>> 1;
                    if (width(textRenderer, content.substring(0, mid)) <= available) {
                        low = mid;
                    } else {
                        high = mid - 1;
                    }
                }

                return content.substring(0, low) + ellipsis;
            }
        }
    }
}
