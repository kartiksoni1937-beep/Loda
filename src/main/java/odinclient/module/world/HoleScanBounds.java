package odinclient.module.world;

public final class HoleScanBounds {
    public static final int MIN_Y = 18;
    public static final int MAX_Y = 50;
    public static final int MAX_ENCLOSURE_RADIUS = 3;

    private HoleScanBounds() {}

    public static boolean isInScanRange(int y) {
        return y >= MIN_Y && y <= MAX_Y;
    }

    public static boolean isEnclosureValid(int radius, boolean flag, boolean enclosed) {
        return enclosed && (radius != 1 || !flag);
    }
}
