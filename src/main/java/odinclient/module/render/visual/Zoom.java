package odinclient.module.render.visual;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.KeybindSetting;
import odinclient.util.InventoryActions;

import java.util.ArrayList;
import java.util.List;

public final class Zoom extends Module {
    private static final double MIN_ZOOM = 0.03;
    private static final double MAX_ZOOM = 1.0;
    private static volatile Zoom instance;
    private final SettingGroup generalSettings = this.settings.getGeneral();
    private final Setting<Integer> activationKey =
            this.generalSettings.add(
                    new KeybindSetting.Builder()
                            .name("activation-key")
                            .description(
                                    "Hold this key to zoom, or press it once when Toggle is"
                                        + " enabled.")
                            .defaultValue(67)
                            .build());
    private final Setting<Integer> scrollSensitivity =
            this.generalSettings.add(
                    new IntSetting.Builder()
                            .name("scroll-sensitivity")
                            .description("Controls how quickly the scroll wheel zooms in and out.")
                            .defaultValue(5)
                            .range(1, 20)
                            .sliderRange(1, 20)
                            .build());
    private final Setting<Integer> sensitivity =
            this.generalSettings.add(
                    new IntSetting.Builder()
                            .name("sensitivity")
                            .description(
                                    "Controls how quickly you can look around while zoomed in.")
                            .defaultValue(50)
                            .range(1, 100)
                            .sliderRange(1, 100)
                            .build());
    private final Setting<Boolean> toggleMode =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("toggle")
                            .description(
                                    "Press once to keep zoom active and press again to stop"
                                        + " zooming.")
                            .defaultValue(false)
                            .build());
    private volatile boolean zooming;
    private volatile double zoomFactor = 1.0;
    private boolean wasKeyDown;

    public Zoom() {
        super(Categories.MAIN, "zoom", "Scroll-wheel zoom with hold and toggle activation modes.");
        this.title = "Zoom";
        instance = this;
    }

    @Override
    public void onActivate() {
        this.zooming = false;
        this.zoomFactor = 1.0;
        this.wasKeyDown = InventoryActions.isInputPressed(this.mc, this.activationKey.getValue());
    }

    @Override
    public void onDeactivate() {
        this.zooming = false;
        this.zoomFactor = 1.0;
        this.wasKeyDown = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean keyDown = InventoryActions.isInputPressed(this.mc, this.activationKey.getValue());
        if (this.mc.currentScreen != null) {
            if (!Boolean.TRUE.equals(this.toggleMode.getValue())) {
                this.zooming = false;
            }

            this.wasKeyDown = keyDown;
        } else {
            if (Boolean.TRUE.equals(this.toggleMode.getValue())) {
                if (keyDown && !this.wasKeyDown) {
                    this.zooming = !this.zooming;
                }
            } else {
                this.zooming = keyDown;
            }

            this.wasKeyDown = keyDown;
        }
    }

    public static boolean onScroll(double amount) {
        Zoom zoom = instance;
        if (zoom == null || !zoom.isActive() || !zoom.zooming || amount == 0.0) {
            return false;
        } else if (zoom.mc.currentScreen != null) {
            return false;
        } else {
            double steps =
                    Math.max(-10.0, Math.min(10.0, amount))
                            * (Math.max(1, zoom.scrollSensitivity.getValue()) / 5.0);
            zoom.zoomFactor =
                    Math.max(0.03, Math.min(1.0, zoom.zoomFactor * Math.pow(0.85, steps)));
            return true;
        }
    }

    public static double scaleMouseDelta(double delta) {
        Zoom zoom = instance;
        if (zoom != null && zoom.isActive() && zoom.zooming) {
            return delta * (Math.max(1, Math.min(100, zoom.sensitivity.getValue())) / 100.0);
        } else {
            return delta;
        }
    }

    public static float applyZoomFov(float fov) {
        Zoom zoom = instance;
        return zoom != null && zoom.isActive() && zoom.zooming
                ? (float) (fov * zoom.zoomFactor)
                : fov;
    }

    public static String[] appendModuleName(String[] names) {
        ArrayList<String> result = new ArrayList<>(List.of(names));
        if (!result.contains("Zoom")) {
            result.add("Zoom");
        }

        return result.toArray(String[]::new);
    }
}
