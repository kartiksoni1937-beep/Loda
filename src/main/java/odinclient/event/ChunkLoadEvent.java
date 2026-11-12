package odinclient.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.chunk.WorldChunk;

@Environment(EnvType.CLIENT)
public final class ChunkLoadEvent {
    private final WorldChunk chunk;

    public ChunkLoadEvent(WorldChunk chunk) {
        this.chunk = chunk;
    }

    public WorldChunk getChunk() {
        return this.chunk;
    }
}
