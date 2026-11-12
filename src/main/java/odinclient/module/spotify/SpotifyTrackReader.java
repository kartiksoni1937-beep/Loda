package odinclient.module.spotify;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
final class SpotifyTrackReader {
    private static final long CACHE_MAX_AGE_MS = TimeUnit.MINUTES.toMillis(30L);
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5L))
                    .followRedirects(Redirect.NORMAL)
                    .build();
    private static final Pattern TITLE_ARTIST_PATTERN =
            Pattern.compile("(?is)<title>.*?song and lyrics by\\s+(.+?)\\s*\\|\\s*Spotify</title>");
    private static final Pattern ARTISTS_JSON_PATTERN =
            Pattern.compile(
                    "(?is)\"artists\"\\s*:\\s*\\[\\s*\\{.{0,700}?\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ARTWORK_URL_PATTERN =
            Pattern.compile("\"artworkUrl100\"\\s*:\\s*\"([^\"]+)\"");
    private static final String MEDIA_SESSION_SCRIPT =
            "$ErrorActionPreference = \"Stop\"\n"
                + "[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)\n"
                + "function E([string] $v) {\n"
                + "    if ($null -eq $v) { $v = \"\" }\n"
                + "    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($v))\n"
                + "}\n"
                + "try {\n"
                + "    Add-Type -AssemblyName System.Runtime.WindowsRuntime\n"
                + "    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,"
                + " Windows.Media.Control, ContentType = WindowsRuntime] > $null\n"
                + "    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,"
                + " Windows.Media.Control, ContentType = WindowsRuntime] > $null\n"
                + "    [Windows.Storage.Streams.IRandomAccessStreamWithContentType,"
                + " Windows.Storage.Streams, ContentType = WindowsRuntime] > $null\n"
                + "    [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType ="
                + " WindowsRuntime] > $null\n"
                + "    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() |\n"
                + "        Where-Object {\n"
                + "            $_.Name -eq \"AsTask\" -and\n"
                + "            $_.IsGenericMethod -and\n"
                + "            $_.GetParameters().Count -eq 1\n"
                + "        } |\n"
                + "        Select-Object -First 1\n"
                + "    function A($op, $type) {\n"
                + "        $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))\n"
                + "        return $task.GetAwaiter().GetResult()\n"
                + "    }\n"
                + "    $manager = A (\n"
                + "       "
                + " [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()\n"
                + "    )"
                + " ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n"
                + "    $session = $manager.GetSessions() |\n"
                + "        Where-Object { $_.SourceAppUserModelId -match \"Spotify\" } |\n"
                + "        Select-Object -First 1\n"
                + "    if ($null -eq $session) {\n"
                + "        Write-Output \"NONE\"\n"
                + "        exit 0\n"
                + "    }\n"
                + "    $props = A (\n"
                + "        $session.TryGetMediaPropertiesAsync()\n"
                + "    )"
                + " ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n"
                + "    $playback = $session.GetPlaybackInfo()\n"
                + "    $timeline = $session.GetTimelineProperties()\n"
                + "    $art = \"\"\n"
                + "    if ($null -ne $props.Thumbnail) {\n"
                + "        $stream = A (\n"
                + "            $props.Thumbnail.OpenReadAsync()\n"
                + "        ) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n"
                + "        if ($null -ne $stream -and $stream.Size -gt 0 -and $stream.Size -lt"
                + " 4194304) {\n"
                + "            $reader = [Windows.Storage.Streams.DataReader]::new($stream)\n"
                + "            $null = A ($reader.LoadAsync([uint32]$stream.Size)) ([uint32])\n"
                + "            $bytes = [byte[]]::new([int]$stream.Size)\n"
                + "            $reader.ReadBytes($bytes)\n"
                + "            $art = [Convert]::ToBase64String($bytes)\n"
                + "            $reader.Dispose()\n"
                + "            $stream.Dispose()\n"
                + "        }\n"
                + "    }\n"
                + "    $fields = @(\n"
                + "        \"OK\",\n"
                + "        (E $props.Title),\n"
                + "        (E $props.Artist),\n"
                + "        (E $props.AlbumTitle),\n"
                + "        (E $playback.PlaybackStatus.ToString()),\n"
                + "        ([long]$timeline.Position.TotalMilliseconds),\n"
                + "        ([long]$timeline.EndTime.TotalMilliseconds),\n"
                + "        $art\n"
                + "    )\n"
                + "    Write-Output ($fields -join \"`t\")\n"
                + "} catch {\n"
                + "    Write-Output \"UNAVAILABLE\"\n"
                + "}\n";
    private volatile boolean mediaSessionUnavailable;
    private volatile Path cacheFile;
    private volatile String metadataTrackId = "";
    private volatile String cachedArtist = "";
    private volatile byte[] cachedLocalArt = new byte[0];
    private volatile String artworkKey = "";
    private volatile byte[] artworkBytes = new byte[0];
    private volatile String requestedArtworkKey = "";
    private volatile long lastArtworkRequestTime;

    void reset() {
        this.mediaSessionUnavailable = false;
    }

    NowPlayingTrack fetchTrack() {
        try {
            NowPlayingTrack initial =
                    this.mediaSessionUnavailable
                            ? NowPlayingTrack.EMPTY
                            : this.readFromMediaSession();
            NowPlayingTrack track = initial;
            if (!initial.hasTrack()) {
                NowPlayingTrack cached = this.readFromLocalCache();
                if (cached.hasTrack()) {
                    track = cached;
                }
            }

            if (track.hasTrack() && track.art().length == 0) {
                if (track.key().equals(this.artworkKey) && this.artworkBytes.length > 0) {
                    track =
                            new NowPlayingTrack(
                                    track.title(),
                                    track.artist(),
                                    track.album(),
                                    track.playing(),
                                    track.positionMs(),
                                    track.durationMs(),
                                    track.fetchedAt(),
                                    this.artworkBytes);
                } else {
                    this.updateArtwork(track.key(), track.title(), track.artist());
                }
            }

            return track;
        } catch (Exception e) {
            return NowPlayingTrack.EMPTY;
        }
    }

    private void updateArtwork(String key, String title, String artist) {
        long now = System.currentTimeMillis();
        if (!key.equals(this.requestedArtworkKey) || now - this.lastArtworkRequestTime >= 20000L) {
            this.requestedArtworkKey = key;
            this.lastArtworkRequestTime = now;
            Runnable task =
                    () -> {
                        byte[] art = this.downloadArtwork(title, artist);
                        if (art.length > 0 && key.equals(this.requestedArtworkKey)) {
                            this.artworkKey = key;
                            this.artworkBytes = art;
                        }
                    };
            Thread.ofVirtual().name("OdinClient-Spotify-Artwork").start(task);
        }
    }

    private byte[] downloadArtwork(String title, String artist) {
        return SpotifyArtFetcher.fetchAlbumArt(title, artist);
    }

    private String findLocalImageUrl(String title) {
        try {
            Path file = this.cacheFile;
            if (file == null || !Files.isRegularFile(file)) {
                file = findCacheFile().orElse(null);
                this.cacheFile = file;
            }

            if (file == null || title.isBlank()) {
                return "";
            }

            byte[] data = Files.readAllBytes(file);
            String wanted = title.trim();

            for (int offset : findFieldOffsets(data, "title")) {
                if (readFieldValue(data, offset, "title").trim().equalsIgnoreCase(wanted)) {
                    String imageId = readNearestField(data, offset, "image_xlarge_url");
                    if (imageId.isBlank()) {
                        imageId = readNearestField(data, offset, "image_url");
                    }

                    if (imageId.startsWith("spotify:image:")) {
                        imageId = imageId.substring("spotify:image:".length());
                    }

                    if (imageId.matches("[0-9a-f]{20,64}")) {
                        return "https://i.scdn.co/image/" + imageId;
                    }
                }
            }
        } catch (Exception e) {
        }

        return "";
    }

    private String searchItunesArtwork(String title, String artist) {
        try {
            String query =
                    (artist
                                    + " "
                                    + title.replaceAll("\\(.*?\\)|\\[.*?\\]", " ")
                                            .replaceAll("\\s+", " ")
                                            .trim())
                            .trim();
            if (query.isBlank()) {
                return "";
            }

            String url =
                    "https://itunes.apple.com/search?media=music&entity=song&limit=1&term="
                            + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(8L))
                            .header("User-Agent", "OdinClient-Spotify-HUD")
                            .GET()
                            .build();
            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                Matcher matcher = ARTWORK_URL_PATTERN.matcher(response.body());
                if (matcher.find()) {
                    return matcher.group(1).replace("\\/", "/").replace("100x100bb", "600x600bb");
                }
            }
        } catch (Exception e) {
        }

        return "";
    }

    private byte[] downloadImage(String url) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(8L))
                            .header("User-Agent", "OdinClient-Spotify-HUD")
                            .GET()
                            .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200
                    && response.statusCode() < 400
                    && response.body().length > 0
                    && response.body().length < 4194304) {
                return response.body();
            }
        } catch (Exception e) {
        }

        return new byte[0];
    }

    private static List<Integer> findFieldOffsets(byte[] data, String field) {
        List<Integer> offsets = new ArrayList<>();
        byte[] fieldBytes = field.getBytes(StandardCharsets.US_ASCII);

        for (int i = 2; i <= data.length - fieldBytes.length - 1; i++) {
            if ((data[i - 2] & 255) == 10 && (data[i - 1] & 255) == fieldBytes.length) {
                boolean matches = true;

                for (int j = 0; j < fieldBytes.length; j++) {
                    if (data[i + j] != fieldBytes[j]) {
                        matches = false;
                        break;
                    }
                }

                if (matches && (data[i + fieldBytes.length] & 255) == 18) {
                    offsets.add(i);
                }
            }
        }

        return offsets;
    }

    private static String readNearestField(byte[] data, int origin, String field) {
        int bestOffset = -1;
        int bestDistance = 6000;

        for (int offset : findFieldOffsets(data, field)) {
            int distance = Math.abs(offset - origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestOffset = offset;
            }
        }

        return bestOffset < 0 ? "" : readFieldValue(data, bestOffset, field);
    }

    private NowPlayingTrack readFromMediaSession() throws IOException {
        String encodedScript =
                Base64.getEncoder()
                        .encodeToString(MEDIA_SESSION_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        String[] command =
                new String[] {
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-EncodedCommand",
                    encodedScript
                };
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        String output;
        try {
            output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                            .trim();
        } catch (Throwable t) {
            process.destroy();
            throw t;
        }

        process.destroy();
        if (output.lines().anyMatch("UNAVAILABLE"::equals)) {
            this.mediaSessionUnavailable = true;
            return NowPlayingTrack.EMPTY;
        } else {
            return parseMediaSessionOutput(output);
        }
    }

    private static NowPlayingTrack parseMediaSessionOutput(String output) {
        if (output != null && !output.isBlank()) {
            Predicate<String> lineFilter = line -> line.startsWith("OK\t") || line.equals("NONE");
            String[] fields =
                    output.lines()
                            .filter(lineFilter)
                            .reduce((line, latest) -> latest)
                            .orElse("NONE")
                            .split("\t", -1);

            try {
                if (fields.length >= 8 && fields[0].equals("OK")) {
                    String title = decodeBase64(fields[1]);
                    String artist = decodeBase64(fields[2]);
                    String album = decodeBase64(fields[3]);
                    boolean playing = "playing".equalsIgnoreCase(decodeBase64(fields[4]));
                    long positionMs = Math.max(0L, Long.parseLong(fields[5]));
                    long durationMs = Math.max(0L, Long.parseLong(fields[6]));
                    byte[] art =
                            fields[7].isBlank()
                                    ? new byte[0]
                                    : Base64.getDecoder().decode(fields[7]);

                    return new NowPlayingTrack(
                            title,
                            artist,
                            album,
                            playing,
                            positionMs,
                            durationMs,
                            System.currentTimeMillis(),
                            art);
                }
            } catch (IllegalArgumentException e) {
                return NowPlayingTrack.EMPTY;
            }

            return NowPlayingTrack.EMPTY;
        } else {
            return NowPlayingTrack.EMPTY;
        }
    }

    private NowPlayingTrack readFromLocalCache() {
        try {
            Path file = this.cacheFile;
            if (file == null || !Files.isRegularFile(file)) {
                file = findCacheFile().orElse(null);
                this.cacheFile = file;
            }

            if (file == null || !isSpotifyRunning()) {
                return NowPlayingTrack.EMPTY;
            } else if (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis()
                    > CACHE_MAX_AGE_MS) {
                return NowPlayingTrack.EMPTY;
            } else {
                byte[] data = Files.readAllBytes(file);
                int pausedOffset = findFieldOffset(data, "kb.is_paused", 0, data.length);
                if (pausedOffset < 0) {
                    return NowPlayingTrack.EMPTY;
                } else if (!"false"
                        .equalsIgnoreCase(readFieldValue(data, pausedOffset, "kb.is_paused"))) {
                    return NowPlayingTrack.EMPTY;
                } else {
                    int trackOffset =
                            lastIndexOf(
                                    data,
                                    "spotify:track:".getBytes(StandardCharsets.US_ASCII),
                                    pausedOffset);
                    if (trackOffset < 0) {
                        return NowPlayingTrack.EMPTY;
                    } else {
                        String trackId =
                                readAlphanumeric(data, trackOffset + "spotify:track:".length(), 22);
                        String title = readFieldInRange(data, "title", trackOffset, pausedOffset);
                        String album =
                                readFieldInRange(data, "album_title", trackOffset, pausedOffset);
                        String imageUrl =
                                readFieldInRange(
                                        data, "image_xlarge_url", trackOffset, pausedOffset);
                        if (imageUrl.isBlank()) {
                            imageUrl =
                                    readFieldInRange(data, "image_url", trackOffset, pausedOffset);
                        }

                        if (title.isBlank()) {
                            return NowPlayingTrack.EMPTY;
                        } else {
                            this.updateMetadata(trackId, imageUrl);
                            return new NowPlayingTrack(
                                    title,
                                    this.cachedArtist,
                                    album,
                                    true,
                                    0L,
                                    0L,
                                    System.currentTimeMillis(),
                                    this.cachedLocalArt);
                        }
                    }
                }
            }
        } catch (Exception e) {
            return NowPlayingTrack.EMPTY;
        }
    }

    private void updateMetadata(String trackId, String imageUrl) {
        if (!trackId.equals(this.metadataTrackId)) {
            this.metadataTrackId = trackId;
            this.cachedArtist = "";
            this.cachedLocalArt = new byte[0];
            Runnable task = () -> this.fetchMetadata(trackId, imageUrl);
            Thread.ofVirtual().name("OdinClient-Spotify-Metadata").start(task);
        }
    }

    private void fetchMetadata(String trackId, String imageUrl) {
        String artist = "";
        byte[] art = new byte[0];
        if (!trackId.isBlank()) {
            try {
                HttpRequest pageRequest =
                        HttpRequest.newBuilder()
                                .uri(URI.create("https://open.spotify.com/track/" + trackId))
                                .timeout(Duration.ofSeconds(7L))
                                .header("User-Agent", "Mozilla/5.0 OdinClient-Spotify-HUD")
                                .GET()
                                .build();
                HttpResponse<String> pageResponse =
                        HTTP_CLIENT.send(
                                pageRequest, BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (pageResponse.statusCode() >= 200 && pageResponse.statusCode() < 400) {
                    artist = extractArtistFromPage(pageResponse.body());
                }
            } catch (Exception pageError) {
                artist = "";
            }
        }

        String imageId =
                imageUrl.startsWith("spotify:image:")
                        ? imageUrl.substring("spotify:image:".length())
                        : "";
        if (!imageId.isBlank()) {
            try {
                HttpRequest imageRequest =
                        HttpRequest.newBuilder()
                                .uri(URI.create("https://i.scdn.co/image/" + imageId))
                                .timeout(Duration.ofSeconds(7L))
                                .header("User-Agent", "OdinClient-Spotify-HUD")
                                .GET()
                                .build();
                HttpResponse<byte[]> imageResponse =
                        HTTP_CLIENT.send(imageRequest, BodyHandlers.ofByteArray());
                if (imageResponse.statusCode() >= 200
                        && imageResponse.statusCode() < 400
                        && imageResponse.body().length < 4194304) {
                    art = imageResponse.body();
                }
            } catch (Exception imageError) {
                art = new byte[0];
            }
        }

        if (trackId.equals(this.metadataTrackId)) {
            this.cachedArtist = artist;
            this.cachedLocalArt = art;
        }
    }

    private static String extractArtistFromPage(String html) {
        Matcher titleMatcher = TITLE_ARTIST_PATTERN.matcher(html);
        if (titleMatcher.find()) {
            return decodeHtmlEntities(titleMatcher.group(1).trim());
        } else {
            Matcher jsonMatcher = ARTISTS_JSON_PATTERN.matcher(html);
            return jsonMatcher.find() ? decodeJsonEscapes(jsonMatcher.group(1)) : "";
        }
    }

    private static String decodeHtmlEntities(String text) {
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String decodeJsonEscapes(String text) {
        return text.replace("\\u0026", "&").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static Optional<Path> findCacheFile() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            Path[] roots =
                    new Path[] {
                        Path.of(
                                localAppData,
                                "Packages",
                                "SpotifyAB.SpotifyMusic_zpdnekdrzrea0",
                                "LocalState",
                                "Spotify",
                                "Users"),
                        Path.of(localAppData, "Spotify", "Users")
                    };

            for (Path dir : roots) {
                if (Files.isDirectory(dir)) {
                    try (Stream<Path> children = Files.list(dir)) {
                        Optional<Path> newest =
                                children.map(path -> path.resolve("context_player_state_restore"))
                                        .filter(Files::isRegularFile)
                                        .max(
                                                Comparator.comparingLong(
                                                        SpotifyTrackReader::getLastModified));
                        if (newest.isPresent()) {
                            return newest;
                        }
                    } catch (IOException e) {
                    }
                }
            }

            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    private static long getLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static boolean isSpotifyRunning() {
        try (Stream<ProcessHandle> handles = ProcessHandle.allProcesses()) {
            return handles.anyMatch(
                    handle ->
                            handle.info()
                                    .command()
                                    .map(
                                            path ->
                                                    path.toLowerCase(Locale.ROOT)
                                                            .endsWith("spotify.exe"))
                                    .orElse(false));
        }
    }

    private static String readFieldInRange(byte[] data, String field, int start, int end) {
        int offset = findFieldOffset(data, field, start, end);
        return offset < 0 ? "" : readFieldValue(data, offset, field);
    }

    private static int findFieldOffset(byte[] data, String field, int start, int end) {
        byte[] fieldBytes = field.getBytes(StandardCharsets.US_ASCII);
        int last = Math.min(end, data.length) - fieldBytes.length - 1;

        for (int i = Math.max(2, start); i <= last; i++) {
            if ((data[i - 2] & 255) == 10 && (data[i - 1] & 255) == fieldBytes.length) {
                boolean matches = true;

                for (int j = 0; j < fieldBytes.length; j++) {
                    if (data[i + j] != fieldBytes[j]) {
                        matches = false;
                        break;
                    }
                }

                if (matches && (data[i + fieldBytes.length] & 255) == 18) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static String readFieldValue(byte[] data, int offset, String field) {
        int pos = offset + field.getBytes(StandardCharsets.US_ASCII).length;
        if (pos < data.length) {
            int index = pos + 1;
            if ((data[pos] & 255) == 18) {
                int length = 0;

                for (int shift = 0; index < data.length && shift <= 28; shift += 7) {
                    int b = data[index++] & 255;
                    length |= (b & 127) << shift;
                    if ((b & 128) == 0) {
                        break;
                    }
                }

                if (length >= 0 && index + length <= data.length) {
                    return new String(data, index, length, StandardCharsets.UTF_8);
                }

                return "";
            }
        }

        return "";
    }

    private static int lastIndexOf(byte[] data, byte[] pattern, int from) {
        for (int i = Math.min(from, data.length - pattern.length); i >= 0; i--) {
            boolean matches = true;

            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                return i;
            }
        }

        return -1;
    }

    private static String readAlphanumeric(byte[] data, int start, int maxLength) {
        int limit = Math.min(data.length, start + maxLength);

        int i;
        for (i = start; i < limit; i++) {
            int c = data[i] & 255;
            if ((c < 97 || c > 122) && (c < 65 || c > 90) && (c < 48 || c > 57)) {
                break;
            }
        }

        return new String(data, start, i - start, StandardCharsets.US_ASCII);
    }

    private static String decodeBase64(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
