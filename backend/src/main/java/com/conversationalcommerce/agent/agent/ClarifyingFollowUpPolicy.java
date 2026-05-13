package com.conversationalcommerce.agent.agent;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides when follow-up clarifying questions (and related chips) should be offered based on
 * how many products matched in Retail.
 */
public final class ClarifyingFollowUpPolicy {

    private static final Set<String> STORAGE_TYPE_VALUES = Set.of(
            "FROZEN", "REFRIGERATED", "AMBIENT", "DRY_STORAGE", "F", "C", "S", "R", "D", "N"
    );

    private ClarifyingFollowUpPolicy() {
    }

    /**
     * Prefer Retail total hit count when known; otherwise use the number of products on the current page.
     */
    public static int effectiveProductCountForClarifying(List<?> productsOnPage, SearchResult searchResult) {
        if (searchResult != null && searchResult.totalSize() >= 0) {
            return (int) Math.min(searchResult.totalSize(), Integer.MAX_VALUE);
        }
        return productsOnPage != null ? productsOnPage.size() : 0;
    }

    public static int effectiveProductCountForClarifying(int productsOnPage, long productTotalSize) {
        if (productTotalSize >= 0) {
            return (int) Math.min(productTotalSize, Integer.MAX_VALUE);
        }
        return productsOnPage;
    }

    public static boolean shouldOfferClarifyingFollowUp(int effectiveProductCount, int productCountThreshold) {
        return effectiveProductCount > productCountThreshold;
    }

    public static boolean isStorageSuggestionValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String u = value.trim().toUpperCase(Locale.ROOT);
        if (STORAGE_TYPE_VALUES.contains(u)) {
            return true;
        }
        return STORAGE_TYPE_VALUES.contains(u.replace(' ', '_'));
    }

    /**
     * When the UI shows a clarifying follow-up about stock or storage, VAISR storage chips (S/R/D, etc.)
     * are valid answers and must not be stripped just because product rows were returned.
     */
    public static boolean clarifyingQuestionImpliesStorageChoice(String clarifyingQuestion) {
        if (clarifyingQuestion == null || clarifyingQuestion.isBlank()) {
            return false;
        }
        String lower = clarifyingQuestion.toLowerCase(Locale.ROOT);
        return lower.contains("stock") || lower.contains("storage");
    }

    /**
     * Drops storage/stock chips when we are not offering a clarifying follow-up (small result set).
     * Leaves non-storage suggestions (e.g. brands) untouched.
     */
    public static List<ConversationalCommerceClient.SuggestedAnswer> withoutStorageSuggestions(
            List<ConversationalCommerceClient.SuggestedAnswer> list) {
        if (list == null || list.isEmpty()) {
            return list != null ? list : List.of();
        }
        return list.stream().filter(sa -> !isStorageSuggestionValue(sa.value())).toList();
    }
}
