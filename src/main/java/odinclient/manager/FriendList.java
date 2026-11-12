package odinclient.manager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class FriendList {
    private static final FriendList INSTANCE = new FriendList();
    private final Set<String> friendNames = ConcurrentHashMap.newKeySet();

    private FriendList() {}

    public static FriendList getInstance() {
        return INSTANCE;
    }

    public boolean isFriend(PlayerEntity player) {
        return player != null
                && this.friendNames.contains(player.getName().getString().toLowerCase());
    }
}
