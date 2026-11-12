package odinclient.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

@Environment(EnvType.CLIENT)
public final class PingUtil {
    private PingUtil() {}

    public static int getPing(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player != null && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
            return entry == null ? 0 : entry.getLatency();
        } else {
            return 0;
        }
    }
}
