package odinclient.module.automation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.module.Module;
import odinclient.module.privacy.PlayerFilter;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;

@Environment(EnvType.CLIENT)
public final class AutoLog extends Module {
    private static AutoLog instance;
    private boolean loggedOut;
    private final Setting<Boolean> logoutOnPlayer;
    private final Setting<Boolean> logoutOnTotemPop;
    private final Setting<Boolean> logoutOnAntiVanish;

    public AutoLog() {
        super(
                Categories.MAIN,
                "autolog",
                "Disconnects on players, your totem pop or AntiVanish detection.");
        this.title = "AutoLog";
        instance = this;
        SettingGroup general = this.settings.getGeneral();
        this.logoutOnPlayer =
                general.add(
                        new BooleanSetting.Builder()
                                .name("logout-on-player")
                                .description(
                                        "Disconnect immediately when another loaded player is"
                                            + " detected.")
                                .defaultValue(true)
                                .build());
        this.logoutOnTotemPop =
                general.add(
                        new BooleanSetting.Builder()
                                .name("logout-on-totem-pop")
                                .description("Disconnect when your own player pops a totem.")
                                .defaultValue(false)
                                .build());
        this.logoutOnAntiVanish =
                general.add(
                        new BooleanSetting.Builder()
                                .name("logout-on-antivanish-detection")
                                .description(
                                        "Disconnect on confirmed AntiVanish detection, even when"
                                            + " its display module is off.")
                                .defaultValue(false)
                                .build());
    }

    @Override
    public void onActivate() {
        this.loggedOut = false;
        this.checkForPlayers();
    }

    @Override
    public void onDeactivate() {
        this.loggedOut = false;
    }

    public static boolean isLoggedOut() {
        return instance != null && instance.isActive() && instance.loggedOut;
    }

    @EventHandler
    private void onWorldJoin(WorldJoinEvent event) {
        this.loggedOut = false;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        this.checkForPlayers();
    }

    private void checkForPlayers() {
        if (Boolean.TRUE.equals(this.logoutOnPlayer.getValue())
                && !this.loggedOut
                && this.mc.world != null
                && this.mc.player != null
                && this.mc.getNetworkHandler() != null) {
            PlayerEntity closest = null;

            for (PlayerEntity player : this.mc.world.getPlayers()) {
                if (PlayerFilter.isOtherPlayer(this.mc, player)
                        && (closest == null
                                || player.distanceTo(this.mc.player)
                                        < closest.distanceTo(this.mc.player))) {
                    closest = player;
                }
            }

            if (closest != null) {
                this.disconnect("Player detected (" + closest.getGameProfile().name() + ")");
            }
        }
    }

    public static boolean isAntiVanishLogoutActive() {
        return instance != null
                && instance.isActive()
                && !instance.loggedOut
                && instance.logoutOnAntiVanish.getValue();
    }

    public static void onTotemPop() {
        if (instance != null && instance.isActive() && instance.logoutOnTotemPop.getValue()) {
            instance.disconnect("Totem popped");
        }
    }

    public static void onAntiVanishDetected(String player, String reason) {
        if (isAntiVanishLogoutActive()) {
            instance.disconnect("Antivanish detected (" + player + "): " + reason);
        }
    }

    private void disconnect(String reason) {
        if (!this.loggedOut
                && this.mc.world != null
                && this.mc.player != null
                && this.mc.getNetworkHandler() != null) {
            this.loggedOut = true;
            this.mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
        }
    }
}
