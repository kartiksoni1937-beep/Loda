package odinclient.module.render.itemglow;

public record OutlineHull(float[] corners) {
    public static final int[][] FACES;

    public OutlineHull(float[] corners) {
        if (corners.length != 24) {
            throw new IllegalArgumentException("A held-item outline hull needs eight corners.");
        }

        this.corners = corners;
    }

    public static OutlineHull box(
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return new OutlineHull(
                new float[] {
                    minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, minX,
                    minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ
                });
    }

    public float x(int index) {
        return this.corners[index * 3];
    }

    public float y(int index) {
        return this.corners[index * 3 + 1];
    }

    public float z(int index) {
        return this.corners[index * 3 + 2];
    }

    static {
        FACES =
                new int[][] {
                    {0, 1, 2, 3},
                    {4, 5, 6, 7},
                    {0, 1, 5, 4},
                    {1, 2, 6, 5},
                    {2, 3, 7, 6},
                    {3, 0, 4, 7}
                };
    }
}
