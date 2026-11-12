package odinclient.manager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.module.Category;
import odinclient.module.Module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Environment(EnvType.CLIENT)
public final class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private static final List<Category> CATEGORIES = new CopyOnWriteArrayList<>();
    private final List<Module> modules = new CopyOnWriteArrayList<>();

    private ModuleManager() {}

    public static ModuleManager get() {
        return INSTANCE;
    }

    public static void registerCategory(Category category) {
        if (category != null && !CATEGORIES.contains(category)) {
            CATEGORIES.add(category);
        }
    }

    public void register(Module module) {
        if (module != null && this.getByName(module.name) == null) {
            this.modules.add(module);
        }
    }

    public Module getByName(String name) {
        if (name == null) {
            return null;
        } else {
            for (Module module : this.modules) {
                if (module.name.equalsIgnoreCase(name)) {
                    return module;
                }
            }

            return null;
        }
    }

    public Collection<Module> getAll() {
        return new ArrayList<>(this.modules);
    }

    public boolean isModuleActive(Class<?> type) {
        for (Module module : this.modules) {
            if (type.isInstance(module)) {
                return module.isActive();
            }
        }

        return false;
    }

    public void sortByName() {
        this.modules.sort(Comparator.comparing(module -> module.name));
    }
}
