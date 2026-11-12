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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
final class SpotifyArtFetcher {
    private static final int MAX_IMAGE_BYTES = 4194304;
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(
                            Duration.ofSeconds(-1698769431411448290L ^ -1698769431411448294L))
                    .followRedirects(Redirect.NORMAL)
                    .build();
    private static final Pattern ARTWORK_URL_PATTERN =
            Pattern.compile("\"artworkUrl100\"\\s*:\\s*\"([^\"]+)\"");
    private static final Path ART_TEMP_PATH;
    private static final String MEDIA_ART_SCRIPT =
            "[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)\n"
                + "[void][System.Reflection.Assembly]::LoadFile('C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\System.Runtime.WindowsRuntime.dll')\n"
                + "$null ="
                + " [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]\n"
                + "$g = ([System.WindowsRuntimeSystemExtensions].GetMethods() |\n"
                + "    Where-Object {\n"
                + "        $_.Name -eq 'AsTask' -and\n"
                + "        $_.GetParameters().Count -eq 1 -and\n"
                + "        $_.GetParameters()[0].ParameterType.Name -like 'IAsyncOperation*'\n"
                + "    })[0]\n"
                + "function Aw($op,$t) {\n"
                + "    $m = $g.MakeGenericMethod($t)\n"
                + "    $task = $m.Invoke($null,@($op))\n"
                + "    $task.GetAwaiter().GetResult()\n"
                + "}\n"
                + "$asStreamForRead = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() |\n"
                + "    Where-Object {\n"
                + "        $_.Name -eq 'AsStreamForRead' -and\n"
                + "        $_.GetParameters().Count -eq 1\n"
                + "    } |\n"
                + "    Select-Object -First 1\n"
                + "try {\n"
                + "    $mgr = Aw (\n"
                + "       "
                + " [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()\n"
                + "    )"
                + " ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n"
                + "    $s = $mgr.GetCurrentSession()\n"
                + "    if ($s) {\n"
                + "        $p = Aw (\n"
                + "            $s.TryGetMediaPropertiesAsync()\n"
                + "        )"
                + " ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n"
                + "        if ($p.Title -and $p.Thumbnail -and $asStreamForRead) {\n"
                + "            $stream = Aw (\n"
                + "                $p.Thumbnail.OpenReadAsync()\n"
                + "            ) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n"
                + "            $netStream = $asStreamForRead.Invoke($null, @($stream))\n"
                + "            $outPath = Join-Path $env:TEMP"
                + " 'odinclient_odinclient_spotify_art.png'\n"
                + "            $tmpPath = $outPath + '.tmp'\n"
                + "            $fs = [System.IO.File]::Create($tmpPath)\n"
                + "            $netStream.CopyTo($fs)\n"
                + "            $fs.Close()\n"
                + "            $netStream.Close()\n"
                + "            Move-Item -LiteralPath $tmpPath -Destination $outPath -Force\n"
                + "            $encodedTitle = [Convert]::ToBase64String(\n"
                + "                [Text.Encoding]::UTF8.GetBytes([string]$p.Title)\n"
                + "            )\n"
                + "            Write-Output ('OdinClient_ART' + \"`t\" + $encodedTitle)\n"
                + "        }\n"
                + "    }\n"
                + "} catch {}\n";

    private SpotifyArtFetcher() {}

    static byte[] fetchAlbumArt(String title, String artist) {
        CompletableFuture<byte[]> localArt = new CompletableFuture<>();
        CompletableFuture<byte[]> mediaArt = new CompletableFuture<>();
        Thread.ofVirtual()
                .name("OdinClient-Spotify-Local-Art")
                .start(() -> localArt.complete(fetchLocalCacheArt(title)));
        Thread.ofVirtual()
                .name("OdinClient-Spotify-OdinClient-Art")
                .start(() -> mediaArt.complete(fetchWindowsMediaArt(title)));
        long deadline =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(1554947910561572023L ^ 1554947910561572017L);

        while (System.nanoTime() < deadline) {
            byte[] local = getCompletedBytes(localArt);
            if (local.length > 0) {
                return local;
            }

            byte[] media = getCompletedBytes(mediaArt);
            if (media.length > 0) {
                return media;
            }

            if (localArt.isDone() && mediaArt.isDone()) {
                break;
            }

            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                break;
            }

            try {
                CompletableFuture.anyOf(localArt, mediaArt)
                        .get(
                                Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20L)),
                                TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new byte[0];
            } catch (TimeoutException | ExecutionException ignored) {
                // keep polling until the deadline
            }
        }

        String url = searchItunesArtUrl(title, artist);
        return url.isBlank() ? new byte[0] : downloadImage(url);
    }

    private static byte[] getCompletedBytes(CompletableFuture<byte[]> future) {
        if (!future.isDone()) {
            return new byte[0];
        } else {
            try {
                byte[] bytes = future.getNow(new byte[0]);
                return bytes == null ? new byte[0] : bytes;
            } catch (Exception e) {
                return new byte[0];
            }
        }
    }

    private static byte[] fetchWindowsMediaArt(String title) {
        Process process = null;

        try {
            String encodedScript =
                    Base64.getEncoder()
                            .encodeToString(MEDIA_ART_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
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
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(-330395545573391173L ^ -330395545573391171L, TimeUnit.SECONDS);
            Predicate<String> isArtLine = line -> line.startsWith("OdinClient_ART\t");
            String artTitle =
                    output.lines()
                            .filter(isArtLine)
                            .map(line -> line.substring("OdinClient_ART\t".length()).trim())
                            .map(SpotifyArtFetcher::decodeBase64)
                            .findFirst()
                            .orElse("");
            if (!normalizeTitle(artTitle).equals(normalizeTitle(title))) {
                return new byte[0];
            } else if (!Files.isRegularFile(ART_TEMP_PATH)) {
                return new byte[0];
            } else {
                byte[] image = Files.readAllBytes(ART_TEMP_PATH);
                return isValidImage(image) ? image : new byte[0];
            }
        } catch (Exception e) {
            return new byte[0];
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private static byte[] fetchLocalCacheArt(String title) {
        try {
            Optional<Path> usersFile = findSpotifyUsersDir();
            if (usersFile.isEmpty()) {
                return new byte[0];
            }

            byte[] data = Files.readAllBytes(usersFile.get());
            String normalizedTitle = normalizeTitle(title);

            for (int offset : findFieldOffsets(data, "title")) {
                if (normalizeTitle(readStringField(data, offset, "title"))
                        .equals(normalizedTitle)) {
                    String imageId = readNearestField(data, offset, "image_xlarge_url");
                    if (imageId.isBlank()) {
                        imageId = readNearestField(data, offset, "image_url");
                    }

                    if (imageId.startsWith("spotify:image:")) {
                        imageId = imageId.substring("spotify:image:".length());
                    }

                    if (imageId.matches("[0-9a-fA-F]{20,64}")) {
                        byte[] image = downloadImage("https://i.scdn.co/image/" + imageId);
                        if (image.length > 0) {
                            return image;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        return new byte[0];
    }

    private static String searchItunesArtUrl(String title, String artist) {
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
                            .timeout(
                                    Duration.ofSeconds(
                                            -5489300785830691720L ^ -5489300785830691714L))
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

    private static byte[] downloadImage(String url) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(
                                    Duration.ofSeconds(7510362039724005144L ^ 7510362039724005150L))
                            .header("User-Agent", "OdinClient-Spotify-HUD")
                            .GET()
                            .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200
                    && response.statusCode() < 400
                    && isValidImage(response.body())) {
                return response.body();
            }
        } catch (Exception e) {
        }

        return new byte[0];
    }

    private static boolean isValidImage(byte[] data) {
        return data != null && data.length >= 200 && data.length < 4194304;
    }

    private static Optional<Path> findSpotifyUsersDir() {
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

            for (Path root : roots) {
                if (Files.isDirectory(root)) {
                    try (Stream<Path> users = Files.list(root)) {
                        Optional<Path> newest =
                                users.map(dir -> dir.resolve("context_player_state_restore"))
                                        .filter(file -> Files.isRegularFile(file))
                                        .max(
                                                Comparator.comparingLong(
                                                        SpotifyArtFetcher::getLastModified));
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
            return -2441180172726883833L ^ -2441180172726883833L;
        }
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
        int nearest = -1;
        int nearestDistance = 6000;

        for (int offset : findFieldOffsets(data, field)) {
            int distance = Math.abs(offset - origin);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = offset;
            }
        }

        return nearest < 0 ? "" : readStringField(data, nearest, field);
    }

    private static String readStringField(byte[] data, int offset, String field) {
        int index = offset + field.length();
        if (index < data.length) {
            int pos = index + 1;
            if ((data[index] & 255) == 18) {
                int length = 0;

                for (byte shift = 0; pos < data.length && shift <= 28; shift += 7) {
                    int current = pos++;
                    int b = data[current] & 255;
                    length |= (b & 127) << shift;
                    if ((b & 128) == 0) {
                        break;
                    }
                }

                if (length >= 0 && pos + length <= data.length) {
                    return new String(data, pos, length, StandardCharsets.UTF_8);
                }

                return "";
            }
        }

        return "";
    }

    private static String normalizeTitle(String title) {
        return title == null ? "" : title.strip().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String decodeBase64(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    static {
        ART_TEMP_PATH =
                Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "odinclient_odinclient_spotify_art.png");
    }
}
