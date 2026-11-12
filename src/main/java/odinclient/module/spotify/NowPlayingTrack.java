package odinclient.module.spotify;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record NowPlayingTrack(
        String title,
        String artist,
        String album,
        boolean playing,
        long positionMs,
        long durationMs,
        long fetchedAt,
        byte[] art) {
    public static final NowPlayingTrack EMPTY =
            new NowPlayingTrack("", "", "", false, 0L, 0L, 0L, new byte[0]);

    public String key() {
        return this.title + this.artist + this.album;
    }

    public long estimatedPosition() {
        return !this.playing
                ? this.positionMs
                : this.positionMs + Math.max(0L, System.currentTimeMillis() - this.fetchedAt);
    }

    public boolean hasTrack() {
        return this.title != null && !this.title.isBlank();
    }
}
