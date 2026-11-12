package odinclient.module.world;

import java.util.function.BiFunction;
import java.util.function.IntPredicate;

public final class FarmDensityCheck {
    public static final int GRID_SIZE = 3;
    public static final int NEIGHBOR_RADIUS = 1;
    public static final int MIN_CACTUS_COUNT = 5;
    public static final int MIN_SUGAR_CANE_COUNT = 3;

    private FarmDensityCheck() {}

    public static FarmDensityCheck.CropCounts sumNeighborhood(
            int centerX,
            int centerZ,
            BiFunction<Integer, Integer, FarmDensityCheck.CropCounts> lookup) {
        long cactusTotal = 0L;
        long sugarCaneTotal = 0L;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long x = (long) centerX + dx;
                long z = (long) centerZ + dz;
                if (x >= -2147483648L
                        && x <= 2147483647L
                        && z >= -2147483648L
                        && z <= 2147483647L) {
                    FarmDensityCheck.CropCounts counts = lookup.apply((int) x, (int) z);
                    if (counts != null) {
                        cactusTotal += Math.max(0, counts.cactus);
                        sugarCaneTotal += Math.max(0, counts.sugarCane);
                    }
                }
            }
        }

        return new FarmDensityCheck.CropCounts(
                (int) Math.min(2147483647L, cactusTotal),
                (int) Math.min(2147483647L, sugarCaneTotal));
    }

    public static boolean hasClearColumn(int offset, int min, int max, IntPredicate blocked) {
        if (offset >= min && offset <= max - 3L) {
            if (offset > min && blocked.test(-1)) {
                return false;
            } else {
                for (int i = 0; i < 3; i++) {
                    if (!blocked.test(i)) {
                        return false;
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    public record CropCounts(int cactus, int sugarCane) {
        public boolean enoughCactus() {
            return this.cactus >= 5;
        }

        public boolean enoughSugarCane() {
            return this.sugarCane >= 3;
        }
    }
}
