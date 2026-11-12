package odinclient.util.input;

import net.minecraft.client.option.KeyBinding;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MouseKeyBindState {
    private static volatile MouseKeyBindState.ReflectionAccessors accessors;
    private static volatile boolean initFailed;

    private MouseKeyBindState() {}

    public static boolean isKeyPressed(KeyBinding keyBinding) {
        if (keyBinding != null && !initFailed) {
            MouseKeyBindState.ReflectionAccessors reflection = accessors;
            if (reflection == null) {
                synchronized (MouseKeyBindState.class) {
                    reflection = accessors;
                    if (reflection == null && !initFailed) {
                        try {
                            reflection = MouseKeyBindState.ReflectionAccessors.create();
                            accessors = reflection;
                        } catch (Throwable e) {
                            initFailed = true;
                            return false;
                        }
                    }
                }
            }

            try {
                return reflection != null && reflection.isPressed(keyBinding);
            } catch (Exception e2) {
                return false;
            }
        } else {
            return false;
        }
    }

    private static final class ReflectionAccessors {
        private final Field boundKeyField;
        private final Method getKeyCodeMethod;
        private final Method getKeyTypeMethod;
        private final Object mouseKeyType;
        private final Method getClientMethod;
        private final Method getWindowMethod;
        private final Method isKeyDownMethod;
        private final Method getHandleMethod;
        private final Method getMouseButtonMethod;

        private ReflectionAccessors(
                Field boundKeyField,
                Method getKeyCodeMethod,
                Method getKeyTypeMethod,
                Object mouseKeyType,
                Method getClientMethod,
                Method getWindowMethod,
                Method isKeyDownMethod,
                Method getHandleMethod,
                Method getMouseButtonMethod) {
            this.boundKeyField = boundKeyField;
            this.getKeyCodeMethod = getKeyCodeMethod;
            this.getKeyTypeMethod = getKeyTypeMethod;
            this.mouseKeyType = mouseKeyType;
            this.getClientMethod = getClientMethod;
            this.getWindowMethod = getWindowMethod;
            this.isKeyDownMethod = isKeyDownMethod;
            this.getHandleMethod = getHandleMethod;
            this.getMouseButtonMethod = getMouseButtonMethod;
        }

        private static MouseKeyBindState.ReflectionAccessors create()
                throws ReflectiveOperationException {
            Class<?> keyBindingClass = Class.forName("net.minecraft.class_304");
            Class<?> keyClass = Class.forName("net.minecraft.class_3675$class_306");
            Class<?> keyTypeClass = Class.forName("net.minecraft.class_3675$class_307");
            Class<?> inputUtilClass = Class.forName("net.minecraft.class_3675");
            Class<?> clientClass = Class.forName("net.minecraft.class_310");
            Class<?> windowClass = Class.forName("net.minecraft.class_1041");
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            Field boundKeyField = keyBindingClass.getDeclaredField("field_1655");
            boundKeyField.setAccessible(true);
            Method getKeyCodeMethod = keyClass.getDeclaredMethod("method_1444");
            Method getKeyTypeMethod = keyClass.getDeclaredMethod("method_1442");
            Field mouseTypeField = keyTypeClass.getDeclaredField("field_1672");
            Method getClientMethod = clientClass.getDeclaredMethod("method_1551");
            Method getWindowMethod = clientClass.getDeclaredMethod("method_22683");
            Method isKeyDownMethod =
                    inputUtilClass.getDeclaredMethod("method_15987", windowClass, int.class);
            Method getHandleMethod = windowClass.getDeclaredMethod("method_4490");
            Method getMouseButtonMethod =
                    glfwClass.getDeclaredMethod("glfwGetMouseButton", long.class, int.class);
            getKeyCodeMethod.setAccessible(true);
            getKeyTypeMethod.setAccessible(true);
            mouseTypeField.setAccessible(true);
            getClientMethod.setAccessible(true);
            getWindowMethod.setAccessible(true);
            isKeyDownMethod.setAccessible(true);
            getHandleMethod.setAccessible(true);
            getMouseButtonMethod.setAccessible(true);
            return new MouseKeyBindState.ReflectionAccessors(
                    boundKeyField,
                    getKeyCodeMethod,
                    getKeyTypeMethod,
                    mouseTypeField.get(null),
                    getClientMethod,
                    getWindowMethod,
                    isKeyDownMethod,
                    getHandleMethod,
                    getMouseButtonMethod);
        }

        private boolean isPressed(KeyBinding keyBinding) throws ReflectiveOperationException {
            Object boundKey = this.boundKeyField.get(keyBinding);
            if (boundKey == null) {
                return false;
            } else {
                int keyCode = ((Number) this.getKeyCodeMethod.invoke(boundKey)).intValue();
                Object client = this.getClientMethod.invoke(null);
                Object window = client == null ? null : this.getWindowMethod.invoke(client);

                if (window == null) {
                    return false;
                } else if (this.getKeyTypeMethod.invoke(boundKey) == this.mouseKeyType) {
                    long handle = ((Number) this.getHandleMethod.invoke(window)).longValue();
                    return ((Number) this.getMouseButtonMethod.invoke(null, handle, keyCode))
                                    .intValue()
                            == 1;
                } else {
                    return Boolean.TRUE.equals(this.isKeyDownMethod.invoke(null, window, keyCode));
                }
            }
        }
    }
}
