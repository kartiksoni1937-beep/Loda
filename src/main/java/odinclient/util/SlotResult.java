package odinclient.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SlotResult {
    private final int slot;

    public SlotResult(int slot) {
        this.slot = slot;
    }

    public boolean isFound() {
        return this.slot >= 0;
    }

    public int getSlot() {
        return this.slot;
    }
}
