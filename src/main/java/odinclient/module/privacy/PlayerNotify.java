package odinclient.module.privacy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.event.TickEvent;
import odinclient.manager.ModuleManager;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.hud.ItemToastRenderer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public final class PlayerNotify extends Module {
    private static final long NOTIFY_COOLDOWN_NANOS = 600000000000L;
    private static final long TOAST_DURATION_NANOS = 5000000000L;
    private static final float BASE_VOLUME = 0.18F;
    private final Setting<Boolean> sound;
    private final Setting<Double> volume;
    private final Setting<Double> pitch;
    private final Setting<Double> scale;
    private final Map<UUID, Long> notifiedUntil = new HashMap<>();
    private final ArrayDeque<String> pendingNames = new ArrayDeque<>();
    private String currentName;
    private long currentShownAt;
    private Object lastWorld;
    private ItemStack headStack;

    public PlayerNotify() {
        super(
                Categories.MAIN,
                "player-notify",
                "Notifies you of loaded players, at most once per player every ten minutes.");
        this.title = "Player Notify";
        SettingGroup general = this.settings.getGeneral();
        this.sound =
                general.add(
                        new BooleanSetting.Builder()
                                .name("sound")
                                .description("Play a soft local ping when a player is detected.")
                                .defaultValue(true)
                                .build());
        this.volume =
                general.add(
                        new NumberSetting.Builder()
                                .name("volume")
                                .description(
                                        "Ping volume: 100% is the original volume; 200% is twice as"
                                            + " loud.")
                                .defaultValue(100.0)
                                .range(100.0, 200.0)
                                .sliderRange(100.0, 200.0)
                                .visibleWhen(() -> Boolean.TRUE.equals(this.sound.getValue()))
                                .build());
        this.pitch =
                general.add(
                        new NumberSetting.Builder()
                                .name("pitch")
                                .description("Pitch of the player detection ping.")
                                .defaultValue(1.5)
                                .range(0.5, 2.0)
                                .sliderRange(0.5, 2.0)
                                .visibleWhen(() -> Boolean.TRUE.equals(this.sound.getValue()))
                                .build());
        this.scale =
                general.add(
                        new NumberSetting.Builder()
                                .name("scale")
                                .description("Size of player notifications.")
                                .defaultValue(1.0)
                                .range(0.5, 2.0)
                                .sliderRange(0.5, 2.0)
                                .build());
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.lastWorld != this.mc.world) {
            this.reset();
            this.lastWorld = this.mc.world;
        }

        if (this.mc.world != null && this.mc.player != null) {
            long now = System.nanoTime();
            Predicate<Long> expired = until -> now >= until;
            this.notifiedUntil.values().removeIf(expired);
            boolean detected = false;

            for (PlayerEntity player : this.mc.world.getPlayers()) {
                if (PlayerFilter.isOtherPlayer(this.mc, player)) {
                    UUID uuid = player.getGameProfile().id();
                    if (!this.notifiedUntil.containsKey(uuid)) {
                        this.notifiedUntil.put(uuid, now + 600000000000L);
                        this.pendingNames.addLast(player.getGameProfile().name());
                        detected = true;
                    }
                }
            }

            if (detected && Boolean.TRUE.equals(this.sound.getValue())) {
                SoundEvent bell =
                        SoundEvent.of(Identifier.of("minecraft", "block.note_block.bell"));
                float volumeScale = 0.18F * this.volume.getValue().floatValue() / 100.0F;
                this.mc
                        .getSoundManager()
                        .play(
                                PositionedSoundInstance.ui(
                                        bell, this.pitch.getValue().floatValue(), volumeScale));
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.mc.world != null
                && this.mc.player != null
                && this.mc.currentScreen == null
                && !this.mc.getDebugHud().shouldShowDebugHud()) {
            long now = System.nanoTime();
            if (this.currentName != null && now - this.currentShownAt >= 5000000000L) {
                this.currentName = null;
            }

            if (this.currentName == null) {
                this.currentName = this.pendingNames.pollFirst();
                this.currentShownAt = now;
            }

            if (this.currentName != null) {
                if (this.headStack == null) {
                    this.headStack =
                            new ItemStack(
                                    Registries.ITEM
                                            .getEntry(Identifier.of("minecraft", "player_head"))
                                            .orElseThrow());
                }

                float alpha = Math.max(0.0F, 1.0F - (float) (now - this.currentShownAt) / 5.0E9F);
                ItemToastRenderer.drawAnchoredToast(
                        event.context,
                        this.headStack,
                        "Player detected",
                        this.currentName,
                        event.scaledWidth,
                        event.scaledHeight,
                        getBlockNotifierPosition("x-position", 50),
                        getBlockNotifierPosition("y-position", 2),
                        this.scale.getValue().floatValue(),
                        alpha);
            }
        }
    }

    private static int getBlockNotifierPosition(String settingName, int fallback) {
        Module blockNotifier = ModuleManager.get().getByName("block-notifier");
        if (blockNotifier != null) {
            for (SettingGroup group : blockNotifier.settings) {
                for (Setting<?> setting : group) {
                    if (settingName.equals(setting.name)) {
                        Object value = setting.getValue();
                        if (value instanceof Integer) {
                            return (Integer) value;
                        }
                    }
                }
            }
        }

        return fallback;
    }

    private void reset() {
        this.pendingNames.clear();
        this.currentName = null;
        this.currentShownAt = 0L;
        this.lastWorld = null;
    }
}
