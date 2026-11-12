package odinclient.module.spotify;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.module.Module;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Environment(EnvType.CLIENT)
public final class SpotifyHud extends Module {
    private static final long READY_CARD_DURATION_MS = 6000L;
    private static final long POLL_INTERVAL_SECONDS = 3L;
    private final SettingGroup positionGroup = this.settings.addGroup("Position");
    private final SettingGroup appearanceGroup = this.settings.addGroup("Appearance");
    private final Setting<Integer> xPosition =
            this.positionGroup.add(
                    new IntSetting.Builder()
                            .name("x-position")
                            .description("Horizontal position in percent: 0 is left, 100 is right.")
                            .defaultValue(0)
                            .range(0, 100)
                            .sliderRange(0, 100)
                            .build());
    private final Setting<Integer> yPosition =
            this.positionGroup.add(
                    new IntSetting.Builder()
                            .name("y-position")
                            .description("Vertical position in percent: 0 is top, 100 is bottom.")
                            .defaultValue(100)
                            .range(0, 100)
                            .sliderRange(0, 100)
                            .build());
    private final Setting<Integer> margin =
            this.positionGroup.add(
                    new IntSetting.Builder()
                            .name("margin")
                            .description("Distance from the screen edges.")
                            .defaultValue(12)
                            .range(0, 48)
                            .sliderRange(0, 48)
                            .build());
    private final Setting<Double> scale =
            this.appearanceGroup.add(
                    new NumberSetting.Builder()
                            .name("scale")
                            .description("Overall scale of the card.")
                            .defaultValue(1.0)
                            .range(0.5, 1.75)
                            .sliderRange(0.5, 1.75)
                            .build());
    private final Setting<Integer> width =
            this.appearanceGroup.add(
                    new IntSetting.Builder()
                            .name("width")
                            .description("Width of the card in scaled pixels.")
                            .defaultValue(195)
                            .range(165, 280)
                            .sliderRange(165, 280)
                            .build());
    private final Setting<Integer> backgroundOpacity =
            this.appearanceGroup.add(
                    new IntSetting.Builder()
                            .name("background-opacity")
                            .description("Opacity of the dark card background.")
                            .defaultValue(215)
                            .range(40, 255)
                            .sliderRange(40, 255)
                            .build());
    private final Setting<EntityColor> accentColor =
            this.appearanceGroup.add(
                    new ColorSetting.Builder()
                            .name("accent-color")
                            .description(
                                    "Accent color used for the label, equalizer, badge and progress"
                                        + " bar.")
                            .defaultValue(new EntityColor(29, 185, 84))
                            .build());
    private final Setting<Boolean> showAlbumArt =
            this.appearanceGroup.add(
                    new BooleanSetting.Builder()
                            .name("album-art")
                            .description("Shows the current album cover when Windows provides it.")
                            .defaultValue(true)
                            .build());
    private final Setting<Boolean> showProgressBar =
            this.appearanceGroup.add(
                    new BooleanSetting.Builder()
                            .name("progress-bar")
                            .description("Shows playback progress and timestamps.")
                            .defaultValue(true)
                            .build());
    private final Setting<Boolean> showWhenPaused =
            this.appearanceGroup.add(
                    new BooleanSetting.Builder()
                            .name("show-when-paused")
                            .description(
                                    "Keeps the card visible (with a PAUSED label) while playback is"
                                        + " paused.")
                            .defaultValue(true)
                            .build());
    private final SpotifyTrackReader trackProvider = new SpotifyTrackReader();
    private final SpotifyHudTextures cardTextures = new SpotifyHudTextures();
    private final SpotifyCardRenderer cardRenderer = new SpotifyCardRenderer();
    private volatile NowPlayingTrack currentTrack = NowPlayingTrack.EMPTY;
    private long activatedAt;
    private ScheduledExecutorService pollExecutor;

    public SpotifyHud() {
        super(
                Categories.MAIN,
                "spotify-hud",
                "Shows the current Spotify song in a premium on-screen card.");
    }

    @Override
    public void onActivate() {
        this.activatedAt = System.currentTimeMillis();
        this.trackProvider.reset();
        ThreadFactory factory =
                r -> {
                    Thread thread = new Thread(r, "OdinClient-Spotify-HUD");
                    thread.setDaemon(true);
                    return thread;
                };
        this.pollExecutor = Executors.newSingleThreadScheduledExecutor(factory);
        this.pollExecutor.scheduleWithFixedDelay(
                this::pollTrack, 0L, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void onDeactivate() {
        if (this.pollExecutor != null) {
            this.pollExecutor.shutdownNow();
            this.pollExecutor = null;
        }

        this.currentTrack = NowPlayingTrack.EMPTY;
    }

    private void pollTrack() {
        this.currentTrack = this.trackProvider.fetchTrack();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        NowPlayingTrack track = this.currentTrack;
        if (!track.hasTrack() || !track.playing() && !this.showWhenPaused.getValue()) {
            if (System.currentTimeMillis() - this.activatedAt > READY_CARD_DURATION_MS) {
                return;
            }

            track =
                    new NowPlayingTrack(
                            "Spotify HUD is ready",
                            "Play a song in Spotify",
                            "",
                            true,
                            0L,
                            0L,
                            System.currentTimeMillis(),
                            new byte[0]);
        }

        int accent =
                0xFF000000
                        | this.accentColor.getValue().r << 16
                        | this.accentColor.getValue().g << 8
                        | this.accentColor.getValue().b;
        int accentLight = lightenColor(accent, 0.18F);
        this.cardTextures.updateCardTexture(
                this.mc,
                this.width.getValue(),
                62,
                9,
                7,
                this.backgroundOpacity.getValue(),
                accent);
        this.cardTextures.updateBadgeTextures(this.mc, accent, accentLight);
        this.cardTextures.updateFallbackCover(this.mc, accent);
        this.cardTextures.updateCover(this.mc, track.key(), track.art());
        SpotifyCardRenderer.CardLayout layout =
                new SpotifyCardRenderer.CardLayout(
                        this.width.getValue(),
                        this.xPosition.getValue().intValue(),
                        this.yPosition.getValue().intValue(),
                        this.margin.getValue(),
                        this.scale.getValue().floatValue(),
                        this.showAlbumArt.getValue(),
                        this.showProgressBar.getValue(),
                        accent,
                        accentLight);
        this.cardRenderer.render(
                event.context,
                this.mc,
                this.cardTextures,
                track,
                layout,
                event.scaledWidth,
                event.scaledHeight);
    }

    private static int lightenColor(int color, float amount) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * amount));
        g = Math.min(255, (int) (g + (255 - g) * amount));
        b = Math.min(255, (int) (b + (255 - b) * amount));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Environment(EnvType.CLIENT)
    record AlbumArtKey(String key, byte[] bytes) {}
}
