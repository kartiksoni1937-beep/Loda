package odinclient.module.fake;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.StringSetting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public final class FakePay extends Module {
    private static final Pattern PAY_COMMAND_PATTERN =
            Pattern.compile(
                    "(?i)^pay(?:ment)?\\s+(\\.?[A-Za-z0-9_]{1,16})\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]+)?)([kmbtq]?)\\s*$");
    private static final Pattern PAY_PREFIX_PATTERN =
            Pattern.compile("(?i)^pay(?:ment)?(?:\\s.*)?$");
    private static final Pattern BALANCE_PATTERN =
            Pattern.compile("(?i)^\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)([kmbtq]?)$");
    private static final Pattern PAID_YOU_PATTERN =
            Pattern.compile(
                    "(?i)^\\.?[A-Za-z0-9_]{1,16} paid you"
                        + " \\$([0-9][0-9,]*(?:\\.[0-9]+)?)([kmbtq]?)\\.$");
    private static final DecimalFormat AMOUNT_FORMAT =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static FakePay instance;
    private final Setting<Boolean> fakeScoreboard;
    private final Setting<String> fakeMoney;

    public FakePay(Category category) {
        super(
                category,
                "fake-pay",
                "Shows local-only payment messages; /pay never reaches the server while enabled.");
        SettingGroup general = this.settings.getGeneral();
        this.fakeScoreboard =
                general.add(
                        new BooleanSetting.Builder()
                                .name("fake-scoreboard")
                                .description(
                                        "Replace only the visible money amount, preserving the"
                                            + " server's scoreboard style.")
                                .defaultValue(false)
                                .build());
        this.fakeMoney =
                general.add(
                        new StringSetting.Builder()
                                .name("fake-money")
                                .description(
                                        "Visible starting balance, e.g. 50000000 or 50M. Fake"
                                            + " payments subtract from it.")
                                .defaultValue("0")
                                .build());
        instance = this;
    }

    public static boolean isFakeScoreboardActive() {
        return instance != null && instance.isActive() && instance.fakeScoreboard.getValue();
    }

    public static String getFakeBalanceShort() {
        return instance == null ? "0" : formatAmount(parseBalance(instance.fakeMoney.getValue()));
    }

    public static String getFakeBalanceFormatted() {
        return instance == null
                ? "0"
                : AMOUNT_FORMAT.format(parseBalance(instance.fakeMoney.getValue()));
    }

    public static boolean interceptPayCommand(String input) {
        FakePay module = instance;
        if (module == null || !module.isActive() || input == null) {
            return false;
        }

        String command = input.strip();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (!PAY_PREFIX_PATTERN.matcher(command).matches()) {
            return false;
        }

        Matcher matcher = PAY_COMMAND_PATTERN.matcher(command);
        if (!matcher.matches()) {
            module.sendLocalMessage("Usage: /pay <player> <amount>");
            return true;
        }

        BigDecimal amount = parseAmount(matcher.group(2), matcher.group(3));
        if (amount.signum() <= 0) {
            module.sendLocalMessage("Payment amount must be greater than zero.");
            return true;
        }

        String target = matcher.group(1);
        MinecraftClient.getInstance()
                .execute(() -> applyFakePaymentOnClient(module, target, amount));
        return true;
    }

    public static void onChatMessage(Text message) {
        if (instance != null && instance.isActive() && message != null) {
            Matcher matcher = PAID_YOU_PATTERN.matcher(message.getString().strip());
            if (matcher.matches()) {
                instance.adjustBalance(parseAmount(matcher.group(1), matcher.group(2)));
            }
        }
    }

    private void applyFakePayment(String target, BigDecimal amount) {
        this.adjustBalance(amount.negate());
        MutableText text =
                Text.literal("You paid " + target + " ")
                        .formatted(Formatting.WHITE)
                        .append(Text.literal("$").formatted(Formatting.GREEN))
                        .append(
                                Text.literal(" " + formatAmount(amount))
                                        .formatted(Formatting.WHITE));
        if (this.mc.player != null) {
            this.mc.player.sendMessage(text, false);
            this.mc.player.sendMessage(text, true);
        }
    }

    private void adjustBalance(BigDecimal delta) {
        BigDecimal balance =
                parseBalance(this.fakeMoney.getValue()).add(delta).max(BigDecimal.ZERO);
        this.fakeMoney.setValue(formatAmount(balance));
    }

    private static BigDecimal parseAmount(String number, String suffix) {
        try {
            BigDecimal value = new BigDecimal(number.replace(",", ""));
            String unit = suffix.toLowerCase(Locale.ROOT);

            return value.multiply(
                    switch (unit) {
                        case "k" -> BigDecimal.valueOf(1000L);
                        case "m" -> BigDecimal.valueOf(1000000L);
                        case "b" -> BigDecimal.valueOf(1000000000L);
                        case "t" -> BigDecimal.valueOf(1000000000000L);
                        case "q" -> BigDecimal.valueOf(1000000000000000L);
                        default -> BigDecimal.ONE;
                    });
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal parseBalance(String text) {
        if (text == null) {
            return BigDecimal.ZERO;
        }

        Matcher matcher = BALANCE_PATTERN.matcher(text.strip());
        return matcher.matches()
                ? parseAmount(matcher.group(1), matcher.group(2))
                : BigDecimal.ZERO;
    }

    private static String formatAmount(BigDecimal amount) {
        BigDecimal absolute = amount.abs();
        String[] suffixes = {"Q", "T", "B", "M", "K"};
        long[] divisors = {1000000000000000L, 1000000000000L, 1000000000L, 1000000L, 1000L};

        for (int i = 0; i < divisors.length; i++) {
            BigDecimal divisor = BigDecimal.valueOf(divisors[i]);
            if (absolute.compareTo(divisor) >= 0) {
                return amount.divide(divisor, 2, RoundingMode.HALF_UP)
                                .stripTrailingZeros()
                                .toPlainString()
                        + suffixes[i];
            }
        }

        return AMOUNT_FORMAT.format(amount);
    }

    private void sendLocalMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> sendMessageOnClient(client, message));
    }

    private static void sendMessageOnClient(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    private static void applyFakePaymentOnClient(FakePay module, String target, BigDecimal amount) {
        if (module.isActive()) {
            module.applyFakePayment(target, amount);
        }
    }
}
