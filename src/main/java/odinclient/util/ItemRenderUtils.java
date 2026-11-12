package odinclient.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
public final class ItemRenderUtils {
    private ItemRenderUtils() {}

    public static void drawItem(
            DrawContext context,
            ItemStack stack,
            int x,
            int y,
            float scale,
            boolean overlay,
            String countText,
            boolean unused) {
        if (context != null && stack != null && !stack.isEmpty()) {
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            context.drawItem(stack, 0, 0);
            if (overlay) {
                context.drawStackOverlay(
                        MinecraftClient.getInstance().textRenderer, stack, 0, 0, countText);
            }

            matrices.popMatrix();
        }
    }
}
