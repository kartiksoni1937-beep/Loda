package odinclient.module.antivanish;

import odinclient.core.Categories;
import odinclient.module.Module;
import odinclient.module.automation.AutoLog;

public final class AntiVanishDetection extends Module {
    private static AntiVanishDetection instance;

    public AntiVanishDetection() {
        super(
                Categories.MAIN,
                "antivanish-detection",
                "Detects hidden staff using TAB changes and corroborated local activity.");
        this.title = "AntiVanish Detection";
        instance = this;
    }

    public static boolean isEnabled() {
        return instance != null && instance.isActive();
    }

    @Override
    public void onActivate() {
        AntiVanishActivityMonitor.refreshState();
    }

    @Override
    public void onDeactivate() {
        AntiVanishActivityMonitor.refreshState();
    }

    public static void onDetection(VanishTracker.VanishAlert alert) {
        if (isEnabled()) {
            instance.warning(
                    "Antivanish: " + alert.reason() + " (" + alert.name() + ")", new Object[0]);
        }

        if (alert.confirmed()) {
            AutoLog.onAntiVanishDetected(alert.name(), alert.reason());
        }
    }
}
