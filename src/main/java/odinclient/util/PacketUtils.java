package odinclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

/**
 * Single choke point for packets sent by modules.
 *
 * <p>Sends are rejected off the client thread and when no network handler is attached, so a module
 * can never push a packet from a worker thread or during disconnect.
 */
public final class PacketUtils {

    private PacketUtils() {}

    public static boolean send(Packet<?> packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || packet == null) {
            return false;
        }

        if (!client.isOnThread()) {
            return false;
        }

        if (client.getNetworkHandler() == null) {
            return false;
        }

        client.getNetworkHandler().sendPacket(packet);
        return true;
    }

    public static boolean canSend() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.isOnThread() && client.getNetworkHandler() != null;
    }
}
