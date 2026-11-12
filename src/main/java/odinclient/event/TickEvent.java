package odinclient.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class TickEvent {
    private TickEvent() {}

    @Environment(EnvType.CLIENT)
    public static final class Post extends TickEvent {}

    @Environment(EnvType.CLIENT)
    public static final class Pre extends TickEvent {}
}
