package odinclient.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.manager.ConfigStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public abstract class Setting<T> {
    public final String name;
    public final String title;
    public final String description;
    protected final T defaultValue;
    private final VisibilityCondition visibilityCondition;
    private final Consumer<T> changeListener;
    private T value;

    protected Setting(
            String name,
            String description,
            T defaultValue,
            VisibilityCondition visibilityCondition,
            Consumer<T> changeListener) {
        this.name = name;
        this.title = title(name);
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = this.copyValue(defaultValue);
        this.visibilityCondition = visibilityCondition;
        this.changeListener = changeListener;
    }

    public T getValue() {
        return this.value;
    }

    public boolean setValue(T newValue) {
        if (newValue == null) {
            return false;
        } else {
            this.value = newValue;
            if (this.changeListener != null) {
                this.changeListener.accept(newValue);
            }

            ConfigStore.markDirty();
            return true;
        }
    }

    public boolean setValueFromString(String text) {
        try {
            return this.setValue(this.parseValue(text));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean isVisible() {
        return this.visibilityCondition == null || this.visibilityCondition.isVisible();
    }

    public List<String> a_() {
        return List.of();
    }

    protected abstract T parseValue(String text);

    private T copyValue(T value) {
        return (T) (value instanceof List<?> list ? new ArrayList<>(list) : value);
    }

    private static String title(String name) {
        StringBuilder builder = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c != '-' && c != '_') {
                builder.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else {
                builder.append(' ');
                capitalizeNext = true;
            }
        }

        return builder.toString();
    }
}
