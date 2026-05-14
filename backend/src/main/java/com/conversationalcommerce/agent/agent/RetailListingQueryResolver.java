package com.conversationalcommerce.agent.agent;

import java.util.Locale;
import java.util.Set;

/**
 * Picks the text passed to Retail Search for catalog listing: API {@code refinedQuery} can stay stale
 * (e.g. still "rice" while the user typed "uncle bens"), or the conversational layer may classify the turn
 * as {@code PRODUCT_DETAILS} without refreshing the refined query. Natural-language shopping phrasing
 * ("I want to buy some rice") uses the API refined term for Retail when surrounding words are only intent filler.
 */
public final class RetailListingQueryResolver {

    private RetailListingQueryResolver() {}

    /**
     * @param expandedUserMessage      user message after {@link ConversationalCommerceAdapter}-style expansion
     * @param apiRefinedQuery          refined query from conversational commerce (may be stale)
     * @param usedStorageTypeRecovery  when true, caller already set search query from session (e.g. previous "rice")
     * @param usedNoPreferenceRecovery same for "Any" / no preference
     * @param queryType                GCP user query type (e.g. SIMPLE_PRODUCT_SEARCH, PRODUCT_DETAILS)
     */
    public static String chooseSearchQuery(
            String expandedUserMessage,
            String apiRefinedQuery,
            boolean usedStorageTypeRecovery,
            boolean usedNoPreferenceRecovery,
            String queryType) {
        return chooseSearchQuery(
                expandedUserMessage,
                apiRefinedQuery,
                usedStorageTypeRecovery,
                usedNoPreferenceRecovery,
                queryType,
                null,
                null);
    }

    /**
     * @param sessionPreviousRefinedQuery last client-side refined product query (e.g. "rice"); used to merge single-token
     *                                    refinements like "Asian" after storage narrowing so Retail keeps the category
     * @param previousProductFilter       accumulated Retail filter from prior turns; merge only when non-blank (narrowing session)
     */
    public static String chooseSearchQuery(
            String expandedUserMessage,
            String apiRefinedQuery,
            boolean usedStorageTypeRecovery,
            boolean usedNoPreferenceRecovery,
            String queryType,
            String sessionPreviousRefinedQuery,
            String previousProductFilter) {
        if (usedStorageTypeRecovery || usedNoPreferenceRecovery) {
            return apiRefinedQuery;
        }
        String preferred =
                preferUserOrApiExpandedQuery(expandedUserMessage, apiRefinedQuery, queryType);
        return maybeMergeSingleTokenCategoryRefinement(
                preferred,
                expandedUserMessage,
                apiRefinedQuery,
                queryType,
                sessionPreviousRefinedQuery,
                previousProductFilter);
    }

    /**
     * After e.g. Dry storage, user says "Asian": {@link #preferUserOrApiExpandedQuery} returns only "Asian", which drops
     * "rice" and often matches nothing under the same filter. Merge session category + single-token add-on when we are
     * clearly in a Retail narrowing session (prior filter present).
     */
    static String maybeMergeSingleTokenCategoryRefinement(
            String preferred,
            String expandedUserMessage,
            String apiRefinedQuery,
            String queryType,
            String sessionPreviousRefinedQuery,
            String previousProductFilter) {
        if (preferred == null
                || expandedUserMessage == null
                || expandedUserMessage.isBlank()
                || sessionPreviousRefinedQuery == null
                || sessionPreviousRefinedQuery.isBlank()) {
            return preferred;
        }
        String u = expandedUserMessage.trim();
        if (u.split("\\s+").length != 1) {
            return preferred;
        }
        if (!u.equalsIgnoreCase(preferred != null ? preferred.trim() : "")) {
            return preferred;
        }
        String prev = sessionPreviousRefinedQuery.trim();
        if (prev.equalsIgnoreCase(u)) {
            return preferred;
        }
        String ul = u.toLowerCase(Locale.ROOT);
        String pl = prev.toLowerCase(Locale.ROOT);
        if (pl.contains(ul) || ul.contains(pl)) {
            return preferred;
        }
        String qt = queryType != null ? queryType.trim().toUpperCase(Locale.ROOT) : "";
        if (!"SIMPLE_PRODUCT_SEARCH".equals(qt) && !"INTENT_REFINEMENT".equals(qt)) {
            return preferred;
        }
        String f = previousProductFilter != null ? previousProductFilter.trim() : "";
        if (f.isEmpty() || "in_memory_pool_refine".equals(f)) {
            return preferred;
        }
        if (apiRefinedQuery != null && !apiRefinedQuery.isBlank()) {
            String rl = apiRefinedQuery.trim().toLowerCase(Locale.ROOT);
            if (!rl.equals(pl) && !rl.contains(ul) && !ul.contains(rl)) {
                // API already moved to a different line; do not glue stale previousRefinedQuery onto it.
                return preferred;
            }
        }
        return prev + " " + u;
    }

    /**
     * Words that can surround the API refined term without adding a catalog facet (e.g. "i want to buy some rice" → rice).
     */
    private static final Set<String> CATALOG_QUERY_FILLER_WORDS = Set.of(
            "i", "we", "you", "to", "a", "an", "the", "some", "any", "please",
            "want", "wanna", "need", "needs", "get", "got", "buy", "purchase", "purchasing", "order", "ordered",
            "looking", "for", "shop", "shopping", "pick", "up",
            "would", "could", "should", "may", "might", "must",
            "like", "love", "prefer", "trying", "try", "find", "search", "seek",
            "help", "me", "my", "our", "us", "give", "gimme",
            "am", "is", "are", "be", "been", "being", "do", "does", "did",
            "with", "of", "in", "on", "at", "by", "as", "if", "so", "and", "or", "but",
            "gonna", "gotta", "hafta",
            "id", "ill", "im", "ive", // i'd / i'll / i'm / i've after apostrophe strip
            "dont", "doesnt", "cant", "wont", "isnt", "arent",
            "today", "now", "just", "also", "only", "maybe", "really", "actually",
            "will");

    static String preferUserOrApiExpandedQuery(String expandedUser, String apiRefined, String queryType) {
        if (expandedUser == null || expandedUser.isBlank()) {
            return apiRefined;
        }
        if (apiRefined == null || apiRefined.isBlank()) {
            return expandedUser.trim();
        }
        String u = expandedUser.trim();
        String r = apiRefined.trim();
        if (u.equalsIgnoreCase(r)) {
            return r;
        }
        String ul = u.toLowerCase(Locale.ROOT);
        String rl = r.toLowerCase(Locale.ROOT);

        String qt = queryType != null ? queryType.trim().toUpperCase(Locale.ROOT) : "";
        boolean catalogListQueryType = "SIMPLE_PRODUCT_SEARCH".equals(qt) || "INTENT_REFINEMENT".equals(qt);

        int wordIdx = indexOfWholeWord(ul, rl);
        if (wordIdx >= 0 && ul.length() > rl.length()) {
            if (catalogListQueryType) {
                String before = ul.substring(0, wordIdx).trim();
                String after = ul.substring(wordIdx + rl.length()).trim();
                if (isOnlyCatalogFillerOutsideRefined(before) && isOnlyCatalogFillerOutsideRefined(after)) {
                    return r;
                }
            }
            return u;
        }

        if (!rl.contains(ul) && !ul.contains(rl)) {
            return u;
        }
        if (queryType != null) {
            String qt2 = queryType.trim().toUpperCase(Locale.ROOT);
            if ("PRODUCT_DETAILS".equals(qt2) || "GENERAL_QUESTION".equals(qt2)) {
                return u;
            }
        }
        return r;
    }

    /**
     * First whole-word occurrence of {@code needle} in {@code haystack} (both lowercased), or {@code -1}.
     */
    static int indexOfWholeWord(String haystackLower, String needleLower) {
        if (needleLower.isEmpty()) {
            return -1;
        }
        int idx = 0;
        while ((idx = haystackLower.indexOf(needleLower, idx)) >= 0) {
            boolean leftOk = idx == 0 || !Character.isLetterOrDigit(haystackLower.charAt(idx - 1));
            int end = idx + needleLower.length();
            boolean rightOk = end >= haystackLower.length() || !Character.isLetterOrDigit(haystackLower.charAt(end));
            if (leftOk && rightOk) {
                return idx;
            }
            idx++;
        }
        return -1;
    }

    static boolean isOnlyCatalogFillerOutsideRefined(String fragmentLower) {
        if (fragmentLower == null || fragmentLower.isBlank()) {
            return true;
        }
        String normalized = fragmentLower.replace('’', '\'');
        for (String raw : normalized.split("\\s+")) {
            if (raw.isBlank()) {
                continue;
            }
            String token = raw.replaceAll("^[^a-z']+|[^a-z']+$", "").toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            token = token.replace("'", "");
            if (!CATALOG_QUERY_FILLER_WORDS.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
