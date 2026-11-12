package odinclient.module.render.itemglow;

import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;

import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HeldItemOutlineBuilder {
    private static final float EPSILON = 1.0E-5F;
    private static final Map<HeldItemOutlineBuilder.HullCacheKey, List<OutlineHull>> HULL_CACHE =
            new ConcurrentHashMap<>();

    private HeldItemOutlineBuilder() {}

    public static List<OutlineHull> buildHulls(List<BakedQuad> quads, float thickness) {
        if (quads != null && !quads.isEmpty()) {
            float scaledThickness = computeThickness(thickness);
            boolean flat = isFlatItem(quads);
            Object source = quads;
            SpriteContents contents = null;
            if (flat) {
                Sprite sprite = quads.get(0).sprite();
                if (sprite != null) {
                    contents = sprite.getContents();
                    if (contents != null) {
                        source = contents;
                    }
                }
            }

            if (HULL_CACHE.size() > 512) {
                HULL_CACHE.clear();
            }

            HeldItemOutlineBuilder.HullCacheKey key =
                    new HeldItemOutlineBuilder.HullCacheKey(
                            source,
                            Float.floatToIntBits(scaledThickness),
                            flat && contents != null);
            SpriteContents spriteContents = contents;
            return HULL_CACHE.computeIfAbsent(
                    key,
                    k ->
                            spriteContents != null
                                    ? buildSpriteHulls(spriteContents, scaledThickness)
                                    : buildQuadHulls(quads, scaledThickness));
        } else {
            return List.of();
        }
    }

    private static float computeThickness(float thickness) {
        float t = (Math.max(0.5F, Math.min(4.0F, thickness)) - 0.5F) / 3.5F;
        return 0.17678F * (float) Math.pow(4.0, t);
    }

    public static boolean isFlatItem(List<BakedQuad> quads) {
        if (quads != null && !quads.isEmpty()) {
            float minZ = 3.4028235E38F;
            float maxZ = -3.4028235E38F;

            for (BakedQuad quad : quads) {
                for (int i = 0; i < 4; i++) {
                    float z = quad.getPosition(i).z() * 16.0F;
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
            }

            return minZ >= 7.499F && maxZ <= 8.501F;
        } else {
            return false;
        }
    }

    private static List<OutlineHull> buildSpriteHulls(SpriteContents contents, float thickness) {
        int width = contents.getWidth();
        int height = contents.getHeight();
        int frame = contents.getDistinctFrameCount().findFirst().orElse(0);
        float scaleX = 16.0F / width;
        float scaleY = 16.0F / height;
        float pixelScale = Math.min(width / 16.0F, 16.0F);
        float edge = thickness * pixelScale * scaleX;
        float corner = 0.02F * scaleX;
        HeldItemOutlineBuilder.SolidMask mask =
                (x, y) -> !contents.isPixelTransparent(frame, x, height - 1 - y);
        return buildPixelHulls(
                width, height, mask, scaleX, scaleY, edge, corner, 7.55F - edge, 8.45F + edge);
    }

    private static List<OutlineHull> buildPixelHulls(
            int width,
            int height,
            HeldItemOutlineBuilder.SolidMask mask,
            float scaleX,
            float scaleY,
            float edge,
            float inset,
            float minZ,
            float maxZ) {
        List<OutlineHull> hulls = new ArrayList<>();

        for (int row = 0; row < height; row++) {
            addHorizontalEdgeBox(
                    hulls, width, height, mask, row, true, scaleX, scaleY, edge, inset, minZ, maxZ);
            addHorizontalEdgeBox(
                    hulls, width, height, mask, row, false, scaleX, scaleY, edge, inset, minZ,
                    maxZ);
        }

        for (int column = 0; column < width; column++) {
            addVerticalEdgeBox(
                    hulls, width, height, mask, column, true, scaleX, scaleY, edge, inset, minZ,
                    maxZ);
            addVerticalEdgeBox(
                    hulls, width, height, mask, column, false, scaleX, scaleY, edge, inset, minZ,
                    maxZ);
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isSolidPixel(mask, width, height, x, y)) {
                    boolean top = !isSolidPixel(mask, width, height, x, y + 1);
                    boolean bottom = !isSolidPixel(mask, width, height, x, y - 1);
                    boolean left = !isSolidPixel(mask, width, height, x - 1, y);
                    boolean right = !isSolidPixel(mask, width, height, x + 1, y);
                    float x0 = x * scaleX;
                    float x1 = x0 + scaleX;
                    float y0 = y * scaleY;
                    float y1 = y0 + scaleY;
                    if (top && right) {
                        addBox(hulls, x1 - inset, y1 - inset, x1 + edge, y1 + edge, minZ, maxZ);
                    }

                    if (top && left) {
                        addBox(hulls, x0 - edge, y1 - inset, x0 + inset, y1 + edge, minZ, maxZ);
                    }

                    if (bottom && right) {
                        addBox(hulls, x1 - inset, y0 - edge, x1 + edge, y0 + inset, minZ, maxZ);
                    }

                    if (bottom && left) {
                        addBox(hulls, x0 - edge, y0 - edge, x0 + inset, y0 + inset, minZ, maxZ);
                    }
                }
            }
        }

        return List.copyOf(hulls);
    }

    private static void addHorizontalEdgeBox(
            List<OutlineHull> hulls,
            int width,
            int height,
            HeldItemOutlineBuilder.SolidMask mask,
            int row,
            boolean upper,
            float scaleX,
            float scaleY,
            float outer,
            float inner,
            float minZ,
            float maxZ) {
        int x = 0;

        while (x < width) {
            boolean edgePixel =
                    isSolidPixel(mask, width, height, x, row)
                            && !isSolidPixel(mask, width, height, x, row + (upper ? 1 : -1));
            if (!edgePixel) {
                x++;
            } else {
                int start = x++;

                while (x < width
                        && isSolidPixel(mask, width, height, x, row)
                        && !isSolidPixel(mask, width, height, x, row + (upper ? 1 : -1))) {
                    x++;
                }

                float edgeY = (row + (upper ? 1 : 0)) * scaleY;
                addBox(
                        hulls,
                        start * scaleX,
                        edgeY - (upper ? inner : outer),
                        x * scaleX,
                        edgeY + (upper ? outer : inner),
                        minZ,
                        maxZ);
            }
        }
    }

    private static void addVerticalEdgeBox(
            List<OutlineHull> hulls,
            int width,
            int height,
            HeldItemOutlineBuilder.SolidMask mask,
            int column,
            boolean right,
            float scaleX,
            float scaleY,
            float outer,
            float inner,
            float minZ,
            float maxZ) {
        int y = 0;

        while (y < height) {
            boolean edgePixel =
                    isSolidPixel(mask, width, height, column, y)
                            && !isSolidPixel(mask, width, height, column + (right ? 1 : -1), y);
            if (!edgePixel) {
                y++;
            } else {
                int start = y++;

                while (y < height
                        && isSolidPixel(mask, width, height, column, y)
                        && !isSolidPixel(mask, width, height, column + (right ? 1 : -1), y)) {
                    y++;
                }

                float edgeX = (column + (right ? 1 : 0)) * scaleX;
                addBox(
                        hulls,
                        edgeX - (right ? inner : outer),
                        start * scaleY,
                        edgeX + (right ? outer : inner),
                        y * scaleY,
                        minZ,
                        maxZ);
            }
        }
    }

    private static void addBox(
            List<OutlineHull> hulls,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minZ,
            float maxZ) {
        hulls.add(OutlineHull.box(minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static boolean isSolidPixel(
            HeldItemOutlineBuilder.SolidMask mask, int width, int height, int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && mask.solid(x, y);
    }

    private static List<OutlineHull> buildQuadHulls(List<BakedQuad> quads, float thickness) {
        List<OutlineHull> hulls = new ArrayList<>(quads.size());

        for (BakedQuad quad : quads) {
            float[] points = new float[12];

            for (int i = 0; i < 4; i++) {
                Vector3fc pos = quad.getPosition(i);
                points[i * 3] = pos.x() * 16.0F;
                points[i * 3 + 1] = pos.y() * 16.0F;
                points[i * 3 + 2] = pos.z() * 16.0F;
            }

            float[] extruded = extrudeQuad(points, thickness);
            if (extruded != null) {
                hulls.add(new OutlineHull(extruded));
            }
        }

        return List.copyOf(hulls);
    }

    private static float[] extrudeQuad(float[] points, float thickness) {
        float[] u = subtractPoints(points, 1, points, 0);
        float[] v = subtractPoints(points, 3, points, 0);
        float[] normal = cross(u, v);
        if (!(length(u) < 1.0E-5F) && !(length(v) < 1.0E-5F) && !(length(normal) < 1.0E-5F)) {
            normalize(normal);
            normalize(u);
            float projection = dot(u, v);
            v[0] -= u[0] * projection;
            v[1] -= u[1] * projection;
            v[2] -= u[2] * projection;
            if (length(v) < 1.0E-5F) {
                return null;
            } else {
                normalize(v);
                float cx = (points[0] + points[3] + points[6] + points[9]) * 0.25F;
                float cy = (points[1] + points[4] + points[7] + points[10]) * 0.25F;
                float cz = (points[2] + points[5] + points[8] + points[11]) * 0.25F;
                float[] result = new float[24];

                for (int i = 0; i < 4; i++) {
                    float px = points[i * 3];
                    float py = points[i * 3 + 1];
                    float pz = points[i * 3 + 2];
                    float dx = px - cx;
                    float dy = py - cy;
                    float dz = pz - cz;
                    float offsetU =
                            dx * u[0] + dy * u[1] + dz * u[2] >= 0.0F ? thickness : -thickness;
                    float offsetV =
                            dx * v[0] + dy * v[1] + dz * v[2] >= 0.0F ? thickness : -thickness;
                    float x = px + u[0] * offsetU + v[0] * offsetV;
                    float y = py + u[1] * offsetU + v[1] * offsetV;
                    float z = pz + u[2] * offsetU + v[2] * offsetV;
                    result[i * 3] = x - normal[0] * thickness;
                    result[i * 3 + 1] = y - normal[1] * thickness;
                    result[i * 3 + 2] = z - normal[2] * thickness;
                    result[(i + 4) * 3] = x + normal[0] * thickness;
                    result[(i + 4) * 3 + 1] = y + normal[1] * thickness;
                    result[(i + 4) * 3 + 2] = z + normal[2] * thickness;
                }

                return result;
            }
        } else {
            return null;
        }
    }

    private static float[] subtractPoints(float[] a, int aIndex, float[] b, int bIndex) {
        return new float[] {
            a[aIndex * 3] - b[bIndex * 3],
            a[aIndex * 3 + 1] - b[bIndex * 3 + 1],
            a[aIndex * 3 + 2] - b[bIndex * 3 + 2]
        };
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {
            a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
        };
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float length(float[] vec) {
        return (float) Math.sqrt(dot(vec, vec));
    }

    private static void normalize(float[] vec) {
        float len = length(vec);
        vec[0] /= len;
        vec[1] /= len;
        vec[2] /= len;
    }

    private static final class HullCacheKey {
        private final Object source;
        private final int thicknessBits;
        private final boolean spriteBased;

        private HullCacheKey(Object source, int thicknessBits, boolean spriteBased) {
            this.source = source;
            this.thicknessBits = thicknessBits;
            this.spriteBased = spriteBased;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof HeldItemOutlineBuilder.HullCacheKey other
                    && this.source == other.source
                    && this.thicknessBits == other.thicknessBits
                    && this.spriteBased == other.spriteBased;
        }

        @Override
        public int hashCode() {
            return (System.identityHashCode(this.source) * 31 + this.thicknessBits) * 31
                    + (this.spriteBased ? 1 : 0);
        }
    }

    private interface SolidMask {
        boolean solid(int x, int y);
    }
}
