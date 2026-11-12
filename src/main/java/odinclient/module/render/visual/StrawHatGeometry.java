package odinclient.module.render.visual;

import java.util.ArrayList;
import java.util.List;

public final class StrawHatGeometry {
    public static final int RING_SEGMENTS = 64;
    public static final float BRIM_RADIUS = 0.485F;
    public static final long SPIN_PERIOD_NANOS = 24000000000L;
    private static final float BRIM_THICKNESS = 0.009F;
    private static final float[] PROFILE_RADII =
            new float[] {0.0F, 0.012F, 0.12F, 0.27F, 0.4F, 0.465F, 0.485F};
    private static final float[] PROFILE_HEIGHTS =
            new float[] {0.265F, 0.264F, 0.21F, 0.115F, 0.038F, 0.0F, -0.003F};
    private static final List<StrawHatGeometry.HatQuad> QUADS = buildQuads();
    private static final int TABLE_STEPS = 128;
    private static final float[] BARE_SEAT_TABLE = buildSeatHeightTable(0.0F);
    private static final float[] HAT_SEAT_TABLE = buildSeatHeightTable(0.03125F);
    private static final float[] HELMET_SEAT_TABLE = buildSeatHeightTable(0.0625F);

    private StrawHatGeometry() {}

    public static List<StrawHatGeometry.HatQuad> getQuads() {
        return QUADS;
    }

    public static StrawHatGeometry.HatPlacement getPlacement(
            float headPitch, boolean sneaking, boolean helmet) {
        return computePlacement(headPitch, sneaking, helmet, false);
    }

    public static StrawHatGeometry.HatPlacement computePlacement(
            float headPitch, boolean sneaking, boolean helmet, boolean hat) {
        float factor =
                sneaking && Float.isFinite(headPitch)
                        ? Math.min(
                                1.0F,
                                Math.max(0.0F, headPitch) * Math.max(0.0F, headPitch) / 100.0F)
                        : 0.0F;
        float[] table = helmet ? HELMET_SEAT_TABLE : (hat ? HAT_SEAT_TABLE : BARE_SEAT_TABLE);
        float scaled = factor * 128.0F;
        int index = Math.min(127, (int) scaled);
        float seatY = table[index] + (table[index + 1] - table[index]) * (scaled - index);
        return new StrawHatGeometry.HatPlacement(
                seatY, getRearOffset(factor), getBackwardTilt(factor));
    }

    private static float getRearOffset(float factor) {
        return 0.025F + 0.06F * factor;
    }

    private static float getBackwardTilt(float factor) {
        return (float) Math.toRadians(-5.0) * factor;
    }

    private static float[] buildSeatHeightTable(float lift) {
        float[] table = new float[129];

        for (int i = 0; i <= 128; i++) {
            float factor = i / 128.0F;
            table[i] =
                    -0.5F
                            - lift
                            - 0.002F
                            - getHeadSurfaceHeight(
                                    getRearOffset(factor), getBackwardTilt(factor), 0.25F + lift);
        }

        return table;
    }

    private static float getHeadSurfaceHeight(float rearOffset, float tilt, float halfWidth) {
        float cos = (float) Math.cos(tilt);
        float sin = (float) Math.sin(tilt);
        float maxHeight = Float.NEGATIVE_INFINITY;
        float[] points = new float[32];
        float[] scratch = new float[32];

        for (StrawHatGeometry.HatQuad quad : QUADS) {
            if (!(quad.a().ny() <= 0.0F)) {
                float minX = Float.POSITIVE_INFINITY;
                float maxX = Float.NEGATIVE_INFINITY;
                float minZ = Float.POSITIVE_INFINITY;
                float maxZ = Float.NEGATIVE_INFINITY;
                float maxY = Float.NEGATIVE_INFINITY;

                for (int i = 0; i < 4; i++) {
                    StrawHatGeometry.HatVertex vertex =
                            switch (i) {
                                case 0 -> quad.a();
                                case 1 -> quad.b();
                                case 2 -> quad.c();
                                default -> quad.d();
                            };
                    float ty = cos * vertex.y() - sin * vertex.z();
                    float tz = sin * vertex.y() + cos * vertex.z() + rearOffset;
                    points[i * 3] = vertex.x();
                    points[i * 3 + 1] = ty;
                    points[i * 3 + 2] = tz;
                    minX = Math.min(minX, vertex.x());
                    maxX = Math.max(maxX, vertex.x());
                    minZ = Math.min(minZ, tz);
                    maxZ = Math.max(maxZ, tz);
                    maxY = Math.max(maxY, ty);
                }

                if (!(maxY <= maxHeight)
                        && !(maxX < -halfWidth)
                        && !(minX > halfWidth)
                        && !(maxZ < -halfWidth)
                        && !(minZ > halfWidth)) {
                    int count = 4;

                    for (int plane = 0; plane < 4 && count > 0; plane++) {
                        int axis = plane < 2 ? 0 : 2;
                        float sign = (plane & 1) == 0 ? 1.0F : -1.0F;
                        int outCount = 0;
                        int prev = (count - 1) * 3;
                        float prevDist = halfWidth + sign * points[prev + axis];

                        for (int cur = 0; cur < count * 3; cur += 3) {
                            float curDist = halfWidth + sign * points[cur + axis];
                            if (prevDist >= 0.0F != curDist >= 0.0F) {
                                float t = prevDist / (prevDist - curDist);

                                for (int k = 0; k < 3; k++) {
                                    scratch[outCount * 3 + k] =
                                            points[prev + k]
                                                    + t * (points[cur + k] - points[prev + k]);
                                }

                                outCount++;
                            }

                            if (curDist >= 0.0F) {
                                System.arraycopy(points, cur, scratch, outCount * 3, 3);
                                outCount++;
                            }

                            prev = cur;
                            prevDist = curDist;
                        }

                        float[] swap = points;
                        points = scratch;
                        scratch = swap;
                        count = outCount;
                    }

                    for (int j = 0; j < count; j++) {
                        maxHeight = Math.max(maxHeight, points[j * 3 + 1]);
                    }
                }
            }
        }

        return maxHeight;
    }

    public static float getSpinAngle(long nanos) {
        return (float) (Math.floorMod(nanos, 24000000000L) * 6.283185307179586 / 2.4E10);
    }

    private static List<StrawHatGeometry.HatQuad> buildQuads() {
        List<StrawHatGeometry.HatQuad> quads = new ArrayList<>(832);

        for (int i = 0; i < 64; i++) {
            double angle = 6.283185307179586 * i / 64.0;
            double nextAngle = 6.283185307179586 * (i + 1) / 64.0;

            for (int ring = 0; ring < PROFILE_RADII.length - 1; ring++) {
                quads.add(
                        new StrawHatGeometry.HatQuad(
                                createBrimVertex(ring, angle, false),
                                createBrimVertex(ring + 1, angle, false),
                                createBrimVertex(ring + 1, nextAngle, false),
                                createBrimVertex(ring, nextAngle, false)));
                quads.add(
                        new StrawHatGeometry.HatQuad(
                                createBrimVertex(ring, angle, true),
                                createBrimVertex(ring, nextAngle, true),
                                createBrimVertex(ring + 1, nextAngle, true),
                                createBrimVertex(ring + 1, angle, true)));
            }

            quads.add(
                    new StrawHatGeometry.HatQuad(
                            createEdgeVertex(angle, false),
                            createEdgeVertex(angle, true),
                            createEdgeVertex(nextAngle, true),
                            createEdgeVertex(nextAngle, false)));
        }

        return List.copyOf(quads);
    }

    private static StrawHatGeometry.HatVertex createBrimVertex(
            int index, double angle, boolean underside) {
        float radius = PROFILE_RADII[index];
        float slope =
                index == 0
                        ? 0.0F
                        : (PROFILE_HEIGHTS[Math.max(0, index - 1)]
                                        - PROFILE_HEIGHTS[
                                                Math.min(PROFILE_RADII.length - 1, index + 1)])
                                / (PROFILE_RADII[Math.min(PROFILE_RADII.length - 1, index + 1)]
                                        - PROFILE_RADII[Math.max(0, index - 1)]);
        float length = (float) Math.sqrt(1.0F + slope * slope);
        float flip = underside ? -1.0F : 1.0F;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        double shade =
                1.0
                        + 0.012 * Math.cos(angle * 12.0 + radius * 8.0)
                        + 0.006 * Math.sin(angle * 25.0);
        int color =
                scaleColor(
                        underside
                                ? 14531158
                                : (index == PROFILE_RADII.length - 1 ? 14004038 : 15387226),
                        shade);
        return new StrawHatGeometry.HatVertex(
                radius * cos,
                -PROFILE_HEIGHTS[index] + (underside ? 0.009F : 0.0F),
                radius * sin,
                flip * slope * cos / length,
                -flip / length,
                flip * slope * sin / length,
                color);
    }

    private static StrawHatGeometry.HatVertex createEdgeVertex(double angle, boolean raised) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new StrawHatGeometry.HatVertex(
                0.485F * cos,
                -PROFILE_HEIGHTS[PROFILE_HEIGHTS.length - 1] + (raised ? 0.009F : 0.0F),
                0.485F * sin,
                cos,
                0.0F,
                sin,
                -3694265);
    }

    private static int scaleColor(int color, double factor) {
        int red = Math.min(255, (int) Math.round((color >> 16 & 0xFF) * factor));
        int green = Math.min(255, (int) Math.round((color >> 8 & 0xFF) * factor));
        int blue = Math.min(255, (int) Math.round((color & 0xFF) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public record HatPlacement(float seatY, float rearZ, float backwardTilt) {}

    public record HatQuad(
            StrawHatGeometry.HatVertex a,
            StrawHatGeometry.HatVertex b,
            StrawHatGeometry.HatVertex c,
            StrawHatGeometry.HatVertex d) {}

    public record HatVertex(float x, float y, float z, float nx, float ny, float nz, int color) {}
}
