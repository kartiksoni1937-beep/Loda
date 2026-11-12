package odinclient.mixin.scanner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.module.world.AmethystESP;
import odinclient.util.ScannerExecutor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;

@Mixin(AmethystESP.class)
public abstract class AmethystScanGuardMixin {
    private static final Map<Long, WeakReference<WorldChunk>> ODINCLIENT_SCANNED_CHUNKS =
            new ConcurrentHashMap<>();
    private static volatile Object odinclient$worldToken;
    private static int odinclient$pruneCounter;
    @Unique private int odinclient$sharedSliceRemaining;

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/Executors;newSingleThreadExecutor(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"),
            remap = false)
    private ExecutorService odinclient$useUnifiedScanner(ThreadFactory factory) {
        return ScannerExecutor.getExecutor();
    }

    @Inject(method = "drainScanQueue", at = @At("HEAD"), remap = false)
    private void odinclient$beginSharedSlice(CallbackInfo ci) {
        this.odinclient$sharedSliceRemaining = 4;
    }

    @Redirect(
            method = "drainScanQueue",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/LinkedBlockingQueue;poll()Ljava/lang/Object;"),
            remap = false)
    private Object odinclient$pollSharedSlice(LinkedBlockingQueue<?> queue) {
        return this.odinclient$sharedSliceRemaining-- > 0 ? queue.poll() : null;
    }

    @Redirect(
            method = "lambda$new$0",
            at = @At(value = "INVOKE", target = "Ljava/lang/Thread;setPriority(I)V"),
            remap = false)
    private static void odinclient$useBackgroundScanPriority(Thread thread, int priority) {
        thread.setPriority(1);
    }

    @Inject(method = "enqueueChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private void odinclient$skipUnchangedChunk(int chunkX, int chunkZ, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        if (world == null) {
            ci.cancel();
            return;
        }

        if (world != odinclient$worldToken) {
            synchronized (ODINCLIENT_SCANNED_CHUNKS) {
                if (world != odinclient$worldToken) {
                    ODINCLIENT_SCANNED_CHUNKS.clear();
                    odinclient$worldToken = world;
                    odinclient$pruneCounter = 0;
                }
            }
        }

        WorldChunk chunk = client.world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            ci.cancel();
            return;
        }

        long key = ChunkPos.toLong(chunkX, chunkZ);
        WeakReference<WorldChunk> ref = ODINCLIENT_SCANNED_CHUNKS.get(key);
        if (ref != null && ref.get() == chunk) {
            ci.cancel();
            return;
        }

        ODINCLIENT_SCANNED_CHUNKS.put(key, new WeakReference<>(chunk));
        if (++odinclient$pruneCounter >= 2048 && client.player != null) {
            odinclient$pruneCounter = 0;
            ChunkPos playerChunk = client.player.getChunkPos();
            ODINCLIENT_SCANNED_CHUNKS
                    .entrySet()
                    .removeIf(
                            entry -> {
                                if (entry.getValue().get() == null) {
                                    return true;
                                }

                                int x = ChunkPos.getPackedX(entry.getKey());
                                int z = ChunkPos.getPackedZ(entry.getKey());
                                return Math.abs(x - playerChunk.x) > 40
                                        || Math.abs(z - playerChunk.z) > 40;
                            });
        }
    }

    @Inject(
            method = {"onActivate", "onDeactivate"},
            at = @At("HEAD"),
            remap = false)
    private void odinclient$resetChunkScanCache(CallbackInfo ci) {
        ODINCLIENT_SCANNED_CHUNKS.clear();
        odinclient$pruneCounter = 0;
        odinclient$worldToken = MinecraftClient.getInstance().world;
    }
}
