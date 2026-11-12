package odinclient.module.discord;

import odinclient.core.Categories;
import odinclient.module.Module;

public final class DiscordPresents extends Module {
    public DiscordPresents() {
        super(
                Categories.MAIN,
                "discord-presence",
                "Shows OdinClient as your Discord Rich Presence.");
    }

    @Override
    public void onActivate() {
        DiscordIpcClient.start();
    }

    @Override
    public void onDeactivate() {
        DiscordIpcClient.stop();
    }
}
