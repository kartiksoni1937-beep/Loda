package odinclient.module.antivanish;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class VanishTracker {
    private static final long NEVER = -4611686018427387904L;
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Set<String> TRACKED_STAFF_NAMES;
    private final Consumer<VanishTracker.VanishAlert> alertSink;
    private final Map<UUID, VanishTracker.ListedPlayer> listedPlayers = new HashMap<>();
    private final Map<UUID, Long> nearbyTimes = new HashMap<>();
    private final Map<String, Long> recentDepartures = new HashMap<>();
    private final Map<String, Long> signalCooldowns = new HashMap<>();
    private final Map<UUID, VanishTracker.PendingRemoval> pendingRemovals = new HashMap<>();
    private final Map<String, VanishTracker.Suspect> suspects = new HashMap<>();
    private final ArrayDeque<VanishTracker.ActivitySignal> recentSignals = new ArrayDeque<>();
    private final ArrayDeque<Long> recentAlertTimes = new ArrayDeque<>();
    private UUID selfId;
    private String selfName = "";
    private boolean departureMessageSeen;
    private long lastConfirmedAlertTime = NEVER;
    private long lastSignalAlertTime = NEVER;

    public VanishTracker(Consumer<VanishTracker.VanishAlert> alertSink) {
        this.alertSink = alertSink;
    }

    public void reset(UUID id, String name) {
        this.listedPlayers.clear();
        this.nearbyTimes.clear();
        this.recentDepartures.clear();
        this.signalCooldowns.clear();
        this.pendingRemovals.clear();
        this.suspects.clear();
        this.recentSignals.clear();
        this.recentAlertTimes.clear();
        this.departureMessageSeen = false;
        this.lastSignalAlertTime = NEVER;
        this.lastConfirmedAlertTime = NEVER;
        this.selfId = id;
        this.selfName = name;
    }

    public static boolean isTrackedStaffName(String name) {
        return name != null && TRACKED_STAFF_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    public void onListedPlayer(UUID id, String name, long now) {
        if (id != null
                && !id.equals(this.selfId)
                && isTrackedStaffName(name)
                && NAME_PATTERN.matcher(name).matches()) {
            VanishTracker.ListedPlayer existing = this.listedPlayers.get(id);
            long listedSince = existing == null ? now : existing.listedSince;
            this.listedPlayers.put(id, new VanishTracker.ListedPlayer(name, listedSince, now));
            VanishTracker.Suspect suspect = this.suspects.get(name.toLowerCase(Locale.ROOT));
            if (suspect != null && !suspect.spectator) {
                this.suspects.remove(name.toLowerCase(Locale.ROOT));
            }
        }
    }

    public String name(UUID id) {
        VanishTracker.ListedPlayer player = this.listedPlayers.get(id);
        return player == null ? "" : player.name;
    }

    public boolean isEstablishedListing(UUID id, long now) {
        VanishTracker.ListedPlayer player = this.listedPlayers.get(id);
        return id != null
                && !id.equals(this.selfId)
                && player != null
                && now - player.listedSince >= 1500L;
    }

    public void markNearby(UUID id, long now) {
        if (id != null && this.listedPlayers.containsKey(id)) {
            this.nearbyTimes.put(id, now);
        }
    }

    public boolean wasRecentlyNearby(UUID id, long now) {
        return id != null
                && this.nearbyTimes.containsKey(id)
                && now - this.nearbyTimes.get(id) <= 600000L;
    }

    public void onPlayersRemoved(List<UUID> ids, long now) {
        if (ids.size() < 4) {
            for (UUID id : ids) {
                if (this.isEstablishedListing(id, now)) {
                    this.pendingRemovals.put(id, new VanishTracker.PendingRemoval(id, now + 1000L));
                }
            }
        }
    }

    public void onUnlisted(UUID id, long now) {
        if (this.isEstablishedListing(id, now)) {
            this.flagSuspect(id, this.name(id), "Hidden from TAB", 100, false, now);
        }
    }

    public void onGameModeChanged(UUID id, boolean spectator, long now) {
        if (this.isEstablishedListing(id, now)) {
            if (spectator) {
                this.flagSuspect(id, this.name(id), "Switched to spectator", 100, true, now);
            } else {
                this.suspects.remove(this.name(id).toLowerCase(Locale.ROOT));
            }
        }
    }

    public void onChatMessage(String message, String leaver, long now) {
        if (leaver != null && NAME_PATTERN.matcher(leaver).matches()) {
            this.departureMessageSeen = true;
            this.recordDeparture(leaver, now);
        }

        String normalized = normalizeName(message);

        for (VanishTracker.ListedPlayer player : this.listedPlayers.values()) {
            if (Pattern.compile(
                            "(?<![a-z0-9_])"
                                    + Pattern.quote(normalizeName(player.name))
                                    + "\\s+(?:has\\s+)?(?:left(?:\\s+the\\s+game)?|quit|disconnected|logged\\s+out)(?![a-z])")
                    .matcher(normalized)
                    .find()) {
                this.departureMessageSeen = true;
                this.recordDeparture(player.name, now);
            }
        }
    }

    private void recordDeparture(String name, long now) {
        String key = name.toLowerCase(Locale.ROOT);
        this.recentDepartures.put(key, now);
        this.suspects.remove(key);
        this.pendingRemovals
                .entrySet()
                .removeIf(entry -> name.equalsIgnoreCase(this.name(entry.getKey())));
        this.recentSignals.removeIf(signal -> name.equalsIgnoreCase(signal.name));
    }

    private boolean hasRecentDeparture(String name, long now) {
        Long time = this.recentDepartures.get(name.toLowerCase(Locale.ROOT));
        return time != null && now - time <= 8000L;
    }

    public void onCommandSuggestions(List<String> suggestions, Set<String> knownNames, long now) {
        ArrayList<String> hidden = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        for (String name : suggestions) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (isTrackedStaffName(name)
                    && NAME_PATTERN.matcher(name).matches()
                    && !name.equalsIgnoreCase(this.selfName)
                    && !knownNames.contains(lower)
                    && !this.hasRecentDeparture(name, now)
                    && seen.add(lower)) {
                hidden.add(name);
            }
        }

        if (hidden.size() <= 3) {
            for (String hiddenName : hidden) {
                this.flagSuspect(
                        this.listedPlayers.entrySet().stream()
                                .filter(entry -> hiddenName.equalsIgnoreCase(entry.getValue().name))
                                .map(Entry::getKey)
                                .findFirst()
                                .orElse(null),
                        hiddenName,
                        "Hidden but still targetable",
                        90,
                        false,
                        now);
            }
        }
    }

    private void flagSuspect(
            UUID id, String name, String reason, int score, boolean spectator, long now) {
        if (!this.hasRecentDeparture(name, now)) {
            String key = name.toLowerCase(Locale.ROOT);
            VanishTracker.Suspect existing = this.suspects.get(key);
            if (existing == null || existing.spectator != spectator) {
                VanishTracker.Suspect suspect =
                        new VanishTracker.Suspect(id, name, reason, score, spectator);
                this.suspects.put(key, suspect);
                suspect.confirmed = this.wasRecentlyNearby(id, now);
                this.alertSink.accept(
                        new VanishTracker.VanishAlert(name, reason, suspect.confirmed, score));
                if (suspect.confirmed) {
                    this.lastConfirmedAlertTime = now;
                }
            }
        }
    }

    public void tick(long now, Set<UUID> visibleIds, Set<UUID> entityIds) {
        for (VanishTracker.PendingRemoval removal : List.copyOf(this.pendingRemovals.values())) {
            if (removal.due <= now) {
                this.pendingRemovals.remove(removal.id);
                String name = this.name(removal.id);
                if (!visibleIds.contains(removal.id) && !this.hasRecentDeparture(name, now)) {
                    if (entityIds.contains(removal.id)) {
                        this.flagSuspect(
                                removal.id,
                                name,
                                "Entity remained after leaving TAB",
                                100,
                                false,
                                now);
                    } else if (this.departureMessageSeen) {
                        this.flagSuspect(
                                removal.id,
                                name,
                                "Left TAB without a departure message",
                                100,
                                false,
                                now);
                    } else {
                        this.recordSignal(
                                VanishTracker.SignalKind.TAB,
                                removal.id,
                                "Unconfirmed silent TAB removal",
                                30,
                                3000L,
                                now);
                    }
                }
            }
        }

        for (VanishTracker.Suspect suspect : this.suspects.values()) {
            if (!suspect.confirmed && this.wasRecentlyNearby(suspect.id, now)) {
                suspect.confirmed = true;
                this.lastConfirmedAlertTime = now;
                this.alertSink.accept(
                        new VanishTracker.VanishAlert(
                                suspect.name, suspect.reason, true, suspect.score));
            }
        }

        this.recentSignals.removeIf(signal -> now - signal.time > 15000L);
        this.recentDepartures.entrySet().removeIf(entry -> now - entry.getValue() > 8000L);
        this.signalCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 20000L);
        this.nearbyTimes.entrySet().removeIf(entry -> now - entry.getValue() > 600000L);
        this.listedPlayers
                .entrySet()
                .removeIf(
                        entry ->
                                now - entry.getValue().seenAt > 600000L
                                        && !visibleIds.contains(entry.getKey())
                                        && !this.nearbyTimes.containsKey(entry.getKey()));
    }

    public void recordSignal(
            VanishTracker.SignalKind kind,
            UUID id,
            String reason,
            int weight,
            long cooldown,
            long now) {
        if (id == null || this.isEstablishedListing(id, now)) {
            String name = id == null ? "Nearby activity" : this.name(id);
            if (!this.hasRecentDeparture(name, now)) {
                String cooldownKey = kind + "|" + name.toLowerCase(Locale.ROOT);
                Long lastTime = this.signalCooldowns.get(cooldownKey);
                if (lastTime == null || now - lastTime >= cooldown) {
                    this.signalCooldowns.put(cooldownKey, now);
                    this.recentSignals.add(
                            new VanishTracker.ActivitySignal(
                                    kind, name, this.wasRecentlyNearby(id, now), weight, now));

                    while (this.recentSignals.size() > 128) {
                        this.recentSignals.removeFirst();
                    }

                    this.recentSignals.removeIf(old -> now - old.time > 15000L);

                    while (!this.recentAlertTimes.isEmpty()
                            && now - this.recentAlertTimes.peekFirst() > 4000L) {
                        this.recentAlertTimes.removeFirst();
                    }

                    if (now - this.lastSignalAlertTime >= 1500L
                            && this.recentAlertTimes.size() < 6) {
                        this.lastSignalAlertTime = now;
                        this.recentAlertTimes.add(now);
                        this.alertSink.accept(
                                new VanishTracker.VanishAlert(name, reason, false, weight));
                    }

                    EnumMap<VanishTracker.SignalKind, Integer> weightsByKind =
                            new EnumMap<>(VanishTracker.SignalKind.class);
                    VanishTracker.ActivitySignal regionalSignal = null;

                    for (VanishTracker.ActivitySignal signal : this.recentSignals) {
                        weightsByKind.merge(signal.kind, signal.weight, Math::max);
                        if (signal.regional) {
                            regionalSignal = signal;
                        }
                    }

                    int total = weightsByKind.values().stream().mapToInt(Integer::intValue).sum();
                    if (regionalSignal != null
                            && weightsByKind.size() >= 2
                            && total >= 35
                            && now - this.lastConfirmedAlertTime >= 12000L) {
                        this.lastConfirmedAlertTime = now;
                        this.alertSink.accept(
                                new VanishTracker.VanishAlert(
                                        regionalSignal.name,
                                        "Corroborated hidden activity",
                                        true,
                                        total));
                    }
                }
            }
        }
    }

    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        } else {
            String stripped =
                    Normalizer.normalize(name.replaceAll("\u00a7.", ""), Form.NFKD)
                            .replaceAll("\\p{M}", "")
                            .toLowerCase(Locale.ROOT);
            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < stripped.length(); i++) {
                char c = stripped.charAt(i);
                builder.append(
                        switch (c) {
                            case '\ua731', '\u0455' -> 's';
                            case '\u1d21', '\u0461' -> 'w';
                            case '\u1d20', '\u03bd' -> 'v';
                            case '\u1d22' -> 'z';
                            case '\u1d1c', '\u03c5' -> 'u';
                            case '\u1d18', '\u03c1', '\u0440' -> 'p';
                            case '\u1d1b', '\u03c4', '\u0442' -> 't';
                            case '\u1d04', '\u03f2', '\u0441' -> 'c';
                            case '\u1d05', '\u0501' -> 'd';
                            case '\u1d00', '\u0251', '\u03b1', '\u0430' -> 'a';
                            case '\u1d07', '\u03b5', '\u0435' -> 'e';
                            case '\u1d0b', '\u03ba' -> 'k';
                            case '\u1d0d', '\u043c' -> 'm';
                            case '\u1d0f', '\u03bf', '\u043e' -> 'o';
                            case '\u1d0a', '\u0458' -> 'j';
                            case '\u217c', '\u029f' -> 'l';
                            case '\u0280' -> 'r';
                            case '\u026a', '\u03b9', '\u0456' -> 'i';
                            case '\u0274', '\u0578' -> 'n';
                            case '\u029c', '\u04bb', '\u043d' -> 'h';
                            case '\u028f', '\u0443' -> 'y';
                            case '\u0261', '\u0262' -> 'g';
                            case '\u0299', '\u03b2', '\u044c' -> 'b';
                            case '\u03c7', '\u0445' -> 'x';
                            case '\u0493' -> 'f';
                            default -> c;
                        });
            }

            return builder.toString().replaceAll("\\s+", " ").trim();
        }
    }

    static {
        TRACKED_STAFF_NAMES =
                Set.of(
                        "fluffymaster07",
                        "archivepedro",
                        "munkerlich",
                        "frenk_btw",
                        "napooo_",
                        "auzzitech",
                        "cryptodaveyt",
                        "w1zox_",
                        "zeef69",
                        "showered",
                        "captainmoose35",
                        "bobisfound",
                        "noahvdaa",
                        "0gsummer",
                        "lzouzmp5",
                        "pastagamer08",
                        "u_vv",
                        "owen1212055",
                        "splaterd",
                        "fallerfly");
    }

    private record ActivitySignal(
            VanishTracker.SignalKind kind, String name, boolean regional, int weight, long time) {}

    private record ListedPlayer(String name, long listedSince, long seenAt) {}

    private record PendingRemoval(UUID id, long due) {}

    public static enum SignalKind {
        TAB,
        INVISIBLE,
        SOUND,
        PARTICLE;
    }

    private static final class Suspect {
        final UUID id;
        final String name;
        final String reason;
        final int score;
        final boolean spectator;
        boolean confirmed;

        Suspect(UUID id, String name, String reason, int score, boolean spectator) {
            this.id = id;
            this.name = name;
            this.reason = reason;
            this.score = score;
            this.spectator = spectator;
        }
    }

    public record VanishAlert(String name, String reason, boolean confirmed, int score) {}
}
