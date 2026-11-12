package odinclient.ui.draw;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.GuiGraphicsAccessor;
import odinclient.mixin.accessor.ShaderPipelineInvoker;
import odinclient.module.ModuleContext;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class SdfFontRenderer {
    private static final float BASELINE_OFFSET = 7.0F;
    private static final int MAX_CACHE_SIZE = 2048;
    private static final VertexFormat VERTEX_FORMAT =
            VertexFormat.builder()
                    .add("Position", VertexFormatElement.POSITION)
                    .add("UV0", VertexFormatElement.UV0)
                    .add("Color", VertexFormatElement.COLOR)
                    .build();
    private static final Map<SdfFontRenderer.FontAtlasType, SdfFontRenderer.FontAtlas> ATLASES =
            new EnumMap<>(SdfFontRenderer.FontAtlasType.class);
    private static final Map<SdfFontRenderer.FontStyle, Map<String, Float>> WIDTH_CACHE =
            new EnumMap<>(SdfFontRenderer.FontStyle.class);
    private static RenderPipeline pipeline;

    private SdfFontRenderer() {}

    public static void preloadResources() {
        getAtlas(SdfFontRenderer.FontAtlasType.BRAND);
        getPipeline();
        width(SdfFontRenderer.FontStyle.INTRO, "DIN");
        width(SdfFontRenderer.FontStyle.INTRO, "LIENT");
    }

    public static float width(SdfFontRenderer.FontStyle style, String text) {
        if (text != null && !text.isEmpty()) {
            Map<String, Float> cache = WIDTH_CACHE.computeIfAbsent(style, k -> new HashMap<>());
            Float cached = cache.get(text);
            if (cached == null) {
                if (cache.size() >= MAX_CACHE_SIZE) {
                    cache.clear();
                }

                SdfFontRenderer.FontAtlas atlas = getAtlas(style.atlas);
                float total = 0.0F;

                for (int i = 0; i < text.length(); i++) {
                    total += atlas.getGlyph(text.charAt(i)).advance;
                }

                cached = total * style.size;
                cache.put(text, cached);
            }

            return cached;
        } else {
            return 0.0F;
        }
    }

    public static void drawTextAtTop(
            DrawContext context,
            SdfFontRenderer.FontStyle style,
            String text,
            float x,
            float y,
            int color) {
        if (text != null && !text.isEmpty() && color >>> 24 != 0) {
            SdfFontRenderer.FontAtlas atlas = getAtlas(style.atlas);
            if (atlas.texture != null) {
                float size = style.size;
                float baseline = y + BASELINE_OFFSET;
                float[] quads = new float[text.length() * 8];
                int count = 0;
                float cursor = x;
                float minX = Float.MAX_VALUE;
                float minY = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;

                for (int i = 0; i < text.length(); i++) {
                    SdfFontRenderer.Glyph glyph = atlas.getGlyph(text.charAt(i));
                    if (glyph.visible) {
                        int offset = count * 8;
                        quads[offset] = cursor + glyph.left * size;
                        quads[offset + 1] = baseline + glyph.top * size;
                        quads[offset + 2] = cursor + glyph.right * size;
                        quads[offset + 3] = baseline + glyph.bottom * size;
                        quads[offset + 4] = glyph.u0;
                        quads[offset + 5] = glyph.v0;
                        quads[offset + 6] = glyph.u1;
                        quads[offset + 7] = glyph.v1;
                        minX = Math.min(minX, quads[offset]);
                        minY = Math.min(minY, quads[offset + 1]);
                        maxX = Math.max(maxX, quads[offset + 2]);
                        maxY = Math.max(maxY, quads[offset + 3]);
                        count++;
                    }

                    cursor += glyph.advance * size;
                }

                if (count != 0) {
                    Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
                    ScreenRect scissor = SdfShapeRenderer.getCurrentScissor(context);
                    ScreenRect bounds =
                            new ScreenRect(
                                            (int) Math.floor(minX),
                                            (int) Math.floor(minY),
                                            (int) Math.ceil(maxX - minX) + 1,
                                            (int) Math.ceil(maxY - minY) + 1)
                                    .transformEachVertex(pose);
                    if (scissor != null) {
                        bounds = scissor.intersection(bounds);
                        if (bounds == null) {
                            return;
                        }
                    }

                    SdfFontRenderer.SdfTextRenderState state =
                            new SdfFontRenderer.SdfTextRenderState(
                                    getPipeline(),
                                    atlas.textureSetup,
                                    pose,
                                    quads,
                                    count,
                                    color,
                                    scissor,
                                    bounds);
                    ((GuiGraphicsAccessor) context)
                            .odinclient$renderState()
                            .addSimpleElement(state);
                }
            }
        }
    }

    public static void drawTextCenteredY(
            DrawContext context,
            SdfFontRenderer.FontStyle style,
            String text,
            float x,
            float y,
            int color) {
        drawTextAtTop(context, style, text, x, y - style.capTop - style.capHeight * 0.5F, color);
    }

    public static void drawTextCentered(
            DrawContext context,
            SdfFontRenderer.FontStyle style,
            String text,
            float x,
            float y,
            int color) {
        drawTextCenteredY(context, style, text, x - width(style, text) * 0.5F, y, color);
    }

    public static void drawTextRightAligned(
            DrawContext context,
            SdfFontRenderer.FontStyle style,
            String text,
            float x,
            float y,
            int color) {
        drawTextCenteredY(context, style, text, x - width(style, text), y, color);
    }

    public static void drawIcon(
            DrawContext context, String icon, float x, float y, float height, int color) {
        if (color >>> 24 != 0) {
            float scale = height / SdfFontRenderer.FontStyle.ICONS.capHeight;
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            drawTextAtTop(
                    context,
                    SdfFontRenderer.FontStyle.ICONS,
                    icon,
                    -SdfFontRenderer.FontStyle.ICONS.capHeight * 0.5F,
                    -SdfFontRenderer.FontStyle.ICONS.capTop
                            - SdfFontRenderer.FontStyle.ICONS.capHeight * 0.5F,
                    color);
            matrices.popMatrix();
        }
    }

    public static String truncateToWidth(
            SdfFontRenderer.FontStyle style, String text, float maxWidth) {
        String source = text == null ? "" : text;
        if (width(style, source) <= maxWidth) {
            return source;
        } else {
            String ellipsis = "\u2026";
            int length = source.length();

            while (length > 0 && width(style, source.substring(0, length) + ellipsis) > maxWidth) {
                length--;
            }

            return length <= 0 ? "" : source.substring(0, length).stripTrailing() + ellipsis;
        }
    }

    private static SdfFontRenderer.FontAtlas getAtlas(SdfFontRenderer.FontAtlasType type) {
        return ATLASES.computeIfAbsent(type, SdfFontRenderer.FontAtlas::load);
    }

    private static RenderPipeline getPipeline() {
        if (pipeline == null) {
            pipeline =
                    ShaderPipelineInvoker.odinclient$registerPipeline(
                            RenderPipeline.builder(ShaderPipelineInvoker.odinclient$getGuiSnippet())
                                    .withLocation(Identifier.of("odinclient", "pipeline/sdf_text"))
                                    .withVertexShader(Identifier.of("odinclient", "core/sdf_text"))
                                    .withFragmentShader(
                                            Identifier.of("odinclient", "core/sdf_text"))
                                    .withSampler("Sampler0")
                                    .withVertexFormat(VERTEX_FORMAT, DrawMode.QUADS)
                                    .build());
        }

        return pipeline;
    }

    private static final class FontAtlas {
        private final Map<Character, SdfFontRenderer.Glyph> glyphs = new HashMap<>();
        private NativeImageBackedTexture texture;
        private TextureSetup textureSetup;

        SdfFontRenderer.Glyph getGlyph(char c) {
            SdfFontRenderer.Glyph glyph = this.glyphs.get(c);
            return glyph != null
                    ? glyph
                    : this.glyphs.getOrDefault('?', SdfFontRenderer.Glyph.MISSING);
        }

        static SdfFontRenderer.FontAtlas load(SdfFontRenderer.FontAtlasType type) {
            SdfFontRenderer.FontAtlas atlas = new SdfFontRenderer.FontAtlas();
            String path = "/assets/odinclient/textures/sdf_font/" + type.file;

            try (InputStream json = SdfFontRenderer.class.getResourceAsStream(path + ".json");
                    InputStream png = SdfFontRenderer.class.getResourceAsStream(path + ".png"); ) {
                if (json == null || png == null) {
                    throw new IllegalStateException("Missing SDF atlas " + type.file);
                }

                JsonObject root =
                        JsonParser.parseReader(new InputStreamReader(json, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                float atlasWidth = root.get("atlasWidth").getAsFloat();
                float atlasHeight = root.get("atlasHeight").getAsFloat();

                for (Entry<String, JsonElement> entry : root.getAsJsonObject("glyphs").entrySet()) {
                    JsonArray values = entry.getValue().getAsJsonArray();
                    char c = (char) Integer.parseInt(entry.getKey());
                    if (values.size() == 1) {
                        atlas.glyphs.put(
                                c,
                                new SdfFontRenderer.Glyph(
                                        values.get(0).getAsFloat(),
                                        false,
                                        0.0F,
                                        0.0F,
                                        0.0F,
                                        0.0F,
                                        0.0F,
                                        0.0F,
                                        0.0F,
                                        0.0F));
                    } else {
                        atlas.glyphs.put(
                                c,
                                new SdfFontRenderer.Glyph(
                                        values.get(0).getAsFloat(),
                                        true,
                                        values.get(1).getAsFloat(),
                                        values.get(2).getAsFloat(),
                                        values.get(3).getAsFloat(),
                                        values.get(4).getAsFloat(),
                                        values.get(5).getAsFloat() / atlasWidth,
                                        values.get(6).getAsFloat() / atlasHeight,
                                        values.get(7).getAsFloat() / atlasWidth,
                                        values.get(8).getAsFloat() / atlasHeight));
                    }
                }

                NativeImage image = NativeImage.read(png);
                atlas.texture =
                        new NativeImageBackedTexture(() -> "odinclient sdf " + type.file, image);
                atlas.textureSetup =
                        TextureSetup.of(
                                atlas.texture.getGlTextureView(),
                                RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            } catch (Exception e) {
                ModuleContext.LOGGER.error("Failed to load OdinClient font atlas " + type.file, e);
            }

            return atlas;
        }
    }

    private static enum FontAtlasType {
        BODY("body"),
        BOLD("bold"),
        BRAND("brand"),
        ICONS("icons");

        private final String file;

        private FontAtlasType(String file) {
            this.file = file;
        }
    }

    public static enum FontStyle {
        BODY(SdfFontRenderer.FontAtlasType.BODY, 8.0F, 0.727F),
        BODY_SMALL(SdfFontRenderer.FontAtlasType.BODY, 6.5F, 0.727F),
        BOLD(SdfFontRenderer.FontAtlasType.BOLD, 8.0F, 0.727F),
        BOLD_SMALL(SdfFontRenderer.FontAtlasType.BOLD, 7.0F, 0.727F),
        TITLE(SdfFontRenderer.FontAtlasType.BOLD, 12.0F, 0.727F),
        TINY(SdfFontRenderer.FontAtlasType.BOLD, 5.5F, 0.727F),
        MICRO(SdfFontRenderer.FontAtlasType.BOLD, 5.0F, 0.727F),
        BRAND(SdfFontRenderer.FontAtlasType.BRAND, 13.0F, 0.69F),
        INTRO(SdfFontRenderer.FontAtlasType.BRAND, 32.0F, 0.69F),
        ICONS(SdfFontRenderer.FontAtlasType.ICONS, 10.0F, 0.875F, 1.0F);

        private final SdfFontRenderer.FontAtlasType atlas;
        public final float size;
        public final float capTop;
        public final float capHeight;

        private FontStyle(SdfFontRenderer.FontAtlasType atlas, float size, float cap) {
            this(atlas, size, cap, cap);
        }

        private FontStyle(
                SdfFontRenderer.FontAtlasType atlas,
                float size,
                float capTopRatio,
                float capHeightRatio) {
            this.atlas = atlas;
            this.size = size;
            this.capTop = BASELINE_OFFSET - size * capTopRatio;
            this.capHeight = size * capHeightRatio;
        }
    }

    private record Glyph(
            float advance,
            boolean visible,
            float left,
            float top,
            float right,
            float bottom,
            float u0,
            float v0,
            float u1,
            float v1) {
        static final SdfFontRenderer.Glyph MISSING =
                new SdfFontRenderer.Glyph(
                        0.3F, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private record SdfTextRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2fc pose,
            float[] quads,
            int count,
            int color,
            ScreenRect scissor,
            ScreenRect bounds)
            implements SimpleGuiElementRenderState {
        @Override
        public void setupVertices(VertexConsumer vertices) {
            for (int i = 0; i < this.count; i++) {
                int offset = i * 8;
                float x0 = this.quads[offset];
                float y0 = this.quads[offset + 1];
                float x1 = this.quads[offset + 2];
                float y1 = this.quads[offset + 3];
                float u0 = this.quads[offset + 4];
                float v0 = this.quads[offset + 5];
                float u1 = this.quads[offset + 6];
                float v1 = this.quads[offset + 7];
                vertices.vertex(this.pose, x0, y0).texture(u0, v0).color(this.color);
                vertices.vertex(this.pose, x0, y1).texture(u0, v1).color(this.color);
                vertices.vertex(this.pose, x1, y1).texture(u1, v1).color(this.color);
                vertices.vertex(this.pose, x1, y0).texture(u1, v0).color(this.color);
            }
        }

        @Override
        public ScreenRect scissorArea() {
            return this.scissor;
        }
    }
}
