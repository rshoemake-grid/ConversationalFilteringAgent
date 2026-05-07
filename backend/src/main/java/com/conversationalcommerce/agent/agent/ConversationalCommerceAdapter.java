package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.gemini.ClarifyingQuestionGenerator;
import com.conversationalcommerce.agent.orchestration.ContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that wraps the GCP Conversational Commerce API as a ConversationalAgent.
 */
@Component
public class ConversationalCommerceAdapter implements ConversationalAgent {

    public static final String AGENT_ID = "conversational-commerce";
    private static final String FALLBACK_CLARIFY = "I found many %s matching your search. To narrow it down, could you tell me more? " +
            "For example: What type or form? (e.g. raw, cooked, peeled — or style, color) Any size or price range?";

    private static final Logger log = LoggerFactory.getLogger(ConversationalCommerceAdapter.class);

    private final ConversationalCommerceClient client;
    private final RetailSearchClient searchClient;
    private final ProductEnrichmentService enrichmentService;
    private final ConversationalCommerceConfig config;
    private final Optional<ClarifyingQuestionGenerator> clarifyingGenerator;

    public ConversationalCommerceAdapter(
            ConversationalCommerceClient client,
            RetailSearchClient searchClient,
            ProductEnrichmentService enrichmentService,
            ConversationalCommerceConfig config,
            Optional<ClarifyingQuestionGenerator> clarifyingGenerator) {
        this.client = client;
        this.searchClient = searchClient;
        this.enrichmentService = enrichmentService;
        this.config = config;
        this.clarifyingGenerator = clarifyingGenerator != null ? clarifyingGenerator : Optional.empty();
    }

    @Override
    public AgentResponse sendMessage(String conversationId, String message, Map<String, Object> context) {
        String visitorId = getVisitorId(context);
        String productPageToken = (String) context.get("productPageToken");
        String prevRefinedForLoadMore = (String) context.get("previousRefinedQuery");
        String prevFilter = (String) context.get("previousProductFilter");
        Integer productPageSizeOverride = context.get("productPageSize") instanceof Number n ? n.intValue() : null;

        // Load more: skip conversational API, fetch next page directly
        if (productPageToken != null && !productPageToken.isBlank() && prevRefinedForLoadMore != null && !prevRefinedForLoadMore.isBlank()) {
            try {
                SearchResult sr = searchClient.searchWithPagination(
                        config.placement(), config.branch(), prevRefinedForLoadMore.trim(), visitorId,
                        prevFilter != null && !prevFilter.isBlank() ? prevFilter : null,
                        productPageToken,
                        productPageSizeOverride);
                var prods = enrichmentService.enrich(sr.products());
                String countText = prods.isEmpty() ? "No more products." : (prods.size() == 1 ? "1 more product." : prods.size() + " more products.");
                long totalSize = sr.totalSize();
                boolean totalSizeIsApproximate = false;
                if (totalSize < 0 && !prods.isEmpty()) {
                    int pageSize = getEffectivePageSize(productPageSizeOverride);
                    totalSize = prods.size() + (sr.nextPageToken() != null && !sr.nextPageToken().isBlank() ? pageSize : 0);
                    totalSizeIsApproximate = true;
                }
                return AgentResponse.builder()
                        .text(countText)
                        .conversationId(conversationId != null ? conversationId : "")
                        .refinedQuery(prevRefinedForLoadMore.trim())
                        .products(prods)
                        .queryType("SIMPLE_PRODUCT_SEARCH")
                        .source("app")
                        .productTotalSize(totalSize >= 0 ? totalSize : -1)
                        .productTotalSizeIsApproximate(totalSizeIsApproximate)
                        .productNextPageToken(sr.nextPageToken())
                        .productFilter(prevFilter)
                        .build();
            } catch (Exception e) {
                log.warn("Load more failed: {}", e.getMessage());
            }
        }

        String imageBase64 = (String) context.get("imageBase64");
        String userInput = (message != null && !message.isBlank()) ? message.trim()
                : (imageBase64 != null ? "Find products similar to this image" : "");
        final String query = expandShortAttributeValue(userInput, context);
        final String originalUserInput = userInput;

        var request = new ConversationalCommerceClient.ConversationalCommerceRequest(
                config.placement(),
                config.branch(),
                query,
                visitorId,
                conversationId != null ? conversationId : "",
                imageBase64
        );

        var result = client.search(request);

        List<AgentResponse.ProductResult> products = List.of();
        SearchResult searchResult = null;
        String productFilterUsed = null;
        String searchQuery = result.refinedQuery();
        boolean usedNoPreferenceRecovery = false;
        boolean usedStorageTypeRecovery = false;
        String storageTypeFilter = null;

        // When user said "Any"/"no preference" and API returns empty refinedQuery, use previous refined query to search.
        if (isNoPreference(query) && (searchQuery == null || searchQuery.isEmpty())) {
            String prevRefined = (String) context.get("previousRefinedQuery");
            if (prevRefined != null && !prevRefined.isBlank()) {
                searchQuery = prevRefined.trim();
                usedNoPreferenceRecovery = true;
                log.debug("No-preference recovery (queryType={}): using previousRefinedQuery \"{}\"", result.queryType(), searchQuery);
            }
        }

        // When user selected a storage-type suggested answer (S, R, D), use previousRefinedQuery for product search
        // and add storage filter. Avoids treating "DRY_STORAGE" as search text (matches "dry storage" in names).
        if (!usedNoPreferenceRecovery && isStorageTypeSelection(query, originalUserInput, context)) {
            String prevRefined = (String) context.get("previousRefinedQuery");
            if (prevRefined != null && !prevRefined.isBlank()) {
                searchQuery = prevRefined.trim();
                storageTypeFilter = buildStorageTypeFilter(query);
                usedStorageTypeRecovery = true;
                log.debug("Storage-type selection recovery: using previousRefinedQuery \"{}\" + filter {}", searchQuery, storageTypeFilter);
            }
        }

        if (searchQuery != null && !searchQuery.isEmpty()) {
            try {
                String filter = buildBrandFilterWhenApplicable(query, result.suggestedAnswers(), context);
                if (storageTypeFilter != null) {
                    filter = (filter != null && !filter.isBlank())
                            ? filter + " AND " + storageTypeFilter
                            : storageTypeFilter;
                }
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
                log.warn("Product search failed (may use gRPC; try transport=rest for full REST): {}", e.getMessage());
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
            // When storage-type recovery returns no products, fall through to previous-agent fallback below
        }
        List<AgentResponse.ProductResult> productsToReturn = products;
        int productCountThreshold = config.productCountThreshold();
        if (products.isEmpty()) {
            log.info("Products empty; userQueryType={}", result.queryType() != null ? result.queryType() : "(none)");
        }
        String refinedQuery = ((usedNoPreferenceRecovery || usedStorageTypeRecovery) && searchQuery != null) ? searchQuery : result.refinedQuery();
        boolean isSearchingFallback = text.startsWith("Searching for:");
        boolean useConvoCommerceOnly = "convo_commerce".equals(context.get("orchestrationMode"));
        boolean hasRefinedQuery = refinedQuery != null && !refinedQuery.isEmpty();

        List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers = result.suggestedAnswers() != null ? result.suggestedAnswers() : List.of();
        String responseSource = result.source() != null ? result.source() : "agent";

        // When only one suggested answer is given, auto-run it instead of asking
        boolean usedAutoRunSingleSuggestion = false;
        if (suggestedAnswers.size() == 1 && "SIMPLE_PRODUCT_SEARCH".equals(result.queryType())) {
            String baseQuery = (String) context.get("previousRefinedQuery");
            if (baseQuery == null || baseQuery.isBlank()) baseQuery = result.refinedQuery();
            if (baseQuery != null && !baseQuery.isBlank()) {
                String soleValue = suggestedAnswers.get(0).value();
                if (soleValue != null && !soleValue.isBlank()) {
                    var autoResult = tryAutoRunSingleSuggestion(baseQuery.trim(), soleValue, result.suggestedAnswers(), context, visitorId);
                    if (autoResult != null) {
                        products = autoResult.products();
                        productsToReturn = autoResult.products();
                        text = autoResult.text();
                        suggestedAnswers = List.of();
                        responseSource = "app";
                        usedAutoRunSingleSuggestion = true;
                    }
                }
            }
        }

        // When SIMPLE_PRODUCT_SEARCH or storage-type recovery, no products: show previous question with suggested answers minus the one tried
        boolean simpleProductSearchNoProducts = "SIMPLE_PRODUCT_SEARCH".equals(result.queryType())
                && products.isEmpty()
                && (text == null || text.isEmpty() || text.startsWith("Searching for:"));
        boolean storageTypeRecoveryNoProducts = usedStorageTypeRecovery && products.isEmpty();
        boolean usedPreviousAgentFallback = false;
        if (simpleProductSearchNoProducts || storageTypeRecoveryNoProducts) {
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
                    : (suggestedAnswers != null ? suggestedAnswers.stream()
                            .filter(sa -> !originalUserInput.trim().equals(sa.value() != null ? sa.value().trim() : ""))
                            .toList() : List.of());

            // When only one option remains, auto-run it instead of re-asking
            if (storageTypeRecoveryNoProducts && remaining.size() == 1 && prevText != null && !prevText.isBlank()) {
                String prevRefined = (String) context.get("previousRefinedQuery");
                if (prevRefined != null && !prevRefined.isBlank()) {
                    String soleValue = remaining.get(0).value();
                    // For stockType (GCP), use S/R/D directly; for storageType catalogs use canonical (AMBIENT etc.)
                    String filterValue = ("S".equals(soleValue.trim()) || "R".equals(soleValue.trim()) || "D".equals(soleValue.trim()))
                            ? soleValue.trim()
                            : expandStorageTypeValue(soleValue);
                    if (filterValue != null) {
                        try {
                            String stFilter = buildStorageTypeFilter(filterValue);
                            List<AgentResponse.ProductResult> autoProducts = searchClient.search(
                                    config.placement(), config.branch(), prevRefined.trim(), visitorId, stFilter);
                            autoProducts = enrichmentService.enrich(autoProducts);
                            products = autoProducts;
                            productsToReturn = autoProducts;
                            if (!autoProducts.isEmpty()) {
                                int n = autoProducts.size();
                                text = n == 1 ? "I found 1 product matching your request." : "I found " + n + " products matching your request.";
                            } else {
                                text = "No products found.";
                            }
                            suggestedAnswers = List.of();
                            responseSource = "app";
                            usedPreviousAgentFallback = true;
                        } catch (Exception e) {
                            log.warn("Auto-run last storage option failed: {}", e.getMessage());
                        }
                    }
                }
            }

            if (!usedPreviousAgentFallback && prevText != null && !prevText.isBlank()) {
                boolean alreadyHasPrefix = prevText != null && prevText.contains("No products found for that option.");
                text = (storageTypeRecoveryNoProducts && !alreadyHasPrefix)
                        ? "No products found for that option.\n\n" + prevText
                        : prevText;
                suggestedAnswers = remaining;
                responseSource = "app";
                usedPreviousAgentFallback = true;
            }
        }
        boolean agentProvidedClarifying = text != null && !text.isEmpty() && !text.startsWith("Searching for:")
                && text.contains("?");
        String clarifyingQuestion = null; // Shown after products in UI
        if (useConvoCommerceOnly) {
            // Approach A: Pass through agent response as-is; no app overrides
            // When agent provided a clarifying question, show it with products (don't clear)
            // Never clear products when user said "Any" and we recovered with previousRefinedQuery
            if (!products.isEmpty() && products.size() > productCountThreshold && !usedNoPreferenceRecovery && !usedStorageTypeRecovery && !usedAutoRunSingleSuggestion && !agentProvidedClarifying) {
                productsToReturn = List.of();
            } else if (!products.isEmpty() && isSearchingFallback) {
                // Replace "Searching for: X" placeholder with a proper count when we have products
                int n = products.size();
                String countPhrase = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                if (agentProvidedClarifying) {
                    clarifyingQuestion = text;
                    text = countPhrase;
                } else {
                    text = countPhrase;
                }
                responseSource = "app";
            } else if (!products.isEmpty() && agentProvidedClarifying && !isSearchingFallback
                    && !usedNoPreferenceRecovery && !usedStorageTypeRecovery) {
                // Agent provided clarifying question; count in text, question shown after products
                int n = products.size();
                String countPhrase = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                clarifyingQuestion = text;
                text = countPhrase;
                responseSource = "app";
            } else if (products.isEmpty() && hasRefinedQuery && !usedPreviousAgentFallback) {
                // Always show "No products found" when search returns empty. Include agent context if meaningful (not placeholder).
                // Skip if we already handled via previous-agent fallback above.
                boolean hasMeaningfulAgentText = text != null && !text.isEmpty() && !text.startsWith("Searching for:");
                text = (hasMeaningfulAgentText ? text + "\n\n" : "") + "No products found.";
                responseSource = "app";
            }
        }
        if (!useConvoCommerceOnly) {
            // Approach B: Use our clarifying logic when many products; when agent provided one, show it with products
            if (!products.isEmpty() && products.size() > productCountThreshold && !usedNoPreferenceRecovery && !usedStorageTypeRecovery && !usedAutoRunSingleSuggestion) {
                String categoryHint = refinedQuery != null ? refinedQuery : "products";
                int productCount = products.size();
                int n = products.size();
                String countPhrase = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                String clarifyingText = agentProvidedClarifying
                        ? text
                        : (clarifyingGenerator
                                .map(gen -> gen.generate(categoryHint, productCount))
                                .filter(t -> t != null && !t.isBlank())
                                .orElse(FALLBACK_CLARIFY.formatted(categoryHint)));
                clarifyingQuestion = clarifyingText;
                text = countPhrase;
                productsToReturn = products;
                responseSource = "app";
            } else if (!products.isEmpty()) {
                if (!usedNoPreferenceRecovery && !usedStorageTypeRecovery && !usedAutoRunSingleSuggestion) {
                    int n = products.size();
                    String countPhrase = n == 1
                            ? "I found 1 product matching your request."
                            : "I found " + n + " products matching your request.";
                    if (!text.isEmpty() && !isSearchingFallback && text.contains("?")) {
                        clarifyingQuestion = text;
                        text = countPhrase;
                    } else {
                        text = (text.isEmpty() || isSearchingFallback) ? countPhrase : countPhrase + "\n\n" + text;
                    }
                }
                responseSource = "app";
            } else if (products.isEmpty() && hasRefinedQuery && !usedPreviousAgentFallback) {
                // Always show "No products found" when search returns empty. Include agent context if meaningful (not placeholder).
                boolean hasMeaningfulAgentText = text != null && !text.isEmpty() && !text.startsWith("Searching for:");
                text = (hasMeaningfulAgentText ? text + "\n\n" : "") + "No products found.";
                responseSource = "app";
            }
        }

        if (suggestedAnswers.isEmpty() && ((text != null && text.contains("?")) || (clarifyingQuestion != null && clarifyingQuestion.contains("?")))) {
            suggestedAnswers = List.of(new ConversationalCommerceClient.SuggestedAnswer("Any", "ANY"));
        }
        suggestedAnswers = applyStorageTypeDisplayMapping(suggestedAnswers);
        // GCP may echo the same storage chips (S/R/D) in the conversational response even after the user
        // picked one; we already applied that filter for product search — drop redundant storage suggestions.
        if (usedStorageTypeRecovery && productsToReturn != null && !productsToReturn.isEmpty()) {
            suggestedAnswers = removeStorageTypeSuggestions(suggestedAnswers);
        }
        // Ensure no-preference/storage-type recovery always returns products when we have them
        if ((usedNoPreferenceRecovery || usedStorageTypeRecovery) && !products.isEmpty()) {
            productsToReturn = products;
            responseSource = "app";
            log.info("{}: returning {} products", usedStorageTypeRecovery ? "Storage-type recovery" : "No-preference recovery", productsToReturn.size());
        }
        long totalSize = searchResult != null ? searchResult.totalSize() : -1;
        String nextPageToken = searchResult != null ? searchResult.nextPageToken() : null;
        boolean totalSizeIsApproximate = false;
        if (totalSize < 0 && productsToReturn != null && !productsToReturn.isEmpty()) {
            int pageSize = getEffectivePageSize(productPageSizeOverride);
            int pagesEstimate = (int) Math.ceil((double) productsToReturn.size() / pageSize)
                    + (nextPageToken != null && !nextPageToken.isBlank() ? 1 : 0);
            totalSize = Math.max(productsToReturn.size(), (long) pagesEstimate * pageSize);
            totalSizeIsApproximate = true;
        }
        // Normalize "I found N products" to "Showing N of Y products" when we have totalSize
        if (totalSize >= 0 && productsToReturn != null && !productsToReturn.isEmpty()
                && text != null && text.matches("I found \\d+ product(s)? matching your request\\.")) {
            String prefix = totalSizeIsApproximate ? "at least " : "";
            text = productsToReturn.size() == 1
                    ? "Showing 1 of " + prefix + totalSize + " product"
                    : "Showing " + productsToReturn.size() + " of " + prefix + totalSize + " products";
        }
        return AgentResponse.builder()
                .text(text)
                .conversationId(result.conversationId())
                .refinedQuery(refinedQuery)
                .products(productsToReturn)
                .queryType(result.queryType())
                .source(responseSource)
                .rawResponse(result.rawResponse())
                .suggestedAnswers(suggestedAnswers)
                .productTotalSize(totalSize)
                .productTotalSizeIsApproximate(totalSizeIsApproximate)
                .productNextPageToken(nextPageToken)
                .productFilter(productFilterUsed)
                .clarifyingQuestion(clarifyingQuestion)
                .build();
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    private String getVisitorId(Map<String, Object> context) {
        return ContextUtils.getVisitorId(context, config.defaultVisitorId());
    }

    private int getEffectivePageSize(Integer override) {
        if (override != null && override > 0) return override;
        return config.productSearchPageSize();
    }

    /** Expand short codes (e.g. F, C) to canonical values before sending to GCP to avoid RETAIL_IRRELEVANT. */
    private String expandShortAttributeValue(String query, Map<String, Object> context) {
        if (query == null || query.isBlank()) return query;
        String trimmed = query.trim();

        // 1. Try previousSuggestedAnswers: if user input matches displayText, use value (handles display text from click)
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        String resolved = trimmed;
        if (prevList != null) {
            for (var m : prevList) {
                String displayText = m.getOrDefault("displayText", "").trim();
                String value = m.getOrDefault("value", "").trim();
                if (!displayText.isEmpty() && trimmed.equalsIgnoreCase(displayText) && !value.equals(trimmed)) {
                    log.debug("Expanding user input \"{}\" to value \"{}\" from previous suggested answer", trimmed, value);
                    resolved = value;
                    break;
                }
            }
        }

        // 2. Run through attribute-value-expansion (e.g. C->REFRIGERATED, brands with spaces) - chain so short codes from step 1 get canonical
        var expansion = config.getAttributeValueExpansion();
        if (expansion != null) {
            for (var attrEntry : expansion.entrySet()) {
                if (attrEntry.getValue() == null) continue;
                String canonical = findExpansionMatch(attrEntry.getValue(), resolved);
                if (canonical != null && !canonical.equals(resolved)) {
                    log.debug("Expanding \"{}\" to \"{}\" for attribute {}", resolved, canonical, attrEntry.getKey());
                    return canonical;
                }
            }
        }

        return resolved;
    }

    private static String findExpansionMatch(java.util.Map<String, String> mapping, String input) {
        if (input == null || mapping == null) return null;
        if (mapping.containsKey(input)) return mapping.get(input);
        for (var e : mapping.entrySet()) {
            if (e.getKey().equalsIgnoreCase(input)) return e.getValue();
        }
        return null;
    }

    private static final java.util.Set<String> STORAGE_TYPE_VALUES = java.util.Set.of(
            "FROZEN", "REFRIGERATED", "AMBIENT", "DRY_STORAGE", "F", "C", "S", "R", "D"
    );

    private static final java.util.Set<String> NON_BRAND_VALUES = java.util.Set.of(
            "FROZEN", "REFRIGERATED", "AMBIENT", "DRY_STORAGE", "COLD", "F", "C", "S", "R", "D", "ANY"
    );

    private static final java.util.Set<String> NO_PREFERENCE_PHRASES = java.util.Set.of(
            "ANY", "NO", "NONE", "NOPREFERENCE", "DON'T CARE", "DONT CARE", "DOESN'T MATTER",
            "DOESNT MATTER", "WHATEVER", "I DON'T CARE", "IDC"
    );

    private static boolean isNoPreference(String input) {
        if (input == null || input.isBlank()) return false;
        String n = input.trim().toUpperCase().replaceAll("\\s+", " ");
        if (NO_PREFERENCE_PHRASES.contains(n)) return true;
        if (n.replace("'", "").replace(" ", "").equals("NOPREFERENCE")) return true;
        return n.equals("NO PREFERENCE") || n.startsWith("NO PREFERENCE");
    }

    /** True when user selected a storage-type suggested answer (S, R, D) that expanded to a canonical storage value. */
    private static boolean isStorageTypeSelection(String expandedQuery, String originalUserInput, Map<String, Object> context) {
        if (expandedQuery == null || expandedQuery.isBlank() || !STORAGE_TYPE_VALUES.contains(expandedQuery.trim().toUpperCase()))
            return false;
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        if (prevList == null) return false;
        String trimmed = originalUserInput != null ? originalUserInput.trim() : "";
        for (var m : prevList) {
            String displayText = m.getOrDefault("displayText", "").trim();
            String value = m.getOrDefault("value", "").trim();
            if (trimmed.equalsIgnoreCase(displayText) || trimmed.equals(value)) return true;
        }
        return false;
    }

    /** Build filter for stock/storage type. Uses config attribute (stockType default); value is S/R/D for stockType catalogs. */
    private String buildStorageTypeFilter(String value) {
        if (value == null || value.isBlank()) return null;
        String attr = config.stockTypeFilterAttribute();
        String escaped = value.trim().replace("\\", "\\\\").replace("\"", "\\\"");
        return "attributes." + attr + ": ANY(\"" + escaped + "\")";
    }

    private static final java.util.Map<String, String> STORAGE_DISPLAY_DEFAULTS = java.util.Map.of(
            "S", "Ambient", "R", "Refrigerated", "D", "Dry storage",
            "F", "Frozen", "C", "Refrigerated"
    );

    /** Remove suggestions whose value is a stock/storage code — redundant after storage-type recovery returned products. */
    private static List<ConversationalCommerceClient.SuggestedAnswer> removeStorageTypeSuggestions(
            List<ConversationalCommerceClient.SuggestedAnswer> list) {
        if (list == null || list.isEmpty()) return list;
        return list.stream().filter(sa -> !isStorageSuggestionValue(sa.value())).toList();
    }

    private static boolean isStorageSuggestionValue(String value) {
        if (value == null || value.isBlank()) return false;
        return STORAGE_TYPE_VALUES.contains(value.trim().toUpperCase());
    }

    /** Apply storageType display mapping so S/R/D show as Ambient/Refrigerated/Dry storage. */
    private List<ConversationalCommerceClient.SuggestedAnswer> applyStorageTypeDisplayMapping(List<ConversationalCommerceClient.SuggestedAnswer> list) {
        if (list == null || list.isEmpty()) return list;
        var storageMap = config.getAttributeDisplayMapping() != null ? config.getAttributeDisplayMapping().get("storageType") : null;
        return list.stream()
                .map(sa -> {
                    String v = sa.value();
                    if (v == null) return sa;
                    String display = (storageMap != null && storageMap.containsKey(v)) ? storageMap.get(v) : STORAGE_DISPLAY_DEFAULTS.get(v);
                    if (display == null || display.equals(sa.displayText())) return sa;
                    return new ConversationalCommerceClient.SuggestedAnswer(display, v);
                })
                .toList();
    }

    /** Try to run a search for a single suggestion; returns null if not applicable or search fails. */
    private AutoRunResult tryAutoRunSingleSuggestion(String baseQuery, String value, List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers,
                                                     Map<String, Object> context, String visitorId) {
        try {
            String normalized = (value != null ? value.trim().toUpperCase() : "");
            if (STORAGE_TYPE_VALUES.contains(normalized) || (value != null && STORAGE_TYPE_VALUES.contains(value.trim()))) {
                String filterValue = ("S".equals(normalized) || "R".equals(normalized) || "D".equals(normalized)) ? value.trim() : expandStorageTypeValue(value);
                if (filterValue == null) filterValue = value;
                String filter = buildStorageTypeFilter(filterValue);
                var prods = searchClient.search(config.placement(), config.branch(), baseQuery, visitorId, filter);
                prods = enrichmentService.enrich(prods);
                String msg = prods.isEmpty() ? "No products found." : (prods.size() == 1 ? "I found 1 product matching your request." : "I found " + prods.size() + " products matching your request.");
                return new AutoRunResult(prods, msg);
            }
            String brandFilter = buildBrandFilterWhenApplicable(value, suggestedAnswers, context);
            if (brandFilter != null) {
                var prods = searchClient.search(config.placement(), config.branch(), baseQuery, visitorId, brandFilter);
                prods = enrichmentService.enrich(prods);
                String msg = prods.isEmpty() ? "No products found." : (prods.size() == 1 ? "I found 1 product matching your request." : "I found " + prods.size() + " products matching your request.");
                return new AutoRunResult(prods, msg);
            }
            String query = (baseQuery + " " + value).trim();
            var prods = searchClient.search(config.placement(), config.branch(), query, visitorId, null);
            prods = enrichmentService.enrich(prods);
            String msg = prods.isEmpty() ? "No products found." : (prods.size() == 1 ? "I found 1 product matching your request." : "I found " + prods.size() + " products matching your request.");
            return new AutoRunResult(prods, msg);
        } catch (Exception e) {
            log.warn("Auto-run single suggestion failed: {}", e.getMessage());
            return null;
        }
    }

    private record AutoRunResult(List<AgentResponse.ProductResult> products, String text) {}

    /** Expand short storage code (S, R, D) or display text to canonical value for filters. D/R/S are suggested-answer values sent to GCP as-is. */
    private String expandStorageTypeValue(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim().toUpperCase();
        return switch (trimmed) {
            case "D" -> "DRY_STORAGE";
            case "R" -> "REFRIGERATED";
            case "S" -> "AMBIENT";
            default -> {
                var expansion = config.getAttributeValueExpansion();
                if (expansion == null) yield value.trim();
                var storageMap = expansion.get("storageType");
                if (storageMap == null) yield value.trim();
                String resolved = findExpansionMatch(storageMap, value.trim());
                if (resolved != null && ("D".equals(resolved) || "R".equals(resolved) || "S".equals(resolved)))
                    yield switch (resolved) { case "D" -> "DRY_STORAGE"; case "R" -> "REFRIGERATED"; case "S" -> "AMBIENT"; default -> resolved; };
                yield resolved != null ? resolved : value.trim();
            }
        };
    }

    /** Add brand filter only when user selected a suggested answer that looks like a brand (not storage type, not a search term like "shrimp"). */
    private String buildBrandFilterWhenApplicable(String canonicalValue, List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers, Map<String, Object> context) {
        if (canonicalValue == null || canonicalValue.isBlank()) return null;
        String trimmed = canonicalValue.trim();
        if (trimmed.length() < 2 || trimmed.length() > 50) return null;
        if (NON_BRAND_VALUES.contains(trimmed.toUpperCase())) return null;
        boolean fromSelection = false;
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        if (prevList != null) {
            for (var m : prevList) {
                if (trimmed.equals(m.getOrDefault("value", "").trim()) || trimmed.equalsIgnoreCase(m.getOrDefault("displayText", "").trim())) {
                    fromSelection = true;
                    break;
                }
            }
        }
        boolean matchesCurrentSuggested = suggestedAnswers != null && suggestedAnswers.stream().anyMatch(sa -> trimmed.equals(sa.value()));
        if ((fromSelection || matchesCurrentSuggested)) {
            String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"");
            return "brands: ANY(\"" + escaped + "\")";
        }
        return null;
    }
}
