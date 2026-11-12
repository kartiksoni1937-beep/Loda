package odinclient.module.hud;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorthTooltip {
    private static final Pattern WORTH_INPUT_PATTERN =
            Pattern.compile("(?i)^\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)([KMBT]?)$");
    private static final Pattern PRICE_LINE_PATTERN =
            Pattern.compile(".*[$\uff04]\\s*[0-9][0-9,]*(?:\\.[0-9]+)?.*");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000L);
    private static final BigDecimal MAX_WORTH = new BigDecimal("999000000000000");

    private WorthTooltip() {}

    public static String formatWorth(String worth) {
        return formatWorthMultiplied(worth, 1);
    }

    public static String formatWorthMultiplied(String worth, int multiplier) {
        Matcher matcher = WORTH_INPUT_PATTERN.matcher(worth == null ? "" : worth.strip());
        if (!matcher.matches()) {
            return "0";
        } else {
            try {
                BigDecimal amount = new BigDecimal(matcher.group(1).replace(",", ""));
                int exponent =
                        switch (matcher.group(2).toUpperCase(Locale.ROOT)) {
                            case "K" -> 1;
                            case "M" -> 2;
                            case "B" -> 3;
                            case "T" -> 4;
                            default -> 0;
                        };

                for (int i = 0; i < exponent; i++) {
                    amount = amount.multiply(THOUSAND);
                }

                BigDecimal scaled =
                        amount.multiply(BigDecimal.valueOf(Math.max(0, multiplier)))
                                .max(BigDecimal.ZERO)
                                .min(MAX_WORTH);

                int unit;
                for (unit = 0; scaled.compareTo(THOUSAND) >= 0 && unit < 4; unit++) {
                    scaled = scaled.divide(THOUSAND, 8, RoundingMode.HALF_UP);
                }

                BigDecimal rounded = scaled.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
                if (rounded.compareTo(THOUSAND) >= 0 && unit < 4) {
                    rounded = rounded.divide(THOUSAND).stripTrailingZeros();
                    unit++;
                }

                String number = rounded.toPlainString();
                String suffix = unit == 0 ? "" : String.valueOf(" KMBT".charAt(unit));
                return number + suffix;
            } catch (RuntimeException e) {
                return "0";
            }
        }
    }

    public static List<Text> insertWorthLine(List<Text> lines, String worth) {
        return insertWorthLineMultiplied(lines, worth, 1);
    }

    public static List<Text> insertWorthLineMultiplied(
            List<Text> lines, String worth, int multiplier) {
        List<Text> result = new ArrayList<>(lines);
        MutableText worthLine =
                Text.literal("~")
                        .formatted(Formatting.DARK_GRAY)
                        .append(Text.literal("$").formatted(Formatting.GREEN))
                        .append(
                                Text.literal(" " + formatWorthMultiplied(worth, multiplier))
                                        .formatted(Formatting.WHITE));

        for (int i = 0; i < result.size(); i++) {
            if (PRICE_LINE_PATTERN.matcher(result.get(i).getString()).matches()) {
                result.set(i, worthLine);
                return result;
            }
        }

        result.add(Math.min(1, result.size()), worthLine);
        return result;
    }
}
