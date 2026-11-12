package odinclient.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
public final class InteractItemEvent {
    public final Hand hand;

    public InteractItemEvent(Hand hand) {
        this.hand = hand;
    }
}
