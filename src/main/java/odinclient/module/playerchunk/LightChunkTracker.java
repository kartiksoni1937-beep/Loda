package odinclient.module.playerchunk;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.util.math.ChunkPos;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.ModuleContext;
import odinclient.util.ScannerExecutor;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LightChunkTracker {
    private static final LightChunkTracker INSTANCE = new LightChunkTracker();
    private static final ArrayBlockingQueue<LightChunkTracker.RawLightChunk> rawQueue =
            new ArrayBlockingQueue<>(8192);
    private static final ArrayBlockingQueue<LightChunkTracker.ProcessedLightChunk> processedQueue =
            new ArrayBlockingQueue<>(8192);
    private static final AtomicBoolean processing = new AtomicBoolean();
    private static final AtomicBoolean registered = new AtomicBoolean();
    private static final int MAX_PROCESSED_PER_TICK = 4096;
    private static final int MAX_RAW_PER_BATCH = 64;
    private static final int MAX_CHUNK_DISTANCE = 34;
    private static final int MIN_WORLD_Y = -64;
    private static final int MAX_WORLD_Y = 184;
    private static final int MAX_SECTION_TOP_Y = 62;
    private static final ExecutorService executor = ScannerExecutor.getExecutor();
    private final Map<Long, Long> foundChunks = new HashMap<>();
    private Object currentWorld;
    private int tickCounter;
    private long version;
    private volatile boolean enabled;

    private LightChunkTracker() {}

    public static void register() {
        if (registered.compareAndSet(false, true)) {
            ModuleContext.EVENT_BUS.register(INSTANCE);
        }
    }

    public static void onLightData(int chunkX, int chunkZ, LightData lightData) {
        if (INSTANCE.enabled && lightData != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientWorld world = client == null ? null : client.world;
            if (world != null) {
                try {
                    BitSet initializedSections = (BitSet) lightData.getInitedSky().clone();
                    ArrayList<byte[]> payloads = new ArrayList<>(lightData.getSkyNibbles());
                    offerDroppingOldest(
                            rawQueue,
                            new LightChunkTracker.RawLightChunk(
                                    chunkX,
                                    chunkZ,
                                    initializedSections,
                                    payloads,
                                    System.nanoTime(),
                                    world));
                    scheduleProcessing();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static Map<Long, Long> getChunksNear(int centerX, int centerZ, int radius) {
        synchronized (INSTANCE) {
            if (!INSTANCE.enabled) {
                return Map.of();
            } else {
                Map<Long, Long> result = new HashMap<>();

                for (Entry<Long, Long> entry : INSTANCE.foundChunks.entrySet()) {
                    int x = ChunkPos.getPackedX(entry.getKey());
                    int z = ChunkPos.getPackedZ(entry.getKey());
                    if (Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                }

                return result;
            }
        }
    }

    public static long getVersion() {
        synchronized (INSTANCE) {
            return INSTANCE.version;
        }
    }

    public static synchronized void reset() {
        INSTANCE.clearAll();
    }

    public static void setEnabled(boolean value) {
        synchronized (INSTANCE) {
            if (INSTANCE.enabled != value) {
                INSTANCE.enabled = value;
                INSTANCE.clearAll();
                if (value) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    INSTANCE.currentWorld = client == null ? null : client.world;
                }
            }
        }
    }

    private static void scheduleProcessing() {
        if (INSTANCE.enabled && processing.compareAndSet(false, true)) {
            executor.execute(LightChunkTracker::processPending);
        }
    }

    private static void processPending() {
        try {
            int processed = 0;

            LightChunkTracker.RawLightChunk raw;
            while (INSTANCE.enabled && processed++ < 64 && (raw = rawQueue.poll()) != null) {
                int[] zeroSections = findZeroSections(raw.initializedSections, raw.payloads);
                if (zeroSections.length > 0) {
                    offerDroppingOldest(
                            processedQueue,
                            new LightChunkTracker.ProcessedLightChunk(
                                    raw.chunkX,
                                    raw.chunkZ,
                                    zeroSections,
                                    raw.arrivalNanos,
                                    raw.worldToken));
                }
            }
        } finally {
            processing.set(false);
            if (INSTANCE.enabled && !rawQueue.isEmpty()) {
                scheduleProcessing();
            }
        }
    }

    private static int[] findZeroSections(BitSet initializedSections, List<byte[]> payloads) {
        int[] zeroSections = new int[Math.min(initializedSections.cardinality(), 32)];
        int count = 0;
        int payloadIndex = 0;

        for (int section = initializedSections.nextSetBit(0);
                section >= 0;
                section = initializedSections.nextSetBit(section + 1)) {
            byte[] payload = payloadIndex < payloads.size() ? payloads.get(payloadIndex) : null;
            payloadIndex++;
            if (LightNibbleUtil.isAllZero(payload)) {
                if (count == zeroSections.length) {
                    int newLength = Math.max(1, zeroSections.length * 2);
                    int[] grown = new int[newLength];
                    System.arraycopy(zeroSections, 0, grown, 0, zeroSections.length);
                    zeroSections = grown;
                }

                zeroSections[count++] = section;
            }
        }

        if (count == zeroSections.length) {
            return zeroSections;
        } else {
            int[] trimmed = new int[count];
            System.arraycopy(zeroSections, 0, trimmed, 0, count);
            return trimmed;
        }
    }

    private static <T> void offerDroppingOldest(ArrayBlockingQueue<T> queue, T item) {
        if (!queue.offer(item)) {
            queue.poll();
            queue.offer(item);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.enabled) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientWorld world = client.world;
            if (world != this.currentWorld) {
                synchronized (this) {
                    this.clearChunks();
                    this.currentWorld = world;
                    if (world == null) {
                        rawQueue.clear();
                        processedQueue.clear();
                    }
                }
            }

            if (client.world != null) {
                int sectionCount = client.world.countVerticalSections();
                int bottomSection = client.world.getBottomSectionCoord();

                for (int i = 0; i < 4096; i++) {
                    LightChunkTracker.ProcessedLightChunk chunk = processedQueue.poll();
                    if (chunk == null) {
                        break;
                    }

                    this.acceptProcessed(chunk, sectionCount, bottomSection);
                }

                if (++this.tickCounter >= 100 && client.player != null) {
                    this.tickCounter = 0;
                    ChunkPos chunkPos = client.player.getChunkPos();
                    this.pruneDistant(chunkPos.x, chunkPos.z);
                }
            }
        }
    }

    private synchronized void acceptProcessed(
            LightChunkTracker.ProcessedLightChunk chunk, int sectionCount, int bottomSection) {
        if (chunk.worldToken == this.currentWorld
                && isInScanRange(chunk.zeroSections, sectionCount, bottomSection)) {
            long packed = ChunkPos.toLong(chunk.chunkX, chunk.chunkZ);
            Long previous = this.foundChunks.putIfAbsent(packed, chunk.arrivalNanos);
            if (previous == null) {
                this.version++;
                PlayerFinder.reportChunkDetection(chunk.chunkX, chunk.chunkZ, chunk.arrivalNanos);
            }
        }
    }

    private static boolean isInScanRange(int[] zeroSections, int sectionCount, int bottomSection) {
        int limit = sectionCount + 1;

        for (int section : zeroSections) {
            if (section >= 1 && section < limit) {
                int minY = (bottomSection + section - 1) * 16;
                int maxY = minY + 15;
                if (maxY >= -64 && minY <= 184 && maxY < 62) {
                    return true;
                }
            }
        }

        return false;
    }

    private synchronized void pruneDistant(int centerX, int centerZ) {
        if (this.foundChunks.keySet().removeIf(packed -> isTooFar(packed, centerX, centerZ))) {
            this.version++;
        }
    }

    private static boolean isTooFar(long packed, int centerX, int centerZ) {
        int x = ChunkPos.getPackedX(packed);
        int z = ChunkPos.getPackedZ(packed);
        return Math.abs(x - centerX) > 34 || Math.abs(z - centerZ) > 34;
    }

    private synchronized void clearAll() {
        this.clearChunks();
        this.currentWorld = null;
        rawQueue.clear();
        processedQueue.clear();
    }

    private void clearChunks() {
        this.foundChunks.clear();
        this.tickCounter = 0;
        this.version++;
    }

    private record ProcessedLightChunk(
            int chunkX, int chunkZ, int[] zeroSections, long arrivalNanos, Object worldToken) {}

    private record RawLightChunk(
            int chunkX,
            int chunkZ,
            BitSet initializedSections,
            List<byte[]> payloads,
            long arrivalNanos,
            Object worldToken) {}
}
