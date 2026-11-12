package odinclient.ui.draw;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import odinclient.mixin.accessor.GuiGraphicsAccessor;
import odinclient.mixin.accessor.ShaderPipelineInvoker;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class SdfShapeRenderer {
    private static final int MODE_FILL = 0;
    private static final int MODE_OUTLINE = 1;
    private static final int MODE_SHADOW = 2;
    private static final float SUBPIXEL_SCALE = 16.0F;
    private static final float MAX_HALF_SIZE = 2047.0F;
    private static final VertexFormat VERTEX_FORMAT =
            VertexFormat.builder()
                    .add("Position", VertexFormatElement.POSITION)
                    .add("Color", VertexFormatElement.COLOR)
                    .add("UV0", VertexFormatElement.UV0)
                    .add("UV1", VertexFormatElement.UV1)
                    .add("UV2", VertexFormatElement.UV2)
                    .add("LineWidth", VertexFormatElement.LINE_WIDTH)
                    .build();
    private static RenderPipeline shapePipeline;
    private static RenderPipeline logoPipeline;
    private static Field scissorStackField;
    private static Method scissorPeekMethod;
    private static boolean scissorLookupFailed;

    private SdfShapeRenderer() {}

    public static void initPipelines() {
        getShapePipeline();
        getLogoPipeline();
    }

    public static void fill(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            int color) {
        submitShape(
                context, x, y, width, height, radius, 0.0F, 0.0F, 0, color, color, color, color);
    }

    public static void fillVerticalGradient(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            int top,
            int bottom) {
        submitShape(context, x, y, width, height, radius, 0.0F, 0.0F, 0, top, top, bottom, bottom);
    }

    public static void fillHorizontalGradient(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            int left,
            int right) {
        submitShape(context, x, y, width, height, radius, 0.0F, 0.0F, 0, left, right, right, left);
    }

    public static void fillCornerGradient(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            int topLeft,
            int topRight,
            int bottomRight,
            int bottomLeft) {
        submitShape(
                context,
                x,
                y,
                width,
                height,
                radius,
                0.0F,
                0.0F,
                0,
                topLeft,
                bottomLeft,
                bottomRight,
                topRight);
    }

    public static void drawOutline(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float thickness,
            int color) {
        submitShape(
                context, x, y, width, height, radius, 0.0F, thickness, 1, color, color, color,
                color);
    }

    public static void drawShadow(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float blur,
            int color) {
        submitShape(
                context,
                x,
                y,
                width,
                height,
                radius,
                Math.max(0.5F, blur),
                0.0F,
                2,
                color,
                color,
                color,
                color);
    }

    public static void fillCircle(
            DrawContext context, float centerX, float centerY, float radius, int color) {
        fill(
                context,
                centerX - radius,
                centerY - radius,
                radius * 2.0F,
                radius * 2.0F,
                radius,
                color);
    }

    public static void drawRing(
            DrawContext context,
            float centerX,
            float centerY,
            float radius,
            float thickness,
            int color) {
        drawOutline(
                context,
                centerX - radius,
                centerY - radius,
                radius * 2.0F,
                radius * 2.0F,
                radius,
                thickness,
                color);
    }

    public static void drawLines(DrawContext context, List<SdfShapeRenderer.LineSegment> segments) {
        if (!segments.isEmpty()) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;

            for (SdfShapeRenderer.LineSegment segment : segments) {
                float padding = Math.max(0.5F, segment.width()) * 0.5F + 2.0F;
                minX = Math.min(minX, Math.min(segment.x1(), segment.x2()) - padding);
                minY = Math.min(minY, Math.min(segment.y1(), segment.y2()) - padding);
                maxX = Math.max(maxX, Math.max(segment.x1(), segment.x2()) + padding);
                maxY = Math.max(maxY, Math.max(segment.y1(), segment.y2()) + padding);
            }

            Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
            ScreenRect scissor = getCurrentScissor(context);
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

            ((GuiGraphicsAccessor) context)
                    .odinclient$renderState()
                    .addSimpleElement(
                            new SdfShapeRenderer.SdfLinesRenderState(
                                    getShapePipeline(),
                                    pose,
                                    List.copyOf(segments),
                                    scissor,
                                    bounds));
        }
    }

    private static void submitShape(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float blur,
            float thickness,
            int mode,
            int topLeft,
            int bottomLeft,
            int bottomRight,
            int topRight) {
        if (!(width <= 0.0F) && !(height <= 0.0F)) {
            if ((topLeft | bottomLeft | bottomRight | topRight) >>> 24 != 0) {
                float margin = 1.0F + (mode == 2 ? blur * 1.5F : 0.0F);
                Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
                ScreenRect scissor = getCurrentScissor(context);
                ScreenRect bounds =
                        new ScreenRect(
                                        (int) Math.floor(x - margin),
                                        (int) Math.floor(y - margin),
                                        (int) Math.ceil(width + margin * 2.0F) + 1,
                                        (int) Math.ceil(height + margin * 2.0F) + 1)
                                .transformEachVertex(pose);
                if (scissor != null) {
                    bounds = scissor.intersection(bounds);
                    if (bounds == null) {
                        return;
                    }
                }

                float halfWidth = Math.min(width * 0.5F, 2047.0F);
                float halfHeight = Math.min(height * 0.5F, 2047.0F);
                SdfShapeRenderer.SdfShapeRenderState state =
                        new SdfShapeRenderer.SdfShapeRenderState(
                                getShapePipeline(),
                                pose,
                                x + width * 0.5F,
                                y + height * 0.5F,
                                halfWidth + margin,
                                halfHeight + margin,
                                1.0F,
                                Math.round(halfWidth * 16.0F),
                                Math.round(halfHeight * 16.0F),
                                Math.round(
                                        Math.max(
                                                        0.0F,
                                                        Math.min(
                                                                radius,
                                                                Math.min(halfWidth, halfHeight)))
                                                * 16.0F),
                                Math.min(4095, Math.round(blur * 16.0F)) | mode << 12,
                                Math.max(0.0F, thickness),
                                topLeft,
                                bottomLeft,
                                bottomRight,
                                topRight,
                                scissor,
                                bounds);
                ((GuiGraphicsAccessor) context).odinclient$renderState().addSimpleElement(state);
            }
        }
    }

    public static void drawLogoHalf(
            DrawContext context,
            float x,
            float y,
            float scale,
            float glow,
            float progress,
            float fade,
            int color) {
        submitLogo(context, x, y, scale, 0.95F, 0.6F, glow, progress, fade, 0, color);
    }

    public static void drawLogo(
            DrawContext context,
            float x,
            float y,
            float scale,
            float progress,
            float fade,
            int color) {
        submitLogo(context, x, y, scale, 1.72F, 0.6F, 0.0F, progress, fade, 1, color);
    }

    public static float getLogoWidth(float height) {
        return height * 2.86F;
    }

    private static void submitLogo(
            DrawContext context,
            float x,
            float y,
            float scale,
            float halfWidth,
            float halfHeight,
            float first,
            float glow,
            float second,
            int mode,
            int color) {
        if (!(scale <= 0.0F) && color >>> 24 != 0 && !(second <= 0.0F)) {
            float glowMargin = glow > 0.0F ? glow * 2.6F : 0.0F;
            float extentX = (halfWidth + glowMargin) * scale;
            float extentY = (halfHeight + glowMargin) * scale;
            Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
            ScreenRect scissor = getCurrentScissor(context);
            ScreenRect bounds =
                    new ScreenRect(
                                    (int) Math.floor(x - extentX),
                                    (int) Math.floor(y - extentY),
                                    (int) Math.ceil(extentX * 2.0F) + 1,
                                    (int) Math.ceil(extentY * 2.0F) + 1)
                            .transformEachVertex(pose);
            if (scissor != null) {
                bounds = scissor.intersection(bounds);
                if (bounds == null) {
                    return;
                }
            }

            SdfShapeRenderer.SdfShapeRenderState state =
                    new SdfShapeRenderer.SdfShapeRenderState(
                            getLogoPipeline(),
                            pose,
                            x,
                            y,
                            extentX,
                            extentY,
                            scale,
                            Math.round(clamp01(first) * 1000.0F),
                            Math.round(Math.max(0.0F, glow) * 1000.0F),
                            Math.round(clamp01(second) * 1000.0F),
                            mode,
                            0.0F,
                            color,
                            color,
                            color,
                            color,
                            scissor,
                            bounds);
            ((GuiGraphicsAccessor) context).odinclient$renderState().addSimpleElement(state);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static RenderPipeline getShapePipeline() {
        if (shapePipeline == null) {
            shapePipeline = createPipeline("sdf_shape");
        }

        return shapePipeline;
    }

    private static RenderPipeline getLogoPipeline() {
        if (logoPipeline == null) {
            logoPipeline = createPipeline("sdf_logo");
        }

        return logoPipeline;
    }

    private static RenderPipeline createPipeline(String name) {
        return ShaderPipelineInvoker.odinclient$registerPipeline(
                RenderPipeline.builder(ShaderPipelineInvoker.odinclient$getGuiSnippet())
                        .withLocation(Identifier.of("odinclient", "pipeline/" + name))
                        .withVertexShader(Identifier.of("odinclient", "core/" + name))
                        .withFragmentShader(Identifier.of("odinclient", "core/" + name))
                        .withVertexFormat(VERTEX_FORMAT, DrawMode.QUADS)
                        .build());
    }

    static ScreenRect getCurrentScissor(DrawContext context) {
        if (scissorLookupFailed) {
            return null;
        } else {
            try {
                if (scissorStackField == null) {
                    scissorStackField = DrawContext.class.getDeclaredField("field_44659");
                    scissorStackField.setAccessible(true);
                    scissorPeekMethod =
                            scissorStackField.getType().getDeclaredMethod("method_70863");
                    scissorPeekMethod.setAccessible(true);
                }

                return (ScreenRect) scissorPeekMethod.invoke(scissorStackField.get(context));
            } catch (RuntimeException | ReflectiveOperationException e) {
                scissorLookupFailed = true;
                return null;
            }
        }
    }

    public record LineSegment(float x1, float y1, float x2, float y2, float width, int color) {}

    private record SdfLinesRenderState(
            RenderPipeline pipeline,
            Matrix3x2fc pose,
            List<SdfShapeRenderer.LineSegment> lines,
            ScreenRect scissor,
            ScreenRect bounds)
            implements SimpleGuiElementRenderState {
        @Override
        public void setupVertices(VertexConsumer vertices) {
            for (SdfShapeRenderer.LineSegment line : this.lines) {
                float dx = line.x2() - line.x1();
                float dy = line.y2() - line.y1();
                float length = (float) Math.hypot(dx, dy);
                if (Float.isFinite(length) && !(length < 0.01F) && line.color() >>> 24 != 0) {
                    float centerX = (line.x1() + line.x2()) * 0.5F;
                    float centerY = (line.y1() + line.y2()) * 0.5F;
                    float halfThickness = Math.max(0.05F, line.width() * 0.5F);
                    float halfLength = length * 0.5F + halfThickness;
                    float unit = Math.max(1.0F, Math.max(halfLength, halfThickness) / 2047.0F);
                    float padding = 2.0F;
                    float dirX = dx / length;
                    float dirY = dy / length;
                    this.vertex(
                            vertices,
                            line,
                            centerX,
                            centerY,
                            dirX,
                            dirY,
                            -halfLength - padding,
                            -halfThickness - padding,
                            halfLength,
                            halfThickness,
                            unit);
                    this.vertex(
                            vertices,
                            line,
                            centerX,
                            centerY,
                            dirX,
                            dirY,
                            -halfLength - padding,
                            halfThickness + padding,
                            halfLength,
                            halfThickness,
                            unit);
                    this.vertex(
                            vertices,
                            line,
                            centerX,
                            centerY,
                            dirX,
                            dirY,
                            halfLength + padding,
                            halfThickness + padding,
                            halfLength,
                            halfThickness,
                            unit);
                    this.vertex(
                            vertices,
                            line,
                            centerX,
                            centerY,
                            dirX,
                            dirY,
                            halfLength + padding,
                            -halfThickness - padding,
                            halfLength,
                            halfThickness,
                            unit);
                }
            }
        }

        private void vertex(
                VertexConsumer vertices,
                SdfShapeRenderer.LineSegment line,
                float centerX,
                float centerY,
                float dirX,
                float dirY,
                float localX,
                float localY,
                float halfLength,
                float halfThickness,
                float unit) {
            float x = centerX + dirX * localX - dirY * localY;
            float y = centerY + dirY * localX + dirX * localY;
            vertices.vertex(this.pose, x, y)
                    .color(line.color())
                    .texture(localX / unit, localY / unit)
                    .overlay(
                            Math.round(halfLength / unit * 16.0F),
                            Math.round(halfThickness / unit * 16.0F))
                    .light(Math.round(halfThickness / unit * 16.0F), 0)
                    .lineWidth(0.0F);
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.empty();
        }

        @Override
        public ScreenRect scissorArea() {
            return this.scissor;
        }
    }

    private record SdfShapeRenderState(
            RenderPipeline pipeline,
            Matrix3x2fc pose,
            float centerX,
            float centerY,
            float halfW,
            float halfH,
            float unitSize,
            int uv1x,
            int uv1y,
            int uv2x,
            int uv2y,
            float border,
            int topLeft,
            int bottomLeft,
            int bottomRight,
            int topRight,
            ScreenRect scissor,
            ScreenRect bounds)
            implements SimpleGuiElementRenderState {
        @Override
        public void setupVertices(VertexConsumer vertices) {
            float left = -this.halfW;
            float right = this.halfW;
            float top = -this.halfH;
            float bottom = this.halfH;
            this.vertex(vertices, left, top, this.topLeft);
            this.vertex(vertices, left, bottom, this.bottomLeft);
            this.vertex(vertices, right, bottom, this.bottomRight);
            this.vertex(vertices, right, top, this.topRight);
        }

        private void vertex(VertexConsumer vertices, float x, float y, int color) {
            vertices.vertex(this.pose, this.centerX + x, this.centerY + y)
                    .color(color)
                    .texture(x / this.unitSize, y / this.unitSize)
                    .overlay(this.uv1x, this.uv1y)
                    .light(this.uv2x, this.uv2y)
                    .lineWidth(this.border);
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.empty();
        }

        @Override
        public ScreenRect scissorArea() {
            return this.scissor;
        }
    }
}
