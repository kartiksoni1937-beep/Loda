package odinclient.module.antivanish;

import com.mojang.brigadier.suggestion.Suggestion;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Entry;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import odinclient.event.EventHandler;
import odinclient.event.PacketEvent;
import odinclient.event.TickEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.module.ModuleContext;
import odinclient.module.automation.AutoLog;
import odinclient.module.hud.regionmap.RegionMapLayout;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AntiVanishActivityMonitor {
    private static final AntiVanishActivityMonitor INSTANCE = new AntiVanishActivityMonitor();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private final ArrayBlockingQueue<AntiVanishActivityMonitor.QueuedPacket> packetQueue =
            new ArrayBlockingQueue<>(4096);
    private final VanishTracker detector = new VanishTracker(AntiVanishDetection::onDetection);
    private final Map<Integer, Long> pendingProbes = new HashMap<>();
    private final Map<Long, Long> interactedBlocks = new HashMap<>();
    private final Map<Long, Long> poweredBlocks = new HashMap<>();
    private final Map<String, Long> recentParticles = new HashMap<>();
    private final ArrayDeque<AntiVanishActivityMonitor.RecentExplosion> recentExplosions =
            new ArrayDeque<>();
    private volatile boolean active;
    private volatile Object connection;
    private Object world;
    private Object player;
    private String brand;
    private String regionKey;
    private int tickCounter;
    private int nextProbeId = 30000;
    private long lastActionTime = -4611686018427387904L;
    private long lastScreenTime = -4611686018427387904L;

    private AntiVanishActivityMonitor() {}

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private static long currentMillis() {
        return System.nanoTime() / 1000000L;
    }

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            ModuleContext.EVENT_BUS.register(INSTANCE);
        }
    }

    public static void refreshState() {
        if (!AntiVanishDetection.isEnabled() && !AutoLog.isAntiVanishLogoutActive()) {
            INSTANCE.reset();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (this.active) {
            this.queuePacket(event.packet, false);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (this.active
                && (event.packet instanceof PlayerInteractBlockC2SPacket
                        || event.packet instanceof PlayerActionC2SPacket
                        || event.packet instanceof PlayerInteractItemC2SPacket
                        || event.packet instanceof PlayerInteractEntityC2SPacket
                        || event.packet instanceof HandSwingC2SPacket)) {
            this.queuePacket(event.packet, true);
        }
    }

    private void queuePacket(Packet<?> packet, boolean sent) {
        if (!sent && packet instanceof BundleS2CPacket) {
            for (Packet<?> inner : ((BundleS2CPacket) packet).getPackets()) {
                this.queuePacket(inner, false);
            }
        } else if (sent
                || packet instanceof PlayerRemoveS2CPacket
                || packet instanceof PlayerListS2CPacket
                || packet instanceof GameMessageS2CPacket
                || packet instanceof EntityTrackerUpdateS2CPacket
                || packet instanceof ExplosionS2CPacket
                || packet instanceof PlaySoundFromEntityS2CPacket
                || packet instanceof ParticleS2CPacket
                || packet instanceof PlaySoundS2CPacket) {
            this.enqueue(
                    new AntiVanishActivityMonitor.QueuedPacket(
                            packet, sent, this.connection, currentMillis()));
        }
    }

    private void enqueue(AntiVanishActivityMonitor.QueuedPacket queued) {
        if (!this.packetQueue.offer(queued)) {
            this.packetQueue.poll();
            this.packetQueue.offer(queued);
        }
    }

    public static boolean interceptCommandSuggestions(CommandSuggestionsS2CPacket packet) {
        if (client().isOnThread()
                && INSTANCE.active
                && INSTANCE.pendingProbes.remove(packet.id()) != null) {
            INSTANCE.enqueue(
                    new AntiVanishActivityMonitor.QueuedPacket(
                            packet, false, INSTANCE.connection, currentMillis()));
            return true;
        } else {
            return false;
        }
    }

    @EventHandler
    private void onWorldLeave(WorldLeaveEvent event) {
        this.reset();
    }

    private void reset() {
        this.active = false;
        this.connection = null;
        this.player = null;
        this.world = null;
        this.regionKey = null;
        this.brand = null;
        this.packetQueue.clear();
        this.pendingProbes.clear();
        this.interactedBlocks.clear();
        this.poweredBlocks.clear();
        this.recentParticles.clear();
        this.recentExplosions.clear();
        this.tickCounter = 0;
        this.lastScreenTime = -4611686018427387904L;
        this.lastActionTime = -4611686018427387904L;
        this.detector.reset(null, "");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient mc = client();
        if ((AntiVanishDetection.isEnabled() || AutoLog.isAntiVanishLogoutActive())
                && mc.world != null
                && mc.player != null
                && mc.getNetworkHandler() != null) {
            String brand = mc.getNetworkHandler().getBrand();
            RegionMapLayout.RegionRect region =
                    mc.world.getRegistryKey() == World.OVERWORLD
                            ? RegionMapLayout.findRegion(mc.player.getX(), mc.player.getZ())
                            : null;
            String newRegionKey =
                    region == null
                            ? mc.world.getRegistryKey().toString()
                            : region.region() + "/" + region.number();

            if (this.world != mc.world
                    || this.player != mc.player
                    || this.connection != mc.getNetworkHandler()
                    || !Objects.equals(this.brand, brand)
                    || !Objects.equals(this.regionKey, newRegionKey)) {
                this.reset();
                this.world = mc.world;
                this.player = mc.player;
                this.connection = mc.getNetworkHandler();
                this.brand = brand;
                this.regionKey = newRegionKey;
                this.detector.reset(mc.player.getUuid(), mc.player.getGameProfile().name());
                this.active = true;
            }

            long now = currentMillis();
            this.tickCounter++;
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                this.lastScreenTime = now;
            }

            HashSet<UUID> listedIds = new HashSet<>();
            HashSet<UUID> nearbyIds = new HashSet<>();

            for (PlayerListEntry listEntry : mc.getNetworkHandler().getPlayerList()) {
                if (listEntry.getProfile() != null) {
                    listedIds.add(listEntry.getProfile().id());
                }
            }

            for (PlayerListEntry listedEntry :
                    mc.getNetworkHandler().getListedPlayerListEntries()) {
                if (listedEntry.getProfile() != null) {
                    this.detector.onListedPlayer(
                            listedEntry.getProfile().id(), listedEntry.getProfile().name(), now);
                }
            }

            for (PlayerEntity other : mc.world.getPlayers()) {
                if (other != mc.player && !other.isRemoved()) {
                    nearbyIds.add(other.getUuid());
                    this.detector.markNearby(other.getUuid(), now);
                }
            }

            for (int i = 0; i < 512; i++) {
                AntiVanishActivityMonitor.QueuedPacket queued = this.packetQueue.poll();
                if (queued == null) {
                    break;
                }

                if (queued.connection == this.connection && now - queued.time <= 10000L) {
                    if (queued.sent) {
                        this.processSentPacket(queued.packet, now);
                    } else {
                        this.processReceivedPacket(queued.packet, now);
                    }
                }
            }

            this.detector.tick(now, listedIds, nearbyIds);
            if (this.tickCounter % 5 == 0) {
                for (PlayerEntity worldPlayer : mc.world.getPlayers()) {
                    this.checkInvisiblePlayer(worldPlayer, now);
                }
            }

            this.interactedBlocks.entrySet().removeIf(entry -> entry.getValue() < now);
            this.poweredBlocks.entrySet().removeIf(entry -> entry.getValue() < now);
            this.recentParticles.entrySet().removeIf(entry -> now - entry.getValue() > 2000L);
            this.recentExplosions.removeIf(entry -> now - entry.time > 3000L);
            this.pendingProbes.entrySet().removeIf(entry -> now - entry.getValue() > 10000L);
            if (this.tickCounter % 100 == 0 && this.pendingProbes.size() < 8) {
                int probeId = this.nextProbeId++;
                if (this.nextProbeId > 40000) {
                    this.nextProbeId = 30000;
                }

                this.pendingProbes.put(probeId, now);
                mc.getNetworkHandler()
                        .sendPacket(
                                new RequestCommandCompletionsC2SPacket(probeId, "minecraft:msg "));
            }
        } else {
            if (this.active) {
                this.reset();
            }
        }
    }

    private void processReceivedPacket(Packet<?> packet, long now) {
        MinecraftClient mc = client();
        if (packet instanceof PlayerRemoveS2CPacket) {
            this.detector.onPlayersRemoved(((PlayerRemoveS2CPacket) packet).profileIds(), now);
        } else if (packet instanceof PlayerListS2CPacket listPacket) {
            EnumSet<Action> actions = listPacket.getActions();
            List<Entry> entries = listPacket.getEntries();
            if (actions.contains(Action.UPDATE_LISTED)) {
                long unlistedCount = entries.stream().filter(e -> !e.listed()).count();
                if (unlistedCount > 0L && unlistedCount < 4L) {
                    for (Entry entry : entries) {
                        if (!entry.listed()) {
                            this.detector.onUnlisted(entry.profileId(), now);
                        }
                    }
                }
            }

            if (actions.contains(Action.UPDATE_GAME_MODE) && !actions.contains(Action.ADD_PLAYER)) {
                for (Entry gameModeEntry : entries) {
                    this.detector.markNearby(gameModeEntry.profileId(), now);
                    this.detector.onGameModeChanged(
                            gameModeEntry.profileId(),
                            gameModeEntry.gameMode() == GameMode.SPECTATOR,
                            now);
                }
            }
        } else if (packet instanceof GameMessageS2CPacket messagePacket) {
            String leftName = "";
            if (messagePacket.content().getContent() instanceof TranslatableTextContent content
                    && "multiplayer.player.left".equals(content.getKey())) {
                Object[] args = content.getArgs();
                if (args.length > 0) {
                    Object firstArg = args[0];
                    leftName =
                            firstArg instanceof Text
                                    ? ((Text) firstArg).getString()
                                    : String.valueOf(firstArg);
                }
            }

            this.detector.onChatMessage(messagePacket.content().getString(), leftName, now);
        } else if (packet instanceof CommandSuggestionsS2CPacket suggestionsPacket) {
            HashSet<String> onlineNames = new HashSet<>();

            for (PlayerListEntry listEntry : mc.getNetworkHandler().getPlayerList()) {
                if (listEntry.getProfile() != null) {
                    onlineNames.add(listEntry.getProfile().name().toLowerCase(Locale.ROOT));
                }
            }

            this.detector.onCommandSuggestions(
                    suggestionsPacket.getSuggestions().getList().stream()
                            .map(Suggestion::getText)
                            .toList(),
                    onlineNames,
                    now);
        } else if (packet instanceof EntityTrackerUpdateS2CPacket) {
            Entity entity = mc.world.getEntityById(((EntityTrackerUpdateS2CPacket) packet).id());
            if (entity instanceof PlayerEntity) {
                this.checkInvisiblePlayer((PlayerEntity) entity, now);
            }
        } else if (packet instanceof ExplosionS2CPacket explosionPacket) {
            this.recentExplosions.add(
                    new AntiVanishActivityMonitor.RecentExplosion(
                            explosionPacket.center(),
                            Math.max(2.0F, explosionPacket.radius() + 4.0F),
                            now));

            while (this.recentExplosions.size() > 8) {
                this.recentExplosions.removeFirst();
            }
        } else if (packet instanceof PlaySoundFromEntityS2CPacket soundEntityPacket) {
            if (isInteractionSound(soundEntityPacket.getSound().value().id().toString())
                    && mc.world.getEntityById(soundEntityPacket.getEntityId())
                            instanceof PlayerEntity soundPlayer
                    && soundPlayer != mc.player
                    && soundPlayer.isInvisible()
                    && soundPlayer.squaredDistanceTo(mc.player) <= 4096.0) {
                this.detector.recordSignal(
                        VanishTracker.SignalKind.SOUND,
                        soundPlayer.getUuid(),
                        "Sound from an invisible player",
                        16,
                        3000L,
                        now);
            }
        } else if (packet instanceof PlaySoundS2CPacket soundPacket) {
            this.sound(
                    soundPacket.getSound().value().id().toString(),
                    new Vec3d(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ()),
                    now);
        } else if (packet instanceof ParticleS2CPacket particlePacket) {
            this.onParticle(
                    Registries.PARTICLE_TYPE
                            .getId(particlePacket.getParameters().getType())
                            .toString(),
                    new Vec3d(particlePacket.getX(), particlePacket.getY(), particlePacket.getZ()),
                    now);
        }
    }

    private void checkInvisiblePlayer(PlayerEntity player, long now) {
        if (player != client().player
                && !player.isRemoved()
                && player.isInvisible()
                && player.squaredDistanceTo(client().player) <= 4096.0) {
            this.detector.recordSignal(
                    VanishTracker.SignalKind.INVISIBLE,
                    player.getUuid(),
                    "Invisible player metadata",
                    35,
                    5000L,
                    now);
        }
    }

    private void processSentPacket(Packet<?> packet, long now) {
        this.lastActionTime = now;
        if (packet instanceof PlayerInteractBlockC2SPacket blockPacket) {
            BlockPos clicked = blockPacket.getBlockHitResult().getBlockPos();
            BlockPos placed = clicked.offset(blockPacket.getBlockHitResult().getSide());
            this.markInteractedBlock(clicked, now);
            this.markInteractedBlock(placed, now);
            String itemId =
                    Registries.ITEM
                            .getId(client().player.getStackInHand(blockPacket.getHand()).getItem())
                            .toString();
            if (itemId.contains("door")
                    || itemId.contains("tall_")
                    || itemId.contains("sunflower")
                    || itemId.contains("lilac")
                    || itemId.contains("peony")
                    || itemId.contains("rose_bush")) {
                this.markInteractedBlock(placed.up(), now);
                this.markInteractedBlock(placed.down(), now);
            }

            if (itemId.contains("bed")) {
                for (Direction direction :
                        List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                    this.markInteractedBlock(placed.offset(direction), now);
                }
            }
        }
    }

    private void markInteractedBlock(BlockPos pos, long now) {
        if (this.interactedBlocks.size() >= 512) {
            this.interactedBlocks.clear();
        }

        this.interactedBlocks.put(pos.asLong(), now + 6000L);
    }

    private static String stripNamespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private static boolean isInteractionSound(String sound) {
        return sound.contains("chest.open")
                || sound.contains("chest.close")
                || sound.contains("chest.locked")
                || sound.contains("barrel.open")
                || sound.contains("barrel.close")
                || sound.contains("shulker_box.open")
                || sound.contains("shulker_box.close")
                || sound.contains("door.open")
                || sound.contains("door.close")
                || sound.contains("fence_gate.open")
                || sound.contains("fence_gate.close")
                || sound.contains("button.click")
                || sound.contains("lever.click");
    }

    private boolean isUnexplainedSource(Vec3d source, long now) {
        MinecraftClient mc = client();
        if (mc.player.getEntityPos().squaredDistanceTo(source) > 4096.0) {
            return false;
        } else {
            for (Entity entity : mc.world.getEntities()) {
                if (entity != mc.player
                        && !entity.isRemoved()
                        && !(entity.getEntityPos().squaredDistanceTo(source) > 16.0)
                        && (entity instanceof PlayerEntity && !entity.isInvisible()
                                || entity instanceof ProjectileEntity)) {
                    return false;
                }
            }

            for (AntiVanishActivityMonitor.RecentExplosion explosion : this.recentExplosions) {
                if (now - explosion.time <= 3000L
                        && explosion.position.squaredDistanceTo(source)
                                <= explosion.radius * explosion.radius) {
                    return false;
                }
            }

            return true;
        }
    }

    private void sound(String soundId, Vec3d source, long now) {
        if (isInteractionSound(soundId)
                && this.isUnexplainedSource(source, now)
                && now - this.lastActionTime >= 1000L) {
            BlockPos blockPos = BlockPos.ofFloored(source);
            if (this.interactedBlocks.getOrDefault(blockPos.asLong(), 0L) <= now) {
                if (!soundId.contains("chest")
                                && !soundId.contains("barrel")
                                && !soundId.contains("shulker_box")
                        || now - this.lastScreenTime >= 2500L) {
                    if (soundId.contains("door")
                            || soundId.contains("fence_gate")
                            || soundId.contains("button")
                            || soundId.contains("lever")) {
                        BlockState state = client().world.getBlockState(blockPos);
                        if (client().world.isReceivingRedstonePower(blockPos)
                                || state.contains(Properties.POWERED)
                                        && state.get(Properties.POWERED)) {
                            if (this.poweredBlocks.size() >= 512) {
                                this.poweredBlocks.clear();
                            }

                            this.poweredBlocks.put(blockPos.asLong(), now + 5000L);
                            return;
                        }

                        if (this.poweredBlocks.getOrDefault(blockPos.asLong(), 0L) > now) {
                            return;
                        }

                        if (!soundId.contains("trapdoor")
                                && (soundId.contains("door") || soundId.contains("fence_gate"))) {
                            for (Entity entity : client().world.getEntities()) {
                                if (entity.getType() == EntityType.VILLAGER
                                        && entity.getEntityPos().squaredDistanceTo(source) <= 9.0) {
                                    return;
                                }
                            }
                        }
                    }

                    this.detector.recordSignal(
                            VanishTracker.SignalKind.SOUND,
                            null,
                            "Suspicious sound: " + stripNamespace(soundId),
                            14,
                            3000L,
                            now);
                }
            }
        }
    }

    private void onParticle(String particleId, Vec3d source, long now) {
        String name = stripNamespace(particleId);
        if ((name.equals("crit")
                        || name.equals("enchanted_hit")
                        || name.equals("damage_indicator")
                        || name.contains("smoke")
                        || name.equals("block"))
                && this.isUnexplainedSource(source, now)
                && now - this.lastActionTime >= 900L) {
            boolean smoke = name.contains("smoke");
            boolean throttled = smoke || name.equals("block");

            if (!throttled || !(client().player.getEntityPos().squaredDistanceTo(source) <= 6.25)) {
                if (smoke) {
                    BlockPos center = BlockPos.ofFloored(source);

                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -2; dy <= 2; dy++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                Block block =
                                        client().world
                                                .getBlockState(center.add(dx, dy, dz))
                                                .getBlock();
                                String blockId = Registries.BLOCK.getId(block).toString();
                                if (blockId.contains("campfire")
                                        || blockId.contains("furnace")
                                        || blockId.contains("smoker")
                                        || blockId.contains("torch")
                                        || blockId.contains("fire")
                                        || blockId.contains("candle")
                                        || blockId.contains("respawn_anchor")) {
                                    return;
                                }
                            }
                        }
                    }
                }

                if (throttled) {
                    Long previous = this.recentParticles.put(name, now);
                    if (previous == null || now - previous > 2000L) {
                        return;
                    }

                    this.recentParticles.remove(name);
                }

                this.detector.recordSignal(
                        VanishTracker.SignalKind.PARTICLE,
                        null,
                        "Suspicious particles: " + name,
                        16,
                        3000L,
                        now);
            }
        }
    }

    private record QueuedPacket(Packet<?> packet, boolean sent, Object connection, long time) {}

    private record RecentExplosion(Vec3d position, double radius, long time) {}
}
