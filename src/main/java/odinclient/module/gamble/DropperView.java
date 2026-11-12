package odinclient.module.gamble;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

public final class DropperView<T, S> {
    private final T[] appearances;
    private final int targetSlot;
    private final BiPredicate<S, S> sameItemTest;
    private final ToIntFunction<S> countGetter;
    private List<S> snapshot;
    private boolean revealed;

    public DropperView(
            T[] appearances,
            int targetSlot,
            BiPredicate<S, S> sameItemTest,
            ToIntFunction<S> countGetter) {
        if (appearances.length == 9 && targetSlot >= 0 && targetSlot < 9) {
            this.appearances = appearances.clone();
            this.targetSlot = targetSlot;
            this.sameItemTest = sameItemTest;
            this.countGetter = countGetter;
        } else {
            throw new IllegalArgumentException("A dropper has nine row-major slots.");
        }
    }

    public static int slotIndex(int column, int row) {
        return column >= 0 && column < 3 && row >= 0 && row < 3 ? row * 3 + column : -1;
    }

    public DropperView.DropperSlotView<T> viewSlot(int slot, int count) {
        if (slot >= 0 && slot < 9 && this.appearances[slot] != null) {
            boolean empty = this.revealed ? count <= 0 : slot == this.targetSlot;
            return new DropperView.DropperSlotView<>(
                    this.appearances[slot], empty, empty ? 0 : Math.max(1, count));
        } else {
            return null;
        }
    }

    public DropperView.DropperSlotView<T> viewSlotFromSnapshot(int slot) {
        int count =
                this.snapshot != null && slot >= 0 && slot < 9
                        ? this.countGetter.applyAsInt(this.snapshot.get(slot))
                        : 0;
        return this.viewSlot(slot, (byte) count);
    }

    public void markTaken(int slot) {
        if (slot == this.targetSlot) {
            this.revealed = true;
        }
    }

    public void updateSnapshot(List<S> slots) {
        if (slots.size() != 9) {
            throw new IllegalArgumentException("Expected nine slots.");
        } else {
            if (!this.revealed && this.snapshot != null) {
                for (int i = 0; i < 9; i++) {
                    S before = this.snapshot.get(i);
                    S after = slots.get(i);
                    int oldCount = this.countGetter.applyAsInt(before);
                    int newCount = this.countGetter.applyAsInt(after);
                    boolean taken =
                            newCount > 0
                                    && (newCount > oldCount
                                            || oldCount > 0
                                                    && !this.sameItemTest.test(before, after));
                    if (taken && (i == this.targetSlot || oldCount <= 0)) {
                        this.revealed = true;
                        break;
                    }
                }
            }

            this.snapshot = List.copyOf(slots);
        }
    }

    public record DropperSlotView<T>(T appearance, boolean empty, int count) {}
}
