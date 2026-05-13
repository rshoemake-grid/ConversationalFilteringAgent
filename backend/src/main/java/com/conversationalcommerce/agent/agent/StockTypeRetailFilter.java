package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the value for Retail {@code attributes.*: ANY("…")} stock-type filters.
 * When canonical values are enabled, emitted filters include both long-form tokens (e.g. AMBIENT) and
 * single-letter codes (e.g. S) so search matches catalogs indexed either way.
 */
public final class StockTypeRetailFilter {

    private static final Set<String> SHORT_STOCK_LETTERS = Set.of("S", "R", "D", "N", "F", "C");

    private StockTypeRetailFilter() {}

    /**
     * Value to place inside {@code ANY("…")} for the configured stock-type attribute.
     * {@code N} is returned trimmed for catalog-specific non-refrigerated codes.
     */
    public static String attributeValueForFilter(String value, ConversationalCommerceConfig config) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (config != null && !config.stockTypeFilterUseCanonicalValues()) {
            return passthroughFilterValue(trimmed, config);
        }
        String trimmedUpper = trimmed.toUpperCase();
        return switch (trimmedUpper) {
            case "D" -> "DRY_STORAGE";
            case "R" -> "REFRIGERATED";
            case "S" -> "AMBIENT";
            case "N" -> trimmed;
            default -> resolveViaExpansion(trimmed, config);
        };
    }

    private static String resolveViaExpansion(String trimmed, ConversationalCommerceConfig config) {
        if (config == null) {
            return trimmed;
        }
        Map<String, Map<String, String>> expansion = config.getAttributeValueExpansion();
        if (expansion == null) {
            return trimmed;
        }
        Map<String, String> storageMap = expansion.get("storageType");
        if (storageMap == null) {
            return trimmed;
        }
        String resolved = findExpansionMatch(storageMap, trimmed);
        if (resolved != null && ("D".equals(resolved) || "R".equals(resolved) || "S".equals(resolved))) {
            return switch (resolved) {
                case "D" -> "DRY_STORAGE";
                case "R" -> "REFRIGERATED";
                case "S" -> "AMBIENT";
                default -> resolved;
            };
        }
        return resolved != null ? resolved : trimmed;
    }

    /** Use conversational / chip values as-is in Retail filters (no S→AMBIENT mapping). */
    private static String passthroughFilterValue(String trimmed, ConversationalCommerceConfig config) {
        String upper = trimmed.toUpperCase();
        if (SHORT_STOCK_LETTERS.contains(upper)) {
            return "N".equals(upper) ? trimmed : upper;
        }
        Map<String, Map<String, String>> expansion = config.getAttributeValueExpansion();
        if (expansion == null) {
            return trimmed;
        }
        Map<String, String> storageMap = expansion.get("storageType");
        if (storageMap == null) {
            return trimmed;
        }
        String resolved = findExpansionMatch(storageMap, trimmed);
        return resolved != null ? resolved : trimmed;
    }

    private static String findExpansionMatch(Map<String, String> mapping, String input) {
        if (input == null || mapping == null) {
            return null;
        }
        if (mapping.containsKey(input)) {
            return mapping.get(input);
        }
        for (var e : mapping.entrySet()) {
            if (e.getKey().equalsIgnoreCase(input)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Literals for {@code attributes.<key>: ANY("a", "b")} after {@link #attributeValueForFilter} expansion.
     */
    public static List<String> indexedAttributeValuesForAnyClause(String expandedFilterValue, ConversationalCommerceConfig config) {
        if (expandedFilterValue == null || expandedFilterValue.isBlank()) {
            return List.of();
        }
        String t = expandedFilterValue.trim();
        if (config != null && !config.stockTypeFilterUseCanonicalValues()) {
            return List.of(t);
        }
        String u = t.toUpperCase(Locale.ROOT);
        return switch (u) {
            case "AMBIENT" -> List.of("AMBIENT", "S");
            case "REFRIGERATED" -> List.of("REFRIGERATED", "R", "C");
            case "DRY_STORAGE" -> List.of("DRY_STORAGE", "D");
            case "FROZEN" -> List.of("FROZEN", "F");
            case "S" -> List.of("AMBIENT", "S");
            case "R" -> List.of("REFRIGERATED", "R", "C");
            case "D" -> List.of("DRY_STORAGE", "D");
            case "F" -> List.of("FROZEN", "F");
            case "C" -> List.of("REFRIGERATED", "R", "C");
            default -> List.of(t);
        };
    }

    /**
     * Full Retail filter segment {@code attributes.<attr>: ANY("…")} with escaped literals.
     */
    public static String buildStorageAttributeAnyClause(
            String expandedFilterValue,
            String attributeName,
            ConversationalCommerceConfig config) {
        if (expandedFilterValue == null || expandedFilterValue.isBlank()) {
            return null;
        }
        String attr = (attributeName != null && !attributeName.isBlank()) ? attributeName.trim() : "stockType";
        List<String> raw = indexedAttributeValuesForAnyClause(expandedFilterValue, config);
        if (raw.isEmpty()) {
            return null;
        }
        var unique = new LinkedHashSet<String>();
        for (String v : raw) {
            if (v != null && !v.isBlank()) {
                unique.add(v.trim());
            }
        }
        if (unique.isEmpty()) {
            return null;
        }
        var escaped = new ArrayList<String>(unique.size());
        for (String v : unique) {
            escaped.add("\"" + escapeRetailFilterString(v) + "\"");
        }
        return "attributes." + attr + ": ANY(" + String.join(", ", escaped) + ")";
    }

    private static String escapeRetailFilterString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
