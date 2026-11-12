package odinclient.module.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Util;

import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.event.TickEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.hud.ItemToastRenderer;

import java.util.ArrayDeque;

@Environment(EnvType.CLIENT)
public final class WeatherNotifier extends Module {
    private static final Category CATEGORY = findMainCategory();
    private final ArrayDeque<WeatherNotifier.WeatherState> pendingNotifications =
            new ArrayDeque<>();
    private WeatherNotifier.WeatherState lastWeather;
    private WeatherNotifier.WeatherState currentNotification;
    private long notificationStartMs;
    private boolean weatherInitialized;
    private final Setting<Integer> displaySeconds =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("display-seconds")
                                    .description(
                                            "How long each weather notification remains visible.")
                                    .defaultValue(5)
                                    .range(1, 15)
                                    .sliderRange(1, 15)
                                    .build());
    private final Setting<Integer> xPosition =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("x-position")
                                    .description("Horizontal notification position.")
                                    .defaultValue(50)
                                    .range(0, 100)
                                    .sliderRange(0, 100)
                                    .build());
    private final Setting<Integer> yPosition =
            this.settings
                    .getGeneral()
                    .add(
                            new IntSetting.Builder()
                                    .name("y-position")
                                    .description("Vertical notification position.")
                                    .defaultValue(2)
                                    .range(0, 100)
                                    .sliderRange(0, 100)
                                    .build());
    private final Setting<Double> size;

    public WeatherNotifier() {
        super(CATEGORY, "weather-notifier", "Shows a notification whenever the weather changes.");
        this.size =
                this.settings
                        .getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("size")
                                        .description("Overall notification size.")
                                        .defaultValue(1.0)
                                        .range(0.5, 2.0)
                                        .sliderRange(0.5, 2.0)
                                        .build());
    }

    private static Category findMainCategory() {
        try {
            return (Category)
                    Class.forName("odinclient.core.Categories").getField("MAIN").get(null);
        } catch (Exception e) {
            return new Category("OdinClient", ItemStack.EMPTY);
        }
    }

    @Override
    public void onActivate() {
        this.reset();
        this.captureCurrentWeather();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    @EventHandler
    private void onWorldJoin(WorldJoinEvent event) {
        this.reset();
    }

    @EventHandler
    private void onWorldLeave(WorldLeaveEvent event) {
        this.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world == null) {
            this.weatherInitialized = false;
            this.lastWeather = null;
        } else {
            WeatherNotifier.WeatherState weather = this.getCurrentWeather();
            if (!this.weatherInitialized) {
                this.lastWeather = weather;
                this.weatherInitialized = true;
            } else if (weather != this.lastWeather) {
                this.lastWeather = weather;
                this.pendingNotifications.addLast(weather);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        long now = Util.getMeasuringTimeMs();
        long durationMs = this.displaySeconds.getValue().intValue() * 1000L;
        if (this.currentNotification != null && now - this.notificationStartMs >= durationMs) {
            this.currentNotification = null;
        }

        if (this.currentNotification == null) {
            this.currentNotification = this.pendingNotifications.pollFirst();
            this.notificationStartMs = now;
        }

        if (this.currentNotification != null
                && this.mc.currentScreen == null
                && !this.mc.getDebugHud().shouldShowDebugHud()) {
            float alpha =
                    Math.max(
                            0.0F,
                            Math.min(
                                    1.0F,
                                    1.0F
                                            - (float) (now - this.notificationStartMs)
                                                    / (float) Math.max(1L, durationMs)));
            ItemToastRenderer.drawAnchoredToast(
                    event.context,
                    getIconStack(this.currentNotification),
                    this.currentNotification.title,
                    this.currentNotification.message,
                    event.scaledWidth,
                    event.scaledHeight,
                    this.xPosition.getValue(),
                    this.yPosition.getValue(),
                    this.size.getValue().floatValue(),
                    alpha);
        }
    }

    private void captureCurrentWeather() {
        if (this.mc.world != null) {
            this.lastWeather = this.getCurrentWeather();
            this.weatherInitialized = true;
        }
    }

    private WeatherNotifier.WeatherState getCurrentWeather() {
        if (this.mc.world.isThundering()) {
            return WeatherNotifier.WeatherState.THUNDER;
        } else {
            return this.mc.world.isRaining()
                    ? WeatherNotifier.WeatherState.RAIN
                    : WeatherNotifier.WeatherState.CLEAR;
        }
    }

    private static ItemStack getIconStack(WeatherNotifier.WeatherState weather) {
        return switch (weather) {
            case CLEAR -> new ItemStack(Items.SUNFLOWER);
            case RAIN -> new ItemStack(Items.WATER_BUCKET);
            case THUNDER -> new ItemStack(Items.LIGHTNING_ROD);
        };
    }

    private void reset() {
        this.pendingNotifications.clear();
        this.currentNotification = null;
        this.notificationStartMs = 0L;
        this.lastWeather = null;
        this.weatherInitialized = false;
    }

    private static enum WeatherState {
        CLEAR("Clear", "The weather cleared"),
        RAIN("Rain", "Started raining"),
        THUNDER("Thunder", "Thunderstorm started");

        private final String title;
        private final String message;

        private WeatherState(String title, String message) {
            this.title = title;
            this.message = message;
        }
    }
}
