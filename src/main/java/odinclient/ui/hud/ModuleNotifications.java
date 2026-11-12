package odinclient.ui.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import odinclient.module.Module;
import odinclient.module.hud.ThemeModule;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroupList;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.anim.AnimationUtil;
import odinclient.ui.draw.SdfFontRenderer;
import odinclient.ui.draw.SdfShapeRenderer;
import odinclient.ui.scale.ScreenScaleCompat;
import odinclient.ui.screen.OdinGuiScreen;

import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.item.ItemStack;

public final class ModuleNotifications {
    private static final Object lock = new Object();
    private static final long DEFAULT_DURATION_MILLIS = 2000L;
    private static final long SLIDE_IN_MILLIS = 260L;
    private static final long SLIDE_OUT_MILLIS = 240L;
    private static final int MAX_NOTIFICATIONS = 4;
    private static final float CARD_WIDTH = 170.0F;
    private static final float CARD_HEIGHT = 36.0F;
    private static final float CARD_SPACING = 5.0F;
    private static final List<ModuleNotifications.Notification> notifications = new ArrayList<>();
    private static final ThreadLocal<ArrayDeque<ModuleNotifications.PendingToggle>> pendingToggles =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile Setting<Boolean> setting;
    private static volatile Setting<Double> scaleSetting;
    private static volatile Setting<Double> durationSetting;
    private static volatile Setting<Boolean> soundsSetting;
    private static volatile Setting<Double> volumeSetting;
    private static volatile long toggleCounter;
    private static final float HUD_SCALE_FACTOR = 0.62F;
    private static final float SCREEN_MARGIN = 4.0F;

    private ModuleNotifications() {}

    public static Setting<Boolean> registerSettings(SettingGroupList groups) {
        Setting<Boolean> enabledSetting =
                groups.getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("toggle-notifications")
                                        .description(
                                                "Shows a notification when a module is enabled or"
                                                    + " disabled.")
                                        .defaultValue(true)
                                        .build());
        Setting<Double> scale =
                groups.getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("notifications-scale")
                                        .description("Changes the size of module notifications.")
                                        .defaultValue(1.0)
                                        .range(0.5, 2.0)
                                        .sliderRange(0.5, 2.0)
                                        .visibleWhen(
                                                () ->
                                                        Boolean.TRUE.equals(
                                                                enabledSetting.getValue()))
                                        .build());
        Setting<Double> duration =
                groups.getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("notification-duration")
                                        .description(
                                                "Changes how many seconds notifications stay on"
                                                    + " screen.")
                                        .defaultValue(2.0)
                                        .range(1.0, 10.0)
                                        .sliderRange(1.0, 10.0)
                                        .visibleWhen(
                                                () ->
                                                        Boolean.TRUE.equals(
                                                                enabledSetting.getValue()))
                                        .build());
        Setting<Boolean> sounds =
                groups.getGeneral()
                        .add(
                                new BooleanSetting.Builder()
                                        .name("sounds")
                                        .description(
                                                "Plays the OdinClient HUD Sound when a notification"
                                                    + " appears.")
                                        .defaultValue(false)
                                        .visibleWhen(
                                                () ->
                                                        Boolean.TRUE.equals(
                                                                enabledSetting.getValue()))
                                        .build());
        Setting<Double> volume =
                groups.getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("volume")
                                        .description(
                                                "Changes the volume of the OdinClient HUD Sound.")
                                        .defaultValue(100.0)
                                        .range(0.0, 100.0)
                                        .sliderRange(0.0, 100.0)
                                        .visibleWhen(
                                                () ->
                                                        Boolean.TRUE.equals(
                                                                        enabledSetting.getValue())
                                                                && Boolean.TRUE.equals(
                                                                        sounds.getValue()))
                                        .build());
        setting = enabledSetting;
        scaleSetting = scale;
        durationSetting = duration;
        soundsSetting = sounds;
        volumeSetting = volume;
        return enabledSetting;
    }

    public static void beginToggle(Module module, boolean requestedState) {
        pendingToggles
                .get()
                .addLast(
                        new ModuleNotifications.PendingToggle(
                                module, module != null && module.isActive(), requestedState));
    }

    public static void endToggle(Module module) {
        ArrayDeque<ModuleNotifications.PendingToggle> stack = pendingToggles.get();
        ModuleNotifications.PendingToggle pending = stack.pollLast();
        if (stack.isEmpty()) {
            pendingToggles.remove();
        }

        if (pending != null && pending.module == module && module != null) {
            boolean active = module.isActive();
            if (pending.oldState != active && pending.requestedState == active) {
                toggleCounter++;
                if (isEnabled()
                        && !(MinecraftClient.getInstance().currentScreen
                                instanceof OdinGuiScreen)) {
                    String title = module.title;
                    if (title == null || title.isBlank()) {
                        title = module.name;
                    }

                    if (title == null || title.isBlank()) {
                        title = "MODULE";
                    }

                    ModuleNotifications.Notification notification =
                            new ModuleNotifications.Notification(
                                    title,
                                    active,
                                    OdinGuiScreen.getModuleIconName(module),
                                    System.currentTimeMillis(),
                                    getDurationMillis(),
                                    false,
                                    null);
                    synchronized (lock) {
                        notifications.add(notification);

                        while (notifications.size() > 4) {
                            notifications.remove(0);
                        }
                    }

                    playSound();
                }
            }
        }
    }

    public static long getToggleCounter() {
        return toggleCounter;
    }

    public static void renderHud(DrawContext context) {
        if (context != null
                && !(MinecraftClient.getInstance().currentScreen instanceof OdinGuiScreen)) {
            float scale = getScale() * 0.62F;

            try (ScreenScaleCompat.ScaleScope scope = ScreenScaleCompat.enter(context)) {
                renderNotifications(
                        context,
                        ScreenScaleCompat.getScaledWidth() - 4.0F,
                        ScreenScaleCompat.getScaledHeight() - 4.0F,
                        scale,
                        false,
                        false);
            }
        }
    }

    public static void renderOverMenu(DrawContext context, int mouseX, int mouseY) {
        if (context != null) {
            try (ScreenScaleCompat.ScaleScope scope = ScreenScaleCompat.enter(context)) {
                renderNotifications(
                        context,
                        ScreenScaleCompat.getScaledWidth() - 4.0F,
                        ScreenScaleCompat.getScaledHeight() - 4.0F,
                        getScale() * 0.62F,
                        false,
                        true);
            }
        }
    }

    public static void pushCustomNotification(
            String icon, String title, String message, boolean enabled) {
        ModuleNotifications.Notification notification =
                new ModuleNotifications.Notification(
                        title,
                        enabled,
                        icon,
                        System.currentTimeMillis(),
                        getDurationMillis(),
                        true,
                        message);
        synchronized (lock) {
            notifications.add(notification);

            while (notifications.size() > 4) {
                notifications.remove(0);
            }
        }

        playSound();
    }

    private static void renderNotifications(
            DrawContext context,
            float right,
            float bottom,
            float scale,
            boolean stackHorizontally,
            boolean overMenuOnly) {
        if (!overMenuOnly && !isEnabled()) {
            synchronized (lock) {
                notifications.clear();
            }
        } else {
            long now = System.currentTimeMillis();
            List<ModuleNotifications.Notification> visible;
            synchronized (lock) {
                notifications.removeIf(
                        expired -> now - expired.startedAt >= expired.durationMillis);
                if (notifications.isEmpty()) {
                    return;
                }

                visible = notifications.stream().filter(n -> !overMenuOnly || n.overMenu).toList();
                if (visible.isEmpty()) {
                    return;
                }
            }

            float cardWidth = 170.0F * scale;
            float cardHeight = 36.0F * scale;
            float spacing = 5.0F * scale;
            boolean animated = ThemeModule.areAnimationsEnabled();
            float x = right;
            float y = bottom;

            for (int i = visible.size() - 1; i >= 0; i--) {
                ModuleNotifications.Notification notification = visible.get(i);
                long age = now - notification.startedAt;
                float slideIn =
                        animated ? AnimationUtil.easeOutBack(clamp01((float) age / 260.0F)) : 1.0F;
                float slideOut =
                        animated
                                ? AnimationUtil.easeInCubic(
                                        clamp01(
                                                (float) (age - (notification.durationMillis - 240L))
                                                        / 240.0F))
                                : 0.0F;
                float slide = 1.0F - Math.min(1.0F, slideIn) + slideOut;
                float drawX;
                float drawY;
                if (stackHorizontally) {
                    drawX = x - cardWidth;
                    drawY = y - cardHeight + (cardHeight + 8.0F) * slide;
                    x -= (cardWidth + spacing) * (1.0F - slideOut);
                } else {
                    drawX = x - cardWidth + (cardWidth + 8.0F) * slide;
                    drawY = y - cardHeight;
                    y -= (cardHeight + spacing) * (1.0F - slideOut);
                }

                Matrix3x2fStack matrices = context.getMatrices();
                matrices.pushMatrix();
                matrices.translate(drawX, drawY);
                matrices.scale(scale, scale);

                try {
                    renderNotification(context, notification, age, clamp01(1.0F - slideOut));
                } finally {
                    matrices.popMatrix();
                }
            }
        }
    }

    private static void renderNotification(
            DrawContext context,
            ModuleNotifications.Notification notification,
            long age,
            float alpha) {
        int accent = ThemeModule.getAccentColorAt(0.3F);
        int accentEnd = ThemeModule.getAccentColorAt(0.8F);
        int stateColor = notification.enabled ? -10755160 : -41081;
        SdfShapeRenderer.drawShadow(
                context,
                0.0F,
                2.0F,
                170.0F,
                36.0F,
                9.0F,
                6.0F,
                AnimationUtil.multiplyAlpha(-1879048192, alpha));
        SdfShapeRenderer.fill(
                context,
                0.0F,
                0.0F,
                170.0F,
                36.0F,
                9.0F,
                AnimationUtil.multiplyAlpha(-234552567, alpha));
        SdfShapeRenderer.fillVerticalGradient(
                context,
                0.0F,
                0.0F,
                170.0F,
                36.0F,
                9.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 46), alpha),
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 0), alpha));
        SdfShapeRenderer.drawOutline(
                context,
                0.0F,
                0.0F,
                170.0F,
                36.0F,
                9.0F,
                0.75F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 150), alpha));
        float iconProgress = AnimationUtil.easeOutBack(clamp01((float) (age - 60L) / 320.0F));
        float iconBoxSize = 22.0F * (0.8F + 0.2F * iconProgress);
        float iconCenterX = 18.0F;
        SdfShapeRenderer.fill(
                context,
                iconCenterX - iconBoxSize * 0.5F,
                17.0F - iconBoxSize * 0.5F,
                iconBoxSize,
                iconBoxSize,
                6.5F,
                AnimationUtil.multiplyAlpha(
                        AnimationUtil.lerpColor(
                                -15659750, accent, notification.enabled ? 0.35F : 0.12F),
                        alpha));
        OdinGuiScreen.drawIcon(
                context,
                notification.icon,
                iconCenterX,
                17.0F,
                18.0F * (0.8F + 0.2F * iconProgress),
                AnimationUtil.multiplyAlpha(-1, alpha));
        float textX = 36.0F;
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BOLD,
                SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BOLD, notification.title, 170.0F - textX - 8.0F),
                textX,
                12.0F,
                AnimationUtil.multiplyAlpha(-658693, alpha));
        SdfShapeRenderer.fillCircle(
                context, textX + 2.0F, 22.5F, 2.0F, AnimationUtil.multiplyAlpha(stateColor, alpha));
        String text =
                notification.message != null
                        ? notification.message
                        : (notification.enabled ? "Enabled" : "Disabled");
        SdfFontRenderer.drawTextCenteredY(
                context,
                SdfFontRenderer.FontStyle.BODY_SMALL,
                SdfFontRenderer.truncateToWidth(
                        SdfFontRenderer.FontStyle.BODY_SMALL, text, 170.0F - textX - 10.0F),
                textX + 7.0F,
                22.5F,
                AnimationUtil.multiplyAlpha(stateColor, alpha));
        float remaining =
                ThemeModule.areAnimationsEnabled()
                        ? clamp01(
                                (float) (notification.durationMillis - age)
                                        / (float) notification.durationMillis)
                        : 1.0F;
        float barX = 7.0F;
        float barWidth = 156.0F;
        SdfShapeRenderer.fill(
                context,
                barX,
                31.0F,
                barWidth,
                2.0F,
                1.0F,
                AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 50), alpha));
        if (remaining > 0.0F) {
            SdfShapeRenderer.fillVerticalGradient(
                    context,
                    barX,
                    31.0F,
                    Math.max(2.0F, barWidth * remaining),
                    2.0F,
                    1.0F,
                    AnimationUtil.multiplyAlpha(accent, alpha),
                    AnimationUtil.multiplyAlpha(accentEnd, alpha));
        }
    }

    private static boolean isEnabled() {
        Setting<Boolean> enabledSetting = setting;
        if (enabledSetting != null && Boolean.TRUE.equals(enabledSetting.getValue())) {
            MinecraftClient client = MinecraftClient.getInstance();
            return client != null && client.player != null && client.world != null;
        } else {
            return false;
        }
    }

    private static float getScale() {
        Setting<Double> scale = scaleSetting;
        return scale != null && scale.getValue() != null
                ? Math.max(0.5F, Math.min(2.0F, scale.getValue().floatValue()))
                : 0.8F;
    }

    private static long getDurationMillis() {
        Setting<Double> duration = durationSetting;
        return duration != null && duration.getValue() != null
                ? Math.round(Math.max(1.0, Math.min(10.0, duration.getValue())) * 1000.0)
                : 2000L;
    }

    private static void playSound() {
        Setting<Boolean> sounds = soundsSetting;
        if (sounds != null && Boolean.TRUE.equals(sounds.getValue())) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getSoundManager() != null) {
                try {
                    SoundEvent sound = SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
                    if (sound != null) {
                        client.getSoundManager()
                                .play(PositionedSoundInstance.ui(sound, 1.0F, getSoundVolume()));
                    }
                } catch (RuntimeException e) {
                }
            }
        }
    }

    private static float getSoundVolume() {
        Setting<Double> volume = volumeSetting;
        return volume != null && volume.getValue() != null
                ? 0.8F * Math.max(0.0F, Math.min(1.0F, volume.getValue().floatValue() / 100.0F))
                : 1.0F;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record Notification(
            String title,
            boolean enabled,
            String icon,
            long startedAt,
            long durationMillis,
            boolean overMenu,
            String message) {}

    private record PendingToggle(Module module, boolean oldState, boolean requestedState) {}

    record IconEntry(ItemStack icon, boolean donut) {}

    record ItemIconEntry(ItemStack item, boolean donut) {}

    record ModuleLabel(String displayName, String category) {}
}
