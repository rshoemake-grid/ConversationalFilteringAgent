package com.conversationalcommerce.agent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds Retail filter segments for catalog-encoded pack / weight values such as {@code 3__LB}.
 * These must not use {@code brands: ANY(...)}.
 */
public final class PackFacetRetailFilter {

    /** Common custom-attribute keys for pack size / net weight (tune per catalog if needed). */
    private static final List<String> DEFAULT_ATTRIBUTE_KEYS = List.of(
            "packSize", "pack_size", "netWeight", "net_weight", "packageSize", "package_size");

    private PackFacetRetailFilter() {
    }

    /**
     * When the user clearly selected a pack / UOM chip (matches prior or current suggested answers) and the
     * canonical value uses the {@code <n>__<UNIT>} encoding, returns an OR of {@code attributes.<key>: ANY("value")}.
     */
    public static String buildPackFacetFilterIfSuggestionSelected(
            String canonicalValue,
            List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers,
            Map<String, Object> context) {
        if (canonicalValue == null || canonicalValue.isBlank()) {
            return null;
        }
        String trimmed = canonicalValue.trim();
        if (BrandDisplayResolver.formatNumericDoubleUnderscoreUom(trimmed) == null) {
            return null;
        }
        if (!matchesSuggestedAnswerSelection(trimmed, suggestedAnswers, context)) {
            return null;
        }
        return buildOrClause(trimmed, DEFAULT_ATTRIBUTE_KEYS);
    }

    static String buildOrClause(String encodedValue, List<String> attributeKeys) {
        if (encodedValue == null || encodedValue.isBlank() || attributeKeys == null || attributeKeys.isEmpty()) {
            return null;
        }
        String lit = "\"" + escapeRetailFilterString(encodedValue.trim()) + "\"";
        List<String> parts = new ArrayList<>(attributeKeys.size());
        for (String key : attributeKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String k = key.trim();
            if (k.startsWith("attributes.")) {
                k = k.substring("attributes.".length());
            }
            parts.add("attributes." + k + ": ANY(" + lit + ")");
        }
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return "(" + String.join(" OR ", parts) + ")";
    }

    private static boolean matchesSuggestedAnswerSelection(
            String trimmed,
            List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers,
            Map<String, Object> context) {
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        if (prevList != null) {
            for (var m : prevList) {
                String v = m.getOrDefault("value", "").trim();
                String d = m.getOrDefault("displayText", "").trim();
                if (trimmed.equals(v) || trimmed.equalsIgnoreCase(d)) {
                    return true;
                }
            }
        }
        if (suggestedAnswers != null) {
            for (var sa : suggestedAnswers) {
                String v = sa.value() != null ? sa.value().trim() : "";
                String d = sa.displayText() != null ? sa.displayText().trim() : "";
                if (trimmed.equals(v) || trimmed.equalsIgnoreCase(d)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String escapeRetailFilterString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
