package odinclient.module.privacy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.InputUtil;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.KeybindSetting;
import odinclient.setting.type.StringSetting;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Environment(EnvType.CLIENT)
public final class CordSnapper extends Module {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread =
                                new Thread(
                                        runnable,
                                        "odinclient-cord-snapper-"
                                                + THREAD_COUNTER.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    });
    private final Setting<Integer> activationKey =
            this.settings
                    .getGeneral()
                    .add(
                            new KeybindSetting.Builder()
                                    .name("activation-key")
                                    .description(
                                            "Press this key while Cord Snapper is enabled to send"
                                                + " the current coordinates once.")
                                    .build());
    private final Setting<String> webhook =
            this.settings
                    .getGeneral()
                    .add(
                            new StringSetting.Builder()
                                    .name("webhook")
                                    .description(
                                            "Discord webhook that receives the coordinates after an"
                                                + " activation-key press.")
                                    .defaultValue("")
                                    .hint("https://discord.com/api/webhooks/...")
                                    .build());
    private boolean wasKeyDown;
    private int lastKeyCode = -2147483648;

    public CordSnapper(Category category) {
        super(
                category,
                "cord-snapper",
                "Sends your current coordinates to a Discord webhook after an explicit key press.");
    }

    @Override
    public void onActivate() {
        int keyCode = this.activationKey.getValue();
        this.lastKeyCode = keyCode;
        this.wasKeyDown = this.isKeyDown(keyCode);
    }

    @Override
    public void onDeactivate() {
        this.wasKeyDown = false;
        this.lastKeyCode = -2147483648;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        int keyCode = this.activationKey.getValue();
        boolean keyDown = this.isKeyDown(keyCode);
        if (keyCode != this.lastKeyCode) {
            this.lastKeyCode = keyCode;
            this.wasKeyDown = keyDown;
        } else {
            boolean pressed = keyDown && !this.wasKeyDown;
            this.wasKeyDown = keyDown;
            if (pressed
                    && this.mc.currentScreen == null
                    && this.mc.player != null
                    && this.mc.world != null) {
                String url = this.webhook.getValue().trim();
                if (isValidWebhookUrl(url)) {
                    int x = (int) Math.floor(this.mc.player.getX());
                    int y = (int) Math.floor(this.mc.player.getY());
                    int z = (int) Math.floor(this.mc.player.getZ());
                    String message =
                            String.format(
                                    Locale.ROOT, "Coordinates: `X: %d  Y: %d  Z: %d`", x, y, z);
                    EXECUTOR.execute(
                            () -> {
                                try {
                                    postToWebhook(url, message);
                                } catch (Exception e) {
                                }
                            });
                }
            }
        }
    }

    private boolean isKeyDown(int keyCode) {
        return keyCode >= 0
                && this.mc.getWindow() != null
                && InputUtil.isKeyPressed(this.mc.getWindow(), keyCode);
    }

    private static boolean isValidWebhookUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String path = uri.getPath();
            boolean validHost =
                    host != null
                            && (host.equalsIgnoreCase("discord.com")
                                    || host.toLowerCase(Locale.ROOT).endsWith(".discord.com")
                                    || host.equalsIgnoreCase("discordapp.com")
                                    || host.toLowerCase(Locale.ROOT).endsWith(".discordapp.com"));
            return "https".equalsIgnoreCase(uri.getScheme())
                    && validHost
                    && path != null
                    && path.contains("/api/webhooks/");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void postToWebhook(String url, String content) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "OdinClient-Cord-Snapper");
        connection.setDoOutput(true);
        byte[] body =
                ("{\"content\":\"" + escapeJson(content) + "\"}").getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);

        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }

        int code = connection.getResponseCode();
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Discord returned HTTP " + code);
        }
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
