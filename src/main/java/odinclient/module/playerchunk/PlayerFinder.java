package odinclient.module.playerchunk;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.module.hud.ThemeModule;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.IntSetting;
import odinclient.ui.screen.ArtworkGalleryScreen;
import odinclient.ui.screen.ClickGuiScreen;
import odinclient.ui.screen.ConfigScreen;
import odinclient.ui.screen.HudPositionEditorScreen;
import odinclient.ui.screen.IgnoredPlayersScreen;
import odinclient.ui.screen.OdinGuiScreen;
import odinclient.ui.screen.SkySelectionScreen;
import odinclient.util.ChatUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

@Environment(EnvType.CLIENT)
public final class PlayerFinder extends Module {
    private static final int SCAN_RADIUS = 32;
    private static final int MAX_TRACERS = 64;
    private static final double MARKER_HEIGHT = 64.02;
    private static final long FADE_IN_NANOS = 650000000L;
    private static final long RTP_DELAY_UNIT_NANOS = 10000000L;
    private static final int MIN_RTP_DELAY = 40;
    private static final int MAX_RTP_DELAY = 300;
    private static volatile PlayerFinder instance;
    private final Setting<Boolean> tracers;
    private final Setting<EntityColor> markerColor;
    private final Setting<Boolean> autoRtp;
    private final Setting<Integer> rtpDelay;
    private final Setting<Boolean> east;
    private final Setting<Boolean> west;
    private final Setting<Boolean> euWest;
    private final Setting<Boolean> euCentral;
    private final Setting<Boolean> asia;
    private final Setting<Boolean> oceania;
    private final ConcurrentLinkedQueue<PlayerFinder.ChunkDetection> pendingDetections =
            new ConcurrentLinkedQueue<>();
    private final Set<Long> knownChunks = new HashSet<>();
    private Map<Long, Long> detectedChunks = Map.of();
    private List<PlayerFinder.ChunkRegion> chunkRegions = List.of();
    private List<Long> nearestChunks = List.of();
    private long cachedRevision = -9223372036854775808L;
    private int cachedCenterX = -2147483648;
    private int cachedCenterZ = -2147483648;
    private boolean rtpRunning;
    private boolean wasAutoRtpEnabled;
    private boolean warnedNoRegions;
    private int regionIndex;
    private long nextRtpNanos;
    private long pausedSinceNanos;
    private long rtpStartChunk;
    private boolean awaitingTeleport;
    private boolean chunkFoundHandled;

    public PlayerFinder(Category category) {
        super(
                category,
                "player-chunk",
                "Detects player activity and highlights suspected locations by chunk.");
        this.title = "Playerfinder v1";
        SettingGroup general = this.settings.getGeneral();
        this.tracers =
                general.add(
                        new BooleanSetting.Builder()
                                .name("tracers")
                                .description(
                                        "Draw tracers to the exact center of detected chunk"
                                            + " markers.")
                                .defaultValue(false)
                                .build());
        this.markerColor =
                general.add(
                        new ColorSetting.Builder()
                                .name("marker-color")
                                .description("Color and opacity of the animated chunk markings.")
                                .defaultValue(new EntityColor(176, 72, 255, 105))
                                .build());
        SettingGroup autoRtpGroup = this.settings.addGroup("Auto RTP");
        this.autoRtp =
                autoRtpGroup.add(
                        new BooleanSetting.Builder()
                                .name("auto-rtp")
                                .description(
                                        "Cycle through enabled Donut RTP regions until a player"
                                            + " chunk is detected.")
                                .defaultValue(false)
                                .build());
        this.rtpDelay =
                autoRtpGroup.add(
                        new IntSetting.Builder()
                                .name("rtp-delay")
                                .description(
                                        "Delay after each RTP command. 60 equals 0.6 seconds; the"
                                            + " maximum of 300 equals 3 seconds.")
                                .defaultValue(60)
                                .range(40, 300)
                                .sliderRange(40, 300)
                                .build());
        this.east = autoRtpGroup.add(createRegionSetting("east", "Use the East RTP region.", true));
        this.west =
                autoRtpGroup.add(createRegionSetting("west", "Use the West RTP region.", false));
        this.euWest =
                autoRtpGroup.add(
                        createRegionSetting("eu-west", "Use the EU West RTP region.", false));
        this.euCentral =
                autoRtpGroup.add(
                        createRegionSetting("eu-central", "Use the EU Central RTP region.", false));
        this.asia =
                autoRtpGroup.add(createRegionSetting("asia", "Use the Asia RTP region.", false));
        this.oceania =
                autoRtpGroup.add(
                        createRegionSetting("oceania", "Use the Oceania RTP region.", false));
        instance = this;
    }

    private static Setting<Boolean> createRegionSetting(
            String name, String description, boolean defaultValue) {
        return new BooleanSetting.Builder()
                .name(name)
                .description(description)
                .defaultValue(defaultValue)
                .build();
    }

    public static void reportChunkDetection(int chunkX, int chunkZ, long detectedAtNanos) {
        PlayerFinder finder = instance;
        if (finder != null && finder.isActive()) {
            finder.pendingDetections.offer(
                    new PlayerFinder.ChunkDetection(chunkX, chunkZ, detectedAtNanos));
        }
    }

    @Override
    public void onActivate() {
        LightChunkTracker.setEnabled(true);
        this.pendingDetections.clear();
        this.knownChunks.clear();
        this.rtpRunning = false;
        this.wasAutoRtpEnabled = false;
        this.warnedNoRegions = false;
        this.regionIndex = 0;
        this.nextRtpNanos = 0L;
        this.pausedSinceNanos = 0L;
        this.rtpStartChunk = 0L;
        this.awaitingTeleport = false;
        this.chunkFoundHandled = false;
        this.clearCache();
    }

    @Override
    public void onDeactivate() {
        LightChunkTracker.setEnabled(false);
        this.rtpRunning = false;
        this.wasAutoRtpEnabled = false;
        this.pendingDetections.clear();
        this.nextRtpNanos = 0L;
        this.pausedSinceNanos = 0L;
        this.rtpStartChunk = 0L;
        this.awaitingTeleport = false;
        this.chunkFoundHandled = false;
        this.clearCache();
    }

    public String getHudInfo() {
        if (this.mc.player == null) {
            return null;
        } else {
            ChunkPos chunkPos = this.mc.player.getChunkPos();
            int count = this.getNearbyChunks(chunkPos).size();
            return count == 0 ? null : "Chunks:" + count;
        }
    }

    @EventHandler
    private void onJoin(WorldJoinEvent event) {
        this.pendingDetections.clear();
        this.knownChunks.clear();
        this.regionIndex = 0;
        this.nextRtpNanos = 0L;
        this.pausedSinceNanos = 0L;
        this.rtpStartChunk = 0L;
        this.awaitingTeleport = false;
        this.chunkFoundHandled = false;
        this.clearCache();
    }

    @EventHandler
    private void onLeave(WorldLeaveEvent event) {
        this.pendingDetections.clear();
        this.knownChunks.clear();
        this.rtpRunning = false;
        this.wasAutoRtpEnabled = false;
        this.nextRtpNanos = 0L;
        this.pausedSinceNanos = 0L;
        this.rtpStartChunk = 0L;
        this.awaitingTeleport = false;
        this.clearCache();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        this.processDetections();
        this.updateAutoRtp();
    }

    private void processDetections() {
        if (this.mc.player != null) {
            ChunkPos chunkPos = this.mc.player.getChunkPos();
            Map<Long, Long> nearby = this.getNearbyChunks(chunkPos);

            for (Entry<Long, Long> entry : nearby.entrySet()) {
                if (this.knownChunks.add(entry.getKey())) {
                    this.onChunkFound(
                            ChunkPos.getPackedX(entry.getKey()),
                            ChunkPos.getPackedZ(entry.getKey()));
                }
            }

            PlayerFinder.ChunkDetection detection;
            while ((detection = this.pendingDetections.poll()) != null) {
                long packed = ChunkPos.toLong(detection.chunkX, detection.chunkZ);
                if (this.knownChunks.add(packed)) {
                    this.onChunkFound(detection.chunkX, detection.chunkZ);
                }
            }

            this.knownChunks.retainAll(nearby.keySet());
        }
    }

    private void onChunkFound(int chunkX, int chunkZ) {
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        this.warning("Chunk found at %d, %d", blockX, blockZ);
        boolean autoRtpActive = this.rtpRunning || Boolean.TRUE.equals(this.autoRtp.getValue());
        if (autoRtpActive && !this.chunkFoundHandled) {
            this.chunkFoundHandled = true;
            this.rtpRunning = false;
            this.wasAutoRtpEnabled = false;
            this.nextRtpNanos = 0L;
            this.pausedSinceNanos = 0L;
            this.awaitingTeleport = false;
            this.autoRtp.setValue(false);
            this.warning("Auto RTP stopped after detecting a player chunk.");
            if (this.mc.getNetworkHandler() != null
                    && this.mc.getNetworkHandler().getConnection() != null) {
                this.mc.getNetworkHandler().getConnection().disconnect(Text.of("Chunk found"));
            }
        }
    }

    private void updateAutoRtp() {
        long now = System.nanoTime();
        boolean autoRtpEnabled = Boolean.TRUE.equals(this.autoRtp.getValue());
        if (autoRtpEnabled && !this.wasAutoRtpEnabled) {
            this.rtpRunning = true;
            this.regionIndex = 0;
            this.warnedNoRegions = false;
            this.nextRtpNanos = now;
            this.pausedSinceNanos = 0L;
            this.rtpStartChunk = 0L;
            this.awaitingTeleport = false;
            this.info("Auto RTP started.");
        } else if (!autoRtpEnabled) {
            this.rtpRunning = false;
            this.nextRtpNanos = 0L;
            this.pausedSinceNanos = 0L;
            this.rtpStartChunk = 0L;
            this.awaitingTeleport = false;
        }

        this.wasAutoRtpEnabled = autoRtpEnabled;
        if (this.rtpRunning && this.mc.player != null && this.mc.world != null) {
            if (this.isGuiOpen()) {
                if (this.pausedSinceNanos == 0L) {
                    this.pausedSinceNanos = now;
                }
            } else {
                if (this.pausedSinceNanos != 0L) {
                    if (this.nextRtpNanos != 0L) {
                        this.nextRtpNanos += now - this.pausedSinceNanos;
                    }

                    this.pausedSinceNanos = 0L;
                }

                if (this.awaitingTeleport) {
                    ChunkPos currentPos = this.mc.player.getChunkPos();
                    long currentChunk = ChunkPos.toLong(currentPos.x, currentPos.z);
                    if (currentChunk != this.rtpStartChunk) {
                        this.awaitingTeleport = false;
                        this.nextRtpNanos = now + delayToNanos(this.rtpDelay.getValue());
                    }
                } else if (now >= this.nextRtpNanos) {
                    List<String> commands = this.getEnabledRtpCommands();
                    if (commands.isEmpty()) {
                        if (!this.warnedNoRegions) {
                            this.warning("Enable at least one Auto RTP region.");
                            this.warnedNoRegions = true;
                        }

                        this.nextRtpNanos = now + 2000000000L;
                    } else {
                        this.warnedNoRegions = false;
                        if (this.regionIndex >= commands.size()) {
                            this.regionIndex = 0;
                        }

                        ChunkPos startPos = this.mc.player.getChunkPos();
                        this.rtpStartChunk = ChunkPos.toLong(startPos.x, startPos.z);
                        ChatUtil.sendChat(commands.get(this.regionIndex));
                        this.regionIndex = (this.regionIndex + 1) % commands.size();
                        this.awaitingTeleport = true;
                        this.nextRtpNanos = 0L;
                    }
                }
            }
        }
    }

    private static long delayToNanos(int delay) {
        return Math.max(40, Math.min(300, delay)) * 10000000L;
    }

    private boolean isGuiOpen() {
        Screen screen = this.mc.currentScreen;
        return screen instanceof ClickGuiScreen
                || screen instanceof OdinGuiScreen
                || screen instanceof ConfigScreen
                || screen instanceof HudPositionEditorScreen
                || screen instanceof ArtworkGalleryScreen
                || screen instanceof IgnoredPlayersScreen
                || screen instanceof SkySelectionScreen;
    }

    private List<String> getEnabledRtpCommands() {
        List<String> commands = new ArrayList<>(6);
        if (Boolean.TRUE.equals(this.east.getValue())) {
            commands.add("/rtp east");
        }

        if (Boolean.TRUE.equals(this.west.getValue())) {
            commands.add("/rtp west");
        }

        if (Boolean.TRUE.equals(this.euWest.getValue())) {
            commands.add("/rtp eu west");
        }

        if (Boolean.TRUE.equals(this.euCentral.getValue())) {
            commands.add("/rtp eu central");
        }

        if (Boolean.TRUE.equals(this.asia.getValue())) {
            commands.add("/rtp asia");
        }

        if (Boolean.TRUE.equals(this.oceania.getValue())) {
            commands.add("/rtp oceania");
        }

        return commands;
    }

    @EventHandler
    private void onRender(WorldRenderEvent event) {
        this.updateAutoRtp();
        if (this.mc.player != null && this.mc.world != null) {
            ChunkPos chunkPos = this.mc.player.getChunkPos();
            Map<Long, Long> nearby = this.getNearbyChunks(chunkPos);
            if (!nearby.isEmpty()) {
                long now = System.nanoTime();
                EntityColor color = this.markerColor.getValue();
                boolean animated = ThemeModule.areAnimationsEnabled();
                List<PlayerFinder.ChunkRegion> regions = this.chunkRegions;
                if (!regions.isEmpty()) {
                    double bob = animated ? Math.sin(now / 5.0E8) * 0.018 : 0.0;
                    double y = 64.02 + bob;
                    int rgb = color.toArgb() & 16777215;
                    GizmoDrawing.collect(
                                    (drawer, unused) -> {
                                        for (PlayerFinder.ChunkRegion region : regions) {
                                            double fadeProgress =
                                                    animated
                                                            ? clamp01(
                                                                    (now
                                                                                    - region.firstDetectedNanos)
                                                                            / 6.5E8)
                                                            : 1.0;
                                            fadeProgress = 1.0 - Math.pow(1.0 - fadeProgress, 3.0);
                                            double pulse =
                                                    animated
                                                            ? 0.9
                                                                    + 0.1
                                                                            * Math.sin(
                                                                                    now / 4.2E8
                                                                                            + region.minimumX
                                                                                                    * 0.37
                                                                                            + region.minimumZ
                                                                                                    * 0.29)
                                                            : 1.0;
                                            int alpha =
                                                    clampAlpha(
                                                            (int)
                                                                    Math.round(
                                                                            color.a
                                                                                    * fadeProgress
                                                                                    * pulse));
                                            if (alpha > 0) {
                                                int innerAlpha =
                                                        clampAlpha((int) Math.round(alpha * 0.36));
                                                double minX = region.minimumX * 16.0;
                                                double minZ = region.minimumZ * 16.0;
                                                double maxX = region.maximumXExclusive * 16.0;
                                                double maxZ = region.maximumZExclusive * 16.0;
                                                int fillColor = alpha << 24 | rgb;
                                                drawFlatQuad(
                                                        drawer, minX, y, minZ, maxX, maxZ,
                                                        fillColor);
                                                double borderWidthTarget =
                                                        animated
                                                                ? 2.1
                                                                        + 0.9
                                                                                * (0.5
                                                                                        + 0.5
                                                                                                * Math
                                                                                                        .sin(
                                                                                                                now
                                                                                                                        / 3.6E8))
                                                                : 2.5;
                                                double maxBorderWidth =
                                                        Math.max(
                                                                0.0,
                                                                Math.min(maxX - minX, maxZ - minZ)
                                                                                * 0.5
                                                                        - 0.05);
                                                double borderWidth =
                                                        Math.min(borderWidthTarget, maxBorderWidth);
                                                if (borderWidth > 0.0 && innerAlpha > 0) {
                                                    drawFlatQuad(
                                                            drawer,
                                                            minX + borderWidth,
                                                            y + 0.008,
                                                            minZ + borderWidth,
                                                            maxX - borderWidth,
                                                            maxZ - borderWidth,
                                                            innerAlpha << 24 | rgb);
                                                }
                                            }
                                        }
                                    })
                            .ignoreOcclusion();
                    if (Boolean.TRUE.equals(this.tracers.getValue())) {
                        EntityColor tracerColor = withAlpha(color, Math.max(180, color.a));

                        for (long packed : this.nearestChunks) {
                            int chunkX = ChunkPos.getPackedX(packed);
                            int chunkZ = ChunkPos.getPackedZ(packed);
                            event.renderer.stableTracer(
                                    new Vec3d((chunkX << 4) + 8.0, y + 0.04, (chunkZ << 4) + 8.0),
                                    tracerColor,
                                    1.1F);
                        }
                    }
                }
            }
        }
    }

    private Map<Long, Long> getNearbyChunks(ChunkPos center) {
        long version = LightChunkTracker.getVersion();
        if (version == this.cachedRevision
                && center.x == this.cachedCenterX
                && center.z == this.cachedCenterZ) {
            return this.detectedChunks;
        } else {
            this.cachedRevision = version;
            this.cachedCenterX = center.x;
            this.cachedCenterZ = center.z;
            this.detectedChunks = LightChunkTracker.getChunksNear(center.x, center.z, 32);
            this.chunkRegions = mergeIntoRegions(this.detectedChunks);
            ArrayList<Long> sorted = new ArrayList<>(this.detectedChunks.keySet());
            sorted.sort(
                    Comparator.comparingLong(chunk -> distanceSquared(chunk, center.x, center.z)));
            if (sorted.size() > 64) {
                sorted = new ArrayList<>(sorted.subList(0, 64));
            }

            this.nearestChunks = sorted;
            return this.detectedChunks;
        }
    }

    private void clearCache() {
        this.detectedChunks = Map.of();
        this.chunkRegions = List.of();
        this.nearestChunks = List.of();
        this.cachedRevision = -9223372036854775808L;
        this.cachedCenterX = -2147483648;
        this.cachedCenterZ = -2147483648;
    }

    private static void drawFlatQuad(
            GizmoDrawer drawer,
            double minX,
            double y,
            double minZ,
            double maxX,
            double maxZ,
            int color) {
        drawer.addQuad(
                new Vec3d(minX, y, minZ),
                new Vec3d(minX, y, maxZ),
                new Vec3d(maxX, y, maxZ),
                new Vec3d(maxX, y, minZ),
                color);
    }

    private static List<PlayerFinder.ChunkRegion> mergeIntoRegions(Map<Long, Long> chunks) {
        Map<Long, Long> remaining = new HashMap<>(chunks);
        List<PlayerFinder.ChunkRegion> regions = new ArrayList<>();

        while (!remaining.isEmpty()) {
            long first = remaining.keySet().iterator().next();
            int minX = ChunkPos.getPackedX(first);
            int minZ = ChunkPos.getPackedZ(first);
            int maxX = minX + 1;

            while (remaining.containsKey(ChunkPos.toLong(maxX, minZ))) {
                maxX++;
            }

            int maxZ = minZ + 1;

            while (isRowComplete(remaining, minX, maxX, maxZ)) {
                maxZ++;
            }

            long firstDetected = 9223372036854775807L;

            for (int z = minZ; z < maxZ; z++) {
                for (int x = minX; x < maxX; x++) {
                    Long detectedAt = remaining.remove(ChunkPos.toLong(x, z));
                    if (detectedAt != null) {
                        firstDetected = Math.min(firstDetected, detectedAt);
                    }
                }
            }

            if (firstDetected == 9223372036854775807L) {
                firstDetected = System.nanoTime();
            }

            regions.add(new PlayerFinder.ChunkRegion(minX, minZ, maxX, maxZ, firstDetected));
        }

        return regions;
    }

    private static boolean isRowComplete(Map<Long, Long> chunks, int fromX, int toX, int z) {
        for (int x = fromX; x < toX; x++) {
            if (!chunks.containsKey(ChunkPos.toLong(x, z))) {
                return false;
            }
        }

        return true;
    }

    private static long distanceSquared(long packed, int centerX, int centerZ) {
        long dx = (long) ChunkPos.getPackedX(packed) - centerX;
        long dz = (long) ChunkPos.getPackedZ(packed) - centerZ;
        return dx * dx + dz * dz;
    }

    private static EntityColor withAlpha(EntityColor color, int alpha) {
        return new EntityColor(color.r, color.g, color.b, clampAlpha(alpha));
    }

    private static int clampAlpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record ChunkDetection(int chunkX, int chunkZ, long detectedAtNanos) {}

    private record ChunkRegion(
            int minimumX,
            int minimumZ,
            int maximumXExclusive,
            int maximumZExclusive,
            long firstDetectedNanos) {}
}
