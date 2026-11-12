package odinclient.module.gamble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

public final class ItemSlotTracker<S, V> {
    private static final long EXPIRY_NANOS = 3000000000L;
    private static final int MAX_PENDING = 128;
    private final BiPredicate<S, S> itemMatcher;
    private final ToIntFunction<S> stackCounter;
    private List<S> lastSnapshot;
    private final Map<Integer, ItemSlotTracker.TrackedSlot<S, V>> trackedSlots = new HashMap<>();
    private final List<ItemSlotTracker.PendingPickup<S, V>> pendingPickups = new ArrayList<>();
    private final List<ItemSlotTracker.PendingGain<S>> pendingGains = new ArrayList<>();

    public ItemSlotTracker(BiPredicate<S, S> itemMatcher, ToIntFunction<S> stackCounter) {
        this.itemMatcher = itemMatcher;
        this.stackCounter = stackCounter;
    }

    public void reset() {
        this.lastSnapshot = null;
        this.trackedSlots.clear();
        this.pendingPickups.clear();
        this.pendingGains.clear();
    }

    public Map<Integer, V> getAppearances() {
        HashMap<Integer, V> appearances = new HashMap<>();
        this.trackedSlots.forEach((slot, tracked) -> appearances.put(slot, tracked.appearance));
        return appearances;
    }

    public void recordPickup(S item, V appearance, int count, long time) {
        if (count > 0 && this.stackCounter.applyAsInt(item) > 0) {
            if (this.pendingPickups.size() == MAX_PENDING) {
                this.pendingPickups.removeFirst();
            }

            this.pendingPickups.add(
                    new ItemSlotTracker.PendingPickup<>(item, appearance, count, time));
            this.matchPickups(time);
        }
    }

    public void updateSnapshot(List<S> snapshot, long time) {
        if (this.lastSnapshot == null) {
            this.lastSnapshot = List.copyOf(snapshot);
        } else {
            HashMap<Integer, ItemSlotTracker.TrackedSlot<S, V>> updated = new HashMap<>();
            this.trackedSlots.forEach(
                    (slot, tracked) -> {
                        if (slot < snapshot.size()
                                && this.itemMatcher.test(snapshot.get(slot), tracked.actual)) {
                            updated.put(
                                    slot,
                                    new ItemSlotTracker.TrackedSlot<>(
                                            snapshot.get(slot), tracked.appearance));
                        }
                    });
            Iterator<Entry<Integer, ItemSlotTracker.TrackedSlot<S, V>>> iterator =
                    this.trackedSlots.entrySet().iterator();

            while (iterator.hasNext()) {
                ItemSlotTracker.TrackedSlot<S, V> trackedSlot = iterator.next().getValue();
                if (this.countMatching(snapshot, trackedSlot.actual)
                        <= this.countMatching(this.lastSnapshot, trackedSlot.actual)) {
                    for (int i = 0; i < snapshot.size(); i++) {
                        if (!updated.containsKey(i)
                                && this.itemMatcher.test(snapshot.get(i), trackedSlot.actual)
                                && (i >= this.lastSnapshot.size()
                                        || !this.itemMatcher.test(
                                                this.lastSnapshot.get(i), trackedSlot.actual)
                                        || this.stackCounter.applyAsInt(snapshot.get(i))
                                                > this.stackCounter.applyAsInt(
                                                        this.lastSnapshot.get(i)))) {
                            updated.put(
                                    i,
                                    new ItemSlotTracker.TrackedSlot<>(
                                            snapshot.get(i), trackedSlot.appearance));
                        }
                    }
                }
            }

            this.trackedSlots.clear();
            this.trackedSlots.putAll(updated);
            ArrayList<S> seen = new ArrayList<>();

            for (int index = 0; index < snapshot.size(); index++) {
                S item = snapshot.get(index);
                if (this.stackCounter.applyAsInt(item) > 0) {
                    if (seen.stream().noneMatch(other -> this.itemMatcher.test(other, item))) {
                        seen.add(item);
                        int gained =
                                this.countMatching(snapshot, item)
                                        - this.countMatching(this.lastSnapshot, item);
                        if (gained > 0) {
                            ArrayList<Integer> grownSlots = new ArrayList<>();

                            for (int j = 0; j < snapshot.size(); j++) {
                                if (this.itemMatcher.test(snapshot.get(j), item)) {
                                    int previousCount =
                                            j < this.lastSnapshot.size()
                                                            && this.itemMatcher.test(
                                                                    this.lastSnapshot.get(j), item)
                                                    ? this.stackCounter.applyAsInt(
                                                            this.lastSnapshot.get(j))
                                                    : 0;
                                    if (this.stackCounter.applyAsInt(snapshot.get(j))
                                            > (byte) previousCount) {
                                        grownSlots.add(j);
                                    }
                                }
                            }

                            if (this.pendingGains.size() == MAX_PENDING) {
                                this.pendingGains.removeFirst();
                            }

                            this.pendingGains.add(
                                    new ItemSlotTracker.PendingGain<>(
                                            item, grownSlots, gained, time));
                        }
                    }
                }
            }

            this.lastSnapshot = List.copyOf(snapshot);
            this.matchPickups(time);
        }
    }

    private int countMatching(List<S> list, S item) {
        int total = 0;

        for (S entry : list) {
            if (this.itemMatcher.test(entry, item)) {
                total += this.stackCounter.applyAsInt(entry);
            }
        }

        return total;
    }

    private void matchPickups(long now) {
        this.pendingPickups.removeIf(
                p -> now - p.pickupTime > EXPIRY_NANOS || p.remainingPickup <= 0);
        this.pendingGains.removeIf(g -> now - g.gainTime > EXPIRY_NANOS || g.remainingGain <= 0);
        if (this.lastSnapshot != null) {
            for (ItemSlotTracker.PendingPickup<S, V> pickup : this.pendingPickups) {
                for (ItemSlotTracker.PendingGain<S> pendingGain : this.pendingGains) {
                    if (pickup.remainingPickup > 0
                            && pendingGain.remainingGain > 0
                            && this.itemMatcher.test(pendingGain.item, pickup.pickupItem)) {
                        ArrayList<Integer> candidates = new ArrayList<>(pendingGain.slotIndices);

                        for (int k = 0; k < this.lastSnapshot.size(); k++) {
                            if (!candidates.contains(k)) {
                                candidates.add(k);
                            }
                        }

                        for (int slot : candidates) {
                            S stack = this.lastSnapshot.get(slot);
                            if (this.itemMatcher.test(stack, pickup.pickupItem)
                                    && !this.trackedSlots.containsKey(slot)) {
                                this.trackedSlots.put(
                                        slot,
                                        new ItemSlotTracker.TrackedSlot<>(
                                                stack, pickup.pickupAppearance));
                                int taken =
                                        Math.min(
                                                this.stackCounter.applyAsInt(stack),
                                                Math.min(
                                                        pickup.remainingPickup,
                                                        pendingGain.remainingGain));
                                pickup.remainingPickup -= taken;
                                pendingGain.remainingGain -= taken;
                                if (pickup.remainingPickup <= 0 || pendingGain.remainingGain <= 0) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            this.pendingPickups.removeIf(p -> p.remainingPickup <= 0);
            this.pendingGains.removeIf(g -> g.remainingGain <= 0);
        }
    }

    private static final class PendingGain<S> {
        final S item;
        final List<Integer> slotIndices;
        final long gainTime;
        int remainingGain;

        PendingGain(S item, List<Integer> slotIndices, int gain, long gainTime) {
            this.item = item;
            this.slotIndices = slotIndices;
            this.remainingGain = gain;
            this.gainTime = gainTime;
        }
    }

    private static final class PendingPickup<S, V> {
        final S pickupItem;
        final V pickupAppearance;
        final long pickupTime;
        int remainingPickup;

        PendingPickup(S pickupItem, V pickupAppearance, int pickup, long pickupTime) {
            this.pickupItem = pickupItem;
            this.pickupAppearance = pickupAppearance;
            this.remainingPickup = pickup;
            this.pickupTime = pickupTime;
        }
    }

    private record TrackedSlot<S, V>(S actual, V appearance) {}
}
