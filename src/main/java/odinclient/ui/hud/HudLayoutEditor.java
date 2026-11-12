package odinclient.ui.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import odinclient.module.ModuleContext;
import odinclient.module.hud.ThemeModule;
import odinclient.ui.anim.AnimationUtil;
import odinclient.ui.draw.SdfFontRenderer;
import odinclient.ui.draw.SdfShapeRenderer;
import odinclient.ui.scale.ScreenScaleCompat;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HudLayoutEditor {
    private static final Path LAYOUT_PATH;
    private static final Map<String, float[]> positions = new HashMap<>();
    private static final List<HudLayoutEditor.DragHandle> elements = new ArrayList<>();
    private static boolean loaded;
    private static String draggedId;
    private static float lastMouseX;
    private static float lastMouseY;
    private static float overlayAlpha;
    private static boolean wasMouseDown;

    private HudLayoutEditor() {}

    public static float[] getPosition(String id) {
        loadLayout();
        return positions.computeIfAbsent(id, k -> new float[2]);
    }

    public static float getX(String id) {
        return getPosition(id)[0];
    }

    public static float getY(String id) {
        return getPosition(id)[1];
    }

    public static void registerElement(
            DrawContext context,
            String id,
            String label,
            float x,
            float y,
            float width,
            float height,
            HudLayoutEditor.MoveListener mover) {
        if (isEditing()) {
            Matrix3x2f matrix = new Matrix3x2f(context.getMatrices());
            Vector2f topLeft = matrix.transformPosition(new Vector2f(x, y));
            Vector2f bottomRight = matrix.transformPosition(new Vector2f(x + width, y + height));
            float compensation = ScreenScaleCompat.getCompensation();
            topLeft.div(compensation);
            bottomRight.div(compensation);
            elements.add(
                    new HudLayoutEditor.DragHandle(
                            id,
                            label,
                            Math.min(topLeft.x, bottomRight.x),
                            Math.min(topLeft.y, bottomRight.y),
                            Math.abs(bottomRight.x - topLeft.x),
                            Math.abs(bottomRight.y - topLeft.y),
                            mover));
        }
    }

    public static void registerOffsetElement(
            DrawContext context,
            String id,
            String label,
            float x,
            float y,
            float width,
            float height,
            float scale) {
        registerElement(
                context,
                id,
                label,
                x,
                y,
                width,
                height,
                (dx, dy) -> {
                    float[] position = getPosition(id);
                    position[0] += dx / scale;
                    position[1] += dy / scale;
                    saveLayout();
                });
    }

    public static boolean isEditing() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen instanceof ChatScreen && client.world != null;
    }

    public static void clearElements() {
        elements.clear();
    }

    public static void renderOverlay(DrawContext context, float deltaTicks) {
        boolean editing = isEditing();
        overlayAlpha =
                AnimationUtil.smoothApproach(
                        overlayAlpha, editing ? 1.0F : 0.0F, 10.0F, deltaTicks);
        if (!editing) {
            draggedId = null;
            wasMouseDown = false;
        }

        if (!(overlayAlpha < 0.02F) && !elements.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            double mouseX =
                    client.mouse.getScaledX(client.getWindow())
                            / ScreenScaleCompat.getCompensation();
            double mouseY =
                    client.mouse.getScaledY(client.getWindow())
                            / ScreenScaleCompat.getCompensation();
            updateDrag(mouseX, mouseY);
            int accent = ThemeModule.getAccentColorAt(0.4F);

            for (HudLayoutEditor.DragHandle handle : elements) {
                boolean active = handle.id.equals(draggedId) || handle.contains(mouseX, mouseY);
                float alpha = overlayAlpha * (active ? 1.0F : 0.45F);
                SdfShapeRenderer.drawOutline(
                        context,
                        handle.x - 1.0F,
                        handle.y - 1.0F,
                        handle.w + 2.0F,
                        handle.h + 2.0F,
                        5.0F,
                        active ? 1.4F : 1.0F,
                        AnimationUtil.multiplyAlpha(AnimationUtil.withAlpha(accent, 255), alpha));
                if (active) {
                    SdfShapeRenderer.fill(
                            context,
                            handle.x - 1.0F,
                            handle.y - 1.0F,
                            handle.w + 2.0F,
                            handle.h + 2.0F,
                            5.0F,
                            AnimationUtil.multiplyAlpha(
                                    AnimationUtil.withAlpha(accent, 40), overlayAlpha));
                    float labelWidth =
                            SdfFontRenderer.width(
                                            SdfFontRenderer.FontStyle.BODY_SMALL, handle.label)
                                    + 12.0F;
                    float labelY = Math.max(2.0F, handle.y - 16.0F);
                    SdfShapeRenderer.fill(
                            context,
                            handle.x,
                            labelY,
                            labelWidth,
                            14.0F,
                            5.0F,
                            AnimationUtil.multiplyAlpha(-267778288, overlayAlpha));
                    SdfFontRenderer.drawTextCentered(
                            context,
                            SdfFontRenderer.FontStyle.BODY_SMALL,
                            handle.label,
                            handle.x + labelWidth * 0.5F,
                            labelY + 7.0F,
                            AnimationUtil.multiplyAlpha(-1, overlayAlpha));
                }
            }
        }
    }

    private static void updateDrag(double mouseX, double mouseY) {
        boolean mouseDown =
                GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), 0)
                        == 1;

        if (mouseDown && draggedId == null && !wasMouseDown) {
            for (int i = elements.size() - 1; i >= 0; i--) {
                HudLayoutEditor.DragHandle handle = elements.get(i);
                if (handle.contains(mouseX, mouseY)) {
                    draggedId = handle.id;
                    break;
                }
            }
        } else if (mouseDown && draggedId != null) {
            for (HudLayoutEditor.DragHandle target : elements) {
                if (target.id.equals(draggedId)) {
                    target.mover.move((float) (mouseX - lastMouseX), (float) (mouseY - lastMouseY));
                    break;
                }
            }
        } else if (!mouseDown && draggedId != null) {
            draggedId = null;
            saveLayout();
        }

        wasMouseDown = mouseDown;
        lastMouseX = (float) mouseX;
        lastMouseY = (float) mouseY;
    }

    private static synchronized void loadLayout() {
        if (!loaded) {
            loaded = true;

            try {
                if (!Files.exists(LAYOUT_PATH)) {
                    return;
                }

                try (BufferedReader reader =
                        Files.newBufferedReader(LAYOUT_PATH, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                    for (String key : root.keySet()) {
                        JsonArray array = root.getAsJsonArray(key);
                        positions.put(
                                key,
                                new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat()});
                    }
                }
            } catch (Exception e) {
                ModuleContext.LOGGER.warn("Could not read the OdinClient HUD layout", e);
            }
        }
    }

    public static synchronized void saveLayout() {
        try {
            JsonObject root = new JsonObject();
            positions.forEach(
                    (id, position) -> {
                        JsonArray array = new JsonArray();
                        array.add(position[0]);
                        array.add(position[1]);
                        root.add(id, array);
                    });
            Files.createDirectories(LAYOUT_PATH.getParent());
            Files.writeString(LAYOUT_PATH, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModuleContext.LOGGER.warn("Could not save the OdinClient HUD layout", e);
        }
    }

    static {
        LAYOUT_PATH =
                FabricLoader.getInstance().getConfigDir().resolve("odinclient-hud-layout.json");
    }

    private record DragHandle(
            String id,
            String label,
            float x,
            float y,
            float w,
            float h,
            HudLayoutEditor.MoveListener mover) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x
                    && mouseY >= this.y
                    && mouseX < this.x + this.w
                    && mouseY < this.y + this.h;
        }
    }

    public interface MoveListener {
        void move(float dx, float dy);
    }
}
