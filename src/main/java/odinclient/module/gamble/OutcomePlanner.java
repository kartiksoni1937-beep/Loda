package odinclient.module.gamble;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

public final class OutcomePlanner<T> {
    private OutcomePlanner.PlannedOutcome<T> currentPlan;
    private OutcomePlanner.SlotPair lastPair;

    public OutcomePlanner.PlannedOutcome<T> plan(
            OutcomePlanner.Outcome outcome, T[] lefts, T[] rights, RandomGenerator random) {
        this.currentPlan = null;
        List<OutcomePlanner.SlotPair> candidates = new ArrayList<>();

        for (int i = 0; i < Math.min(9, lefts.length); i++) {
            for (int j = 0; j < Math.min(9, rights.length); j++) {
                if (lefts[i] != null
                        && rights[j] != null
                        && (outcome == OutcomePlanner.Outcome.LOSE && i < j
                                || outcome == OutcomePlanner.Outcome.TIE && i == j
                                || outcome == OutcomePlanner.Outcome.WIN && i > j)) {
                    candidates.add(new OutcomePlanner.SlotPair(i, j));
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        } else {
            if (this.lastPair != null) {
                Predicate<OutcomePlanner.SlotPair> differentSlots =
                        pair ->
                                pair.left != this.lastPair.left
                                        && pair.right != this.lastPair.right;
                List<OutcomePlanner.SlotPair> filtered =
                        candidates.stream().filter(differentSlots).toList();
                if (!filtered.isEmpty()) {
                    candidates = filtered;
                } else if (candidates.size() > 1) {
                    candidates.remove(this.lastPair);
                }
            }

            OutcomePlanner.SlotPair chosen = candidates.get(random.nextInt(candidates.size()));
            OutcomePlanner.PlannedOutcome<T> planned =
                    new OutcomePlanner.PlannedOutcome<>(
                            outcome, chosen, lefts[chosen.left], rights[chosen.right]);
            this.currentPlan = planned;
            return planned;
        }
    }

    public boolean hasPlan() {
        return this.currentPlan != null;
    }

    public void clearPlan() {
        this.currentPlan = null;
    }

    public void reset() {
        this.currentPlan = null;
        this.lastPair = null;
    }

    public OutcomePlanner.ActiveRoll<T> start(
            OutcomePlanner.DropPoint left, OutcomePlanner.DropPoint right, long now) {
        if (this.currentPlan == null) {
            return null;
        } else {
            OutcomePlanner.PlannedOutcome<T> planned = this.currentPlan;
            this.currentPlan = null;
            this.lastPair = planned.pair;
            return new OutcomePlanner.ActiveRoll<>(planned, left, right, now);
        }
    }

    public static final class ActiveRoll<T> {
        public static final long TIMEOUT_NANOS = 15000000000L;
        private final OutcomePlanner.PlannedOutcome<T> plan;
        private final OutcomePlanner.DropPoint leftDropper;
        private final OutcomePlanner.DropPoint rightDropper;
        private final long startedAt;
        private final boolean[] claimed = new boolean[2];
        private final int[] claimedEntityIds;

        private ActiveRoll(
                OutcomePlanner.PlannedOutcome<T> plan,
                OutcomePlanner.DropPoint leftDropper,
                OutcomePlanner.DropPoint rightDropper,
                long startedAt) {
            this.claimedEntityIds = new int[] {-1, -1};
            this.plan = plan;
            this.leftDropper = leftDropper;
            this.rightDropper = rightDropper;
            this.startedAt = startedAt;
        }

        public boolean isExpired(long now) {
            return now - this.startedAt > TIMEOUT_NANOS;
        }

        public OutcomePlanner.PlannedOutcome<T> getPlan() {
            return this.plan;
        }

        public int getDropperIndex(int entityId) {
            for (int i = 0; i < 2; i++) {
                if (this.claimed[i] && this.claimedEntityIds[i] == entityId) {
                    return i;
                }
            }

            return -1;
        }

        public T claimItem(int entityId, OutcomePlanner.DropPoint point, long now) {
            if (!this.isExpired(now)
                    && entityId != this.claimedEntityIds[0]
                    && entityId != this.claimedEntityIds[1]) {
                double leftDistance = point.distanceSquared(this.leftDropper);
                double rightDistance = point.distanceSquared(this.rightDropper);
                if (!(Math.min(leftDistance, rightDistance) > 0.64)
                        && !(Math.abs(leftDistance - rightDistance) < 1.0E-6)) {
                    int index = leftDistance < rightDistance ? 0 : 1;
                    if (this.claimed[index]) {
                        return null;
                    } else {
                        this.claimed[index] = true;
                        this.claimedEntityIds[index] = entityId;
                        return index == 0 ? this.plan.left : this.plan.right;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    public record DropPoint(double x, double y, double z) {
        double distanceSquared(OutcomePlanner.DropPoint other) {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public static enum Outcome {
        LOSE,
        TIE,
        WIN;
    }

    public record PlannedOutcome<T>(
            OutcomePlanner.Outcome outcome, OutcomePlanner.SlotPair pair, T left, T right) {}

    public record SlotPair(int left, int right) {}
}
