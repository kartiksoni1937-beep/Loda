package odinclient.module.fake;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FakePayTextRewriter {
    private static final Pattern DOLLAR_PATTERN =
            Pattern.compile("(?i)\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?[kmbtq]?)");
    private static final Pattern MONEY_LABEL_PATTERN =
            Pattern.compile("(?i)\\bMoney\\s*:\\s*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?[kmbtq]?)");
    private static final Pattern YOU_HAVE_PATTERN =
            Pattern.compile("(?i)\\bYou\\s+have\\b[^\\d]{0,12}(\\d[\\d,.]*)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?i)\\d[\\d.,]*(?:[kmbtq])?");
    private static int nameCallCount;
    private static int scoreCallCount;
    private static boolean nameRewritten;

    private FakePayTextRewriter() {}

    public static void resetCounters() {
        nameCallCount = 0;
        scoreCallCount = 0;
        nameRewritten = false;
    }

    public static Text rewriteSidebarName(Text text) {
        int callIndex = nameCallCount++;
        Text result = rewriteMoney(text);
        if (callIndex == 0) {
            if (result == text) {
                result = rewriteLastNumber(text);
            }

            nameRewritten = result != text;
        }

        return result;
    }

    public static Text rewriteSidebarScore(Text text) {
        int callIndex = scoreCallCount++;
        Text result = rewriteMoney(text);
        if (callIndex == 0 && result == text && !nameRewritten) {
            return rewriteLastNumber(text);
        }

        return result;
    }

    private static Text rewriteLastNumber(Text text) {
        if (!FakePay.isFakeScoreboardActive() || text == null) {
            return text;
        }

        ServerInfo serverInfo = MinecraftClient.getInstance().getCurrentServerEntry();
        if (serverInfo == null
                || serverInfo.address == null
                || !serverInfo.address.toLowerCase(Locale.ROOT).contains("donutsmp")) {
            return text;
        }

        List<TextSegment> segments = splitSegments(text);
        Matcher matcher = NUMBER_PATTERN.matcher(joinSegments(segments));
        int start = -1;
        int end = -1;

        while (matcher.find()) {
            start = matcher.start();
            end = matcher.end();
        }

        return start < 0 ? text : replaceRange(segments, start, end, FakePay.getFakeBalanceShort());
    }

    public static Text rewriteMoney(Text text) {
        if (!FakePay.isFakeScoreboardActive() || text == null) {
            return text;
        }

        List<TextSegment> segments = splitSegments(text);
        String joined = joinSegments(segments);
        Matcher matcher = DOLLAR_PATTERN.matcher(joined);
        if (!matcher.find()) {
            matcher = MONEY_LABEL_PATTERN.matcher(joined);
            if (!matcher.find()) {
                return text;
            }
        }

        return replaceRange(
                segments, matcher.start(1), matcher.end(1), FakePay.getFakeBalanceShort());
    }

    public static Text rewriteBalanceMessage(Text text) {
        if (!FakePay.isFakeScoreboardActive() || text == null) {
            return text;
        }

        List<TextSegment> segments = splitSegments(text);
        Matcher matcher = YOU_HAVE_PATTERN.matcher(joinSegments(segments));
        return !matcher.find()
                ? text
                : replaceRange(
                        segments,
                        matcher.start(1),
                        matcher.end(1),
                        FakePay.getFakeBalanceFormatted());
    }

    private static String joinSegments(List<TextSegment> segments) {
        StringBuilder builder = new StringBuilder();

        for (TextSegment segment : segments) {
            builder.append(segment.text());
        }

        return builder.toString();
    }

    private static List<TextSegment> splitSegments(Text text) {
        ArrayList<TextSegment> segments = new ArrayList<>();
        text.visit(
                (style, content) -> {
                    segments.add(new TextSegment(style, content));
                    return Optional.empty();
                },
                Style.EMPTY);
        return segments;
    }

    private static Text replaceRange(
            List<TextSegment> segments, int start, int end, String replacement) {
        MutableText result = Text.empty();
        int offset = 0;
        boolean replaced = false;

        for (TextSegment segment : segments) {
            int before = Math.max(0, start - offset);
            int relativeEnd = end - offset;
            int after = Math.min(segment.text().length(), relativeEnd);
            if (offset + segment.text().length() > start && offset < end) {
                if (before > 0) {
                    result.append(
                            Text.literal(segment.text().substring(0, before))
                                    .fillStyle(segment.style()));
                }

                if (!replaced) {
                    result.append(Text.literal(replacement).fillStyle(segment.style()));
                    replaced = true;
                }

                if (after < segment.text().length()) {
                    result.append(
                            Text.literal(segment.text().substring(after))
                                    .fillStyle(segment.style()));
                }
            } else {
                result.append(Text.literal(segment.text()).fillStyle(segment.style()));
            }

            offset += segment.text().length();
        }

        return result;
    }

    private record TextSegment(Style style, String text) {}
}
