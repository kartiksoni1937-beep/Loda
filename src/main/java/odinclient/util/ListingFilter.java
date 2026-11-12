package odinclient.util;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class ListingFilter {
    private static final Set<String> hiddenNames = Set.of("Cde8523e89733e5");
    private static final Set<String> hiddenInternalNames = Set.of("spawner-protect");

    private ListingFilter() {}

    public static String[] filterNames(String[] names) {
        Predicate<String> visible = name -> name != null && !hiddenNames.contains(name);
        return Arrays.stream(names).filter(visible).toArray(String[]::new);
    }

    public static List<?> filterEntries(List<?> entries) {
        ArrayList<Object> visible = new ArrayList<>(entries.size());

        for (Object entry : entries) {
            String key = internalName(entry);
            if (!hiddenInternalNames.contains(key)) {
                visible.add(entry);
            }
        }

        return List.copyOf(visible);
    }

    private static String internalName(Object entry) {
        if (entry != null && entry.getClass().isRecord()) {
            try {
                for (RecordComponent component : entry.getClass().getRecordComponents()) {
                    if ("internalName".equals(component.getName())) {
                        Method accessor = component.getAccessor();
                        accessor.setAccessible(true);
                        return accessor.invoke(entry) instanceof String name ? name : "";
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return "";
    }
}
