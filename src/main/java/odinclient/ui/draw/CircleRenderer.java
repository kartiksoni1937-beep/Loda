package odinclient.ui.draw;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import org.joml.Matrix3x2fStack;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class CircleRenderer {
    private static final Identifier TEXTURE =
            Identifier.of("odinclient", "textures/gui/radar_circle.png");
    private static final int TEXTURE_SCALE = 4;

    private CircleRenderer() {}

    public static void drawCircle(
            DrawContext context, float centerX, float centerY, float radius, int color) {
        if (context != null && !(radius <= 0.0F) && color >>> 24 != 0) {
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.scale(0.25F, 0.25F);
            int x = Math.round((centerX - radius) * 4.0F);
            int y = Math.round((centerY - radius) * 4.0F);
            int size = Math.max(2, Math.round(radius * 2.0F * 4.0F));
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    TEXTURE,
                    x,
                    y,
                    0.0F,
                    0.0F,
                    size,
                    size,
                    1024,
                    1024,
                    1024,
                    1024,
                    color);
            matrices.popMatrix();
        }
    }

    record RingRenderState(
            Matrix3x2f pose,
            float centerX,
            float centerY,
            float innerRadius,
            float outerRadius,
            int color,
            int transparentColor,
            int segments,
            ScreenRect bounds)
            implements SimpleGuiElementRenderState {
        @Override
        public void setupVertices(VertexConsumer vertices) {
            double step = 6.283185307179586 / this.segments;

            for (int i = 0; i < this.segments; i++) {
                double startAngle = i * step;
                double endAngle = (i + 1) * step;
                float innerStartX = this.centerX + (float) Math.cos(startAngle) * this.innerRadius;
                float innerStartY = this.centerY + (float) Math.sin(startAngle) * this.innerRadius;
                float innerEndX = this.centerX + (float) Math.cos(endAngle) * this.innerRadius;
                float innerEndY = this.centerY + (float) Math.sin(endAngle) * this.innerRadius;
                float outerStartX = this.centerX + (float) Math.cos(startAngle) * this.outerRadius;
                float outerStartY = this.centerY + (float) Math.sin(startAngle) * this.outerRadius;
                float outerEndX = this.centerX + (float) Math.cos(endAngle) * this.outerRadius;
                float outerEndY = this.centerY + (float) Math.sin(endAngle) * this.outerRadius;
                vertices.vertex(this.pose, this.centerX, this.centerY).color(this.color);
                vertices.vertex(this.pose, innerStartX, innerStartY).color(this.color);
                vertices.vertex(this.pose, innerEndX, innerEndY).color(this.color);
                vertices.vertex(this.pose, this.centerX, this.centerY).color(this.color);
                vertices.vertex(this.pose, innerStartX, innerStartY).color(this.color);
                vertices.vertex(this.pose, outerStartX, outerStartY).color(this.transparentColor);
                vertices.vertex(this.pose, outerEndX, outerEndY).color(this.transparentColor);
                vertices.vertex(this.pose, innerEndX, innerEndY).color(this.color);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.empty();
        }

        @Override
        public ScreenRect scissorArea() {
            return null;
        }

        public int transparent() {
            return this.transparentColor;
        }
    }
}
