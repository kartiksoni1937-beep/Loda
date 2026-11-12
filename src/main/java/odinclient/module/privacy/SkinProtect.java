package odinclient.module.privacy;

import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.SkinTextures;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.manager.ModuleManager;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.EnumSetting;
import odinclient.setting.type.StringSetting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public final class SkinProtect extends Module {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final String PROFILE_LOOKUP_URL =
            "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE_URL =
            "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final int MAX_RESPONSE_BYTES = 1048576;
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8L))
                    .followRedirects(Redirect.NEVER)
                    .build();
    private final AtomicLong requestId = new AtomicLong();
    private volatile SkinTextures skinTextures;
    private volatile String loadedName = "";
    private volatile String pendingName = "";
    private volatile String warnedName = "";
    private final Setting<String> skinIgn =
            this.settings
                    .getGeneral()
                    .add(
                            new StringSetting.Builder()
                                    .name("skin-ign")
                                    .description(
                                            "Minecraft username whose skin is used as the"
                                                + " replacement.")
                                    .hint("Minecraft IGN")
                                    .defaultValue("67skibidi")
                                    .onChange(value -> this.resetSkin())
                                    .build());
    private final Setting<SkinProtect.ApplyTarget> applyTo =
            this.settings
                    .getGeneral()
                    .add(
                            new EnumSetting.Builder<SkinProtect.ApplyTarget>()
                                    .name("apply-to")
                                    .description("Choose whose rendered skin is replaced.")
                                    .defaultValue(SkinProtect.ApplyTarget.Everyone)
                                    .build());

    public SkinProtect() {
        super(
                Categories.MAIN,
                "skin-protect",
                "Replaces player skins with a chosen skin to protect identities in clips.");
    }

    @Override
    public void onActivate() {
        this.updateSkin();
    }

    @Override
    public void onDeactivate() {
        this.requestId.incrementAndGet();
        this.pendingName = "";
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        this.updateSkin();
    }

    public SkinTextures getSkinTextures() {
        return this.skinTextures;
    }

    public boolean shouldReplace(UUID uuid) {
        if (uuid == null) {
            return false;
        } else {
            UUID selfId = this.mc.player == null ? null : this.mc.player.getUuid();
            return switch (this.applyTo.getValue()) {
                case Everyone -> true;
                case Others -> selfId == null || !selfId.equals(uuid);
                case Self -> selfId != null && selfId.equals(uuid);
            };
        }
    }

    public static SkinProtect getActive() {
        return ModuleManager.get().getByName("skin-protect") instanceof SkinProtect module
                        && module.isActive()
                ? module
                : null;
    }

    private void resetSkin() {
        this.requestId.incrementAndGet();
        this.skinTextures = null;
        this.loadedName = "";
        this.pendingName = "";
        this.warnedName = "";
    }

    private void updateSkin() {
        String name = this.skinIgn.getValue().trim();
        if (!USERNAME_PATTERN.matcher(name).matches()) {
            this.skinTextures = null;
            this.loadedName = "";
            if (!name.isEmpty() && !name.equalsIgnoreCase(this.warnedName)) {
                this.warnedName = name;
                this.warning(
                        "Skin Protect: use a valid Minecraft name (1-16 letters, numbers or _). ");
            }
        } else if (!name.equalsIgnoreCase(this.loadedName)
                && !name.equalsIgnoreCase(this.pendingName)) {
            long id = this.requestId.incrementAndGet();
            this.pendingName = name;
            this.skinTextures = null;
            this.loadedName = "";
            BiConsumer<GameProfile, Throwable> callback =
                    (profile, error) ->
                            this.mc.execute(
                                    () -> {
                                        if (this.isActive()
                                                && id == this.requestId.get()
                                                && name.equalsIgnoreCase(
                                                        this.skinIgn.getValue().trim())) {
                                            if (error == null && profile != null) {
                                                this.loadSkinTextures(profile, name, id);
                                            } else {
                                                this.pendingName = "";
                                                this.warnOnce(name, "profile could not be loaded");
                                            }
                                        }
                                    });
            CompletableFuture.supplyAsync(() -> this.fetchProfile(name)).whenComplete(callback);
        }
    }

    private GameProfile fetchProfile(String name) {
        try {
            JsonObject idJson =
                    fetchJson("https://api.mojang.com/users/profiles/minecraft/" + name);
            if (idJson != null && idJson.has("id")) {
                UUID uuid = parseMojangUuid(idJson.get("id").getAsString());
                String profileName = idJson.has("name") ? idJson.get("name").getAsString() : name;
                JsonObject sessionJson =
                        fetchJson(
                                "https://sessionserver.mojang.com/session/minecraft/profile/"
                                        + uuid.toString().replace("-", "")
                                        + "?unsigned=false");
                if (sessionJson != null
                        && sessionJson.has("properties")
                        && sessionJson.get("properties").isJsonArray()) {
                    LinkedHashMultimap<String, Property> properties = LinkedHashMultimap.create();

                    for (JsonElement element : sessionJson.getAsJsonArray("properties")) {
                        if (element.isJsonObject()) {
                            JsonObject property = element.getAsJsonObject();
                            if (property.has("name")
                                    && property.has("value")
                                    && "textures".equals(property.get("name").getAsString())) {
                                String value = property.get("value").getAsString();
                                String signature =
                                        property.has("signature")
                                                ? property.get("signature").getAsString()
                                                : null;
                                Property textures =
                                        signature == null
                                                ? new Property("textures", value)
                                                : new Property("textures", value, signature);
                                properties.put("textures", textures);
                            }
                        }
                    }

                    return properties.isEmpty()
                            ? null
                            : new GameProfile(uuid, profileName, new PropertyMap(properties));
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void loadSkinTextures(GameProfile profile, String name, long id) {
        BiConsumer<Optional<SkinTextures>, Throwable> callback =
                (result, error) ->
                        this.mc.execute(
                                () -> {
                                    if (this.isActive()
                                            && id == this.requestId.get()
                                            && name.equalsIgnoreCase(
                                                    this.skinIgn.getValue().trim())) {
                                        this.pendingName = "";
                                        if (error == null && result != null && result.isPresent()) {
                                            this.skinTextures = result.get();
                                            this.loadedName = name;
                                            this.warnedName = "";
                                        } else {
                                            this.warnOnce(name, "skin texture could not be loaded");
                                        }
                                    }
                                });
        this.mc.getSkinProvider().fetchSkinTextures(profile).whenComplete(callback);
    }

    private void warnOnce(String name, String reason) {
        if (!name.equalsIgnoreCase(this.warnedName)) {
            this.warnedName = name;
            this.warning("Skin Protect: %s for %s.", reason, name);
        }
    }

    private static JsonObject fetchJson(String url) throws Exception {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if ("https".equalsIgnoreCase(uri.getScheme())
                && ("api.mojang.com".equalsIgnoreCase(host)
                        || "sessionserver.mojang.com".equalsIgnoreCase(host))) {
            HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(8L))
                            .header("Accept", "application/json")
                            .GET()
                            .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, BodyHandlers.ofByteArray());
            byte[] body = response.body();
            return response.statusCode() == 200
                            && body != null
                            && body.length != 0
                            && body.length <= 1048576
                    ? JsonParser.parseString(new String(body, StandardCharsets.UTF_8))
                            .getAsJsonObject()
                    : null;
        } else {
            throw new SecurityException("Blocked Skin Protect host");
        }
    }

    private static UUID parseMojangUuid(String hex) {
        if (hex != null && hex.matches("[0-9a-fA-F]{32}")) {
            return UUID.fromString(
                    hex.substring(0, 8)
                            + "-"
                            + hex.substring(8, 12)
                            + "-"
                            + hex.substring(12, 16)
                            + "-"
                            + hex.substring(16, 20)
                            + "-"
                            + hex.substring(20));
        } else {
            throw new IllegalArgumentException("Invalid Mojang UUID");
        }
    }

    public static enum ApplyTarget {
        Everyone,
        Others,
        Self;
    }
}
