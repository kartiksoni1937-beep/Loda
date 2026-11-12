package odinclient.ui.draw;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.GuiGraphicsAccessor;
import odinclient.module.ModuleContext;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class ItemIconAtlas {
    private static final int ICON_SIZE = 128;
    private static final float TOP_BRIGHTNESS = 1.0F;
    private static final float LEFT_BRIGHTNESS = 0.82F;
    private static final float RIGHT_BRIGHTNESS = 0.66F;
    private static final float OUTLINE_THICKNESS = 2.3F;
    private static final int COLUMNS = 8;
    private static final Map<String, ItemIconAtlas.IconSource> sources = new LinkedHashMap<>();
    private static final Map<String, float[]> uvByName = new HashMap<>();
    private static NativeImageBackedTexture atlasTexture;
    private static TextureSetup atlasSetup;
    private static boolean built;

    private ItemIconAtlas() {}

    private static void item(String name, String texture) {
        sources.put(name, new ItemIconAtlas.IconSource(ItemIconAtlas.IconKind.ITEM, texture, null));
    }

    private static void block(String name, String topTexture, String sideTexture) {
        sources.put(
                name,
                new ItemIconAtlas.IconSource(
                        ItemIconAtlas.IconKind.BLOCK, topTexture, sideTexture));
    }

    public static boolean drawIcon(
            DrawContext context, String name, float centerX, float centerY, float size, int color) {
        if (color >>> 24 == 0) {
            return true;
        }

        buildAtlas();
        float[] uv = uvByName.get(name);
        if (uv == null || atlasSetup == null) {
            return false;
        }

        float half = size * 0.5F;
        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect scissor = SdfShapeRenderer.getCurrentScissor(context);
        ScreenRect bounds =
                new ScreenRect(
                                (int) Math.floor(centerX - half),
                                (int) Math.floor(centerY - half),
                                (int) Math.ceil(size) + 1,
                                (int) Math.ceil(size) + 1)
                        .transformEachVertex(pose);
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) {
                return true;
            }
        }

        ((GuiGraphicsAccessor) context)
                .odinclient$renderState()
                .addSimpleElement(
                        new ItemIconAtlas.IconRenderState(
                                RenderPipelines.GUI_TEXTURED,
                                atlasSetup,
                                pose,
                                centerX - half,
                                centerY - half,
                                centerX + half,
                                centerY + half,
                                uv,
                                color,
                                scissor,
                                bounds));
        return true;
    }

    private static void buildAtlas() {
        if (!built) {
            built = true;

            try {
                int rows = (sources.size() + 8 - 1) / 8;
                NativeImage image = new NativeImage(1024, rows * 128, true);
                int index = 0;

                for (Entry<String, ItemIconAtlas.IconSource> entry : sources.entrySet()) {
                    int[] pixels = render(entry.getValue());
                    if (pixels != null) {
                        int originX = index % 8 * 128;
                        int originY = index / 8 * 128;

                        for (int py = 0; py < 128; py++) {
                            for (int px = 0; px < 128; px++) {
                                image.setColorArgb(
                                        originX + px, originY + py, pixels[py * 128 + px]);
                            }
                        }

                        float atlasWidth = 1024.0F;
                        float atlasHeight = rows * 128;
                        uvByName.put(
                                entry.getKey(),
                                new float[] {
                                    originX / atlasWidth,
                                    originY / atlasHeight,
                                    (originX + 128) / atlasWidth,
                                    (originY + 128) / atlasHeight
                                });
                        index++;
                    }
                }

                atlasTexture = new NativeImageBackedTexture(() -> "odinclient item icons", image);
                atlasSetup =
                        TextureSetup.of(
                                atlasTexture.getGlTextureView(),
                                RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            } catch (Exception e) {
                ModuleContext.LOGGER.error("Failed to build OdinClient item icons", e);
            }
        }
    }

    private static int[] render(ItemIconAtlas.IconSource source) {
        try {
            return switch (source.kind) {
                case ITEM ->
                        upscaleItem(normalizeItemAlpha(loadTexture16(source.first, null), 1.0F));
                case BLOCK ->
                        renderIsometricCube(
                                normalizeBlockAlpha(loadTexture16(source.first, null), 1.0F),
                                normalizeBlockAlpha(loadTexture16(source.second, null), 0.82F),
                                normalizeBlockAlpha(loadTexture16(source.second, null), 0.66F),
                                1.0F);
                case CHEST -> chest();
                case HEAD -> playerHead();
                case BED -> bed();
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static int[] loadTexture16(String path, int[] region) throws Exception {
        Identifier id = Identifier.of("minecraft", "textures/" + path + ".png");
        Resource resource =
                MinecraftClient.getInstance().getResourceManager().getResource(id).orElseThrow();

        try (InputStream stream = resource.getInputStream();
                NativeImage image = NativeImage.read(stream); ) {
            int regionX = region == null ? 0 : region[0];
            int regionY = region == null ? 0 : region[1];
            int regionWidth = region == null ? image.getWidth() : region[2];
            int regionHeight =
                    region == null ? Math.min(image.getHeight(), image.getWidth()) : region[3];
            int[] pixels = new int[256];

            for (int py = 0; py < 16; py++) {
                for (int px = 0; px < 16; px++) {
                    pixels[py * 16 + px] =
                            image.getColorArgb(
                                    regionX + px * regionWidth / 16,
                                    regionY + py * regionHeight / 16);
                }
            }

            return pixels;
        }
    }

    private static int[] loadTextureRegion(String path, int x, int y, int width, int height)
            throws Exception {
        Identifier id = Identifier.of("minecraft", "textures/" + path + ".png");
        Resource resource =
                MinecraftClient.getInstance().getResourceManager().getResource(id).orElseThrow();

        int scale;
        try (InputStream stream = resource.getInputStream();
                NativeImage image = NativeImage.read(stream); ) {
            scale = Math.max(1, image.getWidth() / 64);
        }

        return loadTexture16(path, new int[] {x * scale, y * scale, width * scale, height * scale});
    }

    private static int[] normalizeItemAlpha(int[] pixels, float brightness) {
        return normalizeAlpha(pixels, brightness, 0.28F);
    }

    private static int[] normalizeBlockAlpha(int[] pixels, float brightness) {
        return normalizeAlpha(pixels, brightness, 0.7F);
    }

    private static int[] normalizeAlpha(int[] pixels, float brightness, float minAlpha) {
        float[] luminance = new float[pixels.length];
        float[] opaqueLuminance = new float[pixels.length];
        int opaqueCount = 0;

        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            luminance[i] =
                    ((argb >> 16 & 0xFF) * 0.299F
                                    + (argb >> 8 & 0xFF) * 0.587F
                                    + (argb & 0xFF) * 0.114F)
                            / 255.0F;
            if (argb >>> 24 > 24) {
                opaqueLuminance[opaqueCount++] = luminance[i];
            }
        }

        float low = 0.0F;
        float high = 1.0F;
        if (opaqueCount > 0) {
            float[] sorted = Arrays.copyOf(opaqueLuminance, opaqueCount);
            Arrays.sort(sorted);
            low = sorted[(int) (opaqueCount * 0.03F)];
            high = sorted[Math.min(opaqueCount - 1, (int) (opaqueCount * 0.97F))];
        }

        int[] result = new int[pixels.length];

        for (int j = 0; j < pixels.length; j++) {
            float alpha = (pixels[j] >>> 24) / 255.0F;
            float normalized =
                    Math.max(
                            0.0F,
                            Math.min(1.0F, (luminance[j] - low) / Math.max(high - low, 0.001F)));
            float factor =
                    (minAlpha + (1.0F - minAlpha) * (float) Math.pow(normalized, 0.85))
                            * brightness;
            result[j] = Math.round(alpha * factor * 255.0F) << 24 | 16777215;
        }

        return result;
    }

    private static int[] upscaleItem(int[] pixels) {
        int[] result = new int[16384];
        int scale = 7;
        int offset = (128 - scale * 16) / 2;

        for (int y = 0; y < 16 * scale; y++) {
            for (int x = 0; x < 16 * scale; x++) {
                result[(offset + y) * 128 + offset + x] = pixels[y / scale * 16 + x / scale];
            }
        }

        return result;
    }

    private static int[] renderIsometricCube(
            int[] top, int[] left, int[] right, float heightScale) {
        float size = 128.0F;
        float edge = 0.42F * size;
        float halfEdge = edge * 0.5F;
        float depth = edge * 1.2247F * heightScale;
        float centerX = 0.5F * size;
        float topY = (size - (halfEdge * 2.0F + depth)) * 0.5F;
        float[] topPoint = {centerX, topY};
        float[] leftPoint = {centerX - edge, topY + halfEdge};
        float[] bottomPoint = {centerX, topY + halfEdge * 2.0F};
        float[] rightPoint = {centerX + edge, topY + halfEdge};
        float[][] faces = {
            {leftPoint[0], leftPoint[1], topPoint[0], topPoint[1], bottomPoint[0], bottomPoint[1]},
            {
                leftPoint[0],
                leftPoint[1],
                bottomPoint[0],
                bottomPoint[1],
                leftPoint[0],
                leftPoint[1] + depth
            },
            {
                bottomPoint[0],
                bottomPoint[1],
                rightPoint[0],
                rightPoint[1],
                bottomPoint[0],
                bottomPoint[1] + depth
            }
        };
        int[][] textures = {top, left, right};
        int[] result = new int[16384];

        for (int face = 0; face < 3; face++) {
            float[] quad = faces[face];
            float ux = quad[2] - quad[0];
            float uy = quad[3] - quad[1];
            float vx = quad[4] - quad[0];
            float vy = quad[5] - quad[1];
            float det = ux * vy - uy * vx;

            for (int py = 0; py < 128; py++) {
                for (int px = 0; px < 128; px++) {
                    int alphaSum = 0;
                    int samples = 0;

                    for (int sy = 0; sy < 2; sy++) {
                        for (int sx = 0; sx < 2; sx++) {
                            float dx = px + 0.25F + sx * 0.5F - quad[0];
                            float dy = py + 0.25F + sy * 0.5F - quad[1];
                            float u = (dx * vy - dy * vx) / det;
                            float v = (ux * dy - uy * dx) / det;
                            if (u >= 0.0F && u < 1.0F && v >= 0.0F && v < 1.0F) {
                                alphaSum +=
                                        textures[face][
                                                        Math.min(15, (int) (v * 16.0F)) * 16
                                                                + Math.min(15, (int) (u * 16.0F))]
                                                >>> 24;
                                samples++;
                            }
                        }
                    }

                    if (samples != 0) {
                        int average = alphaSum / 4;
                        int current = result[py * 128 + px] >>> 24;
                        result[py * 128 + px] =
                                Math.min(
                                                        255,
                                                        Math.max(current, average)
                                                                + (samples < 4
                                                                        ? Math.min(current, average)
                                                                        : 0))
                                                << 24
                                        | 16777215;
                    }
                }
            }
        }

        float[][] points = {
            {faces[0][2], faces[0][3]},
            {faces[0][4], faces[0][5]},
            {faces[0][0], faces[0][1]},
            {faces[2][2], faces[2][3]},
            {faces[1][4], faces[1][5]},
            {faces[2][4], faces[2][5]},
            {faces[2][2], faces[2][3] + (faces[2][5] - faces[2][1])}
        };
        int[][] segments = {{0, 3}, {0, 2}, {2, 4}, {4, 5}, {5, 6}, {6, 3}, {1, 2}, {1, 3}, {1, 5}};

        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                float distance = Float.MAX_VALUE;

                for (int[] segment : segments) {
                    distance =
                            Math.min(
                                    distance,
                                    distanceToSegment(
                                            x + 0.5F,
                                            y + 0.5F,
                                            points[segment[0]],
                                            points[segment[1]]));
                }

                float outlineAlpha = Math.max(0.0F, Math.min(1.0F, 2.3F - distance + 0.5F));
                if (outlineAlpha > 0.0F) {
                    int existing = result[y * 128 + x] >>> 24;
                    int outline = Math.round(outlineAlpha * 250.0F);
                    result[y * 128 + x] = Math.max(existing, outline) << 24 | 16777215;
                }
            }
        }

        return result;
    }

    private static float distanceToSegment(float px, float py, float[] start, float[] end) {
        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        float t =
                Math.max(
                        0.0F,
                        Math.min(
                                1.0F,
                                ((px - start[0]) * dx + (py - start[1]) * dy)
                                        / (dx * dx + dy * dy)));
        float offsetX = start[0] + dx * t - px;
        float offsetY = start[1] + dy * t - py;
        return (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
    }

    private static int[] chest() throws Exception {
        String path = "entity/chest/normal";
        int[] lid = loadTextureRegion(path, 14, 14, 14, 5);
        int[] body = loadTextureRegion(path, 14, 33, 14, 10);
        int[] latch = loadTextureRegion(path, 1, 1, 2, 4);
        int[] front = new int[256];

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = y < 5 ? lid[y * 16 / 5 * 16 + x] : body[(y - 5) * 16 / 11 * 16 + x];
                if (x >= 7 && x < 9 && y >= 3 && y < 7) {
                    color = latch[(y - 3) * 4 * 16 + (x - 7) * 8];
                }

                front[y * 16 + x] = color;
            }
        }

        int[] top = loadTextureRegion(path, 14, 0, 14, 14);
        return renderIsometricCube(
                normalizeBlockAlpha(top, 1.0F),
                normalizeBlockAlpha(front, 0.82F),
                normalizeBlockAlpha(front, 0.66F),
                1.0F);
    }

    private static int[] playerHead() throws Exception {
        String path = "entity/player/wide/steve";
        int[] top =
                overlayLayer(
                        loadTextureRegion(path, 8, 0, 8, 8), loadTextureRegion(path, 40, 0, 8, 8));
        int[] left =
                overlayLayer(
                        loadTextureRegion(path, 0, 8, 8, 8), loadTextureRegion(path, 32, 8, 8, 8));
        int[] front =
                overlayLayer(
                        loadTextureRegion(path, 8, 8, 8, 8), loadTextureRegion(path, 40, 8, 8, 8));
        return renderIsometricCube(
                normalizeBlockAlpha(top, 1.0F),
                normalizeBlockAlpha(left, 0.82F),
                normalizeBlockAlpha(front, 0.66F),
                1.0F);
    }

    private static int[] bed() throws Exception {
        String path = "entity/bed/red";
        int[] head = loadTextureRegion(path, 6, 6, 16, 16);
        int[] foot = loadTextureRegion(path, 6, 28, 16, 16);
        int[] top = new int[256];

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                top[y * 16 + x] = y < 8 ? head[y * 2 * 16 + x] : foot[(y - 8) * 2 * 16 + x];
            }
        }

        int[] side = loadTextureRegion(path, 22, 22, 16, 6);
        return renderIsometricCube(
                normalizeBlockAlpha(top, 1.0F),
                normalizeBlockAlpha(side, 0.82F),
                normalizeBlockAlpha(side, 0.66F),
                0.5F);
    }

    private static int[] overlayLayer(int[] base, int[] overlay) {
        int[] result = base.clone();

        for (int i = 0; i < result.length; i++) {
            if (overlay[i] >>> 24 > 24) {
                result[i] = overlay[i];
            }
        }

        return result;
    }

    static {
        item("totem_of_undying", "item/totem_of_undying");
        item("iron_axe", "item/iron_axe");
        block("target", "block/target_top", "block/target_side");
        item("mace", "item/mace");
        item("end_crystal", "item/end_crystal");
        block("respawn_anchor", "block/respawn_anchor_top", "block/respawn_anchor_side0");
        item("spyglass", "item/spyglass");
        item("ender_eye", "item/ender_eye");
        item("clock", "item/clock_00");
        item("sugar", "item/sugar");
        item("redstone_torch", "block/redstone_torch");
        item("torch", "block/torch");
        item("diamond_pickaxe", "item/diamond_pickaxe");
        item("diamond_sword", "item/diamond_sword");
        sources.put(
                "red_bed", new ItemIconAtlas.IconSource(ItemIconAtlas.IconKind.BED, null, null));
        item("chorus_fruit", "item/chorus_fruit");
        item("ender_pearl", "item/ender_pearl");
        sources.put(
                "player_head",
                new ItemIconAtlas.IconSource(ItemIconAtlas.IconKind.HEAD, null, null));
        item("name_tag", "item/name_tag");
        block("diamond_ore", "block/diamond_ore", "block/diamond_ore");
        block("glass", "block/glass", "block/glass");
        item("glow_ink_sac", "item/glow_ink_sac");
        sources.put(
                "chest", new ItemIconAtlas.IconSource(ItemIconAtlas.IconKind.CHEST, null, null));
        block("bedrock", "block/bedrock", "block/bedrock");
        item("ladder", "block/ladder");
        item("feather", "item/feather");
        block("glowstone", "block/glowstone", "block/glowstone");
        item("painting", "item/painting");
        block("structure_block", "block/structure_block", "block/structure_block");
        block("suspicious_sand", "block/suspicious_sand_0", "block/suspicious_sand_0");
        item("zombie_spawn_egg", "item/zombie_spawn_egg");
        block("spawner", "block/spawner", "block/spawner");
        item("bell", "item/bell");
        item("lightning_rod", "block/lightning_rod");
        item("amethyst_cluster", "block/amethyst_cluster");
        block("ancient_debris", "block/ancient_debris_top", "block/ancient_debris_side");
        item("filled_map", "item/filled_map");
        item("recovery_compass", "item/recovery_compass_00");
        item("item_frame", "item/item_frame");
        item("redstone", "item/redstone");
        block("jukebox", "block/jukebox_top", "block/jukebox_side");
        item("book", "item/book");
        item("shears", "item/shears");
        item("writable_book", "item/writable_book");
        item("nether_star", "item/nether_star");
        item("compass", "item/compass_00");
        item("music_disc", "item/music_disc_cat");
        item("enchanted_book", "item/enchanted_book");
    }

    private static enum IconKind {
        ITEM,
        BLOCK,
        CHEST,
        HEAD,
        BED;
    }

    private record IconRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2fc pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float[] uv,
            int color,
            ScreenRect scissor,
            ScreenRect bounds)
            implements SimpleGuiElementRenderState {
        @Override
        public void setupVertices(VertexConsumer vertices) {
            vertices.vertex(this.pose, this.x0, this.y0)
                    .texture(this.uv[0], this.uv[1])
                    .color(this.color);
            vertices.vertex(this.pose, this.x0, this.y1)
                    .texture(this.uv[0], this.uv[3])
                    .color(this.color);
            vertices.vertex(this.pose, this.x1, this.y1)
                    .texture(this.uv[2], this.uv[3])
                    .color(this.color);
            vertices.vertex(this.pose, this.x1, this.y0)
                    .texture(this.uv[2], this.uv[1])
                    .color(this.color);
        }

        @Override
        public ScreenRect scissorArea() {
            return this.scissor;
        }
    }

    private record IconSource(ItemIconAtlas.IconKind kind, String first, String second) {}
}
