package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public final class MapSetting extends Setting<Map<String, String>> {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_KEY_LENGTH = 16;
    private static final int MAX_VALUE_LENGTH = 32;

    private MapSetting(MapSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                sanitizeMap(builder.defaultValue),
                builder.visibility,
                builder.changeListener);
    }

    public boolean setValue(Map<String, String> map) {
        return map != null && super.setValue(sanitizeMap(map));
    }

    protected Map<String, String> parseValue(String text) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(";")) {
                int separator = part.indexOf(':');
                if (separator > 0 && separator < part.length() - 1) {
                    try {
                        result.put(
                                decodeBase64(part.substring(0, separator)),
                                decodeBase64(part.substring(separator + 1)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            return sanitizeMap(result);
        } else {
            return result;
        }
    }

    public String serialize() {
        Function<Entry<String, String>, String> encoder =
                entry -> encodeBase64(entry.getKey()) + ":" + encodeBase64(entry.getValue());
        return this.getValue().entrySet().stream().map(encoder).collect(Collectors.joining(";"));
    }

    public static Map<String, String> sanitize(Map<String, String> map) {
        return sanitizeMap(map);
    }

    private static Map<String, String> sanitizeMap(Map<String, String> map) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (map == null) {
            return result;
        } else {
            for (Entry<String, String> entry : map.entrySet()) {
                if (result.size() >= MAX_ENTRIES) {
                    break;
                }

                String key = sanitizeKey(entry.getKey());
                String value = sanitizeValue(entry.getValue());
                if (!key.isEmpty() && !value.isEmpty() && !key.equalsIgnoreCase(value)) {
                    String existingKey = null;

                    for (String candidate : result.keySet()) {
                        if (candidate.equalsIgnoreCase(key)) {
                            existingKey = candidate;
                        }
                    }

                    if (existingKey != null) {
                        result.remove(existingKey);
                    }

                    result.put(key, value);
                }
            }

            return result;
        }
    }

    private static String sanitizeKey(String input) {
        if (input == null) {
            return "";
        } else {
            String stripped = input.strip();
            StringBuilder builder = new StringBuilder(Math.min(stripped.length(), MAX_KEY_LENGTH));
            int index = 0;

            while (index < stripped.length() && builder.length() < MAX_KEY_LENGTH) {
                int codePoint = stripped.codePointAt(index);
                index += Character.charCount(codePoint);
                if (codePoint == '_'
                        || codePoint >= '0' && codePoint <= '9'
                        || codePoint >= 'A' && codePoint <= 'Z'
                        || codePoint >= 'a' && codePoint <= 'z') {
                    builder.appendCodePoint(codePoint);
                }
            }

            return builder.toString();
        }
    }

    private static String sanitizeValue(String input) {
        if (input == null) {
            return "";
        } else {
            StringBuilder builder = new StringBuilder();
            boolean afterSection = false;
            int index = 0;

            while (index < input.length() && builder.length() < MAX_VALUE_LENGTH) {
                int codePoint = input.codePointAt(index);
                index += Character.charCount(codePoint);
                if (afterSection) {
                    afterSection = false;
                } else if (codePoint == 0xA7) {
                    afterSection = true;
                } else if (!Character.isISOControl(codePoint)) {
                    builder.appendCodePoint(codePoint);
                }
            }

            return builder.toString().strip();
        }
    }

    private static String encodeBase64(String text) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64(String text) {
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<MapSetting.Builder, MapSetting, Map<String, String>> {
        public Builder() {
            this.defaultValue = new LinkedHashMap<>();
        }

        public MapSetting build() {
            return new MapSetting(this);
        }
    }
}
