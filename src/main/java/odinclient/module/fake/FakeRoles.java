package odinclient.module.fake;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.StyleSpriteSource.Font;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.MapSetting;
import odinclient.setting.type.StringSetting;

import java.util.Locale;
import java.util.Map.Entry;

@Environment(EnvType.CLIENT)
public final class FakeRoles extends Module {
    private static final int MAX_NAME_LENGTH = 32;
    private static final StyleSpriteSource ROLE_ICON_FONT =
            new Font(Identifier.of("odinclient", "role_icons"));
    private static FakeRoles instance;
    private final Setting<String> fakeName =
            this.settings
                    .getGeneral()
                    .add(
                            new StringSetting.Builder()
                                    .name("fake-name")
                                    .description(
                                            "Name shown above your own player on this client only."
                                                + " Leave empty to use your real name.")
                                    .hint("Your name")
                                    .defaultValue("")
                                    .build());
    private final Setting<FakeRoles.Role> role =
            this.settings
                    .getGeneral()
                    .add(
                            new EnumSetting.Builder<FakeRoles.Role>()
                                    .name("role")
                                    .description(
                                            "Local DonutSMP-style role icon placed before your fake"
                                                + " name.")
                                    .defaultValue(FakeRoles.Role.NONE)
                                    .build());
    private final Setting<Boolean> boldName =
            this.settings
                    .getGeneral()
                    .add(
                            new BooleanSetting.Builder()
                                    .name("bold-name")
                                    .description("Draw the locally rendered fake name in bold.")
                                    .defaultValue(false)
                                    .build());
    private final MapSetting playerRoles =
            this.addMapSetting(
                    new MapSetting.Builder()
                            .name("player-roles")
                            .description("Assign local-only fake roles to other players.")
                            .build());

    public FakeRoles(Category category) {
        super(
                category,
                "fake-roles",
                "Customizes locally rendered player nametags and DonutSMP-style roles.");
        instance = this;
    }

    public static FakeRoles getInstance() {
        return instance;
    }

    public boolean shouldOverrideName(PlayerLikeEntity entity) {
        return this.isActive()
                && this.mc.player != null
                && entity != null
                && (entity == this.mc.player || this.getRoleFor(entity) != FakeRoles.Role.NONE);
    }

    public Text getFakeName(PlayerLikeEntity entity) {
        boolean self = this.mc.player != null && entity == this.mc.player;
        String displayName = self ? sanitizeName(this.fakeName.getValue()) : "";
        String realName = sanitizeName(entity.getName().getString());
        if (displayName.isEmpty()) {
            displayName = realName;
        }

        if (displayName.isEmpty()) {
            displayName = "Player";
        }

        FakeRoles.Role selectedRole = self ? this.role.getValue() : this.getAssignedRole(realName);
        MutableText result = Text.empty();
        if (selectedRole != FakeRoles.Role.NONE && !selectedRole.isSuffix) {
            int prefixColor = selectedRole.color;
            MutableText prefix = Text.literal(selectedRole.label);
            if (selectedRole.usesIconFont) {
                prefix.styled(
                        style ->
                                style.withColor(prefixColor)
                                        .withBold(false)
                                        .withFont(ROLE_ICON_FONT));
            } else {
                prefix.styled(style -> style.withColor(prefixColor).withBold(false));
            }

            result.append(prefix);
        }

        boolean bold = self && this.boldName.getValue();
        result.append(
                Text.literal(displayName)
                        .styled(style -> style.withColor(16777215).withBold(bold)));
        if (selectedRole.isSuffix) {
            int suffixColor = selectedRole.color;
            result.append(
                    Text.literal(selectedRole.label)
                            .styled(style -> style.withColor(suffixColor).withBold(true)));
        }

        return result;
    }

    public static String serializeRole(FakeRoles.Role role) {
        FakeRoles.Role effective =
                role != null && role != FakeRoles.Role.NONE ? role : FakeRoles.Role.ADMIN;
        return "ROLE:" + effective.name();
    }

    public static FakeRoles.Role parseRole(String text) {
        if (text != null && !text.isBlank()) {
            String name = text.strip();
            if (name.regionMatches(true, 0, "ROLE:", 0, 5)) {
                name = name.substring(5);
            }

            try {
                FakeRoles.Role role =
                        FakeRoles.Role.valueOf(name.toUpperCase(Locale.ROOT).replace(' ', '_'));
                return role == FakeRoles.Role.NONE ? FakeRoles.Role.ADMIN : role;
            } catch (IllegalArgumentException e) {
                return FakeRoles.Role.ADMIN;
            }
        } else {
            return FakeRoles.Role.ADMIN;
        }
    }

    public static FakeRoles.Role nextRole(String current) {
        FakeRoles.Role role = parseRole(current);
        FakeRoles.Role[] cycle = {
            FakeRoles.Role.ADMIN,
            FakeRoles.Role.MEDIA,
            FakeRoles.Role.DONUT_PLUS,
            FakeRoles.Role.DONUT_PLUS_PLUS,
            FakeRoles.Role.DONUT_PLUS_PLUS_PLUS,
            FakeRoles.Role.DEVELOPER
        };

        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == role) {
                return cycle[(i + 1) % cycle.length];
            }
        }

        return cycle[0];
    }

    public static String getRoleLabel(String text) {
        return switch (parseRole(text)) {
            case ADMIN -> "ADMIN";
            case MEDIA -> "MEDIA";
            case DONUT_PLUS -> "DONUT +";
            case DONUT_PLUS_PLUS -> "DONUT ++";
            case DONUT_PLUS_PLUS_PLUS -> "DONUT +++";
            case DEVELOPER -> "DEVELOPER";
            default -> "ADMIN";
        };
    }

    private MapSetting addMapSetting(MapSetting setting) {
        this.settings.getGeneral().add(setting);
        return setting;
    }

    private FakeRoles.Role getRoleFor(PlayerLikeEntity entity) {
        return entity == null
                ? FakeRoles.Role.NONE
                : this.getAssignedRole(sanitizeName(entity.getName().getString()));
    }

    private FakeRoles.Role getAssignedRole(String name) {
        if (name != null && !name.isBlank()) {
            for (Entry<String, String> entry : this.playerRoles.getValue().entrySet()) {
                if (entry.getKey() != null && entry.getKey().strip().equalsIgnoreCase(name)) {
                    return parseRole(entry.getValue());
                }
            }

            return FakeRoles.Role.NONE;
        } else {
            return FakeRoles.Role.NONE;
        }
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        } else {
            StringBuilder builder = new StringBuilder();
            boolean afterSection = false;
            int index = 0;

            while (index < name.length() && builder.length() < 32) {
                int codePoint = name.codePointAt(index);
                index += Character.charCount(codePoint);
                if (afterSection) {
                    afterSection = false;
                } else if (codePoint == 167) {
                    afterSection = true;
                } else if (!Character.isISOControl(codePoint)) {
                    builder.appendCodePoint(codePoint);
                }
            }

            return builder.toString().strip();
        }
    }

    @Environment(EnvType.CLIENT)
    public static enum Role {
        NONE("", 16777215, false, false),
        ADMIN("\ue000", 5635925, false, true),
        MEDIA("\ud83d\udcf9", 16733655, false, false),
        DONUT_PLUS("+", 2666495, true, false),
        DONUT_PLUS_PLUS("++", 2666495, true, false),
        DONUT_PLUS_PLUS_PLUS("+++", 2666495, true, false),
        DEVELOPER("\ue000", 11818495, false, true);

        private final String label;
        private final int color;
        private final boolean isSuffix;
        private final boolean usesIconFont;

        private Role(String label, int color, boolean isSuffix, boolean usesIconFont) {
            this.label = label;
            this.color = color;
            this.isSuffix = isSuffix;
            this.usesIconFont = usesIconFont;
        }
    }
}
