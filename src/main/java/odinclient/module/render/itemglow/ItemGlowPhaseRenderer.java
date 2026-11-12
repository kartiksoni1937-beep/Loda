package odinclient.module.render.itemglow;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;

import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.ShaderLayerInvoker;
import odinclient.mixin.accessor.ShaderPipelineInvoker;
import odinclient.render.compat.IrisCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ItemGlowPhaseRenderer {
    private static final List<ItemGlowPhaseRenderer.GlowEntry> handEntries = new ArrayList<>(4);
    private static final List<ItemGlowPhaseRenderer.GlowEntry> worldEntries = new ArrayList<>(16);
    private static boolean handPhaseActive;
    private static final int MAX_GROUND_ENTRIES = 48;
    private static final int MAX_WORLD_ENTRIES = 64;

    private ItemGlowPhaseRenderer() {}

    public static void onHandPhaseStart() {
        handEntries.clear();
        handPhaseActive = true;
    }

    public static void onWorldPhaseStart() {
        worldEntries.clear();
    }

    public static void reset() {
        handEntries.clear();
        worldEntries.clear();
        handPhaseActive = false;
    }

    public static void submitItem(
            MatrixStack matrices,
            ItemDisplayContext displayContext,
            int light,
            int overlay,
            int color,
            int[] tints,
            List<BakedQuad> quads,
            RenderLayer layer,
            Glint glint) {
        boolean firstPerson = ItemGlow.isFirstPerson(displayContext);
        boolean thirdPerson = ItemGlow.isThirdPerson(displayContext);
        boolean ground = displayContext == ItemDisplayContext.GROUND;
        ItemGlow.GlowStyle style =
                ground ? ItemGlow.getStyleForColor(color) : ItemGlow.getHeldStyle(displayContext);

        if ((firstPerson && handPhaseActive || thirdPerson || ground)
                && style != null
                && !IrisCompat.isRenderingShadowPass()
                && quads != null
                && !quads.isEmpty()) {
            if ((!ground || worldEntries.size() < 48)
                    && (!thirdPerson || worldEntries.size() < 64)) {
                boolean useShader = ItemGlow.isShaderEnabled() && ItemGlowRenderer.isAvailable();
                List<OutlineHull> outline =
                        useShader
                                ? List.of()
                                : HeldItemOutlineBuilder.buildHulls(quads, style.width());

                if (useShader || !outline.isEmpty()) {
                    boolean flat = HeldItemOutlineBuilder.isFlatItem(quads);
                    List<OutlineHull> middleGlow =
                            useShader
                                    ? List.of()
                                    : HeldItemOutlineBuilder.buildHulls(
                                            quads, style.width() + 0.75F);
                    List<OutlineHull> outerGlow =
                            useShader
                                    ? List.of()
                                    : HeldItemOutlineBuilder.buildHulls(
                                            quads, style.width() + 1.5F);

                    Entry itemPose = matrices.peek().copy();
                    matrices.push();
                    matrices.scale(0.0625F, 0.0625F, 0.0625F);
                    Entry outlinePose = matrices.peek().copy();
                    matrices.pop();
                    int[] tintCopy = tints == null ? new int[0] : tints.clone();
                    ItemGlowPhaseRenderer.GlowEntry entry =
                            new ItemGlowPhaseRenderer.GlowEntry(
                                    outlinePose,
                                    itemPose,
                                    outline,
                                    middleGlow,
                                    outerGlow,
                                    quads,
                                    layer,
                                    light,
                                    overlay,
                                    tintCopy,
                                    style,
                                    !flat);
                    if (!thirdPerson && !ground) {
                        handEntries.add(entry);
                    } else {
                        worldEntries.add(entry);
                    }
                }
            }
        }
    }

    public static void flushHandPhase() {
        try {
            if (!handEntries.isEmpty()) {
                List<ItemGlowPhaseRenderer.GlowEntry> entries = List.copyOf(handEntries);
                handEntries.clear();
                if (!renderWithShader(entries)) {
                    redrawItems(entries);
                }
            }
        } finally {
            handPhaseActive = false;
            handEntries.clear();
        }
    }

    public static void flushWorldPhase() {
        try {
            if (!worldEntries.isEmpty()) {
                List<ItemGlowPhaseRenderer.GlowEntry> entries = List.copyOf(worldEntries);
                worldEntries.clear();
                renderWithShader(entries);
            }
        } finally {
            worldEntries.clear();
        }
    }

    private static boolean renderWithShader(List<ItemGlowPhaseRenderer.GlowEntry> entries) {
        if (ItemGlow.isShaderEnabled() && ItemGlowRenderer.isAvailable()) {
            Function<ItemGlowPhaseRenderer.GlowEntry, ItemGlowRenderer.GlowQuadBatch> toBatch =
                    entry ->
                            new ItemGlowRenderer.GlowQuadBatch(
                                    entry.itemPose(),
                                    entry.itemQuads(),
                                    entry.itemLayer(),
                                    color(entry.style(), 1.0F));
            if (ItemGlowRenderer.render(
                    entries.stream().map(toBatch).toList(),
                    ItemGlow.getShaderSoftness(),
                    ItemGlow.getShaderBrightness(),
                    entries.getFirst().style().width())) {
                return true;
            }
        }

        drawGlowPass(entries, ItemGlowPhaseRenderer.GlowPass.OUTER, 0.16F);
        drawGlowPass(entries, ItemGlowPhaseRenderer.GlowPass.MIDDLE, 0.34F);
        drawGlowPass(entries, ItemGlowPhaseRenderer.GlowPass.CORE, 1.0F);
        return false;
    }

    private static void drawGlowPass(
            List<ItemGlowPhaseRenderer.GlowEntry> entries,
            ItemGlowPhaseRenderer.GlowPass pass,
            float alpha) {
        BufferBuilder builder =
                Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (ItemGlowPhaseRenderer.GlowEntry entry : entries) {
            int glowColor = color(entry.style(), alpha);

            List<OutlineHull> hulls =
                    switch (pass) {
                        case CORE -> entry.outline();
                        case MIDDLE -> entry.middleGlow();
                        case OUTER -> entry.outerGlow();
                    };
            if (hulls.isEmpty()) {
                float extraWidth;
                if (pass == ItemGlowPhaseRenderer.GlowPass.OUTER) {
                    extraWidth = 1.5F;
                } else if (pass == ItemGlowPhaseRenderer.GlowPass.MIDDLE) {
                    extraWidth = 0.75F;
                } else {
                    extraWidth = 0.0F;
                }

                hulls =
                        HeldItemOutlineBuilder.buildHulls(
                                entry.itemQuads(), entry.style().width() + extraWidth);
            }

            for (OutlineHull hull : hulls) {
                for (int[] face : OutlineHull.FACES) {
                    for (int vertex : face) {
                        builder.vertex(
                                        entry.outlinePose(),
                                        hull.x(vertex),
                                        hull.y(vertex),
                                        hull.z(vertex))
                                .color(glowColor);
                    }
                }
            }
        }

        BuiltBuffer buffer = builder.endNullable();
        if (buffer != null) {
            ItemGlowPhaseRenderer.SilhouetteLayerHolder.COLOR.draw(buffer);
        }
    }

    private static void redrawItems(List<ItemGlowPhaseRenderer.GlowEntry> entries) {
        for (ItemGlowPhaseRenderer.GlowEntry entry : entries) {
            if (entry.redrawItem()) {
                BufferBuilder builder =
                        Tessellator.getInstance()
                                .begin(
                                        DrawMode.QUADS,
                                        VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

                for (BakedQuad quad : entry.itemQuads()) {
                    int tint = getQuadTint(quad, entry.tints());
                    float red = (tint >> 16 & 0xFF) / 255.0F;
                    float green = (tint >> 8 & 0xFF) / 255.0F;
                    float blue = (tint & 0xFF) / 255.0F;
                    float alpha = (tint >>> 24 & 0xFF) / 255.0F;
                    builder.quad(
                            entry.itemPose(),
                            quad,
                            red,
                            green,
                            blue,
                            alpha,
                            entry.light(),
                            entry.overlay());
                }

                BuiltBuffer buffer = builder.endNullable();
                if (buffer != null) {
                    entry.itemLayer().draw(buffer);
                }
            }
        }
    }

    private static int getQuadTint(BakedQuad quad, int[] tints) {
        if (!quad.hasTint()) {
            return -1;
        } else {
            int tintIndex = quad.tintIndex();
            return tintIndex >= 0 && tintIndex < tints.length ? tints[tintIndex] : -1;
        }
    }

    private static int color(ItemGlow.GlowStyle style, float alpha) {
        return Math.max(1, Math.min(255, Math.round(style.alphaFloat(alpha) * 255.0F))) << 24
                | style.red() << 16
                | style.green() << 8
                | style.blue();
    }

    private static RenderPipeline createSilhouettePipeline() {
        RenderPipeline pipeline =
                ShaderPipelineInvoker.odinclient$registerPipeline(
                        RenderPipeline.builder()
                                .withLocation(
                                        Identifier.of(
                                                "odinclient", "pipeline/held_item_silhouette"))
                                .withVertexShader("core/position_color")
                                .withFragmentShader("core/position_color")
                                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                                .withBlend(BlendFunction.TRANSLUCENT)
                                .withCull(false)
                                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                                .withColorWrite(true)
                                .withDepthWrite(false)
                                .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
                                .build());
        IrisCompat.assignBasicColorPipeline(pipeline);
        return pipeline;
    }

    private record GlowEntry(
            Entry outlinePose,
            Entry itemPose,
            List<OutlineHull> outline,
            List<OutlineHull> middleGlow,
            List<OutlineHull> outerGlow,
            List<BakedQuad> itemQuads,
            RenderLayer itemLayer,
            int light,
            int overlay,
            int[] tints,
            ItemGlow.GlowStyle style,
            boolean redrawItem) {}

    private static enum GlowPass {
        CORE,
        MIDDLE,
        OUTER;
    }

    private static final class SilhouetteLayerHolder {
        private static final RenderLayer COLOR =
                ShaderLayerInvoker.odinclient$createLayer(
                        "odinclient_held_item_silhouette",
                        RenderSetup.builder(ItemGlowPhaseRenderer.createSilhouettePipeline()).build());
    }
}
