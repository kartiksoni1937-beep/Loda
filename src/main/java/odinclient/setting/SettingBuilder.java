package odinclient.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public abstract class SettingBuilder<B, S extends Setting<T>, T> {
    public String name = "setting";
    public String description = "";
    public T defaultValue;
    public VisibilityCondition visibility;
    public Consumer<T> changeListener;

    public B name(String value) {
        this.name = value;
        return (B) this;
    }

    public B description(String text) {
        this.description = text;
        return (B) this;
    }

    public B defaultValue(T value) {
        this.defaultValue = value;
        return (B) this;
    }

    public B visibleWhen(VisibilityCondition condition) {
        this.visibility = condition;
        return (B) this;
    }

    public B onChange(Consumer<T> listener) {
        this.changeListener = listener;
        return (B) this;
    }

    public abstract S build();
}
