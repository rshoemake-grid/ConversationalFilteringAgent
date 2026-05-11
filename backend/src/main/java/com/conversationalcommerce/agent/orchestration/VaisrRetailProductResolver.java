package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ClarifyingFollowUpPolicy;
import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.agent.StockTypeRetailFilter;
import com.conversationalcommerce.agent.agent.ProductEnrichmentService;
import com.conversationalcommerce.agent.agent.RetailSearchClient;
import com.conversationalcommerce.agent.agent.SearchResult;
import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.gemini.ClarifyingQuestionGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runs Retail Search + enrichment after a VAISR conversational search result, mirroring Approach A so Approach B
 * lists real products instead of model-only prose.
 */
@Component
public class VaisrRetailProductResolver {

    private static final Logger log = LoggerFactory.getLogger(VaisrRetailProductResolver.class);

    private static final String FALLBACK_CLARIFY = "I found many %s matching your search. To narrow it down, could you tell me more? "
            + "For example: What type or form? (e.g. raw, cooked, peeled — or style, color) Any size or price range?";

    private static final Set<String> STORAGE_TYPE_VALUES = Set.of(
            "FROZEN", "REFRIGERATED", "AMBIENT", "DRY_STORAGE", "F", "C", "S", "R", "D", "N"
    );

    private static final Set<String> NON_BRAND_VALUES = Set.of(
            "FROZEN", "REFRIGERATED", "AMBIENT", "DRY_STORAGE", "COLD", "F", "C", "S", "R", "D", "N", "ANY"
    );

    private static final Set<String> NO_PREFERENCE_PHRASES = Set.of(
            "ANY", "NO", "NONE", "NOPREFERENCE", "DON'T CARE", "DONT CARE", "DOESN'T MATTER",
            "DOESNT MATTER", "WHATEVER", "I DON'T CARE", "IDC"
    );

    private final RetailSearchClient searchClient;
    private final ProductEnrichmentService enrichmentService;
    private final ConversationalCommerceConfig config;
    private final Optional<ClarifyingQuestionGenerator> clarifyingGenerator;

    public VaisrRetailProductResolver(
            RetailSearchClient searchClient,
            ProductEnrichmentService enrichmentService,
            ConversationalCommerceConfig config,
            Optional<ClarifyingQuestionGenerator> clarifyingGenerator) {
        this.searchClient = searchClient;
        this.enrichmentService = enrichmentService;
        this.config = config;
        this.clarifyingGenerator = clarifyingGenerator != null ? clarifyingGenerator : Optional.empty();
    }

    /**
     * Outcome merged into {@link com.conversationalcommerce.agent.agent.AgentResponse} for Approach B.
     */
    public record Augmentation(
            List<AgentResponse.ProductResult> products,
            String refinedQuery,
            String text,
            String productFilter,
            long productTotalSize,
            boolean productTotalSizeIsApproximate,
            String productNextPageToken,
            String clarifyingQuestion,
            String responseSource,
            /** When non-null, replaces tool suggested answers (e.g. re-ask after a failed storage chip). */
            List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswersOverride) {
    }

    public Augmentation resolve(
            ConversationalCommerceClient.ConversationalCommerceResult result,
            String userMessage,
            Map<String, Object> context) {
        String visitorId = ContextUtils.getVisitorId(context, config.defaultVisitorId());
        String productPageToken = (String) context.get("productPageToken");
        Integer productPageSizeOverride = context.get("productPageSize") instanceof Number n ? n.intValue() : null;

        String imageBase64 = (String) context.get("imageBase64");
        String userInput = (userMessage != null && !messageIsBlank(userMessage))
                ? userMessage.trim()
                : (imageBase64 != null ? "Find products similar to this image" : "");
        final String originalUserInput = userInput;
        final String query = expandShortAttributeValue(userInput, context);

        String searchQuery = result.refinedQuery();
        boolean usedNoPreferenceRecovery = false;
        boolean usedStorageTypeRecovery = false;
        String storageTypeFilter = null;

        if (isNoPreference(query) && (searchQuery == null || searchQuery.isEmpty())) {
            String prevRefined = (String) context.get("previousRefinedQuery");
            if (prevRefined != null && !prevRefined.isBlank()) {
                searchQuery = prevRefined.trim();
                usedNoPreferenceRecovery = true;
            }
        }

        if (!usedNoPreferenceRecovery && isStorageTypeSelection(query, originalUserInput, context)) {
            String prevRefined = (String) context.get("previousRefinedQuery");
            if (prevRefined != null && !prevRefined.isBlank()) {
                searchQuery = prevRefined.trim();
                storageTypeFilter = buildStorageTypeFilter(StockTypeRetailFilter.attributeValueForFilter(query, config));
                usedStorageTypeRecovery = true;
            }
        }

        List<AgentResponse.ProductResult> products = List.of();
        SearchResult searchResult = null;
        String productFilterUsed = null;

        if (searchQuery != null && !searchQuery.isEmpty()) {
            try {
                String filter = buildBrandFilterWhenApplicable(query, result.suggestedAnswers(), context);
                if (storageTypeFilter != null) {
                    filter = (filter != null && !filter.isBlank())
                            ? filter + " AND " + storageTypeFilter
                            : storageTypeFilter;
                }
                filter = combineRetailFilters(sanitizeSessionProductFilter((String) context.get("previousProductFilter")), filter);
                productFilterUsed = filter;
                String pageToken = (productPageToken != null && !productPageToken.isBlank()) ? productPageToken : null;
                searchResult = searchClient.searchWithPagination(
                        config.placement(),
                        config.branch(),
                        searchQuery,
                        visitorId,
                        filter,
                        pageToken,
                        productPageSizeOverride
                );
                products = enrichmentService.enrich(searchResult.products());
            } catch (Exception e) {
                log.warn("ADK retail product search failed: {}", e.getMessage());
            }
        }

        String text = result.text() != null ? result.text() : "";
        if (usedNoPreferenceRecovery || usedStorageTypeRecovery) {
            if (!products.isEmpty()) {
                int n = products.size();
                text = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
            } else if (!usedStorageTypeRecovery) {
                text = "No products found.";
            }
        }

        List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswersOverride = null;
        if (usedStorageTypeRecovery && products.isEmpty()) {
            String prevText = (String) context.get("previousAssistantText");
            @SuppressWarnings("unchecked")
            var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
            List<ConversationalCommerceClient.SuggestedAnswer> remaining = prevList != null && !prevList.isEmpty()
                    ? prevList.stream()
                            .filter(m -> !originalUserInput.trim().equals(m.getOrDefault("value", "").trim())
                                    && !originalUserInput.trim().equalsIgnoreCase(m.getOrDefault("displayText", "").trim()))
                            .map(m -> new ConversationalCommerceClient.SuggestedAnswer(
                                    m.getOrDefault("displayText", m.getOrDefault("value", "")),
                                    m.getOrDefault("value", "")))
                            .toList()
                    : (result.suggestedAnswers() != null
                            ? result.suggestedAnswers().stream()
                                    .filter(sa -> !originalUserInput.trim().equals(
                                            sa.value() != null ? sa.value().trim() : ""))
                                    .toList()
                            : List.of());

            boolean usedStorageReaskFallback = false;
            if (remaining.size() == 1 && prevText != null && !prevText.isBlank()) {
                String prevRefined = (String) context.get("previousRefinedQuery");
                if (prevRefined != null && !prevRefined.isBlank()) {
                    ConversationalCommerceClient.SuggestedAnswer sole = remaining.get(0);
                    String soleValue = sole.value();
                    String filterValue = StockTypeRetailFilter.attributeValueForFilter(soleValue, config);
                    if (filterValue == null && soleValue != null) {
                        filterValue = soleValue.trim();
                    }
                    if (filterValue != null) {
                        try {
                            String stFilter = combineRetailFilters(
                                    sanitizeSessionProductFilter((String) context.get("previousProductFilter")),
                                    buildStorageTypeFilter(filterValue));
                            List<AgentResponse.ProductResult> autoProducts = searchClient.search(
                                    config.placement(), config.branch(), prevRefined.trim(), visitorId, stFilter);
                            autoProducts = enrichmentService.enrich(autoProducts);
                            products = autoProducts;
                            productFilterUsed = stFilter;
                            if (!autoProducts.isEmpty()) {
                                int n = autoProducts.size();
                                text = n == 1
                                        ? "I found 1 product matching your request."
                                        : "I found " + n + " products matching your request.";
                                searchResult = SearchResult.of(autoProducts, null, Math.max((long) n, 1L));
                            } else {
                                text = "No products found.";
                            }
                            suggestedAnswersOverride = List.of();
                            usedStorageReaskFallback = true;
                        } catch (Exception e) {
                            log.warn("Auto-run last storage option failed: {}", e.getMessage());
                        }
                    }
                }
            }

            if (!usedStorageReaskFallback && prevText != null && !prevText.isBlank()) {
                boolean alreadyHasPrefix = prevText.contains("No products found for that option.");
                text = !alreadyHasPrefix
                        ? "No products found for that option.\n\n" + prevText
                        : prevText;
                suggestedAnswersOverride = List.copyOf(applyStorageTypeDisplayMapping(remaining));
                usedStorageReaskFallback = true;
            } else if (!usedStorageReaskFallback && !remaining.isEmpty()) {
                text = "No products found for that option.\n\nWhat would you like to try next?";
                suggestedAnswersOverride = List.copyOf(applyStorageTypeDisplayMapping(remaining));
            } else if (!usedStorageReaskFallback) {
                text = "No products found.";
            }
        }

        List<AgentResponse.ProductResult> productsToReturn = products;
        String refinedQuery = ((usedNoPreferenceRecovery || usedStorageTypeRecovery) && searchQuery != null)
                ? searchQuery
                : result.refinedQuery();

        boolean isSearchingFallback = text.startsWith("Searching for:");
        boolean hasRefinedQuery = refinedQuery != null && !refinedQuery.isEmpty();
        int productCountThreshold = config.productCountThreshold();
        int clarifyingCount = ClarifyingFollowUpPolicy.effectiveProductCountForClarifying(products, searchResult);

        boolean agentProvidedClarifying = text != null && !text.isEmpty() && !text.startsWith("Searching for:")
                && text.contains("?");
        String clarifyingQuestion = null;

        // Approach B formatting (aligned with ConversationalCommerceAdapter when orchestrationMode is not convo_commerce)
        if (!products.isEmpty() && ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold)
                && !usedNoPreferenceRecovery && !usedStorageTypeRecovery) {
            String categoryHint = refinedQuery != null ? refinedQuery : "products";
            int n = products.size();
            String countPhrase = n == 1
                    ? "I found 1 product matching your request."
                    : "I found " + n + " products matching your request.";
            String clarifyingText = agentProvidedClarifying
                    ? text
                    : (clarifyingGenerator
                            .map(gen -> gen.generate(categoryHint, clarifyingCount))
                            .filter(t -> t != null && !t.isBlank())
                            .orElse(FALLBACK_CLARIFY.formatted(categoryHint)));
            clarifyingQuestion = clarifyingText;
            text = countPhrase;
            productsToReturn = products;
        } else if (!products.isEmpty()) {
            if (!usedNoPreferenceRecovery && !usedStorageTypeRecovery) {
                int n = products.size();
                String countPhrase = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                if (!text.isEmpty() && !isSearchingFallback && text.contains("?")
                        && ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold)) {
                    clarifyingQuestion = text;
                    text = countPhrase;
                } else if (text.isEmpty() || isSearchingFallback) {
                    text = countPhrase;
                } else if (ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold)) {
                    text = countPhrase + "\n\n" + text;
                } else {
                    if (text.contains("?")) {
                        text = countPhrase;
                    } else {
                        text = (text.isEmpty() || isSearchingFallback) ? countPhrase : countPhrase + "\n\n" + text;
                    }
                }
            }
        } else if (products.isEmpty() && hasRefinedQuery && !usedNoPreferenceRecovery && !usedStorageTypeRecovery) {
            // Recovery paths above already set "No products found." when the retail search is empty; do not append again.
            boolean hasMeaningfulAgentText = text != null && !text.isEmpty() && !text.startsWith("Searching for:");
            if (isNoProductsOnlyAgentText(text)) {
                text = "No products found.";
            } else {
                text = (hasMeaningfulAgentText ? text + "\n\n" : "") + "No products found.";
            }
        }

        long totalSize = searchResult != null ? searchResult.totalSize() : -1;
        String nextPageToken = searchResult != null ? searchResult.nextPageToken() : null;
        boolean totalSizeIsApproximate = false;
        if (totalSize < 0 && productsToReturn != null && !productsToReturn.isEmpty()) {
            int pageSize = getEffectivePageSize(productPageSizeOverride);
            int pagesEstimate = (int) Math.ceil((double) productsToReturn.size() / (double) pageSize)
                    + (nextPageToken != null && !nextPageToken.isBlank() ? 1 : 0);
            totalSize = Math.max(productsToReturn.size(), (long) pagesEstimate * pageSize);
            totalSizeIsApproximate = true;
        }
        if (totalSize >= 0 && productsToReturn != null && !productsToReturn.isEmpty()
                && text != null && text.matches("I found \\d+ product(s)? matching your request\\.")) {
            String prefix = totalSizeIsApproximate ? "at least " : "";
            text = productsToReturn.size() == 1
                    ? "Showing 1 of " + prefix + totalSize + " product"
                    : "Showing " + productsToReturn.size() + " of " + prefix + totalSize + " products";
        }

        return new Augmentation(
                productsToReturn != null ? productsToReturn : List.of(),
                refinedQuery != null ? refinedQuery : "",
                text != null ? text : "",
                productFilterUsed,
                totalSize,
                totalSizeIsApproximate,
                nextPageToken,
                clarifyingQuestion,
                "app",
                suggestedAnswersOverride
        );
    }

    private static boolean messageIsBlank(String m) {
        return m == null || m.trim().isEmpty();
    }

    /** True when VAISR / agent text is already only the standard empty-result line (avoid "No products found." twice). */
    private static boolean isNoProductsOnlyAgentText(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return "No products found.".equalsIgnoreCase(t) || "No products found".equalsIgnoreCase(t);
    }

    private int getEffectivePageSize(Integer override) {
        if (override != null && override > 0) {
            return override;
        }
        return config.productSearchPageSize();
    }

    private String expandShortAttributeValue(String query, Map<String, Object> context) {
        if (query == null || query.isBlank()) {
            return query;
        }
        String trimmed = query.trim();
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        String resolved = trimmed;
        if (prevList != null) {
            for (var m : prevList) {
                String displayText = m.getOrDefault("displayText", "").trim();
                String value = m.getOrDefault("value", "").trim();
                if (!value.isEmpty() && trimmed.equalsIgnoreCase(value)) {
                    resolved = value;
                    break;
                }
                if (!displayText.isEmpty() && trimmed.equalsIgnoreCase(displayText) && !value.equalsIgnoreCase(trimmed)) {
                    resolved = value;
                    break;
                }
            }
        }
        var expansion = config.getAttributeValueExpansion();
        if (expansion != null) {
            for (var attrEntry : expansion.entrySet()) {
                if (attrEntry.getValue() == null) {
                    continue;
                }
                String canonical = findExpansionMatch(attrEntry.getValue(), resolved);
                if (canonical != null && !canonical.equals(resolved)) {
                    return canonical;
                }
            }
        }
        return resolved;
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

    private static boolean isNoPreference(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String n = input.trim().toUpperCase().replaceAll("\\s+", " ");
        if (NO_PREFERENCE_PHRASES.contains(n)) {
            return true;
        }
        if (n.replace("'", "").replace(" ", "").equals("NOPREFERENCE")) {
            return true;
        }
        return n.equals("NO PREFERENCE") || n.startsWith("NO PREFERENCE");
    }

    /**
     * User picked a storage/stock chip or typed an equivalent code — run Retail using previousRefinedQuery + stock filter
     * like Approach A. If {@code previousRefinedQuery} is set, we do not require {@code previousSuggestedAnswers} (Approach B
     * sometimes omits it); we still require the message to look like a storage token.
     */
    private static boolean isStorageTypeSelection(String expandedQuery, String originalUserInput, Map<String, Object> context) {
        if (expandedQuery == null || expandedQuery.isBlank() || context == null) {
            return false;
        }
        if (!looksLikeStorageTypeToken(expandedQuery)) {
            return false;
        }
        Object pr = context.get("previousRefinedQuery");
        String prevRefined = pr != null ? pr.toString().trim() : "";
        if (!prevRefined.isEmpty()) {
            return true;
        }
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        if (prevList == null || prevList.isEmpty()) {
            return false;
        }
        String trimmed = originalUserInput != null ? originalUserInput.trim() : "";
        for (var m : prevList) {
            String displayText = m.getOrDefault("displayText", "").trim();
            String value = m.getOrDefault("value", "").trim();
            if (trimmed.equalsIgnoreCase(displayText) || trimmed.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** Accepts S/R/D/N, DRY_STORAGE, "dry storage", etc. */
    private static boolean looksLikeStorageTypeToken(String expandedQuery) {
        String u = expandedQuery.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return STORAGE_TYPE_VALUES.contains(expandedQuery.trim().toUpperCase(Locale.ROOT))
                || STORAGE_TYPE_VALUES.contains(u);
    }

    private String buildStorageTypeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String attr = config.stockTypeFilterAttribute();
        String escaped = value.trim().replace("\\", "\\\\").replace("\"", "\\\"");
        return "attributes." + attr + ": ANY(\"" + escaped + "\")";
    }

    private static final Map<String, String> STORAGE_DISPLAY_DEFAULTS = Map.of(
            "S", "Ambient",
            "R", "Refrigerated",
            "D", "Dry storage",
            "F", "Frozen",
            "C", "Refrigerated",
            "N", "Non-refrigerated");

    private List<ConversationalCommerceClient.SuggestedAnswer> applyStorageTypeDisplayMapping(
            List<ConversationalCommerceClient.SuggestedAnswer> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        var storageMap = config.getAttributeDisplayMapping() != null
                ? config.getAttributeDisplayMapping().get("storageType")
                : null;
        List<ConversationalCommerceClient.SuggestedAnswer> out = new ArrayList<>(list.size());
        for (ConversationalCommerceClient.SuggestedAnswer sa : list) {
            String v = sa.value();
            if (v == null) {
                out.add(sa);
                continue;
            }
            String display = (storageMap != null && storageMap.containsKey(v))
                    ? storageMap.get(v)
                    : STORAGE_DISPLAY_DEFAULTS.get(v);
            if (display == null || display.equals(sa.displayText())) {
                out.add(sa);
            } else {
                out.add(new ConversationalCommerceClient.SuggestedAnswer(display, v));
            }
        }
        return out;
    }

    private String buildBrandFilterWhenApplicable(
            String canonicalValue,
            List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers,
            Map<String, Object> context) {
        if (canonicalValue == null || canonicalValue.isBlank()) {
            return null;
        }
        String trimmed = canonicalValue.trim();
        if (trimmed.length() < 2 || trimmed.length() > 50) {
            return null;
        }
        if (NON_BRAND_VALUES.contains(trimmed.toUpperCase())) {
            return null;
        }
        boolean fromSelection = false;
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        if (prevList != null) {
            for (var m : prevList) {
                if (trimmed.equals(m.getOrDefault("value", "").trim())
                        || trimmed.equalsIgnoreCase(m.getOrDefault("displayText", "").trim())) {
                    fromSelection = true;
                    break;
                }
            }
        }
        boolean matchesCurrentSuggested = suggestedAnswers != null && suggestedAnswers.stream()
                .anyMatch(sa -> trimmed.equals(sa.value()));
        if (fromSelection || matchesCurrentSuggested) {
            String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"");
            return "brands: ANY(\"" + escaped + "\")";
        }
        return null;
    }

    private static String combineRetailFilters(String previous, String next) {
        String a = previous != null ? previous.trim() : "";
        String b = next != null ? next.trim() : "";
        if (a.isEmpty()) {
            return b.isEmpty() ? null : b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + " AND " + b;
    }

    private static String sanitizeSessionProductFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        if ("in_memory_pool_refine".equals(filter.trim())) {
            return null;
        }
        return filter.trim();
    }
}
