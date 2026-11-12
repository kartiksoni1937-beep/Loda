package odinclient.module.spawner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public final class SpawnerMobFilter implements Predicate<Object> {
    private static final Set<String> SPAWNER_MOBS =
            Set.of(
                    "skeleton",
                    "iron_golem",
                    "blaze",
                    "creeper",
                    "spider",
                    "enderman",
                    "zombie",
                    "cave_spider",
                    "magma_cube");

    @Override
    public boolean test(Object entity) {
        String name = getEntityTypeName(entity);
        return SPAWNER_MOBS.contains(name)
                || name.equals("ender_man")
                || name.contains("enderman")
                || name.contains("ender_man");
    }

    static String getEntityTypeName(Object entity) {
        if (entity == null) {
            return "";
        } else {
            try {
                Class<?> entityTypeClass = Class.forName("net.minecraft.class_1299");
                Method getId = entityTypeClass.getMethod("method_5890", entityTypeClass);
                return normalizeEntityName(String.valueOf(getId.invoke(null, entity)));
            } catch (Throwable e) {
                try {
                    return normalizeEntityName(
                            String.valueOf(
                                    entity.getClass().getMethod("method_5882").invoke(entity)));
                } catch (Throwable e2) {
                    return normalizeEntityName(String.valueOf(entity));
                }
            }
        }
    }

    private static String normalizeEntityName(String raw) {
        String name = raw.toLowerCase(Locale.ROOT).trim();
        if (!name.contains("enderman") && !name.contains("ender_man")) {
            int colon = name.indexOf(58);
            if (colon >= 0) {
                name = name.substring(colon + 1);
            }

            if (name.startsWith("entity.minecraft.")) {
                name = name.substring("entity.minecraft.".length());
            }

            String normalized = name.replace(' ', '_').replace('-', '_');
            return !normalized.contains("enderman") && !normalized.contains("ender_man")
                    ? normalized
                    : "enderman";
        } else {
            return "enderman";
        }
    }
}
