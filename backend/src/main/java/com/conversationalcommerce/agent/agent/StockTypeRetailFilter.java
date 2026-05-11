package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;

import java.util.Map;
import java.util.Set;

/**
 * Builds the value for Retail {@code attributes.*: ANY("…")} stock-type filters.
 * By default uses single-letter codes as stored in the catalog; optional canonical mapping when configured.
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
}
