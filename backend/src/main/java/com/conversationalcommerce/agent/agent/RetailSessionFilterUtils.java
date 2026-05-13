package com.conversationalcommerce.agent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helpers for merging session-scoped Retail filters without stacking incompatible clauses.
 */
public final class RetailSessionFilterUtils {

    private static final Pattern STORAGE_ATTRIBUTE_ANY =
            Pattern.compile("(?i)^attributes\\.(stockType|storageType)\\s*:\\s*ANY\\([^)]*\\)\\s*$");

    private RetailSessionFilterUtils() {}

    /**
     * Drops {@code attributes.stockType/storageType: ANY(...)} segments so a new storage chip replaces
     * the previous one instead of AND-ing contradictory facets (e.g. Ambient then Dry).
     */
    public static String stripStorageAttributeAnyFilters(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        String trimmed = filter.trim();
        String[] parts = trimmed.split(" AND ");
        List<String> kept = new ArrayList<>(parts.length);
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (STORAGE_ATTRIBUTE_ANY.matcher(p).matches()) {
                continue;
            }
            kept.add(p);
        }
        if (kept.isEmpty()) {
            return null;
        }
        return String.join(" AND ", kept);
    }
}
