package odinclient.ui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;

import odinclient.render.EntityColor;
import odinclient.setting.type.EntityTypeListSetting;
import odinclient.ui.screen.ClickGuiScreen;

import java.lang.reflect.Method;

public final class MobColorPicker {
    private static int pendingAction;
    private static EntityTypeListSetting selectedSetting;
    private static EntityType<?> selectedEntityType;
    private static Method openColorMethod;

    private MobColorPicker() {}

    public static void setClickAction(int action) {
        pendingAction = action;
    }

    public static void onEntityTypeClicked(
            EntityTypeListSetting setting, EntityType<?> entityType) {
        int action = pendingAction;
        pendingAction = 0;
        if (setting != null && entityType != null) {
            if (action == 1 && "mobs".equals(setting.name)) {
                if (!setting.isSelected(entityType)) {
                    setting.toggle(entityType, EntityTypeListSetting.defaultColor(entityType));
                }

                EntityColor color = setting.getColor(entityType);
                if (color == null) {
                    return;
                }

                selectedSetting = setting;
                selectedEntityType = entityType;
                if (!openColorPicker(color)) {
                    clearSelection();
                }
            } else if (action == 0) {
                setting.toggle(entityType);
            }
        }
    }

    public static boolean hasSelection() {
        return selectedSetting != null && selectedEntityType != null;
    }

    public static void applyColor(EntityColor color) {
        if (hasSelection() && color != null) {
            selectedSetting.setColor(selectedEntityType, color);
        }
    }

    public static void clearSelection() {
        selectedSetting = null;
        selectedEntityType = null;
    }

    public static int resolveColor(
            int defaultColor, EntityTypeListSetting setting, EntityType<?> entityType) {
        if (setting != null && entityType != null && "mobs".equals(setting.name)) {
            EntityColor color = setting.getColor(entityType);
            return color == null
                    ? defaultColor
                    : 0xFF000000 | color.r << 16 | color.g << 8 | color.b;
        } else {
            return defaultColor;
        }
    }

    private static boolean openColorPicker(EntityColor color) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.currentScreen instanceof ClickGuiScreen screen) {
                if (openColorMethod == null) {
                    openColorMethod =
                            ClickGuiScreen.class.getMethod(
                                    "odinclient$openEntityColor", EntityColor.class);
                }

                openColorMethod.invoke(screen, color);
                return true;
            } else {
                return false;
            }
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
