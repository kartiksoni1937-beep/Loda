package odinclient.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface EventBus {
    void register(Object listener);

    void unregister(Object listener);

    <T> T post(T event);
}
