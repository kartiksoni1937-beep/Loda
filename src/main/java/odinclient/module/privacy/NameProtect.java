package odinclient.module.privacy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;

import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.MapSetting;
import odinclient.setting.type.StringSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public final class NameProtect extends Module {
    private static final int MAX_NAME_LENGTH = 32;
    private static NameProtect instance;
    private final Setting<String> protectedName =
            this.settings
                    .getGeneral()
                    .add(
                            new StringSetting.Builder()
                                    .name("your-protected-name")
                                    .description(
                                            "Name shown instead of your real Minecraft account name"
                                                + " on this screen only.")
                                    .hint("Protected name")
                                    .defaultValue("Protected Player")
                                    .build());
    private final MapSetting protectedFriends =
            this.addMapSetting(
                    new MapSetting.Builder()
                            .name("protect-friends")
                            .description(
                                    "Opens a menu for protecting multiple friends with local fake"
                                        + " names.")
                            .build());

    public NameProtect(Category category) {
        super(
                category,
                "name-protect",
                "Replaces selected Minecraft names only while rendering on your screen.");
        instance = this;
    }

    private MapSetting addMapSetting(MapSetting setting) {
        this.settings.getGeneral().add(setting);
        return setting;
    }

    public static String replaceNames(String text) {
        NameProtect nameProtect = instance;
        return nameProtect == null ? text : nameProtect.replaceInString(text);
    }

    public static StringVisitable replaceNamesInVisitable(StringVisitable visitable) {
        NameProtect nameProtect = instance;
        if (nameProtect != null && visitable != null && nameProtect.isEnabled()) {
            ArrayList<StringVisitable> parts = new ArrayList<>();
            visitable.visit(
                    (style, str) -> {
                        parts.add(StringVisitable.styled(nameProtect.replaceInString(str), style));
                        return Optional.empty();
                    },
                    Style.EMPTY);
            return parts.isEmpty() ? visitable : StringVisitable.concat(parts);
        } else {
            return visitable;
        }
    }

    public static OrderedText replaceNamesInOrderedText(OrderedText text) {
        NameProtect nameProtect = instance;
        return nameProtect != null && text != null && nameProtect.isEnabled()
                ? nameProtect.replaceInOrderedText(text)
                : text;
    }

    private String replaceInString(String text) {
        if (text != null && !text.isEmpty() && this.isEnabled()) {
            List<NameProtect.NameReplacement> replacements = this.buildReplacements();
            if (replacements.isEmpty()) {
                return text;
            } else {
                StringBuilder builder = new StringBuilder(text.length());
                boolean replaced = false;
                int index = 0;

                while (index < text.length()) {
                    NameProtect.NameReplacement match = findReplacement(text, index, replacements);
                    if (match != null) {
                        builder.append(match.replacement);
                        index += match.source.length();
                        replaced = true;
                    } else {
                        int codePoint = text.codePointAt(index);
                        builder.appendCodePoint(codePoint);
                        index += Character.charCount(codePoint);
                    }
                }

                return replaced ? builder.toString() : text;
            }
        } else {
            return text;
        }
    }

    private OrderedText replaceInOrderedText(OrderedText text) {
        ArrayList<NameProtect.StyledCodePoint> points = new ArrayList<>();
        text.accept(
                (index, style, codePoint) -> {
                    points.add(new NameProtect.StyledCodePoint(codePoint, style));
                    return true;
                });
        if (points.isEmpty()) {
            return text;
        } else {
            StringBuilder plain = new StringBuilder(points.size());
            ArrayList<Integer> offsets = new ArrayList<>(points.size() + 1);

            for (NameProtect.StyledCodePoint point : points) {
                offsets.add(plain.length());
                plain.appendCodePoint(point.codePoint);
            }

            offsets.add(plain.length());
            String plainString = plain.toString();
            List<NameProtect.NameReplacement> replacements = this.buildReplacements();
            if (replacements.isEmpty()) {
                return text;
            } else {
                ArrayList<NameProtect.StyledCodePoint> result = new ArrayList<>();
                boolean replaced = false;
                int i = 0;

                while (i < points.size()) {
                    int offset = offsets.get(i);
                    NameProtect.NameReplacement match =
                            findReplacement(plainString, offset, replacements);
                    if (match == null) {
                        result.add(points.get(i++));
                    } else {
                        Style style = points.get(i).style;
                        match.replacement
                                .codePoints()
                                .forEach(
                                        codePoint ->
                                                result.add(
                                                        new NameProtect.StyledCodePoint(
                                                                codePoint, style)));
                        int end = offset + match.source.length();

                        do {
                            i++;
                        } while (i < points.size() && offsets.get(i) < end);

                        replaced = true;
                    }
                }

                return !replaced
                        ? text
                        : visitor -> {
                            int index = 0;

                            for (NameProtect.StyledCodePoint styled : result) {
                                if (!visitor.accept(index, styled.style, styled.codePoint)) {
                                    return false;
                                }

                                index += Character.charCount(styled.codePoint);
                            }

                            return true;
                        };
            }
        }
    }

    private boolean isEnabled() {
        return this.isActive() && this.mc.getSession() != null;
    }

    private List<NameProtect.NameReplacement> buildReplacements() {
        ArrayList<NameProtect.NameReplacement> replacements = new ArrayList<>(2);
        addReplacement(
                replacements,
                sanitizeName(this.mc.getSession().getUsername()),
                sanitizeName(this.protectedName.getValue()));
        this.protectedFriends
                .getValue()
                .forEach(
                        (name, replacement) ->
                                addReplacement(
                                        replacements,
                                        sanitizeName(name),
                                        sanitizeName(replacement)));
        replacements.sort(
                Comparator.comparingInt((NameProtect.NameReplacement r) -> r.source.length())
                        .reversed());
        return replacements;
    }

    private static void addReplacement(
            List<NameProtect.NameReplacement> replacements, String source, String replacement) {
        if (!source.isEmpty() && !replacement.isEmpty() && !source.equalsIgnoreCase(replacement)) {
            replacements.add(new NameProtect.NameReplacement(source, replacement));
        }
    }

    private static NameProtect.NameReplacement findReplacement(
            String text, int start, List<NameProtect.NameReplacement> replacements) {
        for (NameProtect.NameReplacement replacement : replacements) {
            int end = start + replacement.source.length();
            if (end <= text.length()
                    && text.regionMatches(
                            true, start, replacement.source, 0, replacement.source.length())
                    && (start <= 0 || !isNameChar(text.codePointBefore(start)))
                    && (end >= text.length() || !isNameChar(text.codePointAt(end)))) {
                return replacement;
            }
        }

        return null;
    }

    private static boolean isNameChar(int c) {
        return c == '_' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        } else {
            StringBuilder builder = new StringBuilder();
            boolean skipNext = false;
            int index = 0;

            while (index < name.length() && builder.length() < MAX_NAME_LENGTH) {
                int codePoint = name.codePointAt(index);
                index += Character.charCount(codePoint);
                if (skipNext) {
                    skipNext = false;
                } else if (codePoint == 167) {
                    skipNext = true;
                } else if (!Character.isISOControl(codePoint)) {
                    builder.appendCodePoint(codePoint);
                }
            }

            return builder.toString().strip();
        }
    }

    @Environment(EnvType.CLIENT)
    private record NameReplacement(String source, String replacement) {}

    @Environment(EnvType.CLIENT)
    private record StyledCodePoint(int codePoint, Style style) {}
}
