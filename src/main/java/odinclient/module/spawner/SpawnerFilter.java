package odinclient.module.spawner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Collection;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public final class SpawnerFilter {
    private SpawnerFilter() {}

    public static boolean matchesFilter(Object mode, Collection<?> entityTypes, String mobName) {
        if (mode != SpawnerFilterMode.Specific) {
            return true;
        } else if (entityTypes != null && !entityTypes.isEmpty()) {
            String normalized = normalizeName(mobName);
            for (Object entityType : entityTypes) {
                String candidate = normalizeName(SpawnerMobFilter.getEntityTypeName(entityType));
                if (!candidate.isEmpty()
                        && (normalized.equals(candidate) || isAlias(normalized, candidate))) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private static boolean isAlias(String name, String candidate) {
        return candidate.equals("magma_cube") && name.contains("magma")
                || candidate.equals("iron_golem") && name.equals("iron_golem")
                || candidate.equals("cave_spider") && name.equals("cave_spider")
                || candidate.equals("enderman")
                        && (name.equals("enderman") || name.equals("ender_man"));
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        } else {
            String result = name.toLowerCase(Locale.ROOT);
            if (result.endsWith(" spawner")) {
                result = result.substring(0, result.length() - 8);
            }

            return result.replace("minecraft:", "")
                    .replace("_", " ")
                    .replace("-", " ")
                    .replaceAll("\\s+", " ")
                    .trim()
                    .replace(' ', '_');
        }
    }
}
