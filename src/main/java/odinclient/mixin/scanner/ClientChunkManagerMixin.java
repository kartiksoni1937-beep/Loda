package odinclient.mixin.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData.BlockEntityVisitor;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.event.ChunkLoadEvent;
import odinclient.module.ModuleContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerMixin {
    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void odinclient$chunkData(
            int chunkX,
            int chunkZ,
            PacketByteBuf buf,
            Map<Type, long[]> heightmaps,
            Consumer<BlockEntityVisitor> consumer,
            CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk chunk = cir.getReturnValue();
        if (chunk != null) {
            ModuleContext.EVENT_BUS.post(new ChunkLoadEvent(chunk));
        }
    }
}
